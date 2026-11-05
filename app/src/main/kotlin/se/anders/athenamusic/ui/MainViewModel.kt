package se.anders.athenamusic.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.anders.athenamusic.AthenaMusicApplication
import se.anders.athenamusic.data.account.AccountState
import se.anders.athenamusic.data.launcher.AppearanceSource
import se.anders.athenamusic.data.server.ServerErrorKind
import se.anders.athenamusic.data.server.ServerException
import se.anders.athenamusic.data.settings.Settings
import se.anders.athenamusic.data.settings.SettingsRepository
import se.anders.athenamusic.data.settings.ThemeMode
import se.anders.athenamusic.data.settings.TypefaceChoice
import se.anders.athenamusic.domain.ArtStyle
import se.anders.athenamusic.domain.EffectiveAppearance
import se.anders.athenamusic.data.art.ArtworkStore
import se.anders.athenamusic.domain.effectiveAppearance
import se.anders.athenamusic.data.account.AccountRepository
import se.anders.athenamusic.playback.PlayerController

sealed interface Screen {
    data object Artists : Screen
    data class Artist(val id: String, val name: String) : Screen
    data class Album(val id: String, val name: String) : Screen
    data object Player : Screen
    data object Settings : Screen
    data object Licences : Screen
}

/** What the sign-in form shows after a failed or refused attempt. */
enum class SignInProblem { InvalidUrl, Network, Auth, NotSubsonic, Server, LocalNetwork, Rejected }

data class SignInUiState(val working: Boolean = false, val problem: SignInProblem? = null)

/** Navigation, account and appearance (single activity, plan §2). */
class MainViewModel(
    private val settingsRepo: SettingsRepository,
    private val launcher: AppearanceSource,
    private val accounts: AccountRepository,
    private val player: PlayerController,
    private val artwork: ArtworkStore,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Theme, typeface and text size actually in use: the launcher's while following it, else the local ones (FR-062). */
    val appearance: StateFlow<EffectiveAppearance> = combine(settingsRepo.settings, launcher.appearance) { settings, live -> effectiveAppearance(settings, live ?: settings.launcherCache) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, effectiveAppearance(Settings(), null))

    val launcherAvailable: StateFlow<Boolean> = launcher.unavailable.map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val account: StateFlow<AccountState> = accounts.state

    /** The stack of screens above the artist list; the last one is shown. */
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Artists))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _signIn = MutableStateFlow(SignInUiState())
    val signIn: StateFlow<SignInUiState> = _signIn.asStateFlow()

    init {
        // Remember the launcher's latest values; forget them once the launcher is known to be gone.
        viewModelScope.launch { launcher.appearance.collect { if (it != null) settingsRepo.cacheLauncher(it) } }
        viewModelScope.launch { launcher.unavailable.collect { if (it) settingsRepo.cacheLauncher(null) } }
    }

    fun refreshLauncher() = launcher.refresh()

    /** FR-014: the artist search text; kept while another screen is open. */
    private val _artistQuery = MutableStateFlow("")
    val artistQuery: StateFlow<String> = _artistQuery.asStateFlow()
    fun setArtistQuery(text: String) { _artistQuery.value = text }

    /** FR-015: one list state per open screen, so Back finds the list where it was left. */
    private val listStates = mutableMapOf<Screen, LazyListState>()
    fun listState(screen: Screen): LazyListState = listStates.getOrPut(screen) { LazyListState() }

    fun open(screen: Screen) = _stack.update { it + screen }
    /** Opens the player unless it is already on top (used when the notification is tapped). */
    fun showPlayer() = _stack.update { if (it.last() == Screen.Player) it else it + Screen.Player }
    fun back() = _stack.update {
        if (it.size > 1) {
            listStates.remove(it.last()) // a screen left with Back starts at the top next time (FR-015)
            it.dropLast(1)
        } else it
    }

    fun signIn(server: String, username: String, password: String) {
        if (_signIn.value.working) return
        _signIn.value = SignInUiState(working = true)
        viewModelScope.launch {
            _signIn.value = try {
                accounts.signIn(server, username, password)
                _stack.value = listOf(Screen.Artists)
                SignInUiState()
            } catch (e: ServerException) {
                SignInUiState(problem = e.kind.toProblem())
            }
        }
    }

    /** The user denied the local-network permission (NFR-003). */
    fun localNetworkDenied() { _signIn.value = SignInUiState(problem = SignInProblem.LocalNetwork) }

    fun signOut() = viewModelScope.launch {
        player.stop()
        artwork.clear()
        accounts.signOut()
        resetBrowsing()
    }

    /** FR-004: the server stopped accepting the saved credentials. */
    fun credentialsRejected() = viewModelScope.launch {
        player.stop()
        artwork.clear()
        accounts.signOut()
        resetBrowsing()
        _signIn.value = SignInUiState(problem = SignInProblem.Rejected)
    }

    private fun resetBrowsing() {
        _stack.value = listOf(Screen.Artists)
        listStates.clear()
        _artistQuery.value = ""
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setTypeface(choice: TypefaceChoice) = viewModelScope.launch { settingsRepo.setTypeface(choice) }
    fun setTextScale(percent: Int) = viewModelScope.launch { settingsRepo.setTextScalePercent(percent) }
    fun setArtStyle(style: ArtStyle) = viewModelScope.launch { settingsRepo.setArtStyle(style) }
    fun setFollowLauncher(value: Boolean) = viewModelScope.launch { settingsRepo.setFollowLauncher(value) }

    private fun ServerErrorKind.toProblem() = when (this) {
        ServerErrorKind.InvalidUrl -> SignInProblem.InvalidUrl
        ServerErrorKind.Network -> SignInProblem.Network
        ServerErrorKind.Auth -> SignInProblem.Auth
        ServerErrorKind.NotSubsonic -> SignInProblem.NotSubsonic
        ServerErrorKind.Server -> SignInProblem.Server
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AthenaMusicApplication).container
                MainViewModel(c.settings, c.launcherAppearance, c.account, c.player, c.artwork)
            }
        }
    }
}
