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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.domain.model.ConflictResolution
import com.codeforge.core.domain.model.ConflictSegment
import com.codeforge.core.domain.model.WordDiff
import com.codeforge.core.domain.model.WordDiffSpan
import com.codeforge.core.domain.model.WordDiffType
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

private val OURS_BG = Color(0x3329A329)
private val THEIRS_BG = Color(0x334C8DFF)
private val BASE_BG = Color(0x33FFB300)
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
                            if (state.isLoading) stringRes(R.string.common_lade) else stringRes(R.string.git_konfliktblock_bloecke_gewaehlt, state.conflictCount, state.conflictCount - state.unresolved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.conflictCount > 0 && !state.isLoading) {
                        IconButton(
                            onClick = { onEvent(GitUiEvent.ConflictUndo) },
                            enabled = !busy && state.history.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, stringRes(R.string.git_konflikt_rueckgaengig))
                        }
                        IconButton(onClick = { onEvent(GitUiEvent.ConflictTogglePreview) }) {
                            Icon(
                                if (state.showPreview) Icons.Filled.Edit else Icons.Filled.Visibility,
                                stringRes(R.string.git_konflikt_vorschau)
                            )
                        }
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
                        val listState = rememberLazyListState()
                        val conflictSegmentIndices = remember(state.segments) {
                            state.segments.mapIndexedNotNull { index, seg -> if (seg is ConflictSegment.Conflict) index else null }
                        }
                        LaunchedEffect(state.activeBlockIndex) {
                            if (state.activeBlockIndex in conflictSegmentIndices.indices) {
                                listState.animateScrollToItem(conflictSegmentIndices[state.activeBlockIndex])
                            }
                        }

                        if (state.totalFiles > 1 || state.conflictCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.totalFiles > 1) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val prevPath = state.allConflictingPaths[state.currentFileIndex - 1]
                                                onEvent(GitUiEvent.ConflictNavigateFile(prevPath))
                                            },
                                            enabled = !busy && state.hasPrevFile
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringRes(R.string.git_vorherige_datei))
                                        }
                                        Text(
                                            stringRes(R.string.git_datei_x_von_y, state.currentFileIndex + 1, state.totalFiles),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        IconButton(
                                            onClick = {
                                                val nextPath = state.allConflictingPaths[state.currentFileIndex + 1]
                                                onEvent(GitUiEvent.ConflictNavigateFile(nextPath))
                                            },
                                            enabled = !busy && state.hasNextFile
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringRes(R.string.git_naechste_datei))
                                        }
                                    }
                                } else {
                                    Box {}
                                }
                                if (state.conflictCount > 1) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                onEvent(GitUiEvent.ConflictJumpToBlock(state.activeBlockIndex - 1))
                                            },
                                            enabled = state.activeBlockIndex > 0
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowUp, stringRes(R.string.git_vorheriger_konflikt))
                                        }
                                        Text(
                                            stringRes(R.string.git_konflikt_x_von_y, (state.activeBlockIndex + 1).coerceAtMost(state.conflictCount), state.conflictCount),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        IconButton(
                                            onClick = {
                                                onEvent(GitUiEvent.ConflictJumpToBlock(state.activeBlockIndex + 1))
                                            },
                                            enabled = state.activeBlockIndex < state.conflictCount - 1
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, stringRes(R.string.git_naechster_konflikt))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }

                        if (state.showPreview) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringRes(R.string.git_konflikt_vorschau),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                SelectionContainer(
                                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = state.previewText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onEvent(GitUiEvent.ConflictChooseAll(ConflictResolution.OURS)) }) { Text(stringRes(R.string.git_alle_aktuell)) }
                                TextButton(onClick = { onEvent(GitUiEvent.ConflictChooseAll(ConflictResolution.THEIRS)) }) { Text(stringRes(R.string.git_alle_eingehend)) }
                                TextButton(onClick = { onEvent(GitUiEvent.ConflictChooseAll(ConflictResolution.BOTH)) }) { Text(stringRes(R.string.git_alle_beide)) }
                                FilterChip(
                                    selected = state.showThreeWay,
                                    onClick = { onEvent(GitUiEvent.ConflictToggleThreeWay) },
                                    label = { Text(stringRes(R.string.git_drei_wege_ansicht)) }
                                )
                                FilterChip(
                                    selected = state.wordDiffEnabled,
                                    onClick = { onEvent(GitUiEvent.ConflictToggleWordDiff) },
                                    label = { Text(stringRes(R.string.git_wortvergleich)) }
                                )
                            }
                            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                                items(state.segments.size, key = { it }) { i ->
                                    when (val seg = state.segments[i]) {
                                        is ConflictSegment.Plain -> PlainBlock(seg.lines)
                                        is ConflictSegment.Conflict -> ConflictBlock(
                                            block = seg,
                                            choice = state.choices[seg.index],
                                            customText = state.customTexts[seg.index],
                                            isEditing = state.editingBlockIndex == seg.index,
                                            showThreeWay = state.showThreeWay,
                                            wordDiffEnabled = state.wordDiffEnabled,
                                            onEvent = onEvent,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        Button(
                            onClick = { onEvent(GitUiEvent.ConflictApply) },
                            enabled = !busy && (state.choices.isNotEmpty() || state.customTexts.isNotEmpty()),
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
private fun ConflictBlock(
    block: ConflictSegment.Conflict,
    choice: ConflictResolution?,
    customText: String?,
    isEditing: Boolean,
    showThreeWay: Boolean,
    wordDiffEnabled: Boolean,
    onEvent: (GitUiEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(R.string.git_conflict_n, block.index + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                if (customText != null) {
                    Text(
                        stringRes(R.string.git_konflikt_benutzerdefiniert),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (isEditing) {
                var editBuffer by remember(block.index) {
                    mutableStateOf(
                        customText ?: when (choice) {
                            ConflictResolution.OURS -> block.ours.joinToString("\n")
                            ConflictResolution.THEIRS -> block.theirs.joinToString("\n")
                            ConflictResolution.BOTH -> (block.ours + block.theirs).joinToString("\n")
                            ConflictResolution.BOTH_REVERSED -> (block.theirs + block.ours).joinToString("\n")
                            null -> block.ours.joinToString("\n")
                        }
                    )
                }
                OutlinedTextField(
                    value = editBuffer,
                    onValueChange = { editBuffer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringRes(R.string.git_konflikt_bearbeiten)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    minLines = 3,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onEvent(GitUiEvent.ConflictCancelEditing) }) {
                        Text(stringRes(R.string.common_abbrechen))
                    }
                    Button(
                        onClick = { onEvent(GitUiEvent.ConflictCustomText(block.index, editBuffer)) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringRes(R.string.git_konflikt_speichern))
                    }
                }
            } else {
                val (oursSpans, theirsSpans) = if (wordDiffEnabled && customText == null) {
                    remember(block.ours, block.theirs) {
                        WordDiff.diffWords(block.ours.joinToString("\n"), block.theirs.joinToString("\n"))
                    }
                } else null to null

                Side(
                    title = Res.string(R.string.common_aktuell) + block.oursLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                    lines = block.ours,
                    background = OURS_BG,
                    selected = (choice == ConflictResolution.OURS || choice == ConflictResolution.BOTH) && customText == null,
                    wordSpans = oursSpans
                )

                if (showThreeWay) {
                    val baseLines = block.base
                    if (baseLines != null) {
                        Side(
                            title = Res.string(R.string.git_basis) + block.baseLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                            lines = baseLines,
                            background = BASE_BG,
                            selected = false,
                            wordSpans = null
                        )
                    } else {
                        Text(
                            stringRes(R.string.git_keine_diff3_basis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }

                Side(
                    title = Res.string(R.string.git_eingehend) + block.theirsLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                    lines = block.theirs,
                    background = THEIRS_BG,
                    selected = (choice == ConflictResolution.THEIRS || choice == ConflictResolution.BOTH_REVERSED || choice == ConflictResolution.BOTH) && customText == null,
                    wordSpans = theirsSpans
                )

                if (customText != null) {
                    Side(Res.string(R.string.git_konflikt_benutzerdefiniert), customText.split('\n'), MaterialTheme.colorScheme.tertiaryContainer, selected = true)
                }

                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice(Res.string(R.string.common_aktuell), ConflictResolution.OURS, block, choice.takeIf { customText == null }, onEvent)
                    Choice(Res.string(R.string.git_eingehend), ConflictResolution.THEIRS, block, choice.takeIf { customText == null }, onEvent)
                    Choice(Res.string(R.string.git_beide), ConflictResolution.BOTH, block, choice.takeIf { customText == null }, onEvent)
                    Choice(Res.string(R.string.git_beide_umgekehrt), ConflictResolution.BOTH_REVERSED, block, choice.takeIf { customText == null }, onEvent)
                    FilterChip(
                        selected = customText != null,
                        onClick = { onEvent(GitUiEvent.ConflictStartEditing(block.index)) },
                        label = { Text(stringRes(R.string.git_konflikt_bearbeiten)) }
                    )
                }
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
private fun Side(
    title: String,
    lines: List<String>,
    background: Color,
    selected: Boolean,
    wordSpans: List<WordDiffSpan>? = null,
) {
    Column(Modifier.fillMaxWidth().background(background.copy(alpha = if (selected) 0.6f else 0.25f)).padding(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (lines.isEmpty()) {
            Text(stringRes(R.string.git_leer), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (wordSpans != null && wordSpans.isNotEmpty()) {
            val annotated = buildAnnotatedString {
                for (span in wordSpans) {
                    when (span.type) {
                        WordDiffType.UNCHANGED -> append(span.text)
                        WordDiffType.ADDED -> withStyle(SpanStyle(background = Color(0x664C8DFF), fontWeight = FontWeight.Bold)) {
                            append(span.text)
                        }
                        WordDiffType.REMOVED -> withStyle(SpanStyle(background = Color(0x66E53935), textDecoration = TextDecoration.LineThrough)) {
                            append(span.text)
                        }
                    }
                }
            }
            Text(text = annotated, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        } else {
            lines.forEach { Text(it.ifEmpty { " " }, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
    }
}
