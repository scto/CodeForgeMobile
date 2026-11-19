/**
 * Plugin: Markdown-Vorschau
 *
 * Registriert eine Vorschau für `*.md`/`*.markdown`. Geladen wird das Plugin erst, wenn eine Markdown-Datei
 * geöffnet wird (`activationEvents: onLanguage:markdown`).
 */
package com.codeforge.plugin.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPreview
import com.codeforge.libs.plugin_ui_api.PluginUiExtensionPoints
import com.codeforge.libs.plugin_ui_api.PreviewFile
import java.io.File

class MarkdownPreviewPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginUiExtensionPoints.PREVIEW, MarkdownPreview)
    }

    override fun onUnload() = Unit
}

private object MarkdownPreview : PluginPreview {
    override val id = "markdown"
    override val title = "Markdown-Vorschau"
    override val filePatterns = listOf("*.{md,markdown,mdown}")

    @Composable
    override fun Content(file: PreviewFile, host: PanelHost, modifier: Modifier) {
        val text = file.text ?: remember(file.path) { runCatching { File(file.path).readText() }.getOrDefault("") }
        val blocks = remember(text) { MarkdownParser.parse(text) }
        LazyColumn(modifier = modifier.heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(blocks) { block -> Block(block, host, File(file.path).parentFile) }
        }
    }
}

@Composable
private fun Block(block: MdBlock, host: PanelHost, dir: File?) {
    when (block) {
        is MdBlock.Heading -> Inline(
            block.text, host, dir,
            when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }.copy(fontWeight = FontWeight.SemiBold)
        )
        is MdBlock.Paragraph -> Inline(block.text, host, dir, MaterialTheme.typography.bodyMedium)
        is MdBlock.Code -> Text(
            block.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        )
        is MdBlock.Quote -> Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(3.dp).heightIn(min = 24.dp).background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.blocks.forEach { Block(it, host, dir) }
            }
        }
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    val bullet = when {
                        item.checked == true -> "☑"
                        item.checked == false -> "☐"
                        block.ordered -> "${block.start + index}."
                        else -> "•"
                    }
                    Text(bullet, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(28.dp))
                    Inline(item.text, host, dir, MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is MdBlock.Table -> Column(
            Modifier.horizontalScroll(rememberScrollState())
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
        ) {
            (listOf(block.header) + block.rows).forEachIndexed { r, row ->
                Row {
                    row.forEach { cell ->
                        Text(
                            cell,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal),
                            modifier = Modifier.width(140.dp).padding(8.dp)
                        )
                    }
                }
                if (r == 0) HorizontalDivider()
            }
        }
        MdBlock.Rule -> HorizontalDivider()
    }
}

@Composable
private fun Inline(text: String, host: PanelHost, dir: File?, style: androidx.compose.ui.text.TextStyle) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(text, linkColor) { annotate(InlineMarkdown.parse(text), linkColor, codeBg) }
    val uri = LocalUriHandler.current
    ClickableText(annotated, style = style.copy(color = MaterialTheme.colorScheme.onSurface)) { offset ->
        val link = annotated.getStringAnnotations("link", offset, offset).firstOrNull()?.item ?: return@ClickableText
        when {
            link.startsWith("http://") || link.startsWith("https://") -> runCatching { uri.openUri(link) }
            dir != null -> File(dir, link.substringBefore('#')).takeIf { it.isFile }?.let { host.openFile(it.absolutePath) }
        }
    }
}

private fun annotate(spans: List<MdSpan>, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        val start = length
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            background = if (s.code) codeBg else Color.Unspecified,
            color = if (s.link != null) linkColor else Color.Unspecified,
            textDecoration = when {
                s.link != null -> TextDecoration.Underline
                s.strike -> TextDecoration.LineThrough
                else -> null
            }
        )
        pushStyle(style)
        append(s.text)
        pop()
        s.link?.let { addStringAnnotation("link", it, start, length) }
    }
}
