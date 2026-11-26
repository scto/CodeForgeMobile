/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsLinkRow
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel

private data class SettingsEntry(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    @StringRes val summaryRes: Int? = null
)

private data class SettingsSection(@StringRes val titleRes: Int, val entries: List<SettingsEntry>)

/** Route der Einstellungsübersicht, gegliedert in Abschnitte wie in CodeAssist. */
@Suppress("LongParameterList")
@Composable
fun SettingsHubRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigateToTheme: () -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSdkManager: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToSigning: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToBasti: () -> Unit = {},
    onNavigateToStore: () -> Unit = {},
    onNavigateToPackages: () -> Unit = {},
    onNavigateToProjectsRoot: () -> Unit = {},
    onNavigateToBuildRun: () -> Unit = {},
    onNavigateToAi: () -> Unit = {},
    onNavigateToComposePreview: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onNavigateToCodeStyle: () -> Unit = {},
    onNavigateToKeymap: () -> Unit = {},
    onNavigateToSymbols: () -> Unit = {},
    onNavigateToSnippets: () -> Unit = {}
) {
    val sections = listOf(
        SettingsSection(
            R.string.settings_section_appearance_editor,
            listOf(
                SettingsEntry(R.string.settings_hub_theme, Icons.Filled.Palette, onNavigateToTheme, R.string.settings_hub_theme_summary),
                SettingsEntry(R.string.common_editor, Icons.Filled.Code, onNavigateToEditor, R.string.settings_hub_editor_summary),
                SettingsEntry(R.string.settings_color_scheme_title, Icons.Filled.ColorLens, onNavigateToColorScheme, R.string.settings_hub_color_scheme_summary),
                SettingsEntry(R.string.settings_code_style_title, Icons.AutoMirrored.Filled.FormatAlignLeft, onNavigateToCodeStyle, R.string.settings_hub_code_style_summary),
                SettingsEntry(R.string.settings_keymap_title, Icons.Filled.Keyboard, onNavigateToKeymap, R.string.settings_hub_keymap_summary),
                SettingsEntry(R.string.settings_symbols_title, Icons.Filled.DataObject, onNavigateToSymbols, R.string.settings_hub_symbols_summary),
                SettingsEntry(R.string.snippets_title, Icons.AutoMirrored.Filled.ShortText, onNavigateToSnippets)
            )
        ),
        SettingsSection(
            R.string.settings_section_build_run,
            listOf(
                SettingsEntry(R.string.settings_hub_build_run, Icons.Filled.PlayArrow, onNavigateToBuildRun, R.string.settings_hub_build_run_summary),
                SettingsEntry(R.string.sdkmanager_title, Icons.Filled.SdCard, onNavigateToSdkManager, R.string.settings_hub_sdk_summary),
                SettingsEntry(R.string.settings_keystore_manager_title, Icons.Filled.Key, onNavigateToSigning, R.string.settings_hub_keystore_summary)
            )
        ),
        SettingsSection(
            R.string.settings_section_more,
            listOf(
                SettingsEntry(R.string.settings_hub_terminal, Icons.Filled.Terminal, onNavigateToTerminal),
                SettingsEntry(R.string.settings_hub_git, Icons.Filled.Source, onNavigateToGit),
                SettingsEntry(R.string.settings_compose_preview_title, Icons.Filled.Preview, onNavigateToComposePreview),
                SettingsEntry(R.string.settings_hub_projects_root, Icons.Filled.Folder, onNavigateToProjectsRoot),
                SettingsEntry(R.string.settings_hub_packages, Icons.Filled.Inventory2, onNavigateToPackages),
                SettingsEntry(R.string.settings_hub_ai, Icons.Filled.AutoAwesome, onNavigateToAi),
                SettingsEntry(R.string.settings_hub_debug, Icons.Filled.BugReport, onNavigateToDebug),
                SettingsEntry(R.string.settings_hub_store, Icons.Filled.Storefront, onNavigateToStore),
                SettingsEntry(R.string.settings_hub_help, Icons.AutoMirrored.Filled.HelpOutline, onNavigateToHelp),
                SettingsEntry(R.string.settings_hub_about, Icons.Filled.Info, onNavigateToAbout),
                SettingsEntry(R.string.settings_hub_basti, Icons.Filled.Pets, onNavigateToBasti)
            )
        )
    )

    SettingsScaffold(
        title = stringRes(R.string.settings_hub_title),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            sections.forEach { section ->
                item(key = section.titleRes) {
                    SettingsSectionLabel(stringRes(section.titleRes))
                    SettingsGroup {
                        section.entries.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDivider()
                            SettingsLinkRow(
                                title = stringRes(entry.labelRes),
                                summary = entry.summaryRes?.let { stringRes(it) },
                                icon = entry.icon,
                                onClick = entry.onClick
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
