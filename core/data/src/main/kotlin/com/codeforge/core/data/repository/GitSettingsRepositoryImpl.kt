// Modul: :core:data
package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.data.security.KeyValueStore
import com.codeforge.core.data.security.KeystoreSecretBox
import com.codeforge.core.data.security.SecretBox
import com.codeforge.core.data.security.SharedPreferencesStore
import com.codeforge.core.domain.model.GitCredential
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitIdentity
import com.codeforge.core.domain.model.GitUrl
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speichert Name/E-Mail im Klartext und Tokens ausschließlich verschlüsselt (AES-GCM, Schlüssel im
 * Android Keystore, nicht exportierbar). Zugangsdaten sind an HTTPS-Hosts gebunden.
 */
@Singleton
class GitSettingsRepositoryImpl internal constructor(
    private val store: KeyValueStore,
    private val box: SecretBox,
) : GitSettingsRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesStore(context.getSharedPreferences("codeforge_git", Context.MODE_PRIVATE)),
        KeystoreSecretBox(),
    )

    private val lock = Mutex()
    private val _identity = MutableStateFlow(readIdentity())
    private val _credentials = MutableStateFlow(readCredentialInfos())

    override val identity: Flow<GitIdentity> = _identity.asStateFlow()
    override val credentials: Flow<List<GitCredentialInfo>> = _credentials.asStateFlow()

    override suspend fun currentIdentity(): GitIdentity = _identity.value

    override suspend fun setIdentity(identity: GitIdentity) = withContext(Dispatchers.IO) {
        lock.withLock {
            val clean = GitIdentity(identity.name.trim(), identity.email.trim())
            store.put(KEY_NAME, clean.name)
            store.put(KEY_EMAIL, clean.email)
            _identity.value = clean
        }
    }

    override suspend fun saveCredential(host: String, username: String, token: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val h = GitUrl.normalizeHostInput(host)
            require(h.isNotEmpty()) { Res.string(R.string.data_host_fehlt) }
            require(username.isNotBlank()) { Res.string(R.string.common_benutzername_fehlt) }
            require(token.isNotBlank()) { Res.string(R.string.common_token_fehlt) }
            store.put("cred.$h.user", username.trim())
            store.put("cred.$h.token", box.encrypt(token.trim()))
            store.put(KEY_HOSTS, (hosts() + h).distinct().sorted().joinToString("\n"))
            _credentials.value = readCredentialInfos()
        }
    }

    override suspend fun removeCredential(host: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val h = GitUrl.normalizeHostInput(host)
            store.remove("cred.$h.user")
            store.remove("cred.$h.token")
            store.put(KEY_HOSTS, (hosts() - h).joinToString("\n"))
            _credentials.value = readCredentialInfos()
        }
    }

    override suspend fun credentialFor(remoteUrl: String): GitCredential? = withContext(Dispatchers.IO) {
        val host = GitUrl.httpHost(remoteUrl) ?: return@withContext null
        if (host !in hosts()) return@withContext null
        val user = store.get("cred.$host.user") ?: return@withContext null
        val token = store.get("cred.$host.token")?.let(box::decrypt) ?: return@withContext null
        GitCredential(host, user, token)
    }

    private fun hosts(): List<String> = store.get(KEY_HOSTS).orEmpty().split('\n').filter { it.isNotBlank() }

    private fun readIdentity() = GitIdentity(store.get(KEY_NAME).orEmpty(), store.get(KEY_EMAIL).orEmpty())

    private fun readCredentialInfos(): List<GitCredentialInfo> =
        hosts().mapNotNull { h -> store.get("cred.$h.user")?.let { GitCredentialInfo(h, it) } }

    private companion object {
        const val KEY_NAME = "identity.name"
        const val KEY_EMAIL = "identity.email"
        const val KEY_HOSTS = "cred.hosts"
    }
}
