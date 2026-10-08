/**
 * Modul: :core:ui
 * @author Thomas Schmid
 */
package com.codeforge.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Geteilter Leerzustand (z.B. "keine Plugins installiert", "kein Verzeichnisinhalt").
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon?.let {
            Icon(imageVector = it, contentDescription = null)
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
