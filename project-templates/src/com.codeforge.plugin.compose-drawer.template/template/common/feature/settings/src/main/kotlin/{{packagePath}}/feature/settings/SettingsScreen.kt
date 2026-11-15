package {{packageName}}.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Design (System/Hell/Dunkel), dynamische Farben und Info. */
@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle(stringResource(R.string.settings_appearance))
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(stringResource(mode.label())) },
                    leadingContent = { RadioButton(selected = state.settings.themeMode == mode, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = state.settings.themeMode == mode,
                        role = Role.RadioButton,
                        onClick = { onEvent(SettingsUiEvent.ThemeModeSelected(mode)) }
                    )
                )
            }
        }
        // Dynamische Farben gibt es erst ab Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_text)) },
                trailingContent = {
                    Switch(
                        checked = state.settings.dynamicColor,
                        onCheckedChange = { onEvent(SettingsUiEvent.DynamicColorChanged(it)) }
                    )
                }
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(R.string.settings_about))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text("1.0") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) }
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(state = SettingsUiState(), onEvent = {})
}
