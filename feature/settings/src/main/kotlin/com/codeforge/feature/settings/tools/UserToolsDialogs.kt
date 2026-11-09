// Modul: :feature:settings
package com.codeforge.feature.settings.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.extension.FormatterContribution
import com.codeforge.core.domain.extension.LspServerContribution
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Zerlegt eine Eingabe an Leerzeichen/Kommas; Anführungszeichen gruppieren ("a b" bleibt ein Wort). */
internal fun splitWords(input: String): List<String> =
    Regex("\"([^\"]*)\"|([^\\s,]+)").findAll(input).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter { it.isNotEmpty() }.toList()

/** Macht aus einem Anzeigenamen eine ID (`a-z0-9-`). */
internal fun slug(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "custom" }

/** Dialog „Externer LSP“: Name, Sprachen (z. B. `kotlin, java`) und Startkommando. */
@Composable
fun ExternalLspDialog(onDismiss: () -> Unit, onSave: (LspServerContribution) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var languages by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    val cmd = splitWords(command)
    val langs = splitWords(languages)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.user_lsp_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.user_lsp_hint))
                OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.user_tool_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(languages, { languages = it }, label = { Text(stringRes(R.string.user_lsp_languages)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(command, { command = it }, label = { Text(stringRes(R.string.user_lsp_command)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && langs.isNotEmpty() && cmd.isNotEmpty(),
                onClick = { onSave(LspServerContribution(id = "user-" + slug(name), label = name.trim(), languages = langs, command = cmd)) },
            ) { Text(stringRes(R.string.common_speichern)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}

/** Dialog „Formatierer hinzufügen“: Name, Dateiendungen und Kommando (`${file}` = Pfad; Inhalt über stdin). */
@Composable
fun FormatterDialog(onDismiss: () -> Unit, onSave: (FormatterContribution) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    val exts = splitWords(extensions).map { it.removePrefix(".").lowercase() }
    val cmd = splitWords(command)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.user_formatter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.user_formatter_hint))
                OutlinedTextField(name, { name = it }, label = { Text(stringRes(R.string.user_tool_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(extensions, { extensions = it }, label = { Text(stringRes(R.string.user_formatter_extensions)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(command, { command = it }, label = { Text(stringRes(R.string.user_lsp_command)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && exts.isNotEmpty() && cmd.isNotEmpty(),
                onClick = { onSave(FormatterContribution(id = "user-" + slug(name), label = name.trim(), extensions = exts, command = cmd)) },
            ) { Text(stringRes(R.string.common_speichern)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}
