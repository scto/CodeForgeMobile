/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/**
 * Bottom-Sheet für JVM-Optionen (Heap, Metaspace, weitere Flags) mit „Zurücksetzen“ und „Speichern“.
 * Ungültige Angaben werden benannt und lassen sich nicht speichern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JvmOptionsSheet(
    title: String,
    subtitle: String,
    initial: JvmOptions,
    onSave: (JvmOptions) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var xms by rememberSaveable { mutableStateOf(initial.xms) }
    var xmx by rememberSaveable { mutableStateOf(initial.xmx) }
    var meta by rememberSaveable { mutableStateOf(initial.maxMetaspace) }
    var flags by rememberSaveable { mutableStateOf(initial.extraFlags) }
    val current = JvmOptions(xms.trim(), xmx.trim(), meta.trim(), flags.trim())
    val invalid = remember(current) { current.firstInvalid() }
    val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
    val keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(stringRes(R.string.jvm_section_memory), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = xms, onValueChange = { xms = it }, singleLine = true, textStyle = mono, keyboardOptions = keyboard,
                    label = { Text(stringRes(R.string.jvm_xms), maxLines = 1) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = xmx, onValueChange = { xmx = it }, singleLine = true, textStyle = mono, keyboardOptions = keyboard,
                    label = { Text(stringRes(R.string.jvm_xmx), maxLines = 1) }, modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = meta, onValueChange = { meta = it }, singleLine = true, textStyle = mono, keyboardOptions = keyboard,
                label = { Text("-XX:MaxMetaspaceSize") }, modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Text(stringRes(R.string.jvm_section_flags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = flags, onValueChange = { flags = it }, minLines = 3, textStyle = mono, keyboardOptions = keyboard,
                label = { Text(stringRes(R.string.jvm_extra_flags)) }, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (invalid != null) stringRes(R.string.jvm_invalid, invalid) else stringRes(R.string.jvm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (invalid != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringRes(R.string.jvm_reset)) }
                Button(onClick = { onSave(current) }, enabled = invalid == null, modifier = Modifier.weight(1f)) {
                    Text(stringRes(R.string.common_speichern))
                }
            }
        }
    }
}
