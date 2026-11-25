/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/**
 * Abschnitt „Berechtigungen & Integration“ (Gegenstück zum Termux-Einrichtungsdialog externer Editoren):
 * erklärt, warum keine Termux-Befehlsberechtigung nötig ist, und bietet die zwei nützlichen Systemrechte an.
 */
@Composable
internal fun TermuxIntegrationCard(
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Nach Rückkehr aus den Systemeinstellungen den Status neu lesen.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onEvent(TerminalSettingsUiEvent.PermissionsRefresh) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(R.string.settings_terminal_integration), style = MaterialTheme.typography.titleSmall)

            Step(
                title = stringRes(R.string.settings_terminal_integration_commands),
                status = stringRes(R.string.settings_terminal_integration_not_needed),
                ok = true,
                text = stringRes(R.string.settings_terminal_integration_commands_desc),
            )
            HorizontalDivider()
            Step(
                title = stringRes(R.string.settings_terminal_integration_external),
                status = stringRes(
                    if (uiState.allowExternalApps) R.string.settings_terminal_integration_on else R.string.settings_terminal_integration_off
                ),
                ok = true,
                text = stringRes(R.string.settings_terminal_integration_external_desc),
            )
            HorizontalDivider()
            PermissionSteps(uiState, onEvent)
            HorizontalDivider()
            ReloadStep(uiState, onEvent)
        }
    }
}

@Composable
private fun PermissionSteps(uiState: TerminalSettingsUiState, onEvent: (TerminalSettingsUiEvent) -> Unit) {
    val context = LocalContext.current
    Step(
        title = stringRes(R.string.settings_terminal_integration_overlay),
        status = grantLabel(uiState.overlayGranted),
        ok = uiState.overlayGranted,
        text = stringRes(R.string.settings_terminal_integration_overlay_desc),
    ) {
        if (!uiState.overlayGranted) {
            OutlinedButton(onClick = {
                context.open(TermuxIntegration.overlayIntent(context), TermuxIntegration.overlayListIntent())
            }) { Text(stringRes(R.string.settings_terminal_integration_allow)) }
        }
        TextButton(onClick = { onEvent(TerminalSettingsUiEvent.PermissionsRefresh) }) {
            Text(stringRes(R.string.settings_terminal_integration_recheck))
        }
    }
    HorizontalDivider()
    Step(
        title = stringRes(R.string.settings_terminal_integration_battery),
        status = grantLabel(uiState.batteryUnrestricted),
        ok = uiState.batteryUnrestricted,
        text = stringRes(R.string.settings_terminal_integration_battery_desc),
    ) {
        if (!uiState.batteryUnrestricted) {
            OutlinedButton(onClick = {
                context.open(TermuxIntegration.batteryIntent(context), TermuxIntegration.batteryListIntent())
            }) { Text(stringRes(R.string.settings_terminal_integration_allow)) }
        }
    }
}

@Composable
private fun ReloadStep(uiState: TerminalSettingsUiState, onEvent: (TerminalSettingsUiEvent) -> Unit) {
    if (uiState.settingsReloaded) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(RELOADED_HINT_MS)
            onEvent(TerminalSettingsUiEvent.ReloadShown)
        }
    }
    Step(
        title = stringRes(R.string.settings_terminal_integration_reload),
        status = null,
        ok = true,
        text = stringRes(R.string.settings_terminal_integration_reload_desc),
    ) {
        OutlinedButton(onClick = { onEvent(TerminalSettingsUiEvent.ReloadSettings) }) {
            Text(stringRes(R.string.settings_terminal_integration_reload_button))
        }
        if (uiState.settingsReloaded) {
            Text(
                stringRes(R.string.settings_terminal_integration_reloaded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun grantLabel(granted: Boolean): String =
    stringRes(if (granted) R.string.settings_terminal_integration_granted else R.string.settings_terminal_integration_missing)

@Composable
private fun Step(
    title: String,
    status: String?,
    ok: Boolean,
    text: String,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            status?.let {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(it, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    },
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { it() }
        }
    }
}

private fun Context.open(intent: Intent, fallback: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(fallback) }
    } catch (_: SecurityException) {
        runCatching { startActivity(fallback) }
    }
}

private const val RELOADED_HINT_MS = 3_000L
