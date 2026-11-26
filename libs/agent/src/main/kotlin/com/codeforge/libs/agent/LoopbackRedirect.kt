/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Einmaliger HTTP-Empfänger auf `127.0.0.1` für OAuth-Anmeldungen (OpenRouter, MCP-Server): Der Browser wird nach
 * der Anmeldung auf `http://localhost:<port>/callback?code=…` umgeleitet, die App liest den Code mit.
 */
package com.codeforge.libs.agent

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Empfänger für genau eine Umleitung; [redirectUri] an den Anbieter geben, dann [await] aufrufen. */
class LoopbackRedirect(private val path: String = "/callback") : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

    /** Umleitungsadresse für den Anbieter. */
    val redirectUri: String = "http://localhost:${server.localPort}$path"

    /**
     * Wartet auf die Umleitung und liefert ihre Query-Parameter (z. B. `code`, `state`, `error`).
     * Wirft `SocketTimeoutException` nach [timeoutMs].
     */
    suspend fun await(timeoutMs: Int = 5 * 60_000, successHtml: String = DEFAULT_HTML): Map<String, String> = withContext(Dispatchers.IO) {
        server.soTimeout = timeoutMs
        var result: Map<String, String>? = null
        while (result == null) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                val requestLine = reader.readLine().orEmpty()
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val target = requestLine.split(' ').getOrNull(1).orEmpty()
                val params = parseQuery(target.substringAfter('?', ""))
                val hit = target.substringBefore('?') == path && params.isNotEmpty()
                val body = if (hit) successHtml else "<html><body>Not found</body></html>"
                val status = if (hit) "200 OK" else "404 Not Found"
                val bytes = body.toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
                if (hit) result = params
            }
        }
        result.orEmpty()
    }

    override fun close() {
        runCatching { server.close() }
    }

    /** Hilfen für PKCE. */
    companion object {
        private const val DEFAULT_HTML =
            "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\">" +
                "<h2>Angemeldet</h2><p>Du kannst zu CodeForge Mobile zurückkehren.</p></body></html>"
        private val random = SecureRandom()

        /** Zufälliger Base64url-Token aus [bytes] Bytes. */
        fun randomToken(bytes: Int): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

        /** S256-Challenge zu [verifier]. */
        fun challenge(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        /** Query-String → Parameter. */
        fun parseQuery(query: String): Map<String, String> = query.split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
    }
}
