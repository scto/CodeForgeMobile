// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.editor.RenameDialogState

@Composable
fun RenameDialog(
    modifier: Modifier = Modifier,
    state: RenameDialogState,
    onNameChanged: (String) -> Unit,
    onRequestPreview: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isPreview) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = {
                Text(text = stringRes(R.string.lsp_rename_title))
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = onNameChanged,
                        label = { Text(stringRes(R.string.lsp_rename_new_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onRequestPreview,
                    enabled = state.newName.isNotBlank() && state.newName != state.currentName
                ) {
                    Text(text = stringRes(R.string.common_preview))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringRes(R.string.common_abbrechen))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = {
                Text(text = stringRes(R.string.lsp_rename_preview_title))
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringRes(
                            R.string.lsp_rename_preview_summary,
                            state.pendingEditCount,
                            state.pendingFileCount
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.currentName} → ${state.newName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = stringRes(R.string.common_umbenennen))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringRes(R.string.common_abbrechen))
                }
            }
        )
    }
}
