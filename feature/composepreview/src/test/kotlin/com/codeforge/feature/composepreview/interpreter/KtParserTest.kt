/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtParserTest {

    private val screen = """
        package com.example.feature.settings

        import androidx.compose.foundation.layout.Column
        import androidx.compose.runtime.Composable
        import com.example.core.resources.R as Res

        /** Screen. */
        @Composable
        fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit, modifier: Modifier = Modifier) {
            val spacing = AppTheme.spacing
            Column(
                modifier
                    .fillMaxSize()
                    .padding(bottom = spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.xl),
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeTile(label = stringResource(mode.label()), selected = state.mode == mode, onClick = { onEvent(SettingsUiEvent.ThemeModeSelected(mode)) })
                }
                Text("Version ${'$'}{state.version} – ${'$'}name")
            }
        }

        private fun ThemeMode.label(): Int = when (this) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT, ThemeMode.DARK -> R.string.settings_theme_light
            else -> 0
        }

        private enum class Local { A, B }

        @Preview
        @Composable
        private fun SettingsScreenPreview() {
            AppTheme { SettingsScreen(state = SettingsUiState(), onEvent = {}) }
        }
    """.trimIndent()

    @Test
    fun `parses declarations imports and unsupported classes`() {
        val file = KtParser(screen).parseFile()
        assertEquals("com.example.feature.settings", file.packageName)
        assertEquals(listOf("Res"), file.imports.mapNotNull { it.alias })
        assertEquals(listOf("SettingsScreen", "label", "SettingsScreenPreview"), file.functions.map { it.name })
        val screenFn = file.functions[0]
        assertTrue(screenFn.isComposable)
        assertEquals(listOf("state", "onEvent", "modifier"), screenFn.params.map { it.name })
        assertEquals("kotlin.Function1", screenFn.params[1].type?.name)
        assertEquals("ThemeMode", file.functions[1].receiver?.name)
        assertTrue(file.functions[1].expressionBody)
        assertEquals(listOf("Local"), file.unsupported.map { it.name })
    }

    @Test
    fun `attaches trailing lambdas and parses multiline chains`() {
        val file = KtParser(screen).parseFile()
        val column = (file.functions[0].body!!.statements[1] as ExprStmt).expr as Call
        assertEquals(KtParser.TRAILING_LAMBDA, column.args.last().name)
        assertEquals("verticalArrangement", column.args[1].name)
        val chain = column.args[0].value as Call
        assertEquals("padding", ((chain.callee) as Navigation).name)
        val template = (((column.args.last().value as LambdaExpr).body.statements[1] as ExprStmt).expr as Call).args[0].value as StringTemplate
        assertEquals(4, template.parts.size)
    }

    @Test
    fun `distinguishes generics from comparisons`() {
        val file = KtParser("fun a() = remember<Int> { 1 }\nfun b(x: Int, y: Int) = x < y && y > 2\n").parseFile()
        val a = (file.functions[0].body!!.statements[0] as ExprStmt).expr as Call
        assertEquals("Int", a.typeArgs.single().name)
        val b = (file.functions[1].body!!.statements[0] as ExprStmt).expr as Binary
        assertEquals("&&", b.op)
    }

    @Test(expected = UnsupportedKotlinException::class)
    fun `rejects object expressions`() {
        KtParser("fun a() = object : Runnable { override fun run() {} }").parseFile()
    }
}
