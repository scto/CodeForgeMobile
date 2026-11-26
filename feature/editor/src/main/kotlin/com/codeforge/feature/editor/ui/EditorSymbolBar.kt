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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.codeforge.core.domain.editor.SymbolBarAction
import com.codeforge.core.domain.editor.SymbolBarConfig
import com.codeforge.core.domain.editor.SymbolBarEntry
import com.codeforge.core.domain.editor.SymbolEntryKind
import com.codeforge.core.ui.symbolActionIcon
import com.codeforge.core.ui.symbolActionLabelRes

/** Taste der Symbolleiste. */
data class EditorSymbolItem(
    val display: String,
    val insert: String = display,
    val isAction: Boolean = false
)

/** Symbolvorgaben je Sprache. */
object EditorSymbolPresets {

    /** Allgemeine Symbole. */
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

    /** Symbole für Kotlin/Java. */
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

    /** Symbole für Python. */
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

    /** Symbole für XML/HTML. */
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

    /** Symbole für Shell. */
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

    /** Symbole passend zum Dateipfad. */
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

/** Sprachspezifische Symbole (ohne Aktionen) für [path]. */
internal fun languageSymbols(path: String?): List<String> =
    EditorSymbolPresets.symbolsForPath(path).filterNot { it.isAction }.map { it.insert }

/**
 * Symbolleiste über der Tastatur aus der konfigurierten Liste (Einstellungen › Symbole & Makros);
 * optional ergänzt um sprachspezifische Symbole.
 */
@Composable
fun EditorSymbolBar(
    entries: List<SymbolBarEntry>,
    onInsertText: (text: String, cursorOffset: Int) -> Unit,
    onAction: (SymbolBarAction) -> Unit,
    modifier: Modifier = Modifier,
    filePath: String? = null,
    languageExtras: Boolean = true
) {
    val visible = remember(entries, filePath, languageExtras) {
        SymbolBarConfig.visible(entries, if (languageExtras) languageSymbols(filePath) else emptyList())
    }
    val barDescription = stringRes(R.string.editor_symbol_bar)

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
            for (entry in visible) {
                SymbolKey(entry, onInsertText, onAction)
            }
        }
    }
}

@Composable
private fun SymbolKey(
    entry: SymbolBarEntry,
    onInsertText: (String, Int) -> Unit,
    onAction: (SymbolBarAction) -> Unit
) {
    val action = entry.action
    if (action != null) {
        val label = stringRes(symbolActionLabelRes(action.id))
        val icon = symbolActionIcon(action.id)
        IconButton(onClick = { onAction(action) }, modifier = Modifier.size(width = 40.dp, height = 34.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            } else {
                Text(label, fontSize = 12.sp)
            }
        }
        return
    }
    val isMacro = entry.kind == SymbolEntryKind.MACRO
    TextButton(
        onClick = {
            val (text, offset) = SymbolBarConfig.expandMacro(entry.text)
            onInsertText(text, offset)
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = entry.label,
            fontFamily = if (isMacro) FontFamily.Default else FontFamily.Monospace,
            fontWeight = if (isMacro) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (isMacro) 13.sp else 15.sp,
            color = if (isMacro) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
