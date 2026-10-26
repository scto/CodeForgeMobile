// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Gemeinsame Hilfen der KI-Clients.
 *
 * Vorher wurde **jede** `IOException` als „Server nicht erreichbar“ gemeldet, auch eine kaputte URL, ein Zeitlimit oder
 * ein Zertifikatsfehler; die echte Ursache ging verloren. Jetzt nennt die Meldung die Art des Fehlers und die technische
 * Einzelheit.
 */
internal object AiHttpSupport {

    private val KEY_PARAM = Regex("([?&]key=)[^&\\s)]+", RegexOption.IGNORE_CASE)

    /** Entfernt Leerraum und Steuerzeichen (eingefügte Schlüssel enden oft auf Zeilenumbruch oder Leerzeichen). */
    fun cleanKey(raw: String): String = raw.filter { !it.isWhitespace() && !it.isISOControl() }

    /**
     * Leere Eingabe ergibt [default]; Leerraum und Endschrägstriche werden entfernt, ebenso ein versehentlich mit
     * eingefügter Endpunkt-Pfad aus [stripSuffixes] (z. B. `/v1/messages`).
     */
    fun normalizeBaseUrl(raw: String?, default: String, stripSuffixes: List<String> = emptyList()): String {
        var url = raw?.trim().orEmpty().trimEnd('/')
        if (url.isEmpty()) return default
        if (!url.contains("://")) url = "https://$url"
        for (suffix in stripSuffixes) {
            if (url.endsWith(suffix, ignoreCase = true)) {
                url = url.dropLast(suffix.length).trimEnd('/')
            }
        }
        return url
    }

    fun describe(e: IOException): String {
        // Meldungen können die Adresse samt `?key=…` enthalten: Schlüssel nie anzeigen.
        val message = e.message.orEmpty().replace(KEY_PARAM, "$1***")
        val detail = "${e.javaClass.simpleName}: $message".trim().trimEnd(':')
        val kind = when (e) {
            is MalformedURLException -> R.string.ai_error_bad_url
            is UnknownHostException -> R.string.ai_error_dns
            is SocketTimeoutException -> R.string.ai_error_timeout
            is SSLException -> R.string.ai_error_tls
            is ConnectException, is NoRouteToHostException -> R.string.ai_error_connect
            else -> R.string.ai_error_offline
        }
        return Res.string(kind) + " (" + detail + ")"
    }
}
