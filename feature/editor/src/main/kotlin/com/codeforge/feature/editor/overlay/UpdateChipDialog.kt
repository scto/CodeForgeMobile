/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor.overlay

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.dependency_updater_api.DependencyUpdate

/** Dialog nach Klick auf einen Update-Chip: diese Bibliothek updaten oder alle Updates anwenden. */
@Composable
fun UpdateChipDialog(
    update: DependencyUpdate,
    totalPending: Int,
    isApplying: Boolean,
    onUpdate: () -> Unit,
    onUpdateAll: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isApplying) onCancel() },
        title = { Text(update.displayName) },
        text = { Text("${update.currentVersion}  ->  ${update.newVersion}") },
        dismissButton = {
            TextButton(enabled = !isApplying, onClick = onUpdateAll) {
                Text(stringRes(R.string.editor_update_all, totalPending))
            }
        },
        confirmButton = {
            Button(enabled = !isApplying, onClick = onUpdate) { Text(stringRes(R.string.common_update)) }
        }
    )
}
