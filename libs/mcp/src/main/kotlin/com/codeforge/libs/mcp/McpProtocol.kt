/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import java.io.IOException
import kotlinx.serialization.json.JsonObject

/** Neueste Protokollversion, die der Client anbietet. */
const val LATEST_PROTOCOL_VERSION = "2025-06-18"

/**
 * Der Draht unter einem [McpClient]: liefert JSON-RPC-Nachrichten an den Server und gibt jede Nachricht des Servers an
 * den Rückruf aus [start]. Eine Transportart, deren Antworten auf derselben Anfrage zurückkommen (Streamable HTTP),
 * liefert sie an diesen Rückruf, bevor [send] zurückkehrt.
 */
interface McpTransport : AutoCloseable {
    /** Öffnet den Transport. [onMessage] bekommt jede Servernachricht, [onClosed] einmal, wenn die Verbindung endet. */
    suspend fun start(onMessage: (JsonObject) -> Unit, onClosed: (Throwable?) -> Unit)

    /** Sendet [message] an den Server. */
    suspend fun send(message: JsonObject)

    /** Die ausgehandelte Protokollversion (Streamable HTTP trägt sie als Header). */
    fun negotiated(protocolVersion: String) {}

    override fun close()
}

/** Der Server antwortete mit einem JSON-RPC-Fehler. */
class McpException(message: String, val code: Int? = null) : Exception(message)

/** Der Server verlangt Anmeldedaten (HTTP 401); [wwwAuthenticate] ist die Herausforderung des Servers. */
class McpAuthRequiredException(val wwwAuthenticate: String?, message: String = "Der Server verlangt eine Anmeldung.") : IOException(message)

/** Der Server kennt die Sitzung nicht mehr (HTTP 404 bei gesendeter Sitzungs-ID); ein neues `initialize` startet eine neue. */
class McpSessionExpiredException : IOException("Die MCP-Sitzung ist abgelaufen.")

/** Der Server antwortete nicht rechtzeitig auf [method]. */
class McpTimeoutException(val method: String, timeoutMs: Long) : IOException("Der Server antwortete nicht auf $method innerhalb von ${timeoutMs / 1000} s.")

/** HTTP-Fehler, der weder Anmeldung noch abgelaufene Sitzung ist. */
class McpHttpException(val status: Int, message: String) : IOException(message)

/** Ein Werkzeug, wie der Server es auflistet; [readOnly]/[destructive] sind dessen Hinweise (`null` = keine Angabe). */
data class McpToolInfo(
    val name: String,
    val title: String?,
    val description: String?,
    val inputSchema: JsonObject,
    val readOnly: Boolean?,
    val destructive: Boolean?,
)

/** Ergebnis eines Aufrufs, für das Modell abgeflacht: Text, Anzahl nicht darstellbarer Bilder, Fehlerkennzeichen. */
data class McpCallResult(val text: String, val imageCount: Int = 0, val isError: Boolean = false)
