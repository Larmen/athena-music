package se.anders.athenamusic.domain

import se.anders.athenamusic.data.launcher.LauncherAppearance
import se.anders.athenamusic.data.settings.Settings
import se.anders.athenamusic.data.settings.ThemeMode
import se.anders.athenamusic.data.settings.TypefaceChoice

/** The theme, typeface and text size actually drawn, and whether they come from Athena Launcher. */
data class EffectiveAppearance(
    val themeMode: ThemeMode,
    val typeface: TypefaceChoice,
    val textScalePercent: Int,
    val followingLauncher: Boolean,
)

/**
 * FR-062: with "Follow launcher" on and the launcher available, its values win, field by field (a field the
 * launcher did not give falls back to the local value). Otherwise the local values apply. The local values are
 * never overwritten by the launcher's, so switching following off brings back the last local choice.
 */
fun effectiveAppearance(settings: Settings, launcher: LauncherAppearance?): EffectiveAppearance {
    if (!settings.followLauncher || launcher == null) return EffectiveAppearance(settings.themeMode, settings.typeface, settings.textScalePercent, false)
    return EffectiveAppearance(
        themeMode = launcher.themeMode ?: settings.themeMode,
        typeface = launcher.typeface ?: settings.typeface,
        textScalePercent = launcher.textScalePercent ?: settings.textScalePercent,
        followingLauncher = true,
    )
}
