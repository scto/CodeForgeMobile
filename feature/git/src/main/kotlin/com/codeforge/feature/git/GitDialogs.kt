// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.GitResetMode
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
internal fun GitDialogs(state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    val dismiss = { onEvent(GitUiEvent.DismissDialog) }
    when (val dialog = state.dialog) {
        null -> Unit

        is GitDialog.NewBranch -> {
            var name by remember { mutableStateOf("") }
            var checkout by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_neuer_branch)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dialog.startPoint != null) Text(stringRes(R.string.git_ausgangspunkt, dialog.startPoint.take(7)), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.git_name_feature_login)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checkout, { checkout = it })
                            Text(stringRes(R.string.git_sofort_auschecken))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.CreateBranch(name, checkout, dialog.startPoint)) }, enabled = name.isNotBlank()) { Text(stringRes(R.string.git_anlegen)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }

        is GitDialog.DeleteBranch -> {
            var force by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_branch_loeschen)) },
                text = {
                    Column {
                        Text(stringRes(R.string.git_wirklich_loeschen, dialog.name))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(force, { force = it })
                            Text(stringRes(R.string.git_auch_wenn_nicht_gemergt_erzwingen))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.DeleteBranchConfirmed(dialog.name, force)) }) { Text(stringRes(R.string.common_loeschen)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }

        is GitDialog.MergeBranch -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_merge)) },
            text = { Text(stringRes(R.string.git_in_mergen, dialog.name, state.status?.branch ?: "HEAD")) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.MergeConfirmed(dialog.name)) }) { Text(stringRes(R.string.git_mergen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        is GitDialog.Discard -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_aenderungen_verwerfen)) },
            text = { Text(stringRes(R.string.git_aenderungen_an_datei_en_gehen, dialog.paths.size)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.DiscardConfirmed(dialog.paths)) }) { Text(stringRes(R.string.git_verwerfen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        GitDialog.ConfirmAbortMerge -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_vorgang_abbrechen)) },
            text = { Text(stringRes(R.string.git_setzt_arbeitsverzeichnis_und_index_auf)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.AbortMergeConfirmed) }) { Text(stringRes(R.string.git_abbrechen_zuruecksetzen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.git_zurueck)) } },
        )

        is GitDialog.RenameBranch -> {
            var name by remember { mutableStateOf(dialog.name) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_branch_umbenennen)) },
                text = { OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.git_neuer_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(onClick = { onEvent(GitUiEvent.RenameBranchConfirmed(dialog.name, name)) }, enabled = name.isNotBlank() && name != dialog.name) { Text(stringRes(R.string.common_umbenennen)) }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }

        is GitDialog.RebaseOnto -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_rebase)) },
            text = {
                Text(
                    stringRes(R.string.git_auf_rebasen_die_eigenen_commits, state.status?.branch ?: "HEAD", dialog.name)
                )
            },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.RebaseConfirmed(dialog.name)) }) { Text(stringRes(R.string.git_rebase)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        is GitDialog.DeleteRemoteBranch -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_remote_branch_loeschen)) },
            text = { Text(stringRes(R.string.git_wird_auf_dem_server_geloescht, dialog.name)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.DeleteRemoteBranchConfirmed(dialog.name)) }) { Text(stringRes(R.string.common_loeschen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        is GitDialog.RemoveRemote -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_remote_entfernen)) },
            text = { Text(stringRes(R.string.git_und_seine_remote_branches_aus, dialog.name)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.RemoveRemoteConfirmed(dialog.name)) }) { Text(stringRes(R.string.common_entfernen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        GitDialog.ConfirmAbortRebase -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_rebase_abbrechen)) },
            text = { Text(stringRes(R.string.git_der_branch_wird_auf_den)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.RebaseAbortConfirmed) }) { Text(stringRes(R.string.git_rebase_abbrechen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.git_zurueck)) } },
        )

        GitDialog.StashSave -> {
            var message by remember { mutableStateOf("") }
            var untracked by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_aenderungen_stashen)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(message, { message = it }, label = { Text(stringRes(R.string.git_nachricht_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(untracked, { untracked = it })
                            Text(stringRes(R.string.git_neue_untracked_dateien_einschliessen))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.StashSaveConfirmed(message, untracked)) }) { Text(stringRes(R.string.git_stashen)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }

        is GitDialog.StashDrop -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_stash_loeschen)) },
            text = { Text(stringRes(R.string.git_unwiderruflich_loeschen, dialog.stash.message.ifBlank { dialog.stash.ref })) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.StashDropConfirmed(dialog.stash.index)) }) { Text(stringRes(R.string.common_loeschen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        is GitDialog.NewTag -> {
            var name by remember { mutableStateOf("") }
            var message by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_neuer_tag)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringRes(R.string.git_auf) + (dialog.target?.take(7) ?: "HEAD"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.git_tag_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            message, { message = it },
                            label = { Text(stringRes(R.string.git_nachricht_leer_einfacher_tag)) },
                            supportingText = { Text(stringRes(R.string.git_mit_nachricht_wird_ein_annotierter)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.CreateTag(name, message, dialog.target)) }, enabled = name.isNotBlank()) { Text(stringRes(R.string.git_anlegen)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }

        is GitDialog.DeleteTag -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_tag_loeschen)) },
            text = { Text(stringRes(R.string.git_tag_lokal_loeschen_ein_bereits, dialog.name)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.DeleteTagConfirmed(dialog.name)) }) { Text(stringRes(R.string.common_loeschen)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        is GitDialog.RevertCommit -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.git_commit_zuruecknehmen)) },
            text = { Text(stringRes(R.string.git_erzeugt_einen_neuen_commit_der, dialog.label)) },
            confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.RevertConfirmed(dialog.hash)) }) { Text(stringRes(R.string.git_revert)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
        )

        is GitDialog.ResetTo -> {
            var mode by remember { mutableStateOf(GitResetMode.MIXED) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_reset_auf_commit)) },
                text = {
                    Column {
                        Text(stringRes(R.string.git_auf_setzen, state.status?.branch ?: "HEAD", dialog.label), style = MaterialTheme.typography.bodyMedium)
                        ResetOption(mode == GitResetMode.SOFT, { mode = GitResetMode.SOFT }, Res.string(R.string.git_soft), Res.string(R.string.git_nur_der_branch_bewegt_sich))
                        ResetOption(mode == GitResetMode.MIXED, { mode = GitResetMode.MIXED }, Res.string(R.string.git_mixed), Res.string(R.string.git_aenderungen_bleiben_im_arbeitsverzeich))
                        ResetOption(mode == GitResetMode.HARD, { mode = GitResetMode.HARD }, Res.string(R.string.git_hard), Res.string(R.string.git_alles_verwerfen_lokale_aenderungen_geh))
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.ResetConfirmed(dialog.hash, mode)) }) { Text(stringRes(R.string.git_reset)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }

        GitDialog.SetRemote -> {
            val existing = state.remotes.firstOrNull()
            var name by remember { mutableStateOf(existing?.name ?: "origin") }
            var url by remember { mutableStateOf(existing?.url.orEmpty()) }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringRes(R.string.git_remote)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.common_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(url, { url = it }, label = { Text(stringRes(R.string.git_url_https)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringRes(R.string.git_nur_https_mit_token_wird), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { onEvent(GitUiEvent.SetRemoteConfirmed(name, url)) }, enabled = name.isNotBlank() && url.isNotBlank()) { Text(stringRes(R.string.common_speichern)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } },
            )
        }
    }
}

@Composable
private fun ResetOption(selected: Boolean, onSelect: () -> Unit, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        RadioButton(selected, onSelect)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
