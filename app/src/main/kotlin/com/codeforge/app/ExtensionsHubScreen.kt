/**
 * Modul: :app
 * @author Thomas Schmid
 *
 * Gemeinsamer Einstieg für Store und installierte Erweiterungen (zwei Reiter statt dreier getrennter Seiten).
 */
package com.codeforge.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.feature.plugins.PluginsRoute
import com.codeforge.feature.settings.store.StoreRoute

/** Reiter der Erweiterungen. */
enum class ExtensionsHubTab { INSTALLED, STORE }

/** Route des Stores (Reiter Installiert und Store; Sprachserver stehen im Store, Verwaltung unter Editor). */
@Composable
fun ExtensionsHubRoute(
    initialTab: ExtensionsHubTab,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    val tabs = ExtensionsHubTab.entries
    val labels = listOf(
        stringRes(R.string.extensions_hub_tab_installed),
        stringRes(R.string.extensions_hub_tab_store),
    )
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.extensions_hub_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selected) {
                labels.forEachIndexed { index, label ->
                    Tab(selected = selected == index, onClick = { selected = index }, text = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.titleSmall) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (tabs[selected]) {
                    ExtensionsHubTab.INSTALLED -> PluginsRoute(onBack = onBack, embedded = true)
                    ExtensionsHubTab.STORE -> StoreRoute(onBack = onBack, embedded = true)
                }
            }
        }
    }
}
