package se.anders.athenamusic.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import se.anders.athenamusic.data.account.AccountRepository
import se.anders.athenamusic.data.account.AccountState
import se.anders.athenamusic.data.art.ArtworkStore
import se.anders.athenamusic.data.launcher.LauncherAppearanceSource
import se.anders.athenamusic.data.secrets.KeystoreSecretStore
import se.anders.athenamusic.data.secrets.SecretStore
import se.anders.athenamusic.data.settings.SettingsRepository
import se.anders.athenamusic.playback.PlayerController

/** Manual dependency container (plan §1). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy { OkHttpClient() }
    val settings: SettingsRepository = SettingsRepository(appContext)
    val secrets: SecretStore by lazy { KeystoreSecretStore(appContext) }
    val account: AccountRepository by lazy { AccountRepository(settings, secrets, http, scope) }
    val artwork: ArtworkStore by lazy { ArtworkStore(appContext, http) { (account.state.value as? AccountState.SignedIn)?.client } }
    val player: PlayerController by lazy { PlayerController(appContext) }
    val launcherAppearance: LauncherAppearanceSource by lazy { LauncherAppearanceSource(appContext) }
}
