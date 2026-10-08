// Modul: :core:domain
package com.codeforge.core.domain.model

/** Hilfen für Remote-URLs (Host-Erkennung für Zugangsdaten). */
object GitUrl {
    fun isHttp(url: String): Boolean = url.startsWith("https://", true) || url.startsWith("http://", true)

    /** Kleingeschriebener Host einer HTTP(S)-URL; `null` für SSH/lokale Pfade. */
    fun httpHost(url: String): String? {
        if (!isHttp(url)) return null
        val rest = url.substringAfter("://")
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPort = authority.substringAfterLast('@')
        val host = hostPort.substringBefore(':').trim().lowercase()
        return host.ifEmpty { null }
    }

    /** Normalisiert Nutzereingaben im Settings-Dialog („https://github.com/x“ → „github.com“). */
    fun normalizeHostInput(input: String): String {
        val t = input.trim()
        return (httpHost(t) ?: t.substringBefore('/').substringBefore(':')).lowercase()
    }
}
