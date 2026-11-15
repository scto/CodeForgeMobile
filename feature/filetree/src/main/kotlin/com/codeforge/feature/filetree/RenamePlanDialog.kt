/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.refactoring.RenameRefactorSource
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.io.File

/** Fragt nach, ob beim Umbenennen die Verweise (package/import …) angepasst werden sollen. */
@Composable
internal fun RenamePlanDialog(
    state: RenamePlanState,
    onAdjust: () -> Unit,
    onRenameOnly: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = state.plan
    val root = remember(plan) { commonParent(plan.changes.map { it.path }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringRes(R.string.filetree_rename_adjust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.filetree_rename_adjust_summary, plan.editCount, plan.fileCount))
                val oldPackage = plan.oldPackage
                val newPackage = plan.newPackage
                if (oldPackage != null && newPackage != null) {
                    Text(
                        text = stringRes(R.string.filetree_rename_package_change, oldPackage, newPackage),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = stringRes(
                        if (plan.source == RenameRefactorSource.LANGUAGE_SERVER) {
                            R.string.filetree_rename_source_server
                        } else {
                            R.string.filetree_rename_source_fallback
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(plan.changes, key = { it.path }) { change ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = change.path.removePrefix(root).trimStart('/'),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.StartEllipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = change.editCount.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdjust) { Text(stringRes(R.string.filetree_rename_adjust_confirm)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) }
                TextButton(onClick = onRenameOnly) { Text(stringRes(R.string.filetree_rename_only)) }
            }
        }
    )
}

/** Hinweis, solange Folgeänderungen ermittelt werden. */
@Composable
internal fun RenamePlanningDialog(modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier,
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringRes(R.string.filetree_rename_planning))
            }
        }
    )
}

private fun commonParent(paths: List<String>): String {
    if (paths.isEmpty()) return ""
    var prefix = File(paths.first()).parent.orEmpty()
    for (p in paths) while (prefix.isNotEmpty() && !p.startsWith("$prefix/")) prefix = File(prefix).parent.orEmpty()
    return prefix
}
