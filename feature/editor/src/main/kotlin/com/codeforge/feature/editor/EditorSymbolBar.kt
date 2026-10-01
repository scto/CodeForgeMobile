/**
 * Modul: :feature:editor
 * Editor Symbol Bar for CodeForge Mobile
 */
package com.codeforge.feature.editor

import android.graphics.Typeface
import android.widget.HorizontalScrollView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView

object SymbolPresets {
    val DEFAULT_SYMBOLS = arrayOf(
        "->", "{", "}", "(", ")",
        ",", ".", ";", "\"", "?",
        "+", "-", "*", "/", "<",
        ">", "[", "]", ":"
    )

    val DEFAULT_INSERTS = arrayOf(
        "\t", "{}", "}", "(", ")",
        ",", ".", ";", "\"", "?",
        "+", "-", "*", "/", "<",
        ">", "[", "]", ":"
    )

    val JAVA_KOTLIN_SYMBOLS = arrayOf(
        "{", "}", "(", ")", ";", "=", ">", "<", "\"", "'", ":", "[", "]",
        "+", "-", "*", "/", "\\", "_", "|", "&", "#", "$", "%", "->", "=>", "==", "!="
    )

    val JAVA_KOTLIN_INSERTS = arrayOf(
        "{\n    \n}", "}", "(", ")", ";", "=", ">", "<", "\"\"", "''", ":", "[", "]",
        "+", "-", "*", "/", "\\", "_", "|", "&", "#", "$", "%", "->", "=>", "==", "!="
    )

    val PYTHON_SYMBOLS = arrayOf(
        ":", "(", ")", "[", "]", "{", "}", "=", "==", "!=", "\"", "'", "#", "+", "-", "*", "/", "%", ">", "<"
    )

    val PYTHON_INSERTS = arrayOf(
        ":\n    ", "(", ")", "[", "]", "{}", "}", "=", "==", "!=", "\"\"", "''", "# ", "+", "-", "*", "/", "%", ">", "<"
    )

    val HTML_XML_SYMBOLS = arrayOf(
        "<", ">", "</", "/>", "=", "\"", "'", "!", "-", "/", ":", "[", "]", "#"
    )

    val HTML_XML_INSERTS = arrayOf(
        "<", ">", "</", "/>", "=", "\"\"", "''", "!", "-", "/", ":", "[", "]", "#"
    )

    val JS_TS_SYMBOLS = arrayOf(
        "=>", "{", "}", "(", ")", ";", "=", "===", "!==", "\"", "'", "`", ":", "[", "]", "+", "-", "*", "/", "?", ".", ","
    )

    val JS_TS_INSERTS = arrayOf(
        "=> ", "{\n    \n}", "}", "(", ")", ";", "=", "===", "!==", "\"\"", "''", "``", ":", "[", "]", "+", "-", "*", "/", "?", ".", ","
    )

    fun getPreset(preset: String): Pair<Array<String>, Array<String>> {
        return when (preset.uppercase()) {
            "JAVA", "KOTLIN", "JAVA_KOTLIN" -> JAVA_KOTLIN_SYMBOLS to JAVA_KOTLIN_INSERTS
            "PYTHON" -> PYTHON_SYMBOLS to PYTHON_INSERTS
            "HTML", "XML", "HTML_XML" -> HTML_XML_SYMBOLS to HTML_XML_INSERTS
            "JS", "TS", "JAVASCRIPT", "TYPESCRIPT" -> JS_TS_SYMBOLS to JS_TS_INSERTS
            else -> DEFAULT_SYMBOLS to DEFAULT_INSERTS
        }
    }
}

@Composable
fun EditorSymbolBar(
    editor: CodeEditor?,
    modifier: Modifier = Modifier,
    preset: String = "DEFAULT",
    typeface: Typeface? = null,
    customSymbols: Array<String>? = null,
    customInserts: Array<String>? = null
) {
    var symbolInputView by remember { mutableStateOf<SymbolInputView?>(null) }
    val (symbols, symbolInsertTexts) = if (customSymbols != null && customInserts != null) {
        customSymbols to customInserts
    } else {
        SymbolPresets.getPreset(preset)
    }

    AndroidView(
        factory = { context ->
            val view = SymbolInputView(context).apply {
                addSymbols(symbols, symbolInsertTexts)
                if (typeface != null) {
                    forEachButton { button -> button.typeface = typeface }
                }
            }
            symbolInputView = view
            editor?.let { view.bindEditor(it) }

            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(view)
            }
        },
        update = {
            editor?.let { currentEditor ->
                symbolInputView?.bindEditor(currentEditor)
                if (typeface != null) {
                    symbolInputView?.forEachButton { button -> button.typeface = typeface }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    )
}
