/**
 * Modul: :core:ui
 * @author Thomas Schmid
 *
 * Hilfen für Einstellungsseiten, die allein (mit eigener TopBar) oder eingebettet in einem
 * Host mit gemeinsamer TopBar und Reitern laufen.
 */
package com.codeforge.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Eingebettet: keine zusätzlichen System-Insets (der Host hat sie schon angewendet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun embeddedAwareInsets(embedded: Boolean): WindowInsets =
    if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets

/** Schmale Aktionszeile statt TopBar, wenn eine Seite eingebettet ist (z. B. „+“ oder „Neu laden“). */
@Composable
fun EmbeddedActionsBar(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = actions,
    )
}
