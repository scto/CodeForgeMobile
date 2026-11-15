package {{packageName}}.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import {{packageName}}.core.designsystem.icon.AppIcons
import {{packageName}}.core.designsystem.theme.AccentPalette
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Einstellungen: Design mit Vorschaukacheln, Akzentfarbe und Standardtempo der Wiedergabe. */
@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val settings = state.settings
    val spacing = AppTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        GradientHeader(title = stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle))
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
        SettingsSection(stringResource(R.string.settings_playback)) {
            SettingsRow(
                icon = AppIcons.Headphones,
                title = stringResource(R.string.settings_speed),
                subtitle = stringResource(R.string.settings_speed_text)
            )
            Row(Modifier.padding(horizontal = spacing.l, vertical = spacing.s), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                AppSettings.SPEEDS.forEach { speed ->
                    PillChip(
                        label = stringResource(R.string.player_speed, speed.toString().removeSuffix(".0")),
                        selected = settings.playbackSpeed == speed,
                        onClick = { onEvent(SettingsUiEvent.SpeedSelected(speed)) }
                    )
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
    AppTheme { SettingsScreen(state = SettingsUiState(), onEvent = {}) }
}
