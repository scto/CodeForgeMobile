/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitIdentity

/** Eingabedialog für einen neuen/zu ersetzenden HTTPS-Zugang. */
@Immutable
data class CredentialDialogState(
    val host: String = "github.com",
    val username: String = "",
    val token: String = "",
    val tokenVisible: Boolean = false,
    val error: String? = null,
)

@Immutable
data class GitSettingsUiState(
    val name: String = "",
    val email: String = "",
    val saved: GitIdentity = GitIdentity(),
    val credentials: List<GitCredentialInfo> = emptyList(),
    val credentialDialog: CredentialDialogState? = null,
    val credentialToDelete: GitCredentialInfo? = null,
    val isLoading: Boolean = true,
) {
    val emailValid: Boolean get() = email.isBlank() || EMAIL.matches(email.trim())
    val identityDirty: Boolean get() = name.trim() != saved.name || email.trim() != saved.email
    val canSaveIdentity: Boolean get() = identityDirty && name.isNotBlank() && email.isNotBlank() && emailValid

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

sealed interface GitSettingsUiEvent {
    data class NameChanged(val value: String) : GitSettingsUiEvent
    data class EmailChanged(val value: String) : GitSettingsUiEvent
    data object SaveIdentity : GitSettingsUiEvent

    data class AddCredentialRequested(val prefillHost: String? = null) : GitSettingsUiEvent
    data class DialogHostChanged(val value: String) : GitSettingsUiEvent
    data class DialogUserChanged(val value: String) : GitSettingsUiEvent
    data class DialogTokenChanged(val value: String) : GitSettingsUiEvent
    data object DialogToggleTokenVisible : GitSettingsUiEvent
    data object DialogConfirm : GitSettingsUiEvent
    data object DialogDismiss : GitSettingsUiEvent

    data class DeleteCredentialRequested(val credential: GitCredentialInfo) : GitSettingsUiEvent
    data object DeleteCredentialConfirmed : GitSettingsUiEvent
    data object DeleteCredentialDismissed : GitSettingsUiEvent
}

sealed interface GitSettingsUiEffect {
    data class ShowSnackbar(val message: String) : GitSettingsUiEffect
}
