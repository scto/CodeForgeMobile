/**
 * Plugin: SQLite-Betrachter
 *
 * Bereich „SQLite-Betrachter“ (Befehlspalette): Datenbank wählen → Tabellen → Zeilen (max. [ROW_LIMIT]) oder eigene
 * lesende Abfrage. Datenbanken werden mit `OPEN_READONLY` geöffnet.
 */
package com.codeforge.plugin.sqlite

import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPanel
import com.codeforge.libs.plugin_ui_api.PluginUiExtensionPoints
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqliteViewerPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginUiExtensionPoints.PANEL, SqlitePanel)
    }

    override fun onUnload() = Unit
}

private const val ROW_LIMIT = 200

/** Ergebnis einer Abfrage. */
private data class QueryResult(val columns: List<String>, val rows: List<List<String>>, val error: String? = null)

private fun query(db: File, sql: String): QueryResult {
    SqlGuard.violation(sql)?.let { return QueryResult(emptyList(), emptyList(), it) }
    return runCatching {
        SQLiteDatabase.openDatabase(db.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery(SqlGuard.withLimit(sql, ROW_LIMIT), null).use { c ->
                val cols = c.columnNames.toList()
                val rows = ArrayList<List<String>>()
                while (c.moveToNext() && rows.size < ROW_LIMIT) {
                    rows += cols.indices.map { i ->
                        when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                            android.database.Cursor.FIELD_TYPE_BLOB -> "<BLOB ${c.getBlob(i).size} B>"
                            else -> c.getString(i).orEmpty()
                        }
                    }
                }
                QueryResult(cols, rows)
            }
        }
    }.getOrElse { QueryResult(emptyList(), emptyList(), it.message ?: it.toString()) }
}

private object SqlitePanel : PluginPanel {
    override val id = "sqlite"
    override val title = "SQLite-Betrachter"

    @Composable
    override fun Content(host: PanelHost, modifier: Modifier) {
        val scope = rememberCoroutineScope()
        var databases by remember { mutableStateOf<List<File>?>(null) }
        var selected by remember { mutableStateOf<File?>(null) }
        var tables by remember { mutableStateOf<List<String>>(emptyList()) }
        var sql by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<QueryResult?>(null) }

        LaunchedEffect(host.projectRoot) {
            databases = withContext(Dispatchers.IO) {
                val root = host.projectRoot?.let(::File)
                val active = host.activeFilePath?.let(::File)?.takeIf { DatabaseFinder.isSqlite(it) }
                (listOfNotNull(active) + (root?.let { DatabaseFinder.find(it) }.orEmpty())).distinct()
            }
            if (selected == null) selected = databases?.firstOrNull()
        }
        LaunchedEffect(selected) {
            val db = selected ?: return@LaunchedEffect
            tables = withContext(Dispatchers.IO) {
                query(db, "SELECT name FROM sqlite_master WHERE type IN ('table','view') ORDER BY name").rows.map { it.first() }
            }
            result = null
        }
        fun run(statement: String) {
            val db = selected ?: return
            sql = statement
            scope.launch { result = withContext(Dispatchers.IO) { query(db, statement) } }
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val list = databases) {
                null -> Text("Suche Datenbanken …")
                emptyList<File>() -> Text("Keine SQLite-Datenbank im Projekt gefunden (.db, .sqlite, .sqlite3).")
                else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { f ->
                        FilterChip(selected = f == selected, onClick = { selected = f }, label = { Text(f.name) })
                    }
                }
            }
            if (tables.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tables.forEach { t -> FilterChip(selected = false, onClick = { run("SELECT * FROM \"$t\"") }, label = { Text(t) }) }
                }
            }
            if (selected != null) {
                OutlinedTextField(
                    sql, { sql = it }, label = { Text("SQL (nur lesend)") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Button(onClick = { run(sql) }, enabled = sql.isNotBlank()) { Text("Ausführen") }
            }
            result?.let { r -> ResultTable(r) }
        }
    }
}

@Composable
private fun ResultTable(r: QueryResult) {
    if (r.error != null) {
        Text(r.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        return
    }
    Text("${r.rows.size} Zeilen" + if (r.rows.size >= ROW_LIMIT) " (begrenzt)" else "", style = MaterialTheme.typography.labelSmall)
    val scroll = rememberScrollState()
    Row(Modifier.horizontalScroll(scroll)) {
        r.columns.forEach { c ->
            Text(c, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(140.dp).padding(6.dp))
        }
    }
    HorizontalDivider()
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(r.rows) { row ->
            Row(Modifier.horizontalScroll(scroll).clickable { }) {
                row.forEach { v ->
                    Text(v, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp).padding(6.dp))
                }
            }
        }
    }
}
