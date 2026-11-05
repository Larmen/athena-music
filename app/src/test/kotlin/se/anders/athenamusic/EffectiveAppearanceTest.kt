package se.anders.athenamusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import se.anders.athenamusic.data.launcher.LauncherAppearance
import se.anders.athenamusic.data.settings.Settings
import se.anders.athenamusic.data.settings.ThemeMode
import se.anders.athenamusic.data.settings.TypefaceChoice
import se.anders.athenamusic.domain.effectiveAppearance

class EffectiveAppearanceTest {
    private val local = Settings(themeMode = ThemeMode.Light, typeface = TypefaceChoice.Serif, textScalePercent = 90)

    @Test fun launcherWinsFieldByFieldWhileFollowing() {
        val e = effectiveAppearance(local, LauncherAppearance(ThemeMode.Dark, null, 120))
        assertEquals(ThemeMode.Dark, e.themeMode)
        assertEquals(TypefaceChoice.Serif, e.typeface) // the launcher gave nothing usable: local value
        assertEquals(120, e.textScalePercent)
    }

    @Test fun localValuesWithoutLauncherOrWhenNotFollowing() {
        assertEquals(ThemeMode.Light, effectiveAppearance(local, null).themeMode)
        val e = effectiveAppearance(local.copy(followLauncher = false), LauncherAppearance(ThemeMode.Dark, TypefaceChoice.Inter, 130))
        assertEquals(ThemeMode.Light, e.themeMode)
        assertFalse(e.followingLauncher)
    }
}
