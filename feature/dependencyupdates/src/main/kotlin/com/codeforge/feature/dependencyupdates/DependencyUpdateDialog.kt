/**
 * Modul: :feature:dependencyupdates
 * @author Thomas Schmid
 */
package com.codeforge.feature.dependencyupdates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.dependency_updater_api.DependencyUpdate

/**
 * Wird im Projekt-Workspace (:app) eingebunden. Startet beim ersten Komposition je Projekt die
 * Hintergrundprüfung und zeigt für jedes fällige Update nacheinander den Dialog.
 */
@Composable
fun DependencyUpdatesHost(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: DependencyUpdatesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(rootPath) { viewModel.start(rootPath) }

    state.current?.let { update ->
        DependencyUpdateDialog(
            modifier = modifier,
            update = update,
            remaining = state.remaining,
            isApplying = state.isApplying,
            errorMessage = state.errorMessage,
            onEvent = viewModel::onEvent
        )
    }
}

@Composable
fun DependencyUpdateDialog(
    modifier: Modifier = Modifier,
    update: DependencyUpdate,
    remaining: Int,
    isApplying: Boolean,
    errorMessage: String?,
    onEvent: (DependencyUpdatesEvent) -> Unit
) {
    Dialog(
        // Zurück/Außen-Tippen = „Ask later“ (nicht destruktiv)
        onDismissRequest = { if (!isApplying) onEvent(DependencyUpdatesEvent.AskLater) },
        properties = DialogProperties(dismissOnBackPress = !isApplying, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = modifier,
            shape = AlertDialogDefaults.shape,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringRes(R.string.dependencyupdates_dependency_update_verfuegbar), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                Text(update.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (update.coordinates.size > 1) {
                    Text(
                        update.coordinates.take(MAX_LISTED_COORDINATES).joinToString("\n") { it.key } +
                            if (update.coordinates.size > MAX_LISTED_COORDINATES) "\n…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                VersionRow(label = Res.string(R.string.common_aktuell), value = update.currentVersion)
                VersionRow(label = Res.string(R.string.common_update), value = update.newVersion, emphasized = true)

                val fileCount = update.locations.map { it.filePath }.distinct().size
                if (update.locations.size > 1) {
                    Text(
                        stringRes(R.string.dependencyupdates_fundstellen_in_dateien, update.locations.size, fileCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (remaining > 0) {
                    Text(
                        stringRes(R.string.dependencyupdates_weitere_updates, remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (isApplying) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.End)
                ) {
                    TextButton(enabled = !isApplying, onClick = { onEvent(DependencyUpdatesEvent.Dismiss) }) {
                        Text(stringRes(R.string.dependencyupdates_dismiss))
                    }
                    TextButton(enabled = !isApplying, onClick = { onEvent(DependencyUpdatesEvent.AskLater) }) {
                        Text(stringRes(R.string.dependencyupdates_ask_later))
                    }
                    Button(enabled = !isApplying, onClick = { onEvent(DependencyUpdatesEvent.Update) }) {
                        Text(stringRes(R.string.common_update))
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private const val MAX_LISTED_COORDINATES = 4
