/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun GitSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: GitSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GitSettingsUiEffect.ShowSnackbar -> snackbar.showSnackbar(effect.message)
            }
        }
    }
    GitSettingsScreen(state = state, snackbar = snackbar, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun GitSettingsScreen(
    state: GitSettingsUiState,
    snackbar: SnackbarHostState,
    onEvent: (GitSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringRes(R.string.common_git)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IdentityCard(state, onEvent)
            CredentialsCard(state, onEvent)
            Text(
                stringRes(R.string.settings_tokens_werden_verschluesselt_im_androi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    state.credentialDialog?.let { CredentialDialog(it, onEvent) }
    state.credentialToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { onEvent(GitSettingsUiEvent.DeleteCredentialDismissed) },
            title = { Text(stringRes(R.string.settings_zugang_entfernen)) },
            text = { Text(stringRes(R.string.settings_gespeicherten_token_fuer_loeschen, target.host, target.username)) },
            confirmButton = { TextButton(onClick = { onEvent(GitSettingsUiEvent.DeleteCredentialConfirmed) }) { Text(stringRes(R.string.common_entfernen)) } },
            dismissButton = { TextButton(onClick = { onEvent(GitSettingsUiEvent.DeleteCredentialDismissed) }) { Text(stringRes(R.string.common_abbrechen)) } },
        )
    }
}

@Composable
private fun IdentityCard(state: GitSettingsUiState, onEvent: (GitSettingsUiEvent) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(R.string.settings_identitaet), style = MaterialTheme.typography.titleMedium)
            Text(stringRes(R.string.settings_wird_als_autor_in_jedem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(GitSettingsUiEvent.NameChanged(it)) },
                label = { Text(stringRes(R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = { onEvent(GitSettingsUiEvent.EmailChanged(it)) },
                label = { Text(stringRes(R.string.settings_mail)) },
                singleLine = true,
                isError = !state.emailValid,
                supportingText = if (!state.emailValid) ({ Text(stringRes(R.string.settings_ungueltige_mail_adresse)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onEvent(GitSettingsUiEvent.SaveIdentity) },
                enabled = state.canSaveIdentity,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringRes(R.string.common_speichern)) }
        }
    }
}

@Composable
private fun CredentialsCard(state: GitSettingsUiState, onEvent: (GitSettingsUiEvent) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringRes(R.string.settings_zugangsdaten_https), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                stringRes(R.string.settings_personal_access_token_pro_host),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.credentials.isEmpty()) {
                Text(stringRes(R.string.settings_noch_keine_zugaenge_gespeichert), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            state.credentials.forEach { c ->
                ListItem(
                    headlineContent = { Text(c.host) },
                    supportingContent = { Text(stringRes(R.string.settings_token_gespeichert, c.username)) },
                    trailingContent = {
                        IconButton(onClick = { onEvent(GitSettingsUiEvent.DeleteCredentialRequested(c)) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringRes(R.string.settings_zugang_entfernen))
                        }
                    },
                )
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEvent(GitSettingsUiEvent.AddCredentialRequested()) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringRes(R.string.settings_zugang_hinzufuegen), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CredentialDialog(dialog: CredentialDialogState, onEvent: (GitSettingsUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(GitSettingsUiEvent.DialogDismiss) },
        title = { Text(stringRes(R.string.settings_zugang_hinzufuegen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("github.com", "gitlab.com", "bitbucket.org").forEach { host ->
                        AssistChip(onClick = { onEvent(GitSettingsUiEvent.DialogHostChanged(host)) }, label = { Text(host) })
                    }
                }
                OutlinedTextField(
                    value = dialog.host,
                    onValueChange = { onEvent(GitSettingsUiEvent.DialogHostChanged(it)) },
                    label = { Text(stringRes(R.string.settings_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.username,
                    onValueChange = { onEvent(GitSettingsUiEvent.DialogUserChanged(it)) },
                    label = { Text(stringRes(R.string.settings_benutzername)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.token,
                    onValueChange = { onEvent(GitSettingsUiEvent.DialogTokenChanged(it)) },
                    label = { Text(stringRes(R.string.settings_token_passwort)) },
                    singleLine = true,
                    visualTransformation = if (dialog.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { onEvent(GitSettingsUiEvent.DialogToggleTokenVisible) }) {
                            Icon(
                                if (dialog.tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (dialog.tokenVisible) stringRes(R.string.settings_token_verbergen) else stringRes(R.string.settings_token_anzeigen),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                dialog.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onEvent(GitSettingsUiEvent.DialogConfirm) }) { Text(stringRes(R.string.common_speichern)) } },
        dismissButton = { TextButton(onClick = { onEvent(GitSettingsUiEvent.DialogDismiss) }) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}
