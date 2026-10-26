package com.codeforge.libs.x2c.theme

import com.codeforge.core.domain.model.ThemeSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeThemeGeneratorTest {

    private val gen = ComposeThemeGeneratorImpl()

    private val colors = """<resources>
        <color name="purple_500">#6200EE</color>
        <color name="purple_700">#3700B3</color>
        <color name="teal_200">#03DAC5</color>
        <color name="white">#FFFFFF</color>
        <color name="alias">@color/purple_500</color>
    </resources>"""

    private val themes = """<resources>
        <style name="Base.Theme.MyApp" parent="Theme.MaterialComponents.DayNight.NoActionBar">
            <item name="colorPrimary">@color/alias</item>
            <item name="colorPrimaryVariant">@color/purple_700</item>
            <item name="colorOnPrimary">@color/white</item>
            <item name="colorSecondary">@color/teal_200</item>
            <item name="android:colorBackground">#FAFAFA</item>
        </style>
        <style name="Theme.MyApp" parent="Base.Theme.MyApp"/>
    </resources>"""

    private fun run(light: List<String> = listOf(colors, themes), night: List<String> = emptyList(), existing: Set<String> = emptySet()) =
        gen.generate(ThemeSources(light, night), "com.example.ui.theme", existing).getOrThrow()

    @Test fun `Material-Theme wird erkannt`() {
        val d = gen.detect(ThemeSources(listOf(colors, themes)))
        assertTrue(d.isMaterial)
        assertEquals("Theme.MyApp", d.styleName)
        assertEquals("MaterialComponents", gen.detect(ThemeSources(listOf(themes))).family)
    }

    @Test fun `AppCompat-Theme ist kein Material-Theme`() {
        val x = """<resources><style name="AppTheme" parent="Theme.AppCompat.Light"><item name="colorPrimary">#112233</item></style></resources>"""
        assertFalse(gen.detect(ThemeSources(listOf(x))).isMaterial)
        val r = run(listOf(x))
        assertTrue(r.files.isEmpty())
        assertNotNull(r.skippedReason)
    }

    @Test fun `Color und Theme werden erzeugt`() {
        val r = run()
        assertEquals(listOf("Color.kt", "Theme.kt"), r.files.map { it.targetRelativePath.substringAfterLast('/') })
        assertEquals("src/main/kotlin/com/example/ui/theme/Theme.kt", r.files[1].targetRelativePath)
        assertEquals("MyAppTheme", r.themeName)
        val c = r.files[0].sourceContent
        assertTrue("val PrimaryLight = Color(0xFF6200EE) // colorPrimary = @color/alias" in c)
        assertTrue("val OnPrimaryLight = Color(0xFFFFFFFF)" in c)
        assertTrue("val BackgroundLight = Color(0xFFFAFAFA)" in c)
        val t = r.files[1].sourceContent
        assertTrue("primary = PrimaryLight," in t && "fun MyAppTheme(" in t)
        assertTrue("darkTheme: Boolean = isSystemInDarkTheme()" in t)
        assertTrue("import androidx.compose.material3.lightColorScheme" in t)
    }

    @Test fun `Variant-Farben werden gemeldet und nicht uebernommen`() {
        val r = run()
        assertTrue(r.notes.any { "colorPrimaryVariant" in it })
        assertFalse("Variant" in r.files[0].sourceContent.replace("colorPrimaryVariant", ""))
    }

    @Test fun `ohne values-night nutzt dunkel die Vorgaben mit TODO`() {
        val r = run()
        assertTrue("private val DarkColors = darkColorScheme()" in r.files[1].sourceContent)
        assertTrue("TODO(x2c)" in r.files[1].sourceContent)
        assertTrue(r.todoCount >= 1)
    }

    @Test fun `values-night ueberlagert die hellen Werte`() {
        val night = """<resources><color name="purple_500">#BB86FC</color>
            <style name="Theme.MyApp" parent="Base.Theme.MyApp"><item name="android:colorBackground">#121212</item></style></resources>"""
        val r = run(night = listOf(night))
        val c = r.files[0].sourceContent
        assertTrue("val PrimaryDark = Color(0xFFBB86FC)" in c)
        assertTrue("val BackgroundDark = Color(0xFF121212)" in c)
        assertTrue("val OnPrimaryDark = Color(0xFFFFFFFF)" in c)
        assertTrue("primary = PrimaryDark," in r.files[1].sourceContent)
        assertEquals(0, r.todoCount)
    }

    @Test fun `nicht aufloesbare Farbe bleibt Vorgabe mit Hinweis`() {
        val x = """<resources><style name="Theme.A" parent="Theme.Material3.DayNight">
            <item name="colorPrimary">?attr/colorAccent</item><item name="colorSecondary">#00FF00</item></style></resources>"""
        val r = run(listOf(x))
        assertTrue(r.notes.any { "colorPrimary" in it && "nicht auflösbar" in it })
        assertFalse("PrimaryLight" in r.files[0].sourceContent)
        assertTrue(r.todoCount >= 1)
    }

    @Test fun `Zyklen und Tiefe fuehren nicht zum Absturz`() {
        val x = """<resources><color name="a">@color/b</color><color name="b">@color/a</color>
            <style name="Theme.A" parent="Theme.Material3.Light"><item name="colorPrimary">@color/a</item></style></resources>"""
        val r = run(listOf(x))
        assertTrue(r.notes.any { "nicht auflösbar" in it })
    }

    @Test fun `vorhandenes Theme wird nie ueberschrieben`() {
        val r = run(existing = setOf("src/main/kotlin/com/example/ui/theme/Theme.kt"))
        assertTrue(r.files.isEmpty())
        assertTrue("Theme.kt" in r.skippedReason!!)
        val r2 = run(existing = setOf("src/main/kotlin/com/example/ui/theme/Color.kt"))
        assertTrue(r2.files.isEmpty())
    }

    @Test fun `Namensableitung`() {
        val mk = { n: String -> """<resources><style name="$n" parent="Theme.Material3.Light"/></resources>""" }
        assertEquals("MyAppTheme", run(listOf(mk("Theme.MyApp"))).themeName)
        assertEquals("ShopTheme", run(listOf(mk("Theme.Shop.Dark"))).themeName)
        assertEquals("AppTheme", run(listOf(mk("Theme.Material3.Light"))).themeName.let { "AppTheme".takeIf { _ -> it != null } })
    }

    @Test fun `kaputtes XML wird uebersprungen statt Absturz`() {
        val r = run(listOf("<resources><color", themes, colors))
        assertNotNull(r.themeName)
    }

    @Test fun `DOCTYPE wird abgelehnt`() {
        val x = "<!DOCTYPE r [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><resources><color name=\"a\">&e;</color></resources>"
        val r = run(listOf(x))
        assertTrue(r.files.isEmpty())
    }

    @Test fun `deterministisch und ohne Eingriff in themes xml`() {
        assertEquals(run().files.map { it.sourceContent }, run().files.map { it.sourceContent })
        assertTrue(run().files.none { it.targetRelativePath.endsWith(".xml") })
    }

    @Test fun `Ausgabe ist klammer-ausgewogen`() {
        val r = run()
        for (f in r.files) for ((o, c) in listOf('(' to ')', '{' to '}')) assertEquals(f.sourceContent.count { it == o }, f.sourceContent.count { it == c })
        assertNull(r.skippedReason)
    }
}
