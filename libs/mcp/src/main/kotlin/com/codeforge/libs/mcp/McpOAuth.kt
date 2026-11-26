/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 *
 * Anmeldung bei MCP-Servern nach der Autorisierungsspezifikation von MCP (OAuth 2.1). Portiert aus CodeAssist
 * (GPL-3.0, `McpOAuth`), auf `HttpURLConnection` umgestellt.
 */
package com.codeforge.libs.mcp

import com.codeforge.libs.agent.LoopbackRedirect
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Ergebnis einer Anmeldung; spätere Anfragen tragen den Token, ein abgelaufener wird erneuert. */
data class McpTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    /** Ablauf in Epoch-Millisekunden; `null`, wenn der Server nichts sagt. */
    val expiresAtMs: Long? = null,
    val tokenEndpoint: String,
    val clientId: String,
    val clientSecret: String? = null,
    /** Ressource, für die der Token ausgestellt wurde (die Server-URL). */
    val resource: String? = null,
) {
    /** Als JSON speichern. */
    fun toJson(): JsonObject = buildJsonObject {
        put("accessToken", accessToken)
        refreshToken?.let { put("refreshToken", it) }
        expiresAtMs?.let { put("expiresAtMs", it) }
        put("tokenEndpoint", tokenEndpoint)
        put("clientId", clientId)
        clientSecret?.let { put("clientSecret", it) }
        resource?.let { put("resource", it) }
    }

    /** Leser. */
    companion object {
        /** Aus JSON; `null` bei fehlenden Pflichtfeldern. */
        fun fromJson(o: JsonObject): McpTokens? {
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            val access = s("accessToken")
            val endpoint = s("tokenEndpoint")
            val client = s("clientId")
            if (access == null || endpoint == null || client == null) return null
            return McpTokens(access, s("refreshToken"), s("expiresAtMs")?.toLongOrNull(), endpoint, client, s("clientSecret"), s("resource"))
        }
    }
}

/** Abgelaufen (mit einer Minute Puffer)? */
fun McpTokens.expired(nowMs: Long = System.currentTimeMillis()): Boolean = expiresAtMs != null && nowMs >= expiresAtMs - EXPIRY_MARGIN_MS

private const val EXPIRY_MARGIN_MS = 60_000L
private const val JSON_TYPE = "application/json"
private const val FORM_TYPE = "application/x-www-form-urlencoded"
private const val KEY_ERROR = "error"
private const val KEY_ERROR_DESCRIPTION = "error_description"

/** Speichert die Anmeldungen je Server (Schlüssel: Name und URL). */
interface McpTokenStore {
    /** Gespeicherte Anmeldung oder `null`. */
    fun get(key: String): McpTokens?

    /** Speichert bzw. entfernt (`null`) die Anmeldung. */
    fun put(key: String, tokens: McpTokens?)
}

/**
 * MCP-Autorisierung (OAuth 2.1):
 * 1. Das 401 des Servers nennt seine Protected-Resource-Metadaten (RFC 9728) und darin den Autorisierungsserver;
 *    ohne sie gilt der Ursprung des MCP-Servers.
 * 2. Dessen Metadaten (RFC 8414 bzw. OpenID Discovery) liefern die Endpunkte.
 * 3. Die App registriert sich als öffentlicher Client (RFC 7591) für die Umleitungs-URI dieser Anmeldung.
 * 4. Der Browser macht den Authorization-Code-Grant mit PKCE (S256) und `resource` (RFC 8707).
 * 5. Der Code wird gegen Tokens getauscht; [refresh] erneuert sie.
 * Server ohne dynamische Registrierung gehen so nicht – dann bleibt der Token im Header.
 */
object McpOAuth {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Alles, was eine Anmeldung zwischen Browser und Rückleitung braucht. */
    data class Attempt(
        val authorizeUrl: String,
        val state: String,
        val verifier: String,
        val redirectUri: String,
        val tokenEndpoint: String,
        val clientId: String,
        val clientSecret: String?,
        val resource: String,
    )

    /** Fehler bei der Anmeldung (Meldung für den Nutzer). */
    class OAuthException(message: String) : IOException(message)

    /** Ganze Anmeldung bei [serverUrl]: Browser über [openBrowser], Rückleitung abwarten, Tokens holen. */
    suspend fun signIn(serverUrl: String, wwwAuthenticate: String?, openBrowser: (String) -> Unit): McpTokens =
        LoopbackRedirect().use { redirect ->
            val attempt = begin(serverUrl, redirect.redirectUri, wwwAuthenticate)
            openBrowser(attempt.authorizeUrl)
            val params = redirect.await()
            params[KEY_ERROR]?.let { throw OAuthException("Anmeldung abgelehnt: ${params[KEY_ERROR_DESCRIPTION] ?: it}") }
            if (params["state"] != attempt.state) throw OAuthException("Anmeldung ungültig (state passt nicht)")
            exchange(attempt, params["code"] ?: throw OAuthException("Der Server hat keinen Code geliefert"))
        }

    /** Ermittelt die Anmeldung des Servers, registriert einen Client und baut die Browser-URL. */
    suspend fun begin(serverUrl: String, redirectUri: String, wwwAuthenticate: String?, clientName: String = "CodeForge Mobile"): Attempt =
        withContext(Dispatchers.IO) {
            val resource = canonicalResource(serverUrl)
            val challenge = parseChallenge(wwwAuthenticate)
            val prm = protectedResourceMetadata(serverUrl, challenge["resource_metadata"])
            val issuer = (prm?.get("authorization_servers") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: origin(serverUrl)
            val meta = authorizationServerMetadata(issuer)
            val authorize = meta?.str("authorization_endpoint") ?: "${origin(issuer)}/authorize"
            val token = meta?.str("token_endpoint") ?: "${origin(issuer)}/token"
            val register = meta?.str("registration_endpoint") ?: (if (meta == null) "${origin(issuer)}/register" else null)
                ?: throw OAuthException("Dieser Server erlaubt keine App-Registrierung. Token stattdessen als Header eintragen (Authorization: Bearer …).")
            val methods = (meta?.get("code_challenge_methods_supported") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (methods != null && "S256" !in methods) throw OAuthException("Die Anmeldung dieses Servers unterstützt kein PKCE.")
            val (clientId, clientSecret) = registerClient(register, redirectUri, clientName)
            val scope = challenge["scope"]
                ?: (prm?.get("scopes_supported") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(" ")?.takeIf { it.isNotBlank() }
            val verifier = LoopbackRedirect.randomToken(48)
            val state = LoopbackRedirect.randomToken(24)
            val query = listOfNotNull(
                "response_type" to "code", "client_id" to clientId, "redirect_uri" to redirectUri,
                "code_challenge" to LoopbackRedirect.challenge(verifier), "code_challenge_method" to "S256",
                "state" to state, "resource" to resource, scope?.let { "scope" to it },
            ).joinToString("&") { (k, v) -> k + "=" + enc(v) }
            val url = authorize + (if (authorize.contains('?')) "&" else "?") + query
            Attempt(url, state, verifier, redirectUri, token, clientId, clientSecret, resource)
        }

    /** Tauscht den [code] der Rückleitung gegen Tokens. */
    suspend fun exchange(attempt: Attempt, code: String): McpTokens = withContext(Dispatchers.IO) {
        val form = listOfNotNull(
            "grant_type" to "authorization_code", "code" to code, "redirect_uri" to attempt.redirectUri,
            "client_id" to attempt.clientId, "code_verifier" to attempt.verifier, "resource" to attempt.resource,
            attempt.clientSecret?.let { "client_secret" to it },
        )
        tokenRequest(attempt.tokenEndpoint, form, attempt.clientId, attempt.clientSecret, attempt.resource, null)
    }

    /** Erneuert [tokens] mit dem Refresh-Token. */
    suspend fun refresh(tokens: McpTokens): McpTokens = withContext(Dispatchers.IO) {
        val refresh = tokens.refreshToken ?: throw OAuthException("Die Anmeldung ist abgelaufen. Bitte neu anmelden.")
        val form = listOfNotNull(
            "grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to tokens.clientId,
            tokens.resource?.let { "resource" to it }, tokens.clientSecret?.let { "client_secret" to it },
        )
        tokenRequest(tokens.tokenEndpoint, form, tokens.clientId, tokens.clientSecret, tokens.resource, refresh)
    }

    private fun tokenRequest(
        endpoint: String,
        form: List<Pair<String, String>>,
        clientId: String,
        clientSecret: String?,
        resource: String?,
        previousRefresh: String?,
    ): McpTokens {
        val body = form.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        val (status, text) = http("POST", endpoint, body, FORM_TYPE)
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (status !in 200..299 || obj == null) {
            throw OAuthException("Der Server hat keinen Token ausgestellt: " + (obj?.str(KEY_ERROR_DESCRIPTION) ?: obj?.str(KEY_ERROR) ?: "HTTP $status"))
        }
        val access = obj.str("access_token") ?: throw OAuthException("Der Server hat keinen Token ausgestellt.")
        return McpTokens(
            accessToken = access,
            refreshToken = obj.str("refresh_token") ?: previousRefresh,
            expiresAtMs = obj.str("expires_in")?.toLongOrNull()?.let { System.currentTimeMillis() + it * 1000 },
            tokenEndpoint = endpoint, clientId = clientId, clientSecret = clientSecret, resource = resource,
        )
    }

    private fun registerClient(endpoint: String, redirectUri: String, clientName: String): Pair<String, String?> {
        val body = buildJsonObject {
            put("client_name", clientName)
            putJsonArray("redirect_uris") { add(JsonPrimitive(redirectUri)) }
            putJsonArray("grant_types") { add(JsonPrimitive("authorization_code")); add(JsonPrimitive("refresh_token")) }
            putJsonArray("response_types") { add(JsonPrimitive("code")) }
            put("token_endpoint_auth_method", "none")
        }.toString()
        val (status, text) = http("POST", endpoint, body, JSON_TYPE)
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        if (status !in 200..299 || obj == null) {
            throw OAuthException("Der Server hat die App nicht registriert: " + (obj?.str(KEY_ERROR_DESCRIPTION) ?: obj?.str(KEY_ERROR) ?: "HTTP $status"))
        }
        return (obj.str("client_id") ?: throw OAuthException("Registrierung ohne client_id")) to obj.str("client_secret")
    }

    private fun protectedResourceMetadata(serverUrl: String, fromChallenge: String?): JsonObject? {
        val path = URI(serverUrl).rawPath.orEmpty().trimEnd('/')
        val base = origin(serverUrl)
        return listOfNotNull(
            fromChallenge,
            if (path.isNotEmpty()) "$base/.well-known/oauth-protected-resource$path" else null,
            "$base/.well-known/oauth-protected-resource",
        ).firstNotNullOfOrNull(::getJson)
    }

    private fun authorizationServerMetadata(issuer: String): JsonObject? {
        val path = runCatching { URI(issuer).rawPath.orEmpty().trimEnd('/') }.getOrNull() ?: return null
        val base = origin(issuer)
        val candidates = if (path.isEmpty()) {
            listOf("$base/.well-known/oauth-authorization-server", "$base/.well-known/openid-configuration")
        } else {
            listOf("$base/.well-known/oauth-authorization-server$path", "$base/.well-known/openid-configuration$path", "$base$path/.well-known/openid-configuration")
        }
        return candidates.firstNotNullOfOrNull(::getJson)
    }

    private fun getJson(url: String): JsonObject? = runCatching {
        val (status, text) = http("GET", url, null, null)
        if (status !in 200..299) null else json.parseToJsonElement(text) as? JsonObject
    }.getOrNull()

    private fun http(method: String, url: String, body: String?, contentType: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", JSON_TYPE)
            conn.setRequestProperty("MCP-Protocol-Version", LATEST_PROTOCOL_VERSION)
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    /** `Bearer realm="x", resource_metadata="https://…", scope="a b"` → Parameter. */
    fun parseChallenge(header: String?): Map<String, String> {
        if (header.isNullOrBlank()) return emptyMap()
        val params = header.substringAfter(' ', "")
        return Regex("""([A-Za-z_][A-Za-z0-9_-]*)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^\s,]+))""").findAll(params)
            .associate { m -> m.groupValues[1].lowercase() to m.groupValues[2].ifEmpty { m.groupValues[3] }.replace("\\\"", "\"") }
    }

    /** Server-URL als Token-Zielgruppe: ohne Fragment und End-Schrägstrich. */
    fun canonicalResource(serverUrl: String): String {
        val u = URI(serverUrl)
        return URI(u.scheme?.lowercase(), u.rawAuthority?.lowercase(), u.rawPath, u.rawQuery, null).toString().trimEnd('/')
    }

    private fun origin(url: String): String {
        val u = URI(url)
        val port = if (u.port == -1) "" else ":${u.port}"
        return "${u.scheme}://${u.host}$port"
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
