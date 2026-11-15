package {{packageName}}.feature.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.ColorSwatch
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.designsystem.theme.CardTint
import {{packageName}}.core.domain.model.Note
import {{packageName}}.core.domain.model.NoteColor
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun EditorRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    EditorScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Editor: Hintergrund in der Notizfarbe (animiert), Titel, Text und Farbleiste. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: EditorUiState, onEvent: (EditorUiEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tint = CardTint.valueOf(state.note.color.name)
    val background by animateColorAsState(if (AppTheme.isDark) tint.dark else tint.light, label = "noteBackground")
    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.editor_back)) }
                },
                actions = {
                    IconButton(onClick = { onEvent(EditorUiEvent.PinToggled) }) {
                        Icon(
                            if (state.note.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                            stringResource(if (state.note.pinned) R.string.notes_unpin else R.string.notes_pin)
                        )
                    }
                    IconButton(onClick = { onEvent(EditorUiEvent.DeleteClicked) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.editor_delete))
                    }
                }
            )
        },
        bottomBar = { ColorBar(selected = state.note.color, onSelect = { onEvent(EditorUiEvent.ColorSelected(it)) }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.l)
        ) {
            PlainField(
                value = state.note.title,
                onValueChange = { onEvent(EditorUiEvent.TitleChanged(it)) },
                placeholder = stringResource(R.string.editor_title_hint),
                style = MaterialTheme.typography.headlineMedium
            )
            PlainField(
                value = state.note.content,
                onValueChange = { onEvent(EditorUiEvent.ContentChanged(it)) },
                placeholder = stringResource(R.string.editor_content_hint),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(EditorUiEvent.DeleteDismissed) },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_text)) },
            confirmButton = {
                TextButton(onClick = { onEvent(EditorUiEvent.DeleteConfirmed) }) { Text(stringResource(R.string.editor_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(EditorUiEvent.DeleteDismissed) }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/** Textfeld ohne Rahmen mit Platzhalter. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = style, color = color.copy(alpha = 0.4f))
            inner()
        }
    )
}

@Composable
private fun ColorBar(selected: NoteColor, onSelect: (NoteColor) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), modifier = modifier.navigationBarsPadding()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AppTheme.spacing.l)
        ) {
            items(NoteColor.entries) { color ->
                val tint = CardTint.valueOf(color.name)
                ColorSwatch(
                    brush = SolidColor(if (AppTheme.isDark) tint.dark else tint.light),
                    selected = color == selected,
                    contentDescription = color.name.lowercase(),
                    onClick = { onSelect(color) }
                )
            }
        }
    }
}

@Preview
@Composable
private fun EditorScreenPreview() {
    AppTheme {
        EditorScreen(
            state = EditorUiState(Note(1, "Einkauf", "Milch\nBrot", NoteColor.MINT, pinned = true), isLoaded = true),
            onEvent = {},
            onBack = {}
        )
    }
}
