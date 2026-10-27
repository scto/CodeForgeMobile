// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** Lädt kleine Textantworten (JSON/HTML) per HTTPS; Fehler kommen als [Result.failure]. */
fun interface HttpTextFetcher {
    suspend fun fetch(url: String): Result<String>
}

@Singleton
class DefaultHttpTextFetcher @Inject constructor() : HttpTextFetcher {
    override suspend fun fetch(url: String): Result<String> = withContext(Dispatchers.IO) {
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
                    val code = conn.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        if (++redirects > 5) error("zu viele Weiterleitungen")
                        current = conn.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel")
                        continue
                    }
                    if (code !in 200..299) error("HTTP $code")
                    return@runCatching conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }
            @Suppress("UNREACHABLE_CODE")
            ""
        }
    }
}
