package com.codeforge.feature.j2k.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codeforge.core.resources.stringRes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.J2kScanResult
import com.codeforge.core.resources.R
import com.codeforge.feature.j2k.J2kIntent
import com.codeforge.feature.j2k.J2kUiState

@Composable
fun J2kDialog(
    state: J2kUiState,
    onIntent: (J2kIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.showDialog) return

    val scan = state.scanResult
    val percentStr = String.format("%.0f", scan?.javaPercentage ?: 0f)

    AlertDialog(
        onDismissRequest = { onIntent(J2kIntent.DismissDialog(dontAskAgain = false)) },
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringRes(R.string.j2k_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringRes(R.string.j2k_prompt_message, percentStr),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringRes(R.string.j2k_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Groovy -> Kotlin DSL Option (nur sichtbar wenn Groovy Gradle Dateien vorhanden sind)
                if (scan?.hasGroovyGradle == true) {
                    OptionSwitchRow(
                        title = stringRes(R.string.j2k_option_convert_gradle),
                        description = stringRes(R.string.j2k_option_convert_gradle_desc),
                        checked = state.convertGradle,
                        onCheckedChange = { onIntent(J2kIntent.SetConvertGradle(it)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Version Catalog Option
                OptionSwitchRow(
                    title = stringRes(R.string.j2k_option_version_catalog),
                    description = stringRes(R.string.j2k_option_version_catalog_desc),
                    checked = state.useVersionCatalog,
                    onCheckedChange = { onIntent(J2kIntent.SetUseVersionCatalog(it)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Build Check Option
                OptionSwitchRow(
                    title = stringRes(R.string.j2k_option_build_check),
                    description = null,
                    checked = state.runBuildCheck,
                    onCheckedChange = { onIntent(J2kIntent.SetRunBuildCheck(it)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Git Branch Option
                OptionSwitchRow(
                    title = stringRes(R.string.j2k_option_git_branch),
                    description = stringRes(R.string.j2k_option_git_branch_desc),
                    checked = state.createGitBranch,
                    onCheckedChange = { onIntent(J2kIntent.SetCreateGitBranch(it)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Backup .git Option
                OptionSwitchRow(
                    title = stringRes(R.string.j2k_option_backup_git),
                    description = null,
                    checked = state.includeGitInBackup,
                    onCheckedChange = { onIntent(J2kIntent.SetIncludeGitInBackup(it)) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onIntent(J2kIntent.StartMigration) }) {
                Text(text = stringRes(R.string.j2k_action_convert))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onIntent(J2kIntent.DismissDialog(dontAskAgain = true)) }) {
                    Text(text = stringRes(R.string.j2k_action_dont_ask))
                }
                OutlinedButton(onClick = { onIntent(J2kIntent.DismissDialog(dontAskAgain = false)) }) {
                    Text(text = stringRes(R.string.j2k_action_later))
                }
            }
        }
    )
}

@Composable
private fun OptionSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
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
                style = MaterialTheme.typography.bodyMedium
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(name = "Light Dialog Preview")
@Composable
private fun J2kDialogLightPreview() {
    MaterialTheme {
        Surface {
            J2kDialog(
                state = J2kUiState(
                    showDialog = true,
                    scanResult = J2kScanResult(
                        projectPath = "/sample",
                        javaFilesCount = 12,
                        javaLinesCount = 1450,
                        kotlinFilesCount = 2,
                        kotlinLinesCount = 120,
                        javaPercentage = 85.7f,
                        hasGroovyGradle = true,
                        hasKotlinDsl = false,
                        hasVersionCatalog = false,
                        qualifiesForAutoPrompt = true
                    ),
                    convertGradle = true,
                    useVersionCatalog = true
                ),
                onIntent = {}
            )
        }
    }
}
