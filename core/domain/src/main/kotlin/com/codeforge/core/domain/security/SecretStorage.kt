// Modul: :core:domain
package com.codeforge.core.domain.security

/**
 * Sicherer Speicher für vertrauliche Daten (Tokens, Keystore-Passwörter, etc.).
 * Implementierungen müssen sicherstellen, dass Daten verschlüsselt gespeichert
 * und niemals im Klartext in Logs oder Dateisystem-Caches abgelegt werden.
 */
interface SecretStorage {
    /** Liest das Geheimnis zu [key] oder `null`. */
    suspend fun getSecret(key: String): String?
    /** Speichert [secret] verschlüsselt unter [key]. */
    suspend fun storeSecret(key: String, secret: String)
    /** Löscht das Geheimnis zu [key]. */
    suspend fun removeSecret(key: String)
}
