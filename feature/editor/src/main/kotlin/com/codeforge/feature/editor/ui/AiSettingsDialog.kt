// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.ai.AiSanitizer
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.launch

/** Dialog für KI-Optionen im Editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsDialog(
    initialOptions: AiChatOptions,
    initialPolicy: AiPrivacyPolicy,
    initialDailyLimit: Int,
    onDismiss: () -> Unit,
    onSave: (options: AiChatOptions, policy: AiPrivacyPolicy, dailyLimit: Int) -> Unit,
    onTestConnection: suspend (AiChatOptions) -> Result<String>,
    modifier: Modifier = Modifier
) {
    var selectedProvider by remember { mutableStateOf(initialOptions.provider) }
    var modelName by remember { mutableStateOf(initialOptions.model) }
    var baseUrl by remember { mutableStateOf(initialOptions.baseUrl ?: initialOptions.provider.defaultBaseUrl) }
    var apiKey by remember { mutableStateOf(initialOptions.apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var selectedPolicy by remember { mutableStateOf(initialPolicy) }
    var dailyLimit by remember { mutableIntStateOf(initialDailyLimit) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.ai_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringRes(R.string.ai_provider),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProviderType.entries.forEach { provider ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProvider = provider
                                modelName = provider.defaultModel
                                baseUrl = provider.defaultBaseUrl
                                testResult = null
                            },
                            label = { Text(provider.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringRes(R.string.ai_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Model suggestions
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedProvider.suggestedModels.forEach { suggested ->
                        FilterChip(
                            selected = modelName == suggested,
                            onClick = { modelName = suggested },
                            label = { Text(suggested) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedProvider.openAiCompatible) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringRes(R.string.ai_base_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { input ->
                        apiKey = input
                        testResult = null
                        // Schlüssel im falschen Anbieter ist die häufigste Fehlerquelle: Anbieter automatisch umstellen.
                        AiProviderType.detectFromKey(input)?.let { detected ->
                            if (detected != selectedProvider) {
                                selectedProvider = detected
                                modelName = detected.defaultModel
                                baseUrl = detected.defaultBaseUrl
                            }
                        }
                    },
                    label = { Text(stringRes(R.string.ai_api_key)) },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            val testOptions = AiChatOptions(
                                provider = selectedProvider,
                                model = modelName,
                                baseUrl = baseUrl,
                                apiKey = apiKey
                            )
                            coroutineScope.launch {
                                testResult = onTestConnection(testOptions)
                                isTesting = false
                            }
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringRes(R.string.ai_test_connection))
                    }
                }

                testResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        val ok = res.isSuccess
                        val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Icon(
                            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (ok) {
                                stringRes(R.string.ai_connection_success, res.getOrDefault(""))
                            } else {
                                stringRes(R.string.ai_connection_failed, res.exceptionOrNull()?.message ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = tint,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringRes(R.string.ai_privacy_policy_label),
                    style = MaterialTheme.typography.labelLarge
                )

                Column {
                    AiPrivacyPolicy.entries.forEach { policy ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPolicy == policy,
                                onClick = { selectedPolicy = policy }
                            )
                            val label = when (policy) {
                                AiPrivacyPolicy.ALWAYS_ASK -> stringRes(R.string.ai_privacy_policy_always_ask)
                                AiPrivacyPolicy.ONLY_SELECTED -> stringRes(R.string.ai_privacy_policy_only_selected)
                                AiPrivacyPolicy.FULL_FILE -> stringRes(R.string.ai_privacy_policy_full_file)
                            }
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dailyLimit.toString(),
                    onValueChange = { dailyLimit = it.toIntOrNull() ?: 0 },
                    label = { Text(stringRes(R.string.ai_daily_limit)) },
                    supportingText = { Text(stringRes(R.string.ai_daily_limit_desc)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringRes(R.string.ai_exclusion_list_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringRes(
                        R.string.ai_exclusion_list_desc,
                        AiSanitizer.EXCLUDED_FILE_PATTERNS.joinToString(", ")
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalOptions = AiChatOptions(
                        provider = selectedProvider,
                        model = modelName.trim(),
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim()
                    )
                    onSave(finalOptions, selectedPolicy, dailyLimit)
                    onDismiss()
                }
            ) {
                Text(stringRes(R.string.common_speichern))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        },
        modifier = modifier
    )
}
