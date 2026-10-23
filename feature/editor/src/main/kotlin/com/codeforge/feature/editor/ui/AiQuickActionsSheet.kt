// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

enum class AiQuickActionType {
    EXPLAIN,
    FIX_ERROR,
    WRITE_TESTS,
    REWRITE,
    GENERATE_KDOC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiQuickActionsSheet(
    onDismiss: () -> Unit,
    onActionSelected: (AiQuickActionType) -> Unit,
    hasError: Boolean = false,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringRes(R.string.ai_quick_actions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            QuickActionItem(
                icon = Icons.Default.HelpOutline,
                title = stringRes(R.string.ai_quick_action_explain),
                onClick = { onActionSelected(AiQuickActionType.EXPLAIN) }
            )

            if (hasError) {
                QuickActionItem(
                    icon = Icons.Default.Build,
                    title = stringRes(R.string.ai_quick_action_fix_error),
                    onClick = { onActionSelected(AiQuickActionType.FIX_ERROR) }
                )
            }

            QuickActionItem(
                icon = Icons.Default.Checklist,
                title = stringRes(R.string.ai_quick_action_write_tests),
                onClick = { onActionSelected(AiQuickActionType.WRITE_TESTS) }
            )

            QuickActionItem(
                icon = Icons.Default.EditNote,
                title = stringRes(R.string.ai_quick_action_rewrite),
                onClick = { onActionSelected(AiQuickActionType.REWRITE) }
            )

            QuickActionItem(
                icon = Icons.Default.Article,
                title = stringRes(R.string.ai_quick_action_kdoc),
                onClick = { onActionSelected(AiQuickActionType.GENERATE_KDOC) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
