/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.extension.PluginSettingItem
import com.codeforge.core.domain.extension.PluginSettingType
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Einstellungsseite eines Plugins aus `contributes.settings`; jede Änderung wird sofort gespeichert. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginSettingsSheetView(
    sheet: PluginSettingsSheet,
    onEvent: (PluginsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = { onEvent(PluginsUiEvent.SettingsDismissed) }, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(sheet.title, style = MaterialTheme.typography.titleLarge)
            sheet.items.forEach { item ->
                SettingItemView(item, sheet.values[item.key] ?: item.default) { value ->
                    onEvent(PluginsUiEvent.SettingChanged(item.key, value))
                }
            }
            Text(
                stringRes(R.string.plugins_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingItemView(item: PluginSettingItem, value: String, onChange: (String?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.type == PluginSettingType.BOOLEAN) {
                Switch(checked = value.toBooleanStrictOrNull() == true, onCheckedChange = { onChange(it.toString()) })
            }
        }
        when (item.type) {
            PluginSettingType.TEXT, PluginSettingType.NUMBER -> TextSetting(item, value, onChange)
            PluginSettingType.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.options.forEach { option ->
                    FilterChip(selected = option.value == value, onClick = { onChange(option.value) }, label = { Text(option.label) })
                }
            }
            PluginSettingType.BOOLEAN -> Unit
        }
        if (value != item.default) {
            TextButton(onClick = { onChange(null) }) { Text(stringRes(R.string.plugins_settings_reset)) }
        }
    }
}

@Composable
private fun TextSetting(item: PluginSettingItem, value: String, onChange: (String?) -> Unit) {
    // Lokaler Entwurf: Zahlen erst übernehmen, wenn sie gültig sind; Text bei jeder Eingabe.
    var draft by remember(item.key, value) { mutableStateOf(value) }
    val isNumber = item.type == PluginSettingType.NUMBER
    OutlinedTextField(
        value = draft,
        onValueChange = { text ->
            draft = text
            if (!isNumber || text.trim().toIntOrNull() != null) onChange(text)
        },
        singleLine = true,
        supportingText = if (isNumber && (item.min != null || item.max != null)) {
            { Text(stringRes(R.string.plugins_settings_range, item.min?.toString() ?: "…", item.max?.toString() ?: "…")) }
        } else {
            null
        },
        keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
}
