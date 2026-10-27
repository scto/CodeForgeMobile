package com.codeforge.feature.projectsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codeforge.core.resources.stringRes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.resources.R
import com.codeforge.core.ui.SettingsTopBar

@Composable
fun ProjectSettingsScreen(
    state: ProjectSettingsUiState,
    onIntent: (ProjectSettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.project_settings_title),
                onBack = onNavigateBack
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Button(
                    onClick = { onIntent(ProjectSettingsIntent.Save) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = stringRes(R.string.common_speichern))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            TabRow(
                selectedTabIndex = if (state.selectedTab == ProjectBuildTypeTab.RELEASE) 0 else 1
            ) {
                Tab(
                    selected = state.selectedTab == ProjectBuildTypeTab.RELEASE,
                    onClick = { onIntent(ProjectSettingsIntent.SelectTab(ProjectBuildTypeTab.RELEASE)) },
                    text = { Text(stringRes(R.string.project_settings_build_type_release)) }
                )
                Tab(
                    selected = state.selectedTab == ProjectBuildTypeTab.DEBUG,
                    onClick = { onIntent(ProjectSettingsIntent.SelectTab(ProjectBuildTypeTab.DEBUG)) },
                    text = { Text(stringRes(R.string.project_settings_build_type_debug)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val current = state.currentTabSettings

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Minify
                SettingSwitchRow(
                    title = stringRes(R.string.project_settings_minify),
                    description = stringRes(R.string.project_settings_minify_desc),
                    checked = current.minifyEnabled,
                    onCheckedChange = { onIntent(ProjectSettingsIntent.ToggleMinify(it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Shrink (requires Minify)
                SettingSwitchRow(
                    title = stringRes(R.string.project_settings_shrink),
                    description = stringRes(R.string.project_settings_shrink_desc),
                    checked = current.shrinkResources,
                    enabled = current.minifyEnabled,
                    onCheckedChange = { onIntent(ProjectSettingsIntent.ToggleShrink(it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // PNG Crunch
                SettingSwitchRow(
                    title = stringRes(R.string.project_settings_crunch),
                    description = stringRes(R.string.project_settings_crunch_desc),
                    checked = current.crunchPngs,
                    onCheckedChange = { onIntent(ProjectSettingsIntent.ToggleCrunch(it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Debuggable
                SettingSwitchRow(
                    title = stringRes(R.string.project_settings_debuggable),
                    description = stringRes(R.string.project_settings_debuggable_desc),
                    checked = current.debuggable,
                    onCheckedChange = { onIntent(ProjectSettingsIntent.ToggleDebuggable(it)) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Signing Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = stringRes(R.string.project_settings_signing),
                            description = stringRes(R.string.project_settings_signing_desc),
                            checked = current.signingEnabled,
                            onCheckedChange = { onIntent(ProjectSettingsIntent.ToggleSigning(it)) }
                        )

                        if (current.signingEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val assign = state.signingAssignment
                            if (assign != null) {
                                Text(
                                    text = "Zugewiesen: ${assign.alias} (${assign.keystoreId})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "Kein projektspezifischer Schlüssel zugewiesen (verwendet Standardschlüssel falls aktiv).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { onIntent(ProjectSettingsIntent.OpenSigningManager) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringRes(R.string.project_settings_signing_manage))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(name = "ProjectSettingsScreen Preview")
@Composable
private fun ProjectSettingsScreenPreview() {
    MaterialTheme {
        Surface {
            ProjectSettingsScreen(
                state = ProjectSettingsUiState(
                    projectPath = "/sample/project",
                    settings = ProjectBuildSettings(
                        projectPath = "/sample/project",
                        release = BuildTypeSettings(
                            minifyEnabled = true,
                            shrinkResources = true,
                            crunchPngs = true,
                            debuggable = false,
                            signingEnabled = true
                        )
                    ),
                    signingAssignment = ProjectSigningAssignment(
                        projectPath = "/sample/project",
                        keystoreId = "release-keystore",
                        alias = "my-key"
                    )
                ),
                onIntent = {},
                onNavigateBack = {}
            )
        }
    }
}
