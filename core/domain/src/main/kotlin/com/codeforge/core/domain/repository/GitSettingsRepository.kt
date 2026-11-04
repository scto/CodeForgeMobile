// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.GitCredential
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitIdentity
import kotlinx.coroutines.flow.Flow

/** Git-Benutzereinstellungen. Tokens werden verschlüsselt (Android Keystore) gespeichert. */
interface GitSettingsRepository {
    /** Git-Identität (Name, E-Mail). */
    val identity: Flow<GitIdentity>
    /** Gespeicherte Zugangsdaten (je Host, ohne Geheimnis). */
    val credentials: Flow<List<GitCredentialInfo>>

    /** Liefert die aktuelle Git-Identität einmalig. */
    suspend fun currentIdentity(): GitIdentity
    /** Speichert die Git-Identität. */
    suspend fun setIdentity(identity: GitIdentity)

    /** Legt den Zugang für [host] an oder ersetzt ihn. */
    suspend fun saveCredential(host: String, username: String, token: String)
    /**
     * Entfernt die Zugangsdaten eines Hosts.
     *
     * @param host Hostname, z. B. `github.com`.
     */
    suspend fun removeCredential(host: String)

    /** Passender Zugang für eine Remote-URL (nur HTTP/HTTPS), sonst `null`. */
    suspend fun credentialFor(remoteUrl: String): GitCredential?
}
