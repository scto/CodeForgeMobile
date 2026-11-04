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

/** Zustand der Git-Einstellungen (Identität, Zugangsdaten, Dialoge, Anzeigeoptionen). */
@Immutable
data class GitSettingsUiState(
    val name: String = "",
    val email: String = "",
    val saved: GitIdentity = GitIdentity(),
    val credentials: List<GitCredentialInfo> = emptyList(),
    val credentialDialog: CredentialDialogState? = null,
    val credentialToDelete: GitCredentialInfo? = null,
    val gutterMarkersEnabled: Boolean = true,
    val conflictDetectionEnabled: Boolean = true,
    val fileNameColorsEnabled: Boolean = true,
    val isLoading: Boolean = true,
) {
    /** Ob die E-Mail-Adresse leer oder gültig ist. */
    val emailValid: Boolean get() = email.isBlank() || EMAIL.matches(email.trim())
    /** Ob Name oder E-Mail vom gespeicherten Stand abweichen. */
    val identityDirty: Boolean get() = name.trim() != saved.name || email.trim() != saved.email
    /** Ob die Identität gespeichert werden kann (geändert, vollständig, gültige E-Mail). */
    val canSaveIdentity: Boolean get() = identityDirty && name.isNotBlank() && email.isNotBlank() && emailValid

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Aktionen in den Git-Einstellungen. */
sealed interface GitSettingsUiEvent {
    /** Der Name wurde geändert. */
    data class NameChanged(val value: String) : GitSettingsUiEvent
    /** Die E-Mail wurde geändert. */
    data class EmailChanged(val value: String) : GitSettingsUiEvent
    /** Identität speichern. */
    data object SaveIdentity : GitSettingsUiEvent
    /** Änderungsmarkierungen am Rand umgeschaltet. */
    data object GutterMarkersToggled : GitSettingsUiEvent
    /** Konflikterkennung umgeschaltet. */
    data object ConflictDetectionToggled : GitSettingsUiEvent
    /** Farbige Dateinamen nach Git-Status umgeschaltet. */
    data object FileNameColorsToggled : GitSettingsUiEvent

    /** Dialog für neue Zugangsdaten öffnen (optional mit Host). */
    data class AddCredentialRequested(val prefillHost: String? = null) : GitSettingsUiEvent
    /** Der Host im Dialog wurde geändert. */
    data class DialogHostChanged(val value: String) : GitSettingsUiEvent
    /** Der Nutzername im Dialog wurde geändert. */
    data class DialogUserChanged(val value: String) : GitSettingsUiEvent
    /** Das Token im Dialog wurde geändert. */
    data class DialogTokenChanged(val value: String) : GitSettingsUiEvent
    /** Sichtbarkeit des Tokens umgeschaltet. */
    data object DialogToggleTokenVisible : GitSettingsUiEvent
    /** Zugangsdaten speichern. */
    data object DialogConfirm : GitSettingsUiEvent
    /** Dialog schließen. */
    data object DialogDismiss : GitSettingsUiEvent

    /** Löschen von Zugangsdaten wurde angefragt. */
    data class DeleteCredentialRequested(val credential: GitCredentialInfo) : GitSettingsUiEvent
    /** Löschen wurde bestätigt. */
    data object DeleteCredentialConfirmed : GitSettingsUiEvent
    /** Löschen wurde abgebrochen. */
    data object DeleteCredentialDismissed : GitSettingsUiEvent
}

/** Einmalige Effekte der Git-Einstellungen. */
sealed interface GitSettingsUiEffect {
    /** Kurze Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : GitSettingsUiEffect
}
