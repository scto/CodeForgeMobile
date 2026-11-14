/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Bildschirm der KI-Einstellungen in Abschnitten: Anbieter, Modell (des aktiven Anbieters), API-Schlüssel und
 * Endpunkte aller Anbieter, Datenschutz und Tageslimit. Änderungen speichert das ViewModel automatisch.
 */
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
    val active = uiState.providers.first { it.provider == uiState.activeProvider }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.aiset_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderSection(uiState, onEvent)
            ModelSection(active, onEvent)
            KeysSection(uiState.providers, onEvent)
            EndpointsSection(uiState.providers, onEvent)
            PrivacySection(uiState, onEvent)
            Text(
                stringRes(R.string.aiset_keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderSection(uiState: AiSettingsUiState, onEvent: (AiSettingsUiEvent) -> Unit) {
    SectionTitle(stringRes(R.string.aiset_section_provider))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        uiState.providers.forEach { p ->
            FilterChip(
                selected = p.provider == uiState.activeProvider,
                onClick = { if (p.provider != uiState.activeProvider) onEvent(AiSettingsUiEvent.SetActive(p.provider)) },
                label = { Text(p.provider.displayName) },
                leadingIcon = if (p.hasKey) {
                    { Icon(Icons.Filled.Key, contentDescription = stringRes(R.string.aiset_key_set), modifier = Modifier.size(16.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun ModelSection(state: AiProviderState, onEvent: (AiSettingsUiEvent) -> Unit) {
    SectionTitle(stringRes(R.string.aiset_section_model, state.provider.displayName))
    Block {
        ModelList(state, onEvent)
        OutlinedTextField(
            value = state.model.takeIf { it !in state.models }.orEmpty(),
            onValueChange = { onEvent(AiSettingsUiEvent.ModelChanged(state.provider, it.trim())) },
            label = { Text(stringRes(R.string.aiset_custom_model)) },
            placeholder = { Text(state.provider.defaultModel, fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ModelActions(state, onEvent)
    }
}

@Composable
private fun ModelList(state: AiProviderState, onEvent: (AiSettingsUiEvent) -> Unit) {
    var showAll by rememberSaveable(state.provider) { mutableStateOf(false) }
    val shown = if (showAll) state.models else state.models.take(MAX_MODELS)
    shown.forEach { name -> ModelRow(name, selected = state.model == name) { onEvent(AiSettingsUiEvent.ModelChanged(state.provider, name)) } }
    if (state.models.size > MAX_MODELS) {
        TextButton(onClick = { showAll = !showAll }) {
            Text(stringRes(if (showAll) R.string.aiset_models_less else R.string.aiset_models_all, state.models.size))
        }
    }
}

@Composable
private fun ModelRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
        Text(
            name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelActions(state: AiProviderState, onEvent: (AiSettingsUiEvent) -> Unit) {
    val provider = state.provider
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { onEvent(AiSettingsUiEvent.FetchModels(provider)) },
            enabled = state.hasKey && state.modelsFetch !is AiCheckState.Running
        ) {
            Busy(state.modelsFetch is AiCheckState.Running)
            Text(stringRes(R.string.aiset_fetch_models))
        }
        OutlinedButton(
            // Ein eigener OpenAI-kompatibler Server (z. B. Ollama) braucht keinen Schlüssel.
            onClick = { onEvent(AiSettingsUiEvent.TestConnection(provider)) },
            enabled = (state.hasKey || provider == AiProviderType.OPENAI) && state.test !is AiCheckState.Running
        ) {
            Busy(state.test is AiCheckState.Running)
            Text(stringRes(R.string.ai_test_connection))
        }
    }
    CheckResult(state.test, success = { stringRes(R.string.ai_connection_success, it) })
    CheckResult(state.modelsFetch, success = { stringRes(R.string.aiset_models_loaded, it) })
}

@Composable
private fun KeysSection(providers: List<AiProviderState>, onEvent: (AiSettingsUiEvent) -> Unit) {
    SectionTitle(stringRes(R.string.aiset_section_keys))
    Block {
        providers.forEach { state ->
            val provider = state.provider
            Text(provider.displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { onEvent(AiSettingsUiEvent.ApiKeyChanged(provider, it)) },
                placeholder = { Text(keyPlaceholder(provider), fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { onEvent(AiSettingsUiEvent.ToggleKeyVisible(provider)) }) {
                        Icon(
                            if (state.keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringRes(R.string.aiset_toggle_key)
                        )
                    }
                },
                visualTransformation = if (state.keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EndpointsSection(providers: List<AiProviderState>, onEvent: (AiSettingsUiEvent) -> Unit) {
    SectionTitle(stringRes(R.string.aiset_section_endpoints))
    Block {
        providers.forEach { state ->
            val provider = state.provider
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { onEvent(AiSettingsUiEvent.BaseUrlChanged(provider, it)) },
                label = { Text(stringRes(R.string.aiset_endpoint, provider.displayName)) },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = if (state.baseUrlChanged) {
                    {
                        IconButton(onClick = { onEvent(AiSettingsUiEvent.ResetBaseUrl(provider)) }) {
                            Icon(Icons.Filled.Restore, contentDescription = stringRes(R.string.aiset_endpoint_reset))
                        }
                    }
                } else {
                    null
                },
                supportingText = endpointHint(provider)?.let { hint -> { Text(hint) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PrivacySection(uiState: AiSettingsUiState, onEvent: (AiSettingsUiEvent) -> Unit) {
    SectionTitle(stringRes(R.string.aiset_section_privacy))
    Block {
        AiPrivacyPolicy.entries.forEach { policy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onEvent(AiSettingsUiEvent.PolicyChanged(policy)) }
            ) {
                RadioButton(selected = uiState.privacyPolicy == policy, onClick = null, modifier = Modifier.padding(8.dp))
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

@Composable
private fun endpointHint(provider: AiProviderType): String? = when (provider) {
    AiProviderType.GEMINI -> stringRes(R.string.aiset_endpoint_hint_gemini)
    AiProviderType.ANTHROPIC -> stringRes(R.string.aiset_endpoint_hint_anthropic)
    AiProviderType.OPENAI -> stringRes(R.string.aiset_endpoint_hint_openai)
    AiProviderType.GROK, AiProviderType.OPENROUTER -> null
}

private fun keyPlaceholder(provider: AiProviderType): String = when (provider) {
    AiProviderType.GEMINI -> "AIza…"
    AiProviderType.ANTHROPIC -> "sk-ant-…"
    AiProviderType.OPENAI -> "sk-…"
    AiProviderType.GROK -> "xai-…"
    AiProviderType.OPENROUTER -> "sk-or-…"
}

private const val MAX_MODELS = 12

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
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.weight(1f))
    }
}
