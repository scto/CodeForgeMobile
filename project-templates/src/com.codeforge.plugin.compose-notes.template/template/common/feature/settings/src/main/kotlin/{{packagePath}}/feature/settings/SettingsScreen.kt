package {{packageName}}.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.ColorSwatch
import {{packageName}}.core.designsystem.component.GradientHeader
import {{packageName}}.core.designsystem.component.PillChip
import {{packageName}}.core.designsystem.component.SettingsRow
import {{packageName}}.core.designsystem.component.SettingsSection
import {{packageName}}.core.designsystem.component.ThemeTile
import {{packageName}}.core.designsystem.component.TilePreview
import {{packageName}}.core.designsystem.theme.AccentPalette
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Einstellungen: Design mit Vorschaukacheln, Akzentfarbe, Darstellung und Sortierung der Notizen. */
@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings = state.settings
    val spacing = AppTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        GradientHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.editor_back), tint = AppTheme.colors.onGradient)
                }
            }
        )
        SettingsSection(stringResource(R.string.settings_appearance)) {
            Row(Modifier.padding(spacing.m), horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeTile(
                        label = stringResource(mode.label()),
                        preview = mode.preview(),
                        selected = settings.themeMode == mode,
                        onClick = { onEvent(SettingsUiEvent.ThemeModeSelected(mode)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        SettingsSection(stringResource(R.string.settings_accent)) {
            Row(Modifier.padding(spacing.l), horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                Accent.entries.forEach { accent ->
                    ColorSwatch(
                        brush = AppGradients.forPalette(AccentPalette.valueOf(accent.name)),
                        selected = settings.accent == accent,
                        contentDescription = accent.name.lowercase(),
                        onClick = { onEvent(SettingsUiEvent.AccentSelected(accent)) }
                    )
                }
            }
        }
        SettingsSection(stringResource(R.string.settings_notes)) {
            SettingsRow(icon = Icons.AutoMirrored.Filled.List, title = stringResource(R.string.settings_layout)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    PillChip(stringResource(R.string.settings_layout_grid), settings.layout == NoteLayout.GRID, {
                        onEvent(SettingsUiEvent.LayoutSelected(NoteLayout.GRID))
                    })
                    PillChip(stringResource(R.string.settings_layout_list), settings.layout == NoteLayout.LIST, {
                        onEvent(SettingsUiEvent.LayoutSelected(NoteLayout.LIST))
                    })
                }
            }
            SettingsRow(icon = Icons.Filled.Star, title = stringResource(R.string.settings_sort)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    PillChip(stringResource(R.string.settings_sort_updated), settings.sortOrder == SortOrder.UPDATED, {
                        onEvent(SettingsUiEvent.SortSelected(SortOrder.UPDATED))
                    })
                    PillChip(stringResource(R.string.settings_sort_title), settings.sortOrder == SortOrder.TITLE, {
                        onEvent(SettingsUiEvent.SortSelected(SortOrder.TITLE))
                    })
                }
            }
        }
        SettingsSection(stringResource(R.string.settings_about)) {
            SettingsRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.settings_version, "1.0")
            )
        }
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun ThemeMode.preview(): TilePreview = when (this) {
    ThemeMode.SYSTEM -> TilePreview.SPLIT
    ThemeMode.LIGHT -> TilePreview.LIGHT
    ThemeMode.DARK -> TilePreview.DARK
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme { SettingsScreen(state = SettingsUiState(), onEvent = {}, onBack = {}) }
}
