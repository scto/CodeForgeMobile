/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Symbolleiste über der Bildschirmtastatur zur schnellen Eingabe von Programmiersymbolen
 * (z. B. Klammern, Semikolons, Operatoren, Tabulator), optimiert für mobile Tastaturen.
 */
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

data class EditorSymbolItem(
    val display: String,
    val insert: String = display,
    val isAction: Boolean = false
)

object EditorSymbolPresets {

    val GENERAL_SYMBOLS: List<EditorSymbolItem> = listOf(
        EditorSymbolItem(display = "Tab", insert = "\t", isAction = true),
        EditorSymbolItem(display = "->"),
        EditorSymbolItem(display = "{"),
        EditorSymbolItem(display = "}"),
        EditorSymbolItem(display = "("),
        EditorSymbolItem(display = ")"),
        EditorSymbolItem(display = "["),
        EditorSymbolItem(display = "]"),
        EditorSymbolItem(display = "<"),
        EditorSymbolItem(display = ">"),
        EditorSymbolItem(display = ";"),
        EditorSymbolItem(display = ","),
        EditorSymbolItem(display = "."),
        EditorSymbolItem(display = "\""),
        EditorSymbolItem(display = "'"),
        EditorSymbolItem(display = ":"),
        EditorSymbolItem(display = "="),
        EditorSymbolItem(display = "+"),
        EditorSymbolItem(display = "-"),
        EditorSymbolItem(display = "*"),
        EditorSymbolItem(display = "/"),
        EditorSymbolItem(display = "!"),
        EditorSymbolItem(display = "?"),
        EditorSymbolItem(display = "&"),
        EditorSymbolItem(display = "|"),
        EditorSymbolItem(display = "_")
    )

    val KOTLIN_JAVA_SYMBOLS: List<EditorSymbolItem> = listOf(
        EditorSymbolItem(display = "Tab", insert = "\t", isAction = true),
        EditorSymbolItem(display = "{"),
        EditorSymbolItem(display = "}"),
        EditorSymbolItem(display = "("),
        EditorSymbolItem(display = ")"),
        EditorSymbolItem(display = ";"),
        EditorSymbolItem(display = "="),
        EditorSymbolItem(display = "->"),
        EditorSymbolItem(display = "=>"),
        EditorSymbolItem(display = "=="),
        EditorSymbolItem(display = "!="),
        EditorSymbolItem(display = ">"),
        EditorSymbolItem(display = "<"),
        EditorSymbolItem(display = "\""),
        EditorSymbolItem(display = "'"),
        EditorSymbolItem(display = ":"),
        EditorSymbolItem(display = "["),
        EditorSymbolItem(display = "]"),
        EditorSymbolItem(display = "+"),
        EditorSymbolItem(display = "-"),
        EditorSymbolItem(display = "*"),
        EditorSymbolItem(display = "/"),
        EditorSymbolItem(display = "\\"),
        EditorSymbolItem(display = "_"),
        EditorSymbolItem(display = "|"),
        EditorSymbolItem(display = "&"),
        EditorSymbolItem(display = "!"),
        EditorSymbolItem(display = "?"),
        EditorSymbolItem(display = "$"),
        EditorSymbolItem(display = "%")
    )

    val PYTHON_SYMBOLS: List<EditorSymbolItem> = listOf(
        EditorSymbolItem(display = "Tab", insert = "\t", isAction = true),
        EditorSymbolItem(display = ":"),
        EditorSymbolItem(display = "("),
        EditorSymbolItem(display = ")"),
        EditorSymbolItem(display = "["),
        EditorSymbolItem(display = "]"),
        EditorSymbolItem(display = "{"),
        EditorSymbolItem(display = "}"),
        EditorSymbolItem(display = "="),
        EditorSymbolItem(display = "=="),
        EditorSymbolItem(display = "!="),
        EditorSymbolItem(display = "\""),
        EditorSymbolItem(display = "'"),
        EditorSymbolItem(display = "#"),
        EditorSymbolItem(display = "+"),
        EditorSymbolItem(display = "-"),
        EditorSymbolItem(display = "*"),
        EditorSymbolItem(display = "/"),
        EditorSymbolItem(display = "%"),
        EditorSymbolItem(display = ">"),
        EditorSymbolItem(display = "<")
    )

    val XML_HTML_SYMBOLS: List<EditorSymbolItem> = listOf(
        EditorSymbolItem(display = "Tab", insert = "\t", isAction = true),
        EditorSymbolItem(display = "<"),
        EditorSymbolItem(display = ">"),
        EditorSymbolItem(display = "</"),
        EditorSymbolItem(display = "/>"),
        EditorSymbolItem(display = "="),
        EditorSymbolItem(display = "\""),
        EditorSymbolItem(display = "'"),
        EditorSymbolItem(display = "!"),
        EditorSymbolItem(display = "-"),
        EditorSymbolItem(display = "/"),
        EditorSymbolItem(display = ":"),
        EditorSymbolItem(display = "["),
        EditorSymbolItem(display = "]"),
        EditorSymbolItem(display = "#")
    )

    val BASH_SYMBOLS: List<EditorSymbolItem> = listOf(
        EditorSymbolItem(display = "Tab", insert = "\t", isAction = true),
        EditorSymbolItem(display = "$"),
        EditorSymbolItem(display = "{"),
        EditorSymbolItem(display = "}"),
        EditorSymbolItem(display = "("),
        EditorSymbolItem(display = ")"),
        EditorSymbolItem(display = "["),
        EditorSymbolItem(display = "]"),
        EditorSymbolItem(display = "|"),
        EditorSymbolItem(display = ">"),
        EditorSymbolItem(display = "<"),
        EditorSymbolItem(display = "&"),
        EditorSymbolItem(display = ";"),
        EditorSymbolItem(display = "\""),
        EditorSymbolItem(display = "'"),
        EditorSymbolItem(display = "="),
        EditorSymbolItem(display = "!"),
        EditorSymbolItem(display = "#"),
        EditorSymbolItem(display = "\\"),
        EditorSymbolItem(display = "/")
    )

    fun symbolsForPath(path: String?): List<EditorSymbolItem> {
        val ext = path?.substringAfterLast('.', "")?.lowercase() ?: return GENERAL_SYMBOLS
        return when (ext) {
            "kt", "kts", "java", "c", "cpp", "cc", "h", "hpp" -> KOTLIN_JAVA_SYMBOLS
            "py" -> PYTHON_SYMBOLS
            "xml", "html", "htm", "svg" -> XML_HTML_SYMBOLS
            "sh", "bash", "zsh" -> BASH_SYMBOLS
            else -> GENERAL_SYMBOLS
        }
    }
}

@Composable
fun EditorSymbolBar(
    modifier: Modifier = Modifier,
    filePath: String? = null,
    onInsertText: (String) -> Unit
) {
    val symbols = remember(filePath) { EditorSymbolPresets.symbolsForPath(filePath) }
    val barDescription = stringRes(R.string.editor_symbol_bar)
    val tabLabel = stringRes(R.string.editor_symbol_tab)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = barDescription },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in symbols) {
                val displayText = if (item.insert == "\t") tabLabel else item.display
                TextButton(
                    onClick = { onInsertText(item.insert) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = displayText,
                        fontFamily = if (item.isAction) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = if (item.isAction) FontWeight.Bold else FontWeight.Medium,
                        fontSize = if (item.isAction) 13.sp else 15.sp,
                        color = if (item.isAction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
