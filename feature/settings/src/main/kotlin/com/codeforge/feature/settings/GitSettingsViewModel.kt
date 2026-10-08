/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.GitIdentity
import com.codeforge.core.domain.model.GitUrl
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitSettingsViewModel @Inject constructor(
    private val repository: GitSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitSettingsUiState())
    val uiState: StateFlow<GitSettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<GitSettingsUiEffect>(Channel.BUFFERED)
    val effects: Flow<GitSettingsUiEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val id = repository.identity.first()
            _uiState.update { it.copy(name = id.name, email = id.email, saved = id, isLoading = false) }
        }
        viewModelScope.launch {
            repository.credentials.collect { list -> _uiState.update { it.copy(credentials = list) } }
        }
    }

    fun onEvent(event: GitSettingsUiEvent) {
        when (event) {
            is GitSettingsUiEvent.NameChanged -> _uiState.update { it.copy(name = event.value) }
            is GitSettingsUiEvent.EmailChanged -> _uiState.update { it.copy(email = event.value) }
            GitSettingsUiEvent.SaveIdentity -> saveIdentity()

            is GitSettingsUiEvent.AddCredentialRequested ->
                _uiState.update { it.copy(credentialDialog = CredentialDialogState(host = event.prefillHost ?: "github.com")) }
            is GitSettingsUiEvent.DialogHostChanged -> updateDialog { it.copy(host = event.value, error = null) }
            is GitSettingsUiEvent.DialogUserChanged -> updateDialog { it.copy(username = event.value, error = null) }
            is GitSettingsUiEvent.DialogTokenChanged -> updateDialog { it.copy(token = event.value, error = null) }
            GitSettingsUiEvent.DialogToggleTokenVisible -> updateDialog { it.copy(tokenVisible = !it.tokenVisible) }
            GitSettingsUiEvent.DialogDismiss -> _uiState.update { it.copy(credentialDialog = null) }
            GitSettingsUiEvent.DialogConfirm -> saveCredential()

            is GitSettingsUiEvent.DeleteCredentialRequested -> _uiState.update { it.copy(credentialToDelete = event.credential) }
            GitSettingsUiEvent.DeleteCredentialDismissed -> _uiState.update { it.copy(credentialToDelete = null) }
            GitSettingsUiEvent.DeleteCredentialConfirmed -> deleteCredential()
        }
    }

    private fun updateDialog(transform: (CredentialDialogState) -> CredentialDialogState) =
        _uiState.update { s -> s.credentialDialog?.let { s.copy(credentialDialog = transform(it)) } ?: s }

    private fun saveIdentity() {
        val s = _uiState.value
        if (!s.canSaveIdentity) return
        viewModelScope.launch {
            val id = GitIdentity(s.name.trim(), s.email.trim())
            repository.setIdentity(id)
            _uiState.update { it.copy(saved = id, name = id.name, email = id.email) }
            _effects.send(GitSettingsUiEffect.ShowSnackbar(Res.string(R.string.settings_identitaet_gespeichert)))
        }
    }

    private fun saveCredential() {
        val d = _uiState.value.credentialDialog ?: return
        val host = GitUrl.normalizeHostInput(d.host)
        val error = when {
            host.isEmpty() -> Res.string(R.string.settings_host_fehlt_github_com)
            d.username.isBlank() -> Res.string(R.string.common_benutzername_fehlt)
            d.token.isBlank() -> Res.string(R.string.common_token_fehlt)
            else -> null
        }
        if (error != null) { updateDialog { it.copy(error = error) }; return }
        viewModelScope.launch {
            runCatching { repository.saveCredential(host, d.username.trim(), d.token.trim()) }
                .onSuccess {
                    _uiState.update { it.copy(credentialDialog = null) }
                    _effects.send(GitSettingsUiEffect.ShowSnackbar(Res.string(R.string.settings_zugang_fuer_gespeichert, host)))
                }
                .onFailure { e -> updateDialog { it.copy(error = e.message ?: Res.string(R.string.settings_speichern_fehlgeschlagen)) } }
        }
    }

    private fun deleteCredential() {
        val target = _uiState.value.credentialToDelete ?: return
        viewModelScope.launch {
            repository.removeCredential(target.host)
            _uiState.update { it.copy(credentialToDelete = null) }
            _effects.send(GitSettingsUiEffect.ShowSnackbar(Res.string(R.string.settings_zugang_fuer_entfernt, target.host)))
        }
    }
}
