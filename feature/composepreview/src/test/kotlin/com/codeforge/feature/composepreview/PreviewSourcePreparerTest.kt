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
}
