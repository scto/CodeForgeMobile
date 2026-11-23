// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectThemeTest {
    private val root: File = Files.createTempDirectory("theme-project").toFile()

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun write(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun setupProject() {
        write(
            "app/src/main/java/com/example/ui/theme/Theme.kt",
            """
                package com.example.ui.theme
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.lightColorScheme
                import androidx.compose.material3.darkColorScheme
                private val Dark = darkColorScheme(primary = Purple80)
                private val Light = lightColorScheme(primary = Purple40)
                @Composable
                fun ExampleTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
                    MaterialTheme(colorScheme = Light, content = content)
                }
            """.trimIndent(),
        )
        write(
            "app/src/main/AndroidManifest.xml",
            """<manifest><application android:label="x" android:theme="@style/Theme.Example"></application></manifest>""",
        )
        write(
            "app/src/main/res/values/themes.xml",
            """<resources><style name="Theme.Example" parent="android:Theme.Material.Light.NoActionBar" /></resources>""",
        )
        write("app/build/intermediates/ui/theme/Theme.kt", "package ignored\nMaterialTheme")
    }

    @Test
    fun scanFindsTargets() {
        setupProject()
        val targets = ProjectThemeScanner.scan(root)
        assertEquals(2, targets.size)
        val compose = targets[0] as ThemeTarget.Compose
        assertEquals("app", compose.module)
        assertEquals("com.example.ui.theme", compose.packageName)
        assertEquals("ExampleTheme", compose.themeFunction)
        val views = targets[1] as ThemeTarget.Views
        assertEquals("Theme.Example", views.styleName)
        assertEquals("android:Theme.Material.Light.NoActionBar", views.parent)
    }

    @Test
    fun planApplyAndReload() {
        setupProject()
        val writer = ProjectThemeWriter(root)
        val spec = ThemeSpec(seed = "#2E7D32", style = ThemeStyle.VIBRANT, name = "Grün")
        val plan = writer.plan(spec, ProjectThemeScanner.scan(root))
        val names = plan.changes.map { it.file.relativeTo(root).invariantSeparatorsPath }.toSet()
        assertTrue(names.contains("app/src/main/java/com/example/ui/theme/ThemeColors.kt"))
        assertTrue(names.contains("app/src/main/java/com/example/ui/theme/Theme.kt"))
        assertTrue(names.contains("app/src/main/res/values-night/theme_colors.xml"))
        assertTrue(names.contains("app/src/main/res/values/themes.xml"))
        assertTrue(names.contains(".codeforge/theme.json"))
        assertTrue(plan.notes.any { it is ThemeNote.DynamicColorDisabled })
        assertTrue(plan.notes.any { it is ThemeNote.LimitedXmlFamily })

        val backup = writer.apply(plan)
        assertNotNull(backup)
        assertTrue(File(backup, "app/src/main/res/values/themes.xml").isFile)
        assertEquals(spec, writer.loadSpec())
        // Erneut planen: nur noch unveränderte Dateien → nichts zu tun.
        assertEquals(emptyList<FileChange>(), writer.plan(spec, ProjectThemeScanner.scan(root)).changes)
    }

    @Test
    fun exportsPluginVariants() {
        val zip = File(root, "out/theme.zip")
        val id = ThemePluginExporter.export(ThemeSpec.CODEFORGE, "Mein Indigo!", zip)
        assertEquals("user.theme.mein-indigo", id)
        ZipFile(zip).use { z ->
            val manifest = Json.parseToJsonElement(z.getInputStream(z.getEntry("plugin.json")).reader().readText()).jsonObject
            assertEquals(id, manifest["id"].toString().trim('"'))
            for (v in listOf("dark", "light")) {
                val colors = Json.parseToJsonElement(z.getInputStream(z.getEntry("themes/$v.json")).reader().readText())
                    .jsonObject["colors"]!!.jsonObject
                assertEquals(36, colors.size)
            }
        }
    }
}
