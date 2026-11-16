/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codeforge.feature.composepreview.PreviewResourceStubs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InterpreterComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val source = """
        package demo

        import androidx.compose.foundation.layout.Arrangement
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.Row
        import androidx.compose.foundation.layout.Spacer
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.foundation.layout.height
        import androidx.compose.foundation.layout.padding
        import androidx.compose.foundation.lazy.LazyColumn
        import androidx.compose.foundation.lazy.items
        import androidx.compose.material.icons.Icons
        import androidx.compose.material.icons.filled.Home
        import androidx.compose.material3.Button
        import androidx.compose.material3.Icon
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.getValue
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.remember
        import androidx.compose.runtime.setValue
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.res.stringResource
        import androidx.compose.ui.res.pluralStringResource
        import androidx.compose.ui.unit.dp
        import demo.R

        private val names = listOf("Anna", "Ben")

        @Composable
        fun Greeting(name: String, modifier: Modifier = Modifier, excited: Boolean = false) {
            Text(
                text = "Hallo ${'$'}name" + if (excited) "!" else "",
                modifier = modifier.padding(4.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        @Preview
        @Composable
        private fun DemoPreview() {
            var count by remember { mutableStateOf(0) }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Greeting("Welt", excited = true)
                Row {
                    Icon(Icons.Filled.Home, contentDescription = "Haus")
                    Text(stringResource(R.string.app_title))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { count++ }) { Text("Zähler: ${'$'}count") }
                names.forEachIndexed { i, n -> Text("${'$'}{i + 1}. ${'$'}n") }
                Text(pluralStringResource(R.plurals.items, names.size, names.size))
                when {
                    count > 0 -> Text("geklickt")
                    else -> Text("noch nicht")
                }
                LazyColumn(Modifier.height(100.dp)) {
                    items(listOf("x", "y")) { item -> Text("Eintrag ${'$'}item") }
                }
            }
        }
    """.trimIndent()

    @Test
    fun `renders an interpreted preview with state resources and lazy lists`() {
        val loader = javaClass.classLoader!!
        val values = mapOf(
            "" to PreviewResourceStubs.parseValuesXml(
                "<resources><string name=\"app_title\">Mein Titel</string><plurals name=\"items\"><item quantity=\"one\">%d Eintrag</item><item quantity=\"other\">%d Einträge</item></plurals></resources>"
            )
        )
        val preview = InterpretedPreview.prepare(source, "DemoPreview", loader, MapClassIndex(ClassIndexes.jvmClassPathNames().asSequence()), values)
        var error: Throwable? = null
        try {
            rule.setContent { preview.Content { if (error == null) error = it } }
            rule.waitForIdle()
        } catch (t: Throwable) {
            throw error ?: t
        }
        error?.let { throw it }

        rule.onNodeWithText("Hallo Welt!").assertIsDisplayed()
        rule.onNodeWithText("Mein Titel").assertIsDisplayed()
        rule.onNodeWithContentDescription("Haus").assertExists()
        rule.onNodeWithText("1. Anna").assertExists()
        rule.onNodeWithText("2. Ben").assertExists()
        rule.onNodeWithText("2 Einträge").assertExists()
        rule.onNodeWithText("Eintrag y").assertExists()
        rule.onNodeWithText("noch nicht").assertExists()

        rule.onNodeWithText("Zähler: 0").performClick()
        rule.waitForIdle()
        error?.let { throw it }
        rule.onNodeWithText("Zähler: 1").assertExists()
        rule.onNodeWithText("geklickt").assertExists()
    }
}
