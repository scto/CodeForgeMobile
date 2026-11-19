/**
 * Plugin: Daten-Betrachter
 *
 * Zwei Vorschauen: Tabelle für CSV/TSV (Spalte antippen = sortieren, Suchfeld filtert Zeilen) und Baum für JSON
 * (Knoten antippen = auf-/zuklappen). Große Dateien werden auf [MAX_ROWS] Zeilen begrenzt.
 */
package com.codeforge.plugin.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPreview
import com.codeforge.libs.plugin_ui_api.PluginUiExtensionPoints
import com.codeforge.libs.plugin_ui_api.PreviewFile
import java.io.File

class DataViewerPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginUiExtensionPoints.PREVIEW, TablePreview)
        context.register(PluginUiExtensionPoints.PREVIEW, JsonPreview)
    }

    override fun onUnload() = Unit
}

private const val MAX_ROWS = 5_000

private fun textOf(file: PreviewFile): String =
    file.text ?: runCatching { File(file.path).readText() }.getOrDefault("")

private object TablePreview : PluginPreview {
    override val id = "table"
    override val title = "Als Tabelle anzeigen"
    override val filePatterns = listOf("*.{csv,tsv}")

    @Composable
    override fun Content(file: PreviewFile, host: PanelHost, modifier: Modifier) {
        val text = remember(file) { textOf(file) }
        val rows = remember(text) {
            Csv.parse(text, Csv.guessSeparator(file.path, text)).filter { r -> r.any { it.isNotBlank() } }.take(MAX_ROWS + 1)
        }
        var sortColumn by remember { mutableStateOf(-1) }
        var descending by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        val header = rows.firstOrNull().orEmpty()
        val body by remember(rows) {
            derivedStateOf {
                val filtered = rows.drop(1).filter { r -> query.isBlank() || r.any { it.contains(query, ignoreCase = true) } }
                if (sortColumn < 0) filtered
                else filtered.sortedWith(compareBy(Csv.cellComparator) { it.getOrElse(sortColumn) { "" } })
                    .let { if (descending) it.reversed() else it }
            }
        }
        Column(modifier) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Filtern") }, modifier = Modifier.fillMaxWidth())
            Text("${body.size} Zeilen · ${header.size} Spalten", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp))
            val scroll = rememberScrollState()
            Row(Modifier.horizontalScroll(scroll)) {
                header.forEachIndexed { c, name ->
                    val marker = if (c == sortColumn) (if (descending) " ▼" else " ▲") else ""
                    Text(
                        name + marker,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp).clickable {
                            if (sortColumn == c) descending = !descending else { sortColumn = c; descending = false }
                        }.padding(6.dp)
                    )
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(body) { row ->
                    Row(Modifier.horizontalScroll(scroll)) {
                        header.indices.forEach { c ->
                            Text(
                                row.getOrElse(c) { "" }, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp).padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private object JsonPreview : PluginPreview {
    override val id = "json"
    override val title = "JSON-Baum"
    override val filePatterns = listOf("*.{json,geojson}")

    @Composable
    override fun Content(file: PreviewFile, host: PanelHost, modifier: Modifier) {
        val text = remember(file) { textOf(file) }
        val parsed = remember(text) { runCatching { Json.parse(text) } }
        val expanded = remember(text) { mutableStateMapOf("$" to true) }
        val error = parsed.exceptionOrNull()
        if (error != null) {
            Text("Ungültiges JSON: ${error.message}", color = MaterialTheme.colorScheme.error, modifier = modifier)
            return
        }
        val lines = remember(parsed, expanded.toMap()) { flatten(parsed.getOrThrow(), expanded) }
        LazyColumn(modifier.heightIn(max = 600.dp)) {
            items(lines, key = { it.path }) { line ->
                Text(
                    "  ".repeat(line.depth) + line.label,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = line.container) {
                        expanded[line.path] = !(expanded[line.path] ?: false)
                    }.padding(vertical = 3.dp)
                )
            }
        }
    }
}

/** Zeile im JSON-Baum. */
internal data class TreeLine(val path: String, val depth: Int, val label: String, val container: Boolean)

/** Flacht den Baum zu Zeilen ab; aufgeklappt ist, was in [expanded] auf `true` steht. */
internal fun flatten(root: JsonNode, expanded: Map<String, Boolean>): List<TreeLine> {
    val out = ArrayList<TreeLine>()
    fun walk(name: String, node: JsonNode, path: String, depth: Int) {
        val open = expanded[path] == true
        when (node) {
            is JsonNode.Obj -> {
                out += TreeLine(path, depth, "${if (open) "▾" else "▸"} $name {${node.entries.size}}", true)
                if (open) node.entries.forEach { (k, v) -> walk("\"$k\":", v, "$path.$k", depth + 1) }
            }
            is JsonNode.Arr -> {
                out += TreeLine(path, depth, "${if (open) "▾" else "▸"} $name [${node.items.size}]", true)
                if (open) node.items.forEachIndexed { i, v -> walk("$i:", v, "$path[$i]", depth + 1) }
            }
            is JsonNode.Str -> out += TreeLine(path, depth, "  $name \"${node.value.take(200)}\"", false)
            is JsonNode.Num -> out += TreeLine(path, depth, "  $name ${node.raw}", false)
            is JsonNode.Bool -> out += TreeLine(path, depth, "  $name ${node.value}", false)
            JsonNode.Null -> out += TreeLine(path, depth, "  $name null", false)
        }
    }
    walk("", root, "$", 0)
    return out
}
