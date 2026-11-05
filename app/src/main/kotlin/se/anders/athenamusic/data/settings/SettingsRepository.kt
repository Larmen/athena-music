package se.anders.athenamusic.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.anders.athenamusic.data.launcher.LauncherAppearance
import se.anders.athenamusic.domain.ArtStyle

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** FR-061: follow the system by default; the user can force light or dark. Names match Athena Launcher. */
enum class ThemeMode { System, Light, Dark }

/** FR-061: the same twelve typefaces as Athena Launcher, with the same names so values map one-to-one. */
enum class TypefaceChoice {
    System, Condensed, Serif, Monospace, Cursive, Casual,

    /** Bundled open-licence fonts; their licences are listed in [se.anders.athenamusic.data.licences.Licences]. */
    Vt323, PlexMono, SpaceMono, JetBrainsMono, Inter, Atkinson,
}

/** FR-061: text size on top of the system font size (same range and default as the launcher). */
const val DEFAULT_TEXT_SCALE_PERCENT = 100
const val MIN_TEXT_SCALE_PERCENT = 80
const val MAX_TEXT_SCALE_PERCENT = 140
const val TEXT_SCALE_STEP = 10

fun clampTextScale(percent: Int): Int = percent.coerceIn(MIN_TEXT_SCALE_PERCENT, MAX_TEXT_SCALE_PERCENT)

/** The music app's own (local) settings. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val typeface: TypefaceChoice = TypefaceChoice.System,
    val textScalePercent: Int = DEFAULT_TEXT_SCALE_PERCENT,
    /** FR-042: how cover art is drawn. */
    val artStyle: ArtStyle = ArtStyle.Grayscale,
    /** FR-062: take theme, typeface and text size from Athena Launcher when it is available. */
    val followLauncher: Boolean = true,
    /** FR-002: the signed-in account. The password itself lives in the secret store, referenced by [passwordRef]. */
    val serverUrl: String = "",
    val username: String = "",
    val passwordRef: String = "",
    /** The launcher's values as last read, so the first frame after a cold start already looks right (FR-062). */
    val launcherCache: LauncherAppearance? = null,
)

/** User preferences, stored locally in DataStore (spec §6). Unknown or damaged values fall back to defaults. */
class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeMode.System,
            typeface = TypefaceChoice.entries.firstOrNull { it.name == prefs[TYPEFACE] } ?: TypefaceChoice.System,
            textScalePercent = clampTextScale(prefs[TEXT_SCALE] ?: DEFAULT_TEXT_SCALE_PERCENT),
            artStyle = ArtStyle.entries.firstOrNull { it.name == prefs[ART_STYLE] } ?: ArtStyle.Grayscale,
            followLauncher = prefs[FOLLOW_LAUNCHER] ?: true,
            serverUrl = prefs[SERVER_URL].orEmpty(),
            username = prefs[USERNAME].orEmpty(),
            passwordRef = prefs[PASSWORD_REF].orEmpty(),
            launcherCache = prefs[CACHE_SCALE]?.let { scale ->
                LauncherAppearance(
                    themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[CACHE_THEME] },
                    typeface = TypefaceChoice.entries.firstOrNull { it.name == prefs[CACHE_TYPEFACE] },
                    textScalePercent = clampTextScale(scale),
                )
            },
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    suspend fun setTypeface(choice: TypefaceChoice) { store.edit { it[TYPEFACE] = choice.name } }
    suspend fun setTextScalePercent(percent: Int) { store.edit { it[TEXT_SCALE] = clampTextScale(percent) } }
    suspend fun setArtStyle(style: ArtStyle) { store.edit { it[ART_STYLE] = style.name } }
    suspend fun setFollowLauncher(value: Boolean) { store.edit { it[FOLLOW_LAUNCHER] = value } }

    suspend fun setAccount(serverUrl: String, username: String, passwordRef: String) {
        store.edit { it[SERVER_URL] = serverUrl; it[USERNAME] = username; it[PASSWORD_REF] = passwordRef }
    }

    suspend fun cacheLauncher(a: LauncherAppearance?) {
        store.edit {
            if (a == null || a.textScalePercent == null) {
                it.remove(CACHE_THEME); it.remove(CACHE_TYPEFACE); it.remove(CACHE_SCALE)
            } else {
                a.themeMode?.let { m -> it[CACHE_THEME] = m.name } ?: it.remove(CACHE_THEME)
                a.typeface?.let { t -> it[CACHE_TYPEFACE] = t.name } ?: it.remove(CACHE_TYPEFACE)
                it[CACHE_SCALE] = a.textScalePercent
            }
        }
    }

    /** Keeps the server address and user name (to prefill sign-in) but forgets the password. */
    suspend fun clearPassword() { store.edit { it.remove(PASSWORD_REF) } }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val TYPEFACE = stringPreferencesKey("typeface")
        val TEXT_SCALE = intPreferencesKey("text_scale_percent")
        val ART_STYLE = stringPreferencesKey("art_style")
        val FOLLOW_LAUNCHER = booleanPreferencesKey("follow_launcher")
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val CACHE_THEME = stringPreferencesKey("launcher_cache_theme")
        val CACHE_TYPEFACE = stringPreferencesKey("launcher_cache_typeface")
        val CACHE_SCALE = intPreferencesKey("launcher_cache_scale")
        val PASSWORD_REF = stringPreferencesKey("password_ref")
    }
}
