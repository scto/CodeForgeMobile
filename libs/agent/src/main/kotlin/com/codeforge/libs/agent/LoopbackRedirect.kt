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
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Empfänger für genau eine Umleitung; [redirectUri] an den Anbieter geben, dann [await] aufrufen.
 *
 * Robust gegen Browser-Eigenheiten: Chrome öffnet vorab leere Verbindungen (Preconnect), die nie eine Anfrage
 * schicken. Jede Verbindung wird deshalb parallel und mit eigener Lesezeitüberschreitung bedient, sonst würde eine
 * leere Verbindung die echte Rückleitung blockieren. Kommt die Rückleitung gar nicht an (z. B. weil Android die
 * App im Hintergrund eingefroren hat), lässt sich die Adresse aus dem Browser per [deliver] nachreichen.
 */
class LoopbackRedirect(private val path: String = "/callback") : Closeable {
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
    private val result = CompletableDeferred<Map<String, String>>()
    private val open: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    /** Umleitungsadresse für den Anbieter. */
    val redirectUri: String = "http://localhost:${server.localPort}$path"

    /**
     * Reicht die Rückleitungsadresse von Hand nach (aus der Adresszeile des Browsers kopiert).
     * Liefert `false`, wenn die Adresse nicht zu diesem Empfänger passt oder keine Parameter enthält.
     */
    fun deliver(callbackUrl: String): Boolean {
        val target = callbackUrl.trim()
        val pathPart = target.substringBefore('?').substringAfter("://", target).substringAfter('/', "")
        val params = parseQuery(target.substringAfter('?', ""))
        if ("/$pathPart" != path || params.isEmpty()) return false
        return result.complete(params)
    }

    /**
     * Wartet auf die Umleitung (oder [deliver]) und liefert ihre Query-Parameter (z. B. `code`, `state`, `error`).
     * Wirft `SocketTimeoutException` nach [timeoutMs].
     */
    suspend fun await(timeoutMs: Int = 10 * 60_000, successHtml: String = DEFAULT_HTML): Map<String, String> = coroutineScope {
        val acceptor = launch(Dispatchers.IO) {
            while (isActive && !result.isCompleted) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                open += socket
                launch(Dispatchers.IO) {
                    try {
                        serve(socket, successHtml)
                    } finally {
                        open -= socket
                    }
                }
            }
        }
        try {
            withTimeoutOrNull(timeoutMs.toLong()) { result.await() }
                ?: throw SocketTimeoutException("Keine Rückleitung innerhalb von ${timeoutMs / 1000} s")
        } finally {
            runCatching { server.close() }
            // Blockierendes Lesen ist nicht abbrechbar: offene (leere) Verbindungen schließen, sonst wartet
            // coroutineScope bis zur Lesezeitüberschreitung.
            open.forEach { runCatching { it.close() } }
            acceptor.cancel()
        }
    }

    private fun serve(socket: Socket, successHtml: String) = socket.use {
        runCatching {
            it.soTimeout = READ_TIMEOUT_MS
            val reader = it.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return@runCatching
            while (reader.readLine()?.isNotEmpty() == true) Unit
            val target = requestLine.split(' ').getOrNull(1).orEmpty()
            val params = parseQuery(target.substringAfter('?', ""))
            val hit = target.substringBefore('?') == path && params.isNotEmpty()
            respond(it, hit, successHtml)
            if (hit) result.complete(params)
        }
    }

    private fun respond(socket: Socket, hit: Boolean, successHtml: String) {
        val body = if (hit) successHtml else "<html><body>Not found</body></html>"
        val status = if (hit) "200 OK" else "404 Not Found"
        val bytes = body.toByteArray()
        socket.getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    override fun close() {
        runCatching { server.close() }
        result.cancel()
    }

    /** Hilfen für PKCE. */
    companion object {
        private const val DEFAULT_HTML =
            "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\">" +
                "<h2>Angemeldet</h2><p>Du kannst zu CodeForge Mobile zurückkehren.</p></body></html>"
        private val random = SecureRandom()
        private const val BACKLOG = 16
        private const val READ_TIMEOUT_MS = 10_000

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
