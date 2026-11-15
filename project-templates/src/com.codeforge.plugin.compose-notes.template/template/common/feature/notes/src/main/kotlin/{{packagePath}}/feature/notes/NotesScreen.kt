package {{packageName}}.feature.notes

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.EmptyState
import {{packageName}}.core.designsystem.component.GradientButton
import {{packageName}}.core.designsystem.component.GradientHeader
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.designsystem.theme.CardTint
import {{packageName}}.core.designsystem.theme.PillShape
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteColor
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun NotesRoute(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotesScreen(state, viewModel::onEvent, onOpenNote, onNewNote, onOpenSettings, modifier)
}

/** Übersicht: Kopf mit Verlauf und Suche, angeheftete und weitere Notizen im Raster oder als Liste. */
@Composable
fun NotesScreen(
    state: NotesUiState,
    onEvent: (NotesUiEvent) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = if (state.layout == NoteLayout.GRID) 2 else 1
    val spacing = AppTheme.spacing
    Box(modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            contentPadding = PaddingValues(bottom = 112.dp),
            verticalItemSpacing = spacing.m,
            horizontalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                GradientHeader(
                    title = stringResource(R.string.notes_title),
                    subtitle = pluralStringResource(
                        R.plurals.notes_count,
                        state.pinned.size + state.others.size,
                        state.pinned.size + state.others.size
                    ),
                    trailing = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, stringResource(R.string.notes_settings), tint = AppTheme.colors.onGradient)
                        }
                    }
                ) {
                    SearchField(state.query, { onEvent(NotesUiEvent.QueryChanged(it)) }, Modifier.padding(top = spacing.l))
                }
            }
            if (state.isEmpty && !state.isLoading) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        icon = Icons.Filled.Create,
                        title = stringResource(if (state.query.isBlank()) R.string.notes_empty_title else R.string.notes_no_results),
                        message = stringResource(R.string.notes_empty_text)
                    )
                }
            }
            section(R.string.notes_pinned, state.pinned, onOpenNote, onEvent)
            section(R.string.notes_others, state.others, onOpenNote, onEvent, showTitle = state.pinned.isNotEmpty())
        }
        GradientButton(
            text = stringResource(R.string.notes_new),
            icon = Icons.Filled.Add,
            onClick = onNewNote,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(spacing.l)
        )
    }
}

private fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.section(
    title: Int,
    notes: List<Note>,
    onOpenNote: (Long) -> Unit,
    onEvent: (NotesUiEvent) -> Unit,
    showTitle: Boolean = true
) {
    if (notes.isEmpty()) return
    if (showTitle) {
        item(span = StaggeredGridItemSpan.FullLine, key = "title-$title") {
            Text(
                stringResource(title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            )
        }
    }
    items(notes, key = { it.id }) { note ->
        NoteCard(
            note = note,
            onClick = { onOpenNote(note.id) },
            onTogglePin = { onEvent(NotesUiEvent.PinToggled(note.id)) },
            modifier = Modifier.padding(horizontal = 16.dp).animateItem()
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.notes_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = PillShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.glass,
            unfocusedContainerColor = AppTheme.colors.glass,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Notizkarte in der Notizfarbe; hell und dunkel jeweils passend getönt. */
@Composable
fun NoteCard(note: Note, onClick: () -> Unit, onTogglePin: () -> Unit, modifier: Modifier = Modifier) {
    val tint = note.color.tint()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (AppTheme.isDark) tint.dark else tint.light,
        modifier = modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(AppTheme.spacing.l), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    note.title.ifBlank { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 10.dp)
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (note.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = stringResource(if (note.pinned) R.string.notes_unpin else R.string.notes_pin),
                        tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (note.content.isNotBlank()) {
                Text(note.content, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            if (note.updatedAt > 0) {
                Text(
                    DateUtils.getRelativeTimeSpanString(note.updatedAt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Kartenfarbe des Designsystems zur Notizfarbe. */
fun NoteColor.tint(): CardTint = CardTint.valueOf(name)

@Preview(showBackground = true)
@Composable
private fun NotesScreenPreview() {
    AppTheme {
        NotesScreen(
            state = NotesUiState(
                pinned = listOf(Note(1, "Willkommen", "Erste Notiz", NoteColor.LAVENDER, pinned = true)),
                others = listOf(Note(2, "Einkauf", "Milch, Brot", NoteColor.MINT), Note(3, "Idee", "Podcast", NoteColor.SKY)),
                isLoading = false
            ),
            onEvent = {},
            onOpenNote = {},
            onNewNote = {},
            onOpenSettings = {}
        )
    }
}
