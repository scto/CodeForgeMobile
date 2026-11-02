/**
 * Modul: :feature:debug
 * @author Thomas Schmid
 */
package com.codeforge.feature.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Konfiguration für JVM-Debugging: Programm starten oder an einen im Terminal gestarteten JDWP-Port anhängen. */
@Composable
fun JvmDebugDialog(
    current: DebugConfig,
    onDismissRequest: () -> Unit,
    onConfirm: (DebugConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var attach by remember { mutableStateOf(current.attachOnly) }
    var mainClass by remember { mutableStateOf(current.mainClass.orEmpty()) }
    var classpath by remember { mutableStateOf(current.classpath.joinToString(":")) }
    var args by remember { mutableStateOf(current.args.joinToString(" ")) }
    var portText by remember { mutableStateOf(current.port.toString()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(text = stringRes(R.string.debug_jvm_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row {
                    FilterChip(
                        selected = !attach,
                        onClick = { attach = false },
                        label = { Text(stringRes(R.string.debug_jvm_mode_launch)) }
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    FilterChip(
                        selected = attach,
                        onClick = { attach = true },
                        label = { Text(stringRes(R.string.debug_jvm_mode_attach)) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (attach) {
                    Text(
                        text = stringRes(R.string.debug_jvm_hint_attach),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    OutlinedTextField(
                        value = mainClass,
                        onValueChange = { mainClass = it.trim() },
                        label = { Text(stringRes(R.string.debug_jvm_main_class)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = classpath,
                        onValueChange = { classpath = it },
                        label = { Text(stringRes(R.string.debug_jvm_classpath)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text(stringRes(R.string.debug_jvm_args)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (attach) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text(stringRes(R.string.debug_jvm_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = attach || mainClass.isNotBlank(),
                onClick = {
                    onConfirm(
                        current.copy(
                            attachOnly = attach,
                            mainClass = mainClass.ifBlank { null },
                            classpath = classpath.split(':').filter { it.isNotBlank() },
                            args = args.split(' ').filter { it.isNotBlank() },
                            host = "127.0.0.1",
                            port = portText.toIntOrNull() ?: 5005
                        )
                    )
                }
            ) {
                Text(stringRes(if (attach) R.string.debug_jvm_confirm_attach else R.string.debug_jvm_confirm_launch))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringRes(R.string.common_abbrechen)) }
        }
    )
}
