// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodegenTest {

    private val templateTheme = """
        package com.example.app.ui.theme

        import android.os.Build
        import androidx.compose.foundation.isSystemInDarkTheme
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.darkColorScheme
        import androidx.compose.material3.dynamicDarkColorScheme
        import androidx.compose.material3.dynamicLightColorScheme
        import androidx.compose.material3.lightColorScheme
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.platform.LocalContext

        private val DarkColorScheme =
            darkColorScheme(primary = Purple80, secondary = PurpleGrey80,
                tertiary = Pink80)

        private val LightColorScheme =
            lightColorScheme(primary = Purple40, secondary = PurpleGrey40,
                tertiary = Pink40)

        @Composable
        fun MyAppTheme(
            darkTheme: Boolean = isSystemInDarkTheme(),
            dynamicColor: Boolean = true,
            content: @Composable () -> Unit
        ) {
            val colorScheme = when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
            MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
        }
    """.trimIndent()

    @Test
    fun colorsFileHasAllRoles() {
        val text = ComposeThemeCodegen.colorsFile("com.example.app.ui.theme", ThemeSpec.CODEFORGE)
        assertTrue(text.startsWith("// Erzeugt vom CodeForgeMobile Theme-Builder"))
        assertTrue(text.contains("package com.example.app.ui.theme"))
        assertEquals(36, Regex("""val \w+Light = Color\(0xFF[0-9A-F]{6}\)""").findAll(text).count())
        assertEquals(36, Regex("""val \w+Dark = Color\(0xFF[0-9A-F]{6}\)""").findAll(text).count())
        assertTrue(text.contains("val AppLightColorScheme = lightColorScheme("))
        assertTrue(text.contains("    surfaceContainerHighest = surfaceContainerHighestDark,"))
    }

    @Test
    fun patchesTemplateTheme() {
        val patch = ComposeThemeCodegen.patchThemeFile(templateTheme)
        assertTrue(patch.schemesReplaced)
        assertTrue(patch.dynamicColorDisabled)
        assertTrue(patch.text.contains("private val DarkColorScheme =\n    AppDarkColorScheme"))
        assertTrue(patch.text.contains("private val LightColorScheme =\n    AppLightColorScheme"))
        assertTrue(patch.text.contains("dynamicColor: Boolean = false"))
        assertFalse(patch.text.contains("import androidx.compose.material3.lightColorScheme"))
        assertTrue(patch.text.contains("dynamicLightColorScheme(context)"))
        assertTrue(patch.text.contains("import androidx.compose.material3.dynamicLightColorScheme"))
        // Zweite Übernahme ändert nichts mehr.
        val again = ComposeThemeCodegen.patchThemeFile(patch.text)
        assertEquals(patch.text, again.text)
        assertTrue(again.schemesReplaced)
    }

    @Test
    fun patchReportsUnknownTheme() {
        val patch = ComposeThemeCodegen.patchThemeFile("@Composable fun X() { MaterialTheme(colorScheme = mine) {} }")
        assertFalse(patch.schemesReplaced)
    }

    @Test
    fun viewsMaterial3Style() {
        val colors = ViewsThemeCodegen.colorsFile(ThemeSpec.CODEFORGE, isDark = true)
        assertEquals(36, Regex("""<color name="md_theme_\w+">#[0-9A-F]{6}</color>""").findAll(colors).count())
        val themes = """
            <resources>
                <style name="Theme.App" parent="Theme.Material3.DayNight.NoActionBar">
                    <item name="colorPrimary">@color/purple_500</item>
                    <item name="android:statusBarColor">@android:color/transparent</item>
                </style>
            </resources>
        """.trimIndent()
        val patch = ViewsThemeCodegen.patchThemesFile(themes, "Theme.App")
        assertTrue(patch.found)
        assertEquals(ViewsThemeCodegen.ThemeFamily.MATERIAL3, patch.family)
        assertFalse(patch.text.contains("purple_500"))
        assertTrue(patch.text.contains("android:statusBarColor"))
        assertTrue(patch.text.contains("""<item name="colorSurfaceContainerHigh">@color/md_theme_surface_container_high</item>"""))
        assertEquals(patch.text, ViewsThemeCodegen.patchThemesFile(patch.text, "Theme.App").text)
    }

    @Test
    fun viewsSelfClosingPlatformStyle() {
        val themes = """<resources><style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" /></resources>"""
        val patch = ViewsThemeCodegen.patchThemesFile(themes, "Theme.App")
        assertEquals(ViewsThemeCodegen.ThemeFamily.BASIC, patch.family)
        assertTrue(patch.text.contains("""<item name="android:colorPrimary">@color/md_theme_primary</item>"""))
        assertTrue(patch.text.contains("</style></resources>"))
        assertFalse(ViewsThemeCodegen.patchThemesFile(themes, "Theme.Other").found)
    }
}
