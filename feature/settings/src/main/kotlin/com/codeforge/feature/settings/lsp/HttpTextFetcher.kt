// Modul: :feature:settings
package com.codeforge.feature.settings.lsp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Antwort eines bedingten Abrufs. */
sealed interface ConditionalText {
    /** Neuer Inhalt mit seinem ETag (falls der Server einen liefert). */
    data class Fresh(val body: String, val etag: String?) : ConditionalText
    /** HTTP 304: der Inhalt mit dem übergebenen ETag ist aktuell. */
    data object NotModified : ConditionalText
}

/** Lädt kleine Textantworten (JSON/HTML) per HTTPS; Fehler kommen als [Result.failure]. */
fun interface HttpTextFetcher {
    /** Lädt den Text unter [url]. */
    suspend fun fetch(url: String): Result<String>

    /** Lädt [url] nur, wenn sich der Inhalt seit [etag] geändert hat (`If-None-Match`). */
    suspend fun fetchIfChanged(url: String, etag: String?): Result<ConditionalText> =
        fetch(url).map { ConditionalText.Fresh(it, null) }
}

/** Standardimplementierung über HTTPS (folgt Weiterleitungen). */
@Singleton
class DefaultHttpTextFetcher @Inject constructor() : HttpTextFetcher {
    override suspend fun fetch(url: String): Result<String> =
        fetchIfChanged(url, null).map { (it as ConditionalText.Fresh).body }

    override suspend fun fetchIfChanged(url: String, etag: String?): Result<ConditionalText> = withContext(Dispatchers.IO) {
        runCatching {
            var current = url
            var redirects = 0
            while (true) {
                val conn = URI(current).toURL().openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 20_000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", "CodeForgeMobile/3.0.0")
                    conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                    if (etag != null) conn.setRequestProperty("If-None-Match", etag)
                    val code = conn.responseCode
                    if (code == HttpURLConnection.HTTP_NOT_MODIFIED && etag != null) return@runCatching ConditionalText.NotModified
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        if (++redirects > 5) error("zu viele Weiterleitungen")
                        current = conn.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel")
                        continue
                    }
                    if (code !in 200..299) error("HTTP $code")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return@runCatching ConditionalText.Fresh(body, conn.getHeaderField("ETag"))
                } finally {
                    conn.disconnect()
                }
            }
            @Suppress("UNREACHABLE_CODE")
            ConditionalText.NotModified
        }
    }
}
