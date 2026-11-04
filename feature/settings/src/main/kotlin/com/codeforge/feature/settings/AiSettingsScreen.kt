/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

/** Route der KI-Einstellungen. */
@Composable
fun AiSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    AiSettingsScreen(modifier = modifier, uiState = uiState, onBack = onBack, onEvent = viewModel::onEvent)
}

/** Bildschirm der KI-Einstellungen (Anbieter, Schlüssel, Modell). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsScreen(
    uiState: AiSettingsUiState,
    onEvent: (AiSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val message = uiState.message?.let {
        when (it) {
            is AiSettingsMessage.Saved -> stringRes(R.string.aiset_saved, it.provider.displayName)
            is AiSettingsMessage.ActiveChanged -> stringRes(R.string.aiset_active_changed, it.provider.displayName)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(AiSettingsUiEvent.MessageShown)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.aiset_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringRes(R.string.aiset_keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.providers.forEach { state ->
                ProviderCard(
                    state = state,
                    isActive = state.provider == uiState.activeProvider,
                    onEvent = onEvent
                )
            }
            PrivacyCard(uiState = uiState, onEvent = onEvent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    state: AiProviderState,
    isActive: Boolean,
    onEvent: (AiSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val provider = state.provider
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    AssistChip(onClick = {}, label = { Text(stringRes(R.string.aiset_active)) })
                } else {
                    TextButton(onClick = { onEvent(AiSettingsUiEvent.SetActive(provider)) }) {
                        Text(stringRes(R.string.aiset_use))
                    }
                }
            }

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { onEvent(AiSettingsUiEvent.ApiKeyChanged(provider, it)) },
                label = { Text(stringRes(R.string.ai_api_key)) },
                singleLine = true,
                visualTransformation = if (state.keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { onEvent(AiSettingsUiEvent.ToggleKeyVisible(provider)) }) {
                        Icon(
                            imageVector = if (state.keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringRes(R.string.aiset_toggle_key)
                        )
                    }
                },
                supportingText = {
                    Text(stringRes(if (state.hasKey) R.string.aiset_key_set else R.string.aiset_key_missing))
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.baseUrlEditable) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { onEvent(AiSettingsUiEvent.BaseUrlChanged(provider, it)) },
                    label = { Text(stringRes(R.string.ai_base_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = state.model,
                onValueChange = { onEvent(AiSettingsUiEvent.ModelChanged(provider, it)) },
                label = { Text(stringRes(R.string.ai_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.models.forEach { name ->
                    FilterChip(
                        selected = state.model == name,
                        onClick = { onEvent(AiSettingsUiEvent.ModelChanged(provider, name)) },
                        label = { Text(name) }
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onEvent(AiSettingsUiEvent.FetchModels(provider)) },
                    enabled = state.hasKey && state.modelsFetch !is AiCheckState.Running
                ) {
                    Busy(state.modelsFetch is AiCheckState.Running)
                    Text(stringRes(R.string.aiset_fetch_models))
                }
                OutlinedButton(
                    onClick = { onEvent(AiSettingsUiEvent.TestConnection(provider)) },
                    enabled = state.hasKey && state.test !is AiCheckState.Running
                ) {
                    Busy(state.test is AiCheckState.Running)
                    Text(stringRes(R.string.ai_test_connection))
                }
            }

            CheckResult(state.test, success = { stringRes(R.string.ai_connection_success, it) })
            CheckResult(state.modelsFetch, success = { stringRes(R.string.aiset_models_loaded, it) })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEvent(AiSettingsUiEvent.Save(provider)) }, enabled = state.dirty) {
                    Text(stringRes(R.string.common_speichern))
                }
                if (state.hasKey) {
                    TextButton(onClick = { onEvent(AiSettingsUiEvent.ClearKey(provider)) }) {
                        Text(stringRes(R.string.aiset_remove_key))
                    }
                }
            }
        }
    }
}

@Composable
private fun Busy(running: Boolean) {
    if (running) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun CheckResult(state: AiCheckState, success: @Composable (String) -> String) {
    val (ok, text) = when (state) {
        is AiCheckState.Success -> true to success(state.detail)
        is AiCheckState.Failure -> false to stringRes(R.string.ai_connection_failed, state.message)
        else -> return
    }
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PrivacyCard(
    uiState: AiSettingsUiState,
    onEvent: (AiSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringRes(R.string.ai_privacy_policy_label), style = MaterialTheme.typography.titleMedium)
            AiPrivacyPolicy.entries.forEach { policy ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = uiState.privacyPolicy == policy, onClick = { onEvent(AiSettingsUiEvent.PolicyChanged(policy)) })
                    Text(
                        text = when (policy) {
                            AiPrivacyPolicy.ALWAYS_ASK -> stringRes(R.string.ai_privacy_policy_always_ask)
                            AiPrivacyPolicy.ONLY_SELECTED -> stringRes(R.string.ai_privacy_policy_only_selected)
                            AiPrivacyPolicy.FULL_FILE -> stringRes(R.string.ai_privacy_policy_full_file)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.dailyLimit.toString(),
                onValueChange = { onEvent(AiSettingsUiEvent.DailyLimitChanged(it.toIntOrNull() ?: 0)) },
                label = { Text(stringRes(R.string.ai_daily_limit)) },
                supportingText = { Text(stringRes(R.string.aiset_today_count, uiState.todayCount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
