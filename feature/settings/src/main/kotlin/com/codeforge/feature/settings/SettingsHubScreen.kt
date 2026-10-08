/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

private data class SettingsEntry(@StringRes val labelRes: Int, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SettingsHubRoute(
    modifier: Modifier = Modifier,
    onNavigateToTheme: () -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSdkManager: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToGit: () -> Unit
) {
    val entries = listOf(
        SettingsEntry(R.string.settings_hub_theme, Icons.Filled.Palette, onNavigateToTheme),
        SettingsEntry(R.string.common_editor, Icons.Filled.Code, onNavigateToEditor),
        SettingsEntry(R.string.settings_hub_terminal, Icons.Filled.Terminal, onNavigateToTerminal),
        SettingsEntry(R.string.settings_hub_git, Icons.Filled.Source, onNavigateToGit),
        SettingsEntry(R.string.sdkmanager_title, Icons.Filled.SdCard, onNavigateToSdkManager),
        SettingsEntry(R.string.settings_hub_plugins, Icons.Filled.Extension, onNavigateToPlugins)
    )

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringRes(R.string.settings_hub_title)) }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    onClick = entry.onClick
                ) {
                    ListItem(
                        headlineContent = { Text(stringRes(entry.labelRes)) },
                        leadingContent = { Icon(entry.icon, contentDescription = null) }
                    )
                }
            }
        }
    }
}
