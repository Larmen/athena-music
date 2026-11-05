package se.anders.athenamusic.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import se.anders.athenamusic.data.secrets.SecretStore
import se.anders.athenamusic.data.server.ServerErrorKind
import se.anders.athenamusic.data.server.ServerException
import se.anders.athenamusic.data.server.SubsonicClient
import se.anders.athenamusic.data.server.normalizeServerUrl
import se.anders.athenamusic.data.settings.SettingsRepository

/** Where the sign-in stands (FR-001..004). */
sealed interface AccountState {
    data object Loading : AccountState
    data class SignedOut(val serverUrl: String, val username: String) : AccountState
    class SignedIn(val serverUrl: String, val username: String, val client: SubsonicClient) : AccountState
}

/** Holds the signed-in account: address and user name in settings, the password only in the encrypted secret store. */
class AccountRepository(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val http: OkHttpClient,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AccountState>(AccountState.Loading)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        scope.launch {
            val s = settings.settings.first()
            val password = if (s.passwordRef.isNotEmpty()) withContext(Dispatchers.IO) { secrets.get(s.passwordRef) } else null
            _state.value = if (s.serverUrl.isNotEmpty() && s.username.isNotEmpty() && password != null) {
                AccountState.SignedIn(s.serverUrl, s.username, SubsonicClient(http, s.serverUrl, s.username, password))
            } else {
                AccountState.SignedOut(s.serverUrl, s.username)
            }
        }
    }

    /** Checks the credentials with `ping` and only then stores them (FR-001). Throws [ServerException]. */
    suspend fun signIn(serverInput: String, username: String, password: String) {
        val url = normalizeServerUrl(serverInput) ?: throw ServerException(ServerErrorKind.InvalidUrl)
        val client = SubsonicClient(http, url, username.trim(), password)
        client.ping()
        withContext(Dispatchers.IO) {
            val old = settings.settings.first().passwordRef
            val ref = secrets.put(password)
            settings.setAccount(url, username.trim(), ref)
            if (old.isNotEmpty()) secrets.delete(old)
        }
        _state.value = AccountState.SignedIn(url, username.trim(), client)
    }

    /** FR-003 / FR-004: forget the password; address and user name stay to prefill the sign-in form. */
    suspend fun signOut() {
        val s = settings.settings.first()
        withContext(Dispatchers.IO) { if (s.passwordRef.isNotEmpty()) secrets.delete(s.passwordRef) }
        settings.clearPassword()
        _state.value = AccountState.SignedOut(s.serverUrl, s.username)
    }
}
