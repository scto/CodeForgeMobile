// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.LspSignatureHelp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Ein Abschnitt der Hover-Dokumentation: Fließtext oder Codeblock. */
internal data class HoverBlock(val text: String, val isCode: Boolean)

/**
 * Zerlegt Markdown aus `textDocument/hover` minimal in Text- und ```-Codeblöcke.
 * Mehr braucht die Anzeige nicht; Inline-Markdown bleibt unverändert lesbar.
 */
internal fun parseHoverBlocks(markdown: String): List<HoverBlock> {
    val blocks = mutableListOf<HoverBlock>()
    val text = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    fun flushText() {
        if (text.isNotBlank()) blocks += HoverBlock(text.toString().trim(), isCode = false)
        text.clear()
    }
    for (line in markdown.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                if (code.isNotBlank()) blocks += HoverBlock(code.toString().trimEnd(), isCode = true)
                code.clear()
                inCode = false
            } else {
                flushText()
                inCode = true
            }
        } else if (inCode) {
            code.appendLine(line)
        } else {
            text.appendLine(line)
        }
    }
    if (inCode && code.isNotBlank()) blocks += HoverBlock(code.toString().trimEnd(), isCode = true)
    flushText()
    return blocks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoverSheet(
    modifier: Modifier = Modifier,
    text: String,
    onDismiss: () -> Unit
) {
    val blocks = remember(text) { parseHoverBlocks(text) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringRes(R.string.lsp_hover_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                blocks.forEach { block ->
                    if (block.isCode) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = block.text,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Hebt in [label] den aktiven Parameter hervor (erste Fundstelle des Parameter-Labels). */
internal fun signatureAnnotated(label: String, parameter: String?, highlight: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        val start = parameter?.takeIf { it.isNotEmpty() }?.let { label.indexOf(it) } ?: -1
        if (start < 0) {
            append(label)
        } else {
            append(label.substring(0, start))
            withStyle(highlight) { append(parameter) }
            append(label.substring(start + parameter!!.length))
        }
    }

/** Schmale Parameter-Hinweiszeile über dem Editor (Signaturhilfe). */
@Composable
fun SignatureHelpBar(
    modifier: Modifier = Modifier,
    help: LspSignatureHelp
) {
    val signature = help.signatures.getOrNull(help.activeSignature) ?: return
    val activeIndex = signature.activeParameter ?: help.activeParameter
    val parameter = signature.parameters.getOrNull(activeIndex)
    val highlight = SpanStyle(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        background = MaterialTheme.colorScheme.primaryContainer
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = signatureAnnotated(signature.label, parameter, highlight),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
