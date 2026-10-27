/**
 * Modul: :core:ui
 * @author Thomas Schmid
 *
 * IconButton mit Tooltip: Beim langen Drücken (Touch) bzw. Hover/Fokus (Maus, Tastatur) erscheint
 * die Beschriftung der Schaltfläche. Der Text ist derselbe wie die Bedienungshilfe-Beschreibung.
 */
package com.codeforge.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}
