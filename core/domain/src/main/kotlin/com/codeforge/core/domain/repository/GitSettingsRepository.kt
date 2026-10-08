// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.GitCredential
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitIdentity
import kotlinx.coroutines.flow.Flow

/** Git-Benutzereinstellungen. Tokens werden verschlüsselt (Android Keystore) gespeichert. */
interface GitSettingsRepository {
    val identity: Flow<GitIdentity>
    val credentials: Flow<List<GitCredentialInfo>>

    suspend fun currentIdentity(): GitIdentity
    suspend fun setIdentity(identity: GitIdentity)

    /** Legt den Zugang für [host] an oder ersetzt ihn. */
    suspend fun saveCredential(host: String, username: String, token: String)
    suspend fun removeCredential(host: String)

    /** Passender Zugang für eine Remote-URL (nur HTTP/HTTPS), sonst `null`. */
    suspend fun credentialFor(remoteUrl: String): GitCredential?
}
