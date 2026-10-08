// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.domain.model.ConflictResolution
import com.codeforge.core.domain.model.ConflictSegment
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

private val OURS_BG = Color(0x3329A329)
private val THEIRS_BG = Color(0x334C8DFF)
private const val PLAIN_CONTEXT = 2

/**
 * Block-weiser Konflikt-Editor: je Konfliktblock Aktuell / Eingehend / Beide / Beide (umgekehrt) wählen.
 * Nach „Übernehmen“ wird die Datei geschrieben; sind alle Blöcke gewählt, wird sie zusätzlich vorgemerkt.
 */
@Composable
internal fun ConflictEditorDialog(
    state: ConflictEditorState,
    busy: Boolean,
    onEvent: (GitUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = { onEvent(GitUiEvent.CloseConflict) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (state.isLoading) "Lade …" else stringRes(R.string.git_konfliktblock_bloecke_gewaehlt, state.conflictCount, state.conflictCount - state.unresolved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEvent(GitUiEvent.CloseConflict) }) { Icon(Icons.Filled.Close, stringRes(R.string.common_schliessen)) }
                }
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.conflictCount == 0 -> Text(
                        state.error ?: stringRes(R.string.git_keine_konfliktmarker_gefunden),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onEvent(GitUiEvent.ConflictChooseAll(ConflictResolution.OURS)) }) { Text(stringRes(R.string.git_alle_aktuell)) }
                            TextButton(onClick = { onEvent(GitUiEvent.ConflictChooseAll(ConflictResolution.THEIRS)) }) { Text(stringRes(R.string.git_alle_eingehend)) }
                            TextButton(onClick = { onEvent(GitUiEvent.ConflictChooseAll(ConflictResolution.BOTH)) }) { Text(stringRes(R.string.git_alle_beide)) }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(state.segments.size, key = { it }) { i ->
                                when (val seg = state.segments[i]) {
                                    is ConflictSegment.Plain -> PlainBlock(seg.lines)
                                    is ConflictSegment.Conflict -> ConflictBlock(seg, state.choices[seg.index], onEvent)
                                }
                            }
                        }
                        HorizontalDivider()
                        Button(
                            onClick = { onEvent(GitUiEvent.ConflictApply) },
                            enabled = !busy && state.choices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        ) {
                            Text(if (state.unresolved == 0) stringRes(R.string.git_uebernehmen_vormerken) else stringRes(R.string.git_uebernehmen_offen, state.unresolved))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainBlock(lines: List<String>) {
    // Lange unveränderte Abschnitte einklappen: nur Kontext am Rand zeigen
    val shown: List<String?> = if (lines.size > PLAIN_CONTEXT * 2 + 2) {
        lines.take(PLAIN_CONTEXT) + listOf<String?>(null) + lines.takeLast(PLAIN_CONTEXT)
    } else lines
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        shown.forEach { line ->
            Text(
                text = line ?: stringRes(R.string.git_unveraenderte_zeilen, lines.size - PLAIN_CONTEXT * 2),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConflictBlock(block: ConflictSegment.Conflict, choice: ConflictResolution?, onEvent: (GitUiEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringRes(R.string.git_conflict_n, block.index + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Side(Res.string(R.string.common_aktuell) + block.oursLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(), block.ours, OURS_BG, choice == ConflictResolution.OURS || choice == ConflictResolution.BOTH)
            Side(Res.string(R.string.git_eingehend) + block.theirsLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(), block.theirs, THEIRS_BG, choice == ConflictResolution.THEIRS || choice == ConflictResolution.BOTH_REVERSED || choice == ConflictResolution.BOTH)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice(Res.string(R.string.common_aktuell), ConflictResolution.OURS, block, choice, onEvent)
                Choice(Res.string(R.string.git_eingehend), ConflictResolution.THEIRS, block, choice, onEvent)
                Choice(Res.string(R.string.git_beide), ConflictResolution.BOTH, block, choice, onEvent)
                Choice(Res.string(R.string.git_beide_umgekehrt), ConflictResolution.BOTH_REVERSED, block, choice, onEvent)
            }
        }
    }
}

@Composable
private fun Choice(label: String, value: ConflictResolution, block: ConflictSegment.Conflict, current: ConflictResolution?, onEvent: (GitUiEvent) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onEvent(GitUiEvent.ConflictChoice(block.index, if (current == value) null else value)) },
        label = { Text(label) },
    )
}

@Composable
private fun Side(title: String, lines: List<String>, background: Color, selected: Boolean) {
    Column(Modifier.fillMaxWidth().background(background.copy(alpha = if (selected) 0.6f else 0.25f)).padding(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (lines.isEmpty()) Text(stringRes(R.string.git_leer), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { Text(it.ifEmpty { " " }, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
    }
}
