package com.codeforge.feature.composepreview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSourcePreparerTest {

    private val mainActivity = """
        package com.compose.myapp

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import com.compose.myapp.ui.theme.MyComposeApplicationTheme

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContent { MyComposeApplicationTheme { Greeting("Android") } }
            }
        }

        @Composable
        fun Greeting(name: String) { Text("Hello ${'$'}name!") }

        @Preview
        @Composable
        fun GreetingPreview() { MyComposeApplicationTheme { Greeting("Android") } }
    """.trimIndent()

    @Test
    fun `entfernt package, Activity-Klasse und Activity-Imports`() {
        val out = PreviewSourcePreparer.prepare(mainActivity)
        assertFalse(out.contains("package "))
        assertFalse(out.contains("ComponentActivity"))
        assertFalse(out.contains("androidx.activity"))
        assertFalse(out.contains("onCreate"))
        assertTrue(out.contains("fun Greeting(name: String)"))
        assertTrue(out.contains("fun GreetingPreview()"))
    }

    @Test
    fun `ersetzt Projekt-Theme durch Material3-Stub`() {
        val out = PreviewSourcePreparer.prepare(mainActivity)
        assertFalse(out.contains("import com.compose.myapp.ui.theme"))
        assertTrue(out.contains("fun MyComposeApplicationTheme(content:"))
        assertTrue(out.contains("androidx.compose.material3.MaterialTheme(content = content)"))
    }

    @Test
    fun `Theme in derselben Datei bekommt keinen Stub`() {
        val src = """
            import com.x.ui.theme.AppTheme
            @androidx.compose.runtime.Composable
            fun AppTheme(content: @androidx.compose.runtime.Composable () -> Unit) { content() }
        """.trimIndent()
        val out = PreviewSourcePreparer.prepare(src)
        assertTrue(out.split("fun AppTheme(").size == 2)
    }

    @Test
    fun `Klammern in Strings stoeren die Klassenentfernung nicht`() {
        val src = """
            class A : ComponentActivity() {
                val s = "}{"
                fun f() { if (true) { } }
            }
            fun keep() {}
        """.trimIndent()
        val out = PreviewSourcePreparer.prepare(src)
        assertFalse(out.contains("class A"))
        assertTrue(out.contains("fun keep()"))
    }

    @Test
    fun `Datei ohne Activity bleibt inhaltlich unveraendert`() {
        val src = "import androidx.compose.material3.Text\n@androidx.compose.runtime.Composable\nfun P() { Text(\"x\") }\n"
        val out = PreviewSourcePreparer.prepare(src)
        assertTrue(out.contains("import androidx.compose.material3.Text"))
        assertTrue(out.contains("fun P()"))
    }

    @Test
    fun `behaelt package wenn keepPackage wahr ist`() {
        val out = PreviewSourcePreparer.prepare(
            source = mainActivity,
            keepPackage = true,
            stubProjectThemes = false,
            stripThemeSideEffects = true
        )
        assertTrue(out.contains("package com.compose.myapp"))
        assertFalse(out.contains("class MainActivity"))
        assertTrue(out.contains("import com.compose.myapp.ui.theme.MyComposeApplicationTheme"))
        assertFalse(out.contains("fun MyComposeApplicationTheme(content:"))
    }

    @Test
    fun `entfernt WindowCompat und SideEffect aus Theme`() {
        val themeSrc = """
            package com.example.ui.theme

            import android.app.Activity
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.SideEffect
            import androidx.compose.ui.platform.LocalView
            import androidx.core.view.WindowCompat

            @Composable
            fun AppTheme(content: @Composable () -> Unit) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                    }
                }
                MaterialTheme(content = content)
            }
        """.trimIndent()

        val out = PreviewSourcePreparer.prepare(
            source = themeSrc,
            keepPackage = true,
            stubProjectThemes = false,
            stripThemeSideEffects = true
        )

        assertFalse(out.contains("WindowCompat"))
        assertFalse(out.contains("SideEffect"))
        assertFalse(out.contains("LocalView"))
        assertFalse(out.contains("isInEditMode"))
        assertTrue(out.contains("package com.example.ui.theme"))
        assertTrue(out.contains("MaterialTheme(content = content)"))
    }

    @Test
    fun `determineFallback aktiviert sich bei Fehler in Nachbardatei und liefert Stub-Quelltext`() {
        val themeFile = java.io.File("/tmp/Theme.kt")
        val failedResult = CompileResult(
            success = false,
            outputDir = java.io.File("/tmp/out"),
            errorMessages = listOf("Theme.kt:14:5 error: unresolved reference 'UnknownType'")
        )

        val fallback = PreviewCompileStep.determineFallback(
            primaryResult = failedResult,
            targetSourceCode = mainActivity,
            siblingFiles = listOf(themeFile)
        )

        org.junit.Assert.assertNotNull(fallback)
        val (reason, stubSource) = fallback!!
        assertTrue(reason.contains("Theme.kt:14:5"))
        assertTrue(stubSource.contains("package com.compose.myapp"))
        assertTrue(stubSource.contains("fun MyComposeApplicationTheme(content:"))
    }

    @Test
    fun `determineFallback ist inaktiv wenn primaere Kompilierung erfolgreich war`() {
        val themeFile = java.io.File("/tmp/Theme.kt")
        val successResult = CompileResult(
            success = true,
            outputDir = java.io.File("/tmp/out"),
            errorMessages = emptyList()
        )

        val fallback = PreviewCompileStep.determineFallback(
            primaryResult = successResult,
            targetSourceCode = mainActivity,
            siblingFiles = listOf(themeFile)
        )

        org.junit.Assert.assertNull(fallback)
    }
}
