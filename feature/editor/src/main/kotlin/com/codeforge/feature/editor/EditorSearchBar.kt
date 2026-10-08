/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Suchen/Ersetzen in der aktiven Datei auf Basis der Engine aus :libs:code-tools
 * ([TextSearch]): Groß-/Kleinschreibung, ganzes Wort, Regex (inkl. `$1`/`${name}` im
 * Ersetzungstext), Trefferzähler, verständliche Regex-Fehler. Treffer werden im Editor per
 * Selektion angesteuert; Ersetzen läuft als Undo-fähiger Edit über [EditorController].
 */
package com.codeforge.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.code_tools.search.SearchOptions
import com.codeforge.libs.code_tools.search.TextMatch
import com.codeforge.libs.code_tools.search.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_MATCHES = 20_000

@Composable
fun EditorSearchBar(
    modifier: Modifier = Modifier,
    uiState: EditorUiState,
    onEvent: (EditorUiEvent) -> Unit,
    controller: EditorController?
) {
    val content = uiState.activeFile?.content.orEmpty()
    val options = remember(uiState.searchQuery, uiState.isCaseSensitiveSearch, uiState.isRegexSearch, uiState.isWholeWordSearch) {
        SearchOptions(uiState.searchQuery, uiState.isCaseSensitiveSearch, uiState.isRegexSearch, uiState.isWholeWordSearch)
    }

    // Trefferliste im Hintergrund (entprellt), damit Tippen auch in großen Dateien flüssig bleibt.
    val computed by produceState<Pair<SearchOptions, Result<List<TextMatch>>>?>(initialValue = null, content, options) {
        if (options.query.isEmpty()) { value = options to Result.success(emptyList()); return@produceState }
        delay(120)
        value = options to withContext(Dispatchers.Default) { TextSearch.findAll(content, options, limit = MAX_MATCHES) }
    }
    // `fresh`: Ergebnis gehört zu den aktuellen Optionen (sonst zeigt es noch den vorherigen Stand)
    val fresh = computed?.first == options
    val matches = computed?.second?.getOrNull().orEmpty()
    val error = if (fresh) computed?.second?.exceptionOrNull()?.message else null

    var current by remember { mutableIntStateOf(-1) }
    var selectOnNextUpdate by remember { mutableStateOf(false) }
    var replaceError by remember { mutableStateOf<String?>(null) }

    fun select(index: Int) {
        if (matches.isEmpty()) return
        val i = ((index % matches.size) + matches.size) % matches.size
        current = i
        controller?.selectRange(matches[i].start, matches[i].end)
    }

    // Bei neuer Trefferliste: ersten Treffer ab Cursor wählen (bzw. nach Ersetzen den nächsten).
    // Reine Textänderungen im Editor (Tippen) dürfen die Auswahl nicht an sich reißen.
    var lastOptions by remember { mutableStateOf<SearchOptions?>(null) }
    LaunchedEffect(matches, fresh) {
        if (!fresh) return@LaunchedEffect
        if (matches.isEmpty()) { current = -1; return@LaunchedEffect }
        when {
            selectOnNextUpdate -> {
                selectOnNextUpdate = false
                select(current.coerceIn(0, matches.size - 1))
            }
            lastOptions != options -> {
                val from = controller?.selection()?.first ?: 0
                select(matches.indexOfFirst { it.start >= from }.takeIf { it >= 0 } ?: 0)
            }
            else -> current = current.coerceIn(0, matches.size - 1)
        }
        lastOptions = options
    }

    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { onEvent(EditorUiEvent.SearchQueryChanged(it)); replaceError = null },
                    label = { Text(stringRes(R.string.editor_suchen)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(
                            when {
                                error != null -> error
                                uiState.searchQuery.isEmpty() -> ""
                                matches.isEmpty() -> Res.string(R.string.common_keine_treffer)
                                else -> "${current + 1} / ${matches.size}" + if (matches.size >= MAX_MATCHES) "+" else ""
                            },
                            maxLines = 2
                        )
                    }
                )
                IconButton(onClick = { select(current - 1) }, enabled = matches.isNotEmpty()) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringRes(R.string.editor_vorherige_fundstelle))
                }
                IconButton(onClick = { select(current + 1) }, enabled = matches.isNotEmpty()) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringRes(R.string.editor_naechste_fundstelle))
                }
                IconButton(onClick = { onEvent(EditorUiEvent.ToggleSearchBar) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.editor_suche_schliessen))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.replaceQuery,
                    onValueChange = { onEvent(EditorUiEvent.ReplaceQueryChanged(it)); replaceError = null },
                    label = { Text(stringRes(R.string.common_ersetzen_durch)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = replaceError != null,
                    supportingText = replaceError?.let { { Text(it, maxLines = 2) } }
                )
                IconButton(
                    enabled = current in matches.indices,
                    onClick = {
                        val m = matches.getOrNull(current) ?: return@IconButton
                        TextSearch.replaceAt(content, options, uiState.replaceQuery, m.start)
                            .onSuccess { r ->
                                if (r.count > 0) { selectOnNextUpdate = true; controller?.applyTextChange(r.text) }
                            }
                            .onFailure { replaceError = it.message }
                    }
                ) {
                    Icon(Icons.Filled.FindReplace, contentDescription = stringRes(R.string.editor_diese_fundstelle_ersetzen))
                }
                TextButton(
                    enabled = matches.isNotEmpty(),
                    onClick = {
                        TextSearch.replaceAll(content, options, uiState.replaceQuery)
                            .onSuccess { r -> if (r.count > 0) controller?.applyTextChange(r.text) }
                            .onFailure { replaceError = it.message }
                    }
                ) {
                    Text(stringRes(R.string.editor_alle))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.isCaseSensitiveSearch,
                    onClick = { onEvent(EditorUiEvent.ToggleCaseSensitiveSearch) },
                    label = { Text(stringRes(R.string.common_aa)) }
                )
                FilterChip(
                    selected = uiState.isWholeWordSearch,
                    onClick = { onEvent(EditorUiEvent.ToggleWholeWordSearch) },
                    label = { Text(stringRes(R.string.common_wort)) }
                )
                FilterChip(
                    selected = uiState.isRegexSearch,
                    onClick = { onEvent(EditorUiEvent.ToggleRegexSearch) },
                    label = { Text(".*") }
                )
            }
        }
    }
}
