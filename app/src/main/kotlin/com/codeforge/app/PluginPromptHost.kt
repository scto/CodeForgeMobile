// Modul: :app
package com.codeforge.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.domain.extension.PluginPrompt
import com.codeforge.core.domain.extension.PluginPromptRegistry
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/**
 * Zeigt die älteste offene Rückfrage eines Plugins als Dialog (Bestätigung oder Auswahl). Sitzt über allen
 * Bildschirmen; die Antwort geht an das wartende Plugin zurück.
 */
@Composable
fun PluginPromptHost(modifier: Modifier = Modifier) {
    val prompts by PluginPromptRegistry.prompts.collectAsStateWithLifecycle()
    val prompt = prompts.firstOrNull() ?: return
    PluginPromptDialog(
        prompt = prompt,
        onAnswer = { PluginPromptRegistry.answer(prompt.id, it) },
        modifier = modifier
    )
}

@Composable
private fun PluginPromptDialog(prompt: PluginPrompt, onAnswer: (Int?) -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        modifier = modifier,
        title = { Text(prompt.title) },
        text = {
            if (prompt.isConfirm) {
                Text(
                    prompt.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(prompt.options) { index, option ->
                        ListItem(
                            headlineContent = { Text(option) },
                            modifier = Modifier.clickable { onAnswer(index) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.isConfirm) TextButton(onClick = { onAnswer(0) }) { Text(prompt.options.first()) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(null) }) { Text(stringRes(R.string.common_abbrechen)) }
        }
    )
}
