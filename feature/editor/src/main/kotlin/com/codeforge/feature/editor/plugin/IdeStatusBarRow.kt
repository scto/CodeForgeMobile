// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.extension.ide.IdeStatusItem

/** Statusleiste der Plugins (API 5) unter dem Editor; Tippen führt den Befehl des Eintrags aus. */
@Composable
fun IdeStatusBarRow(items: List<IdeStatusItem>, onClick: (commandId: String) -> Unit, modifier: Modifier = Modifier) {
    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items.forEach { item ->
                val command = item.commandId
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (command != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .semantics { item.tooltip?.let { contentDescription = it } }
                        .then(if (command != null) Modifier.clickable { onClick(command) } else Modifier)
                )
            }
        }
    }
}
