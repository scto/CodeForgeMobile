// Modul: :core:domain
package com.codeforge.core.domain.security

/**
 * Sicherer Speicher für vertrauliche Daten (Tokens, Keystore-Passwörter, etc.).
 * Implementierungen müssen sicherstellen, dass Daten verschlüsselt gespeichert
 * und niemals im Klartext in Logs oder Dateisystem-Caches abgelegt werden.
 */
interface SecretStorage {
    suspend fun getSecret(key: String): String?
    suspend fun storeSecret(key: String, secret: String)
    suspend fun removeSecret(key: String)
}
