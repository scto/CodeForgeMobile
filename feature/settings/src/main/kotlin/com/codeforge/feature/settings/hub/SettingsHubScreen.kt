@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:settings:hub
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.hub

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class SettingsEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun SettingsHubRoute(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigateToTheme: () -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToFileTree: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSdkManager: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToExtensions: () -> Unit = onNavigateToPlugins,
    onNavigateToDebug: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val entries = listOf(
        SettingsEntry(
            title = "Erscheinungsbild & Theme",
            description = "Farbschema, Dark/Light Mode & Theme Studio Anpassungen",
            icon = Icons.Filled.Palette,
            onClick = onNavigateToTheme
        ),
        SettingsEntry(
            title = "Editor",
            description = "Zeilenumbruch, Schriftart, Einrückung, Formatierung & Verhalten",
            icon = Icons.Filled.Code,
            onClick = onNavigateToEditor
        ),
        SettingsEntry(
            title = "Dateibaum & Ansichten",
            description = "Sortierung, Versteckte Dateien, Module/Projekt/Datei-Ansichtsmodus",
            icon = Icons.Filled.Folder,
            onClick = onNavigateToFileTree
        ),
        SettingsEntry(
            title = "Terminal",
            description = "Distro-Auswahl (Alpine/Ubuntu/Debian), Virtuelle Tasten & Farbschemas",
            icon = Icons.Filled.Terminal,
            onClick = onNavigateToTerminal
        ),
        SettingsEntry(
            title = "SDK Manager",
            description = "Android SDK Build-Tools, NDK, CMake & Java 17 verwalten",
            icon = Icons.Filled.SdCard,
            onClick = onNavigateToSdkManager
        ),
        SettingsEntry(
            title = "Plugins & Erweiterungen (Extensions)",
            description = "Installierte Erweiterungen, Language Server & Tooling Bridge",
            icon = Icons.Filled.Extension,
            onClick = onNavigateToExtensions
        ),
        SettingsEntry(
            title = "Debug & Diagnose",
            description = "Logging-Level, Tracing & Systemdiagnose konfigurieren",
            icon = Icons.Filled.BugReport,
            onClick = onNavigateToDebug
        ),
        SettingsEntry(
            title = "Über CodeForge Mobile",
            description = "App-Version, Lizenzen, Systemübersicht & Entwickler-Informationen",
            icon = Icons.Filled.Info,
            onClick = onNavigateToAbout
        )
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.settings_title)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = entry.onClick,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = entry.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}
