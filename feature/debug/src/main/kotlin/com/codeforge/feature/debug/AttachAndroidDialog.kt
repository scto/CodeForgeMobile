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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.codeforge.core.domain.model.AndroidDebugTarget
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun AttachAndroidDialog(
    currentTarget: AndroidDebugTarget,
    onDismissRequest: () -> Unit,
    onConfirm: (AndroidDebugTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    var packageName by remember { mutableStateOf(currentTarget.packageName) }
    var activityName by remember { mutableStateOf(currentTarget.activityName ?: "") }
    var portText by remember { mutableStateOf(currentTarget.port.toString()) }
    var waitForDebugger by remember { mutableStateOf(currentTarget.waitForDebugger) }
    var stopAppOnDetach by remember { mutableStateOf(currentTarget.stopAppOnDetach) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringRes(R.string.debug_attach_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it.trim() },
                    label = { Text(text = stringRes(R.string.debug_target_package)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it.trim() },
                    label = { Text(text = stringRes(R.string.debug_target_activity)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(text = stringRes(R.string.debug_target_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = waitForDebugger,
                        onCheckedChange = { waitForDebugger = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(R.string.debug_target_wait_for_debugger),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = stopAppOnDetach,
                        onCheckedChange = { stopAppOnDetach = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(R.string.debug_target_stop_on_detach),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val port = portText.toIntOrNull() ?: 5005
                    val target = currentTarget.copy(
                        packageName = packageName,
                        activityName = activityName.ifBlank { null },
                        port = port,
                        waitForDebugger = waitForDebugger,
                        stopAppOnDetach = stopAppOnDetach
                    )
                    onConfirm(target)
                },
                enabled = packageName.isNotBlank()
            ) {
                Text(text = stringRes(R.string.debug_attach_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringRes(R.string.common_abbrechen))
            }
        },
        modifier = modifier
    )
}
