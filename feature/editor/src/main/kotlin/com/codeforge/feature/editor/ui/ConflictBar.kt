// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.editor.overlay.ConflictBlock

/** Leiste über dem Editor: zeigt den aktuellen Merge-Konflikt und bietet Auflösungsaktionen. */
@Composable
fun ConflictBar(
    modifier: Modifier = Modifier,
    conflicts: List<ConflictBlock>,
    index: Int,
    onKeepOurs: () -> Unit,
    onKeepTheirs: () -> Unit,
    onKeepBoth: () -> Unit,
    onNext: () -> Unit
) {
    val block = conflicts.getOrNull(index) ?: return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = stringRes(R.string.editor_conflict_title, index + 1, conflicts.size, block.oursLabel, block.theirsLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onKeepOurs) { Text(stringRes(R.string.editor_conflict_ours), maxLines = 1) }
                TextButton(onClick = onKeepTheirs) { Text(stringRes(R.string.editor_conflict_theirs), maxLines = 1) }
                TextButton(onClick = onKeepBoth) { Text(stringRes(R.string.editor_conflict_both), maxLines = 1) }
                if (conflicts.size > 1) {
                    TextButton(onClick = onNext) { Text(stringRes(R.string.editor_conflict_next), maxLines = 1) }
                }
            }
        }
    }
}
