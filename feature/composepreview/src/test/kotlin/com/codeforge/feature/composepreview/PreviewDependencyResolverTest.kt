/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewDependencyResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolves transitive theme chain Theme Color and Type`() {
        val root = tempFolder.newFolder("src", "main", "kotlin")
        val appDir = File(root, "com/example/app").apply { mkdirs() }
        val themeDir = File(root, "com/example/app/ui/theme").apply { mkdirs() }

        val mainFile = File(appDir, "MainActivity.kt").apply {
            writeText("""
                package com.example.app

                import androidx.compose.runtime.Composable
                import com.example.app.ui.theme.MyAppTheme

                @Composable
                fun MainScreen() {
                    MyAppTheme {
                        Greeting()
                    }
                }
            """.trimIndent())
        }

        File(themeDir, "Theme.kt").apply {
            writeText("""
                package com.example.app.ui.theme

                import androidx.compose.runtime.Composable

                @Composable
                fun MyAppTheme(content: @Composable () -> Unit) {
                    val c = Purple80
                    val t = Typography
                    content()
                }
            """.trimIndent())
        }

        File(themeDir, "Color.kt").apply {
            writeText("""
                package com.example.app.ui.theme

                val Purple80 = 0xFF123456
            """.trimIndent())
        }

        File(themeDir, "Type.kt").apply {
            writeText("""
                package com.example.app.ui.theme

                val Typography = "CustomType"
            """.trimIndent())
        }

        // Unbezogene Datei im Modul
        File(appDir, "Unrelated.kt").apply {
            writeText("""
                package com.example.app

                fun unrelatedFunction() = 42
            """.trimIndent())
        }

        val resolved = PreviewDependencyResolver.resolve(
            targetFile = mainFile,
            moduleSourceRoots = listOf(root)
        )

        assertEquals("com.example.app.MainActivityKt", resolved.targetClassName)
        assertEquals("com.example.app", resolved.targetPackage)

        val siblingNames = resolved.siblingFiles.map { it.name }.toSet()
        assertTrue(siblingNames.contains("Theme.kt"))
        assertTrue(siblingNames.contains("Color.kt"))
        assertTrue(siblingNames.contains("Type.kt"))
        assertFalse(siblingNames.contains("Unrelated.kt"))
    }

    @Test
    fun `resolves sibling in same package without explicit import`() {
        val root = tempFolder.newFolder("src", "main", "kotlin")
        val pkgDir = File(root, "com/example/app").apply { mkdirs() }

        val mainFile = File(pkgDir, "Screen.kt").apply {
            writeText("""
                package com.example.app

                import androidx.compose.runtime.Composable

                @Composable
                fun Screen() {
                    HeaderComponent()
                }
            """.trimIndent())
        }

        File(pkgDir, "Header.kt").apply {
            writeText("""
                package com.example.app

                import androidx.compose.runtime.Composable

                @Composable
                fun HeaderComponent() {}
            """.trimIndent())
        }

        val resolved = PreviewDependencyResolver.resolve(
            targetFile = mainFile,
            moduleSourceRoots = listOf(root)
        )

        val names = resolved.siblingFiles.map { it.name }
        assertEquals(listOf("Header.kt"), names)
    }

    @Test
    fun `handles cyclic dependencies without infinite recursion`() {
        val root = tempFolder.newFolder("src", "main", "kotlin")
        val pkgDir = File(root, "com/example/app").apply { mkdirs() }

        val fileA = File(pkgDir, "A.kt").apply {
            writeText("""
                package com.example.app

                fun callA() { callB() }
            """.trimIndent())
        }

        File(pkgDir, "B.kt").apply {
            writeText("""
                package com.example.app

                fun callB() { callA() }
            """.trimIndent())
        }

        val resolved = PreviewDependencyResolver.resolve(
            targetFile = fileA,
            moduleSourceRoots = listOf(root)
        )

        val names = resolved.siblingFiles.map { it.name }
        assertEquals(listOf("B.kt"), names)
    }

    @Test
    fun `excludes pure Activity and ViewModel sibling files`() {
        val root = tempFolder.newFolder("src", "main", "kotlin")
        val pkgDir = File(root, "com/example/app").apply { mkdirs() }

        val mainFile = File(pkgDir, "MainScreen.kt").apply {
            writeText("""
                package com.example.app

                import androidx.compose.runtime.Composable

                @Composable
                fun MainScreen() {
                    GreetingText()
                }
            """.trimIndent())
        }

        File(pkgDir, "Greeting.kt").apply {
            writeText("""
                package com.example.app

                import androidx.compose.runtime.Composable

                @Composable
                fun GreetingText() {}
            """.trimIndent())
        }

        File(pkgDir, "OtherActivity.kt").apply {
            writeText("""
                package com.example.app

                import androidx.activity.ComponentActivity

                class OtherActivity : ComponentActivity()
            """.trimIndent())
        }

        File(pkgDir, "AppViewModel.kt").apply {
            writeText("""
                package com.example.app

                import androidx.lifecycle.ViewModel

                class AppViewModel : ViewModel()
            """.trimIndent())
        }

        val resolved = PreviewDependencyResolver.resolve(
            targetFile = mainFile,
            moduleSourceRoots = listOf(root)
        )

        val names = resolved.siblingFiles.map { it.name }
        assertEquals(listOf("Greeting.kt"), names)
    }

    @Test
    fun `determines target class name correctly for custom JvmName and empty package`() {
        val root = tempFolder.newFolder("src", "main", "kotlin")
        val file1 = File(root, "Custom.kt").apply {
            writeText("""
                @file:JvmName("CustomHostClass")
                package com.test

                fun test() {}
            """.trimIndent())
        }

        val resolved1 = PreviewDependencyResolver.resolve(
            targetFile = file1,
            moduleSourceRoots = listOf(root)
        )
        assertEquals("com.test.CustomHostClass", resolved1.targetClassName)

        val file2 = File(root, "NoPackage.kt").apply {
            writeText("""
                fun noPkg() {}
            """.trimIndent())
        }

        val resolved2 = PreviewDependencyResolver.resolve(
            targetFile = file2,
            moduleSourceRoots = listOf(root)
        )
        assertEquals("NoPackageKt", resolved2.targetClassName)
    }

    @Test
    fun `handles missing and external symbols gracefully without errors`() {
        val root = tempFolder.newFolder("src", "main", "kotlin")
        val file = File(root, "Target.kt").apply {
            writeText("""
                package com.example

                fun run() {
                    NonExistentSymbol()
                    println("hello")
                }
            """.trimIndent())
        }

        val resolved = PreviewDependencyResolver.resolve(
            targetFile = file,
            moduleSourceRoots = listOf(root)
        )

        assertTrue(resolved.siblingFiles.isEmpty())
        assertEquals("com.example.TargetKt", resolved.targetClassName)
    }
}
