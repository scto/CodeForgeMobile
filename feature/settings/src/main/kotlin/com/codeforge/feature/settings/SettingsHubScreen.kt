/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

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

private data class SettingsEntry(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SettingsHubRoute(
    modifier: Modifier = Modifier,
    onNavigateToTheme: () -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSdkManager: () -> Unit,
    onNavigateToPlugins: () -> Unit
) {
    val entries = listOf(
        SettingsEntry("Theme", Icons.Filled.Palette, onNavigateToTheme),
        SettingsEntry("Editor", Icons.Filled.Code, onNavigateToEditor),
        SettingsEntry("Terminal", Icons.Filled.Terminal, onNavigateToTerminal),
        SettingsEntry("SDK Manager", Icons.Filled.SdCard, onNavigateToSdkManager),
        SettingsEntry("Plugins", Icons.Filled.Extension, onNavigateToPlugins)
    )

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Einstellungen") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    onClick = entry.onClick
                ) {
                    ListItem(
                        headlineContent = { Text(entry.label) },
                        leadingContent = { Icon(entry.icon, contentDescription = null) }
                    )
                }
            }
        }
    }
}
