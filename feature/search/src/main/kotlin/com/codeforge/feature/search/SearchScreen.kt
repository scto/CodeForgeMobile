/**
 * Modul: :feature:search
 * @author Thomas Schmid
 *
 * Projektweite Suche & Ersetzen, ausgelegt für den Navigation-Drawer (schmale Breite):
 * Eingabezeilen mit Toggle-Chips (Aa / Wort / Regex), optionale Filter, gruppierte Trefferliste.
 */
package com.codeforge.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.code_tools.search.FileMatches
import com.codeforge.libs.code_tools.search.TextMatch

/**
 * @param onMatchOpened wird aufgerufen, nachdem ein Treffer im Editor geöffnet wurde (Drawer schließen).
 * @param onMessage Snackbar-Text (der Host besitzt den SnackbarHost); `null` = Meldung verwerfen.
 */
@Composable
fun SearchRoute(
    rootPath: String,
    modifier: Modifier = Modifier,
    onMatchOpened: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(rootPath) { viewModel.onEvent(SearchUiEvent.Initialize(rootPath)) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SearchUiEffect.MatchOpened -> onMatchOpened()
                is SearchUiEffect.ShowSnackbar -> onMessage(effect.message)
            }
        }
    }
    SearchScreen(modifier = modifier, state = state, onEvent = viewModel::onEvent)
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchInputs(state = state, onEvent = onEvent)
        if (state.isSearching || state.isReplacing) LinearProgressIndicator(Modifier.fillMaxWidth())
        SummaryLine(state = state, onEvent = onEvent)
        HorizontalDivider()
        Results(state = state, onEvent = onEvent, modifier = Modifier.weight(1f))
    }

    if (state.confirmReplaceAll) {
        val r = state.result
        AlertDialog(
            onDismissRequest = { onEvent(SearchUiEvent.DismissReplaceAll) },
            title = { Text(stringRes(R.string.search_alle_ersetzen)) },
            text = {
                Text(
                    stringRes(R.string.search_treffer_in_datei_en_werden, r?.totalMatches ?: 0, r?.files?.size ?: 0, state.replacement)
                )
            },
            confirmButton = { TextButton(onClick = { onEvent(SearchUiEvent.ConfirmReplaceAll) }) { Text(stringRes(R.string.search_ersetzen)) } },
            dismissButton = { TextButton(onClick = { onEvent(SearchUiEvent.DismissReplaceAll) }) { Text(stringRes(R.string.common_abbrechen)) } },
        )
    }
}

@Composable
private fun SearchInputs(state: SearchUiState, onEvent: (SearchUiEvent) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringRes(R.string.search_im_projekt_suchen)) },
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailingIcon = {
                IconButton(onClick = { onEvent(SearchUiEvent.ToggleFilters) }) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringRes(R.string.search_dateifilter))
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = state.caseSensitive, onClick = { onEvent(SearchUiEvent.ToggleCase) }, label = { Text(stringRes(R.string.common_aa)) })
            FilterChip(selected = state.wholeWord, onClick = { onEvent(SearchUiEvent.ToggleWholeWord) }, label = { Text(stringRes(R.string.common_wort)) })
            FilterChip(selected = state.regex, onClick = { onEvent(SearchUiEvent.ToggleRegex) }, label = { Text(".*") })
            Box(Modifier.weight(1f))
            IconButton(onClick = { onEvent(SearchUiEvent.ToggleReplace) }) {
                Icon(Icons.Filled.FindReplace, contentDescription = stringRes(R.string.search_ersetzen_ein_ausblenden))
            }
        }
        if (state.showFilters) {
            OutlinedTextField(
                value = state.includeGlobs,
                onValueChange = { onEvent(SearchUiEvent.IncludeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.search_nur_dateien_kt_kts)) },
            )
            OutlinedTextField(
                value = state.excludeGlobs,
                onValueChange = { onEvent(SearchUiEvent.ExcludeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.search_ausschliessen_json)) },
            )
        }
        if (state.showReplace) {
            OutlinedTextField(
                value = state.replacement,
                onValueChange = { onEvent(SearchUiEvent.ReplacementChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (state.regex) stringRes(R.string.search_ersetzen_durch_1_name) else stringRes(R.string.common_ersetzen_durch)) },
            )
        }
    }
}

@Composable
private fun SummaryLine(state: SearchUiState, onEvent: (SearchUiEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val r = state.result
        val text = when {
            state.query.isEmpty() -> Res.string(R.string.search_suchbegriff_eingeben)
            r == null -> if (state.isSearching) Res.string(R.string.search_suche_laeuft) else ""
            else -> Res.string(R.string.search_treffer_in_datei_en, r.totalMatches, if (r.truncated) "+" else "", r.files.size)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onEvent(SearchUiEvent.Rerun) }) {
            Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.search_erneut_suchen))
        }
        if (state.showReplace) {
            TextButton(onClick = { onEvent(SearchUiEvent.RequestReplaceAll) }, enabled = state.canReplace) {
                Text(stringRes(R.string.search_alle_ersetzen_2))
            }
        }
    }
}

@Composable
private fun Results(state: SearchUiState, onEvent: (SearchUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val files = state.result?.files.orEmpty()
    if (files.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isSearching) CircularProgressIndicator(Modifier.size(28.dp))
            else if (state.query.isNotEmpty() && state.error == null && state.result != null) Text(stringRes(R.string.common_keine_treffer), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        files.forEach { file ->
            val collapsed = file.relativePath in state.collapsed
            item(key = "f:${file.path}") { FileHeader(file, collapsed, state.showReplace, onEvent) }
            if (!collapsed) {
                items(file.matches, key = { "m:${file.path}:${it.start}" }) { m ->
                    MatchRow(file.path, m, state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun FileHeader(file: FileMatches, collapsed: Boolean, showReplace: Boolean, onEvent: (SearchUiEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onEvent(SearchUiEvent.ToggleFile(file.relativePath)) }
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(file.relativePath.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val dir = file.relativePath.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) Text(dir, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${file.matches.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (showReplace) {
            IconButton(onClick = { onEvent(SearchUiEvent.ReplaceInFile(file.path)) }) {
                Icon(Icons.Filled.Replay, contentDescription = stringRes(R.string.search_in_dieser_datei_ersetzen), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MatchRow(path: String, match: TextMatch, state: SearchUiState, onEvent: (SearchUiEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onEvent(SearchUiEvent.OpenMatch(path, match)) }
            .padding(start = 30.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${match.line + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        Text(
            text = highlighted(match, MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.showReplace) {
            IconButton(onClick = { onEvent(SearchUiEvent.ReplaceOne(path, match)) }) {
                Icon(Icons.Filled.FindReplace, contentDescription = stringRes(R.string.search_diesen_treffer_ersetzen), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Trefferzeile mit hervorgehobenem Treffer; lange Zeilen werden um den Treffer herum gekürzt. */
private fun highlighted(match: TextMatch, accent: Color): AnnotatedString {
    val line = match.lineText
    val from = (match.column - CONTEXT).coerceAtLeast(0)
    val matchEnd = (match.column + (match.end - match.start)).coerceAtMost(line.length)
    val to = (matchEnd + CONTEXT * 2).coerceAtMost(line.length)
    val colStart = match.column.coerceIn(from, to)
    val colEnd = matchEnd.coerceIn(colStart, to)
    return buildAnnotatedString {
        if (from > 0) append("…")
        append(line.substring(from, colStart).trimStartIfFirst(from))
        withStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.18f))) { append(line.substring(colStart, colEnd)) }
        append(line.substring(colEnd, to))
        if (to < line.length) append("…")
    }
}

private fun String.trimStartIfFirst(from: Int): String = if (from == 0) trimStart() else this

private const val CONTEXT = 24
