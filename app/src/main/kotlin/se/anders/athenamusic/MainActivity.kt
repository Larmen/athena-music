package se.anders.athenamusic

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.anders.athenamusic.data.account.AccountState
import se.anders.athenamusic.data.server.ServerErrorKind
import androidx.compose.runtime.CompositionLocalProvider
import se.anders.athenamusic.ui.components.LocalArtStyle
import se.anders.athenamusic.ui.components.LocalArtworkStore
import se.anders.athenamusic.ui.LibraryViewModel
import se.anders.athenamusic.ui.Load
import se.anders.athenamusic.ui.MainViewModel
import se.anders.athenamusic.ui.Screen
import se.anders.athenamusic.ui.library.AlbumScreen
import se.anders.athenamusic.ui.library.ArtistScreen
import se.anders.athenamusic.ui.library.ArtistsScreen
import se.anders.athenamusic.ui.licences.LicencesScreen
import se.anders.athenamusic.ui.player.NowPlayingBar
import se.anders.athenamusic.ui.player.PlayerScreen
import se.anders.athenamusic.ui.settings.SettingsCallbacks
import se.anders.athenamusic.ui.settings.SettingsScreen
import se.anders.athenamusic.ui.signin.SignInScreen
import se.anders.athenamusic.ui.theme.AthenaTheme
import se.anders.athenamusic.ui.theme.useDarkTheme

/** Android 17 local-network permission (plan §3.5); a literal because it is runtime-requested by name. */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    companion object {
        /** Sent by the media notification and lock-screen widget: show the player. */
        const val ACTION_OPEN_PLAYER = "se.anders.athenamusic.OPEN_PLAYER"
    }

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val library: LibraryViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        (application as AthenaMusicApplication).container.player.connect()
        viewModel.refreshLauncher() // the launcher may have been installed or updated since last time
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_PLAYER) viewModel.showPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_PLAYER) viewModel.showPlayer()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as AthenaMusicApplication).container
        val player = container.player

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val launcherAvailable by viewModel.launcherAvailable.collectAsStateWithLifecycle()
            val account by viewModel.account.collectAsStateWithLifecycle()
            val stack by viewModel.stack.collectAsStateWithLifecycle()
            val signInState by viewModel.signIn.collectAsStateWithLifecycle()
            val playerState by player.state.collectAsStateWithLifecycle()

            // The bar icons follow the theme the user chose, which may differ from the system's (FR-060).
            val dark = useDarkTheme(appearance.themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            // Notification permission is asked for just in time, the first time something is played.
            var askedNotifications by rememberSaveable { mutableStateOf(false) }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            fun requestNotificationsOnce() {
                if (askedNotifications || Build.VERSION.SDK_INT < 33) return
                askedNotifications = true
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // NFR-003: ask for local-network access at sign-in, then sign in either way; a refusal shows up as a network error.
            var pendingSignIn by rememberSaveable { mutableStateOf<List<String>?>(null) }
            val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                val args = pendingSignIn
                pendingSignIn = null
                if (args != null) {
                    if (granted) viewModel.signIn(args[0], args[1], args[2]) else viewModel.localNetworkDenied()
                }
            }

            AthenaTheme(appearance.themeMode, appearance.typeface, appearance.textScalePercent) {
              CompositionLocalProvider(LocalArtworkStore provides container.artwork, LocalArtStyle provides settings.artStyle) {
                when (val current = account) {
                    AccountState.Loading -> Box(Modifier.fillMaxSize())
                    is AccountState.SignedOut -> {
                        LaunchedEffect(Unit) { library.clear() }
                        Box(Modifier.fillMaxSize()) {
                            SignInScreen(current.serverUrl, current.username, signInState) { server, user, pass ->
                                if (checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.signIn(server, user, pass)
                                } else {
                                    pendingSignIn = listOf(server, user, pass)
                                    localNetworkLauncher.launch(LOCAL_NETWORK_PERMISSION)
                                }
                            }
                        }
                    }
                    is AccountState.SignedIn -> {
                        val client = current.client
                        val screen = stack.last()
                        BackHandler(enabled = stack.size > 1) { viewModel.back() }
                        val showBar = playerState.trackId != null && screen != Screen.Player && screen != Screen.Settings && screen != Screen.Licences
                        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                            Box(Modifier.weight(1f)) {
                                when (screen) {
                                    Screen.Artists -> {
                                        LaunchedEffect(client) { library.artists(client) }
                                        val load by library.artists.collectAsState()
                                        RejectionWatcher(load) { viewModel.credentialsRejected() }
                                        val query by viewModel.artistQuery.collectAsStateWithLifecycle()
                                        ArtistsScreen(load, query, viewModel::setArtistQuery, viewModel.listState(screen), onRetry = { library.retryArtists(client) }, onOpen = { viewModel.open(Screen.Artist(it.id, it.name)) }, onSettings = { viewModel.open(Screen.Settings) })
                                    }
                                    is Screen.Artist -> {
                                        val load by library.artist(client, screen.id).collectAsState()
                                        RejectionWatcher(load) { viewModel.credentialsRejected() }
                                        ArtistScreen(screen.name, load, viewModel.listState(screen), onRetry = { library.retryArtist(client, screen.id) }, onOpen = { viewModel.open(Screen.Album(it.id, it.name)) }, onBack = viewModel::back)
                                    }
                                    is Screen.Album -> {
                                        val load by library.album(client, screen.id).collectAsState()
                                        RejectionWatcher(load) { viewModel.credentialsRejected() }
                                        AlbumScreen(
                                            screen.name, load, viewModel.listState(screen),
                                            onRetry = { library.retryAlbum(client, screen.id) },
                                            onPlay = { tracks, index -> requestNotificationsOnce(); player.play(client, tracks, index); viewModel.open(Screen.Player) },
                                            onBack = viewModel::back,
                                        )
                                    }
                                    Screen.Player -> PlayerScreen(playerState, viewModel::back, player::togglePlayPause, player::previous, player::next, player::seekTo)
                                    Screen.Settings -> SettingsScreen(
                                        settings, appearance, launcherAvailable,
                                        SettingsCallbacks(
                                            onBack = viewModel::back,
                                            onThemeChange = viewModel::setThemeMode,
                                            onTypefaceChange = viewModel::setTypeface,
                                            onTextScaleChange = viewModel::setTextScale,
                                            onFollowLauncherChange = viewModel::setFollowLauncher,
                                            onArtStyleChange = viewModel::setArtStyle,
                                            onSignOut = { viewModel.signOut() },
                                            onOpenLicences = { viewModel.open(Screen.Licences) },
                                        ),
                                    )
                                    Screen.Licences -> LicencesScreen(onBack = viewModel::back)
                                }
                            }
                            if (showBar) {
                                NowPlayingBar(playerState, onOpen = { viewModel.open(Screen.Player) }, onPlayPause = player::togglePlayPause, onPrevious = player::previous, onNext = player::next)
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

/** FR-004: a refused login on any page sends the user back to sign-in. */
@androidx.compose.runtime.Composable
private fun RejectionWatcher(load: Load<*>, onRejected: () -> Unit) {
    LaunchedEffect(load) { if (load is Load.Failed && load.kind == ServerErrorKind.Auth) onRejected() }
}
