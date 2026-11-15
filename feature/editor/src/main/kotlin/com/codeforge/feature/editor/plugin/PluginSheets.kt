// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.editor.PluginRunState
import com.codeforge.feature.editor.PluginTestResult
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPanel

/** Ausgabe einer Plugin-Aufgabe (Ausführen/Tests) mit Testzusammenfassung, Abbrechen und Schließen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRunSheet(
    state: PluginRunState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.lines.size) { if (state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            RunHeader(state = state, onCancel = onCancel)
            if (state.tests.isNotEmpty()) TestSummary(tests = state.tests)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(state.lines) { _, line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/** Zeigt den Bereich eines Plugins in einem Blatt; [host] verbindet ihn mit dem Editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPanelSheet(
    panel: PluginPanel,
    host: PanelHost,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(panel.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            panel.Content(host, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RunHeader(state: PluginRunState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.running) {
                TextButton(onClick = onCancel) { Text(stringRes(R.string.common_abbrechen)) }
            } else {
                Text(stringRes(R.string.plugin_run_exit, state.exitCode ?: 0), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
}

@Composable
private fun TestSummary(tests: List<PluginTestResult>, modifier: Modifier = Modifier) {
    val passed = remember(tests) { tests.count { it.passed } }
    // Fehlgeschlagene zuerst, dann die bestandenen.
    val shown = remember(tests) { tests.sortedBy { it.passed }.take(MAX_TESTS_SHOWN) }
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            stringRes(R.string.plugin_test_summary, passed, tests.size - passed),
            style = MaterialTheme.typography.labelLarge,
            color = if (passed == tests.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Column(modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            shown.forEach { t ->
                Text(
                    (if (t.passed) "✓ " else "✗ ") + t.name + t.message?.takeIf { !t.passed }?.let { ": $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private const val MAX_TESTS_SHOWN = 200
