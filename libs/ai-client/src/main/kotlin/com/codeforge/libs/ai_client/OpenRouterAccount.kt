// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.libs.agent.LoopbackRedirect
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * OpenRouter-Konto: „Mit OpenRouter anmelden“ (OAuth PKCE, liefert einen echten Schlüssel des eigenen Kontos) und
 * der Verbrauch eines Schlüssels (`GET /api/v1/key`). Nach dem Vorbild von CodeAssist (GPL-3.0).
 */
object OpenRouterAccount {
    private const val AUTH_URL = "https://openrouter.ai/auth"
    private const val KEYS_URL = "https://openrouter.ai/api/v1/auth/keys"
    private const val KEY_INFO_URL = "https://openrouter.ai/api/v1/key"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Eine Anmeldung: [url] im Browser öffnen, [verifier] für [exchange] behalten. */
    data class Attempt(val url: String, val verifier: String, val state: String)

    /** Startet eine Anmeldung mit Rückleitung auf [callbackUrl]. */
    fun start(callbackUrl: String, keyLabel: String = "CodeForge Mobile"): Attempt {
        val verifier = LoopbackRedirect.randomToken(48)
        val state = LoopbackRedirect.randomToken(16)
        val url = AUTH_URL + "?callback_url=" + enc(callbackUrl) + "&code_challenge=" + LoopbackRedirect.challenge(verifier) +
            "&code_challenge_method=S256&key_label=" + enc(keyLabel) + "&state=" + state
        return Attempt(url, verifier, state)
    }

    /**
     * Ganze Anmeldung: Browser über [openBrowser] öffnen, Rückleitung abwarten, Schlüssel holen.
     * [onWaiting] erhält eine Funktion, mit der sich die Rückleitungsadresse aus dem Browser nachreichen lässt,
     * falls sie die App nicht erreicht (liefert `false` bei unpassender Adresse).
     */
    suspend fun signIn(openBrowser: (String) -> Unit, onWaiting: ((deliver: (String) -> Boolean) -> Unit)? = null): String =
        LoopbackRedirect().use { redirect ->
        val attempt = start(redirect.redirectUri)
        onWaiting?.invoke(redirect::deliver)
        openBrowser(attempt.url)
        val params = redirect.await()
        params["error"]?.let { throw IOException("OpenRouter: $it") }
        if (params["state"] != null && params["state"] != attempt.state) throw IOException("OpenRouter: falscher state")
        val code = params["code"] ?: throw IOException("OpenRouter hat keinen Code geliefert")
        exchange(code, attempt.verifier)
    }

    /** Tauscht den Code gegen den API-Schlüssel. */
    suspend fun exchange(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("code", code); put("code_verifier", verifier); put("code_challenge_method", "S256") }.toString()
        val (status, text) = http("POST", KEYS_URL, emptyMap(), body)
        if (status !in 200..299) throw IOException("OpenRouter hat keinen Schlüssel ausgegeben (HTTP $status)")
        (json.parseToJsonElement(text).jsonObject["key"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IOException("OpenRouter hat keinen Schlüssel ausgegeben")
    }

    /** Verbrauch des Schlüssels [key]. */
    suspend fun usage(key: String, nowMs: Long = System.currentTimeMillis()): OpenRouterUsage = withContext(Dispatchers.IO) {
        val (status, text) = http("GET", KEY_INFO_URL, mapOf("Authorization" to "Bearer $key"), null)
        if (status !in 200..299) throw IOException("OpenRouter: HTTP $status")
        OpenRouterUsage.parse(text, nowMs)
    }

    private fun http(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/**
 * Verbrauch eines OpenRouter-Schlüssels: Gratis-Anfragen heute ([freeRequestsUsed]/[freeRequestsLimit], Reset um
 * Mitternacht UTC) und das Budget, das zuerst greift ([budgetUsedUsd]/[budgetLimitUsd]).
 */
data class OpenRouterUsage(
    val freeRequestsUsed: Int? = null,
    val freeRequestsLimit: Int? = null,
    val budgetUsedUsd: Double? = null,
    val budgetLimitUsd: Double? = null,
    val spentTodayUsd: Double? = null,
    val spentMonthUsd: Double? = null,
    val isFreeTier: Boolean? = null,
    val freeResetsAtMs: Long? = null,
) {
    /** Leser der Antwort von `GET /api/v1/key`. */
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Liest die Antwort. */
        fun parse(text: String, nowMs: Long): OpenRouterUsage {
            val data = json.parseToJsonElement(text).jsonObject["data"] as? JsonObject
                ?: throw IllegalArgumentException("OpenRouter hat keine Schlüsseldaten geliefert")
            fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
            val free = data["free_model_daily_requests"] as? JsonObject
            val freeLimit = free?.str("limit")?.toIntOrNull()
            val freeUsed = free?.str("used")?.toIntOrNull() ?: free?.str("remaining")?.toIntOrNull()?.let { r -> freeLimit?.let { it - r } }
            val effective = data["effective_budget"] as? JsonObject
            val limit: Double?
            val used: Double?
            if (effective?.str("limit_usd")?.toDoubleOrNull() != null) {
                limit = effective.str("limit_usd")?.toDoubleOrNull()
                used = effective.str("spend_usd")?.toDoubleOrNull() ?: effective.str("remaining_usd")?.toDoubleOrNull()?.let { limit!! - it }
            } else {
                limit = data.str("limit")?.toDoubleOrNull()
                used = limit?.let { l -> data.str("limit_remaining")?.toDoubleOrNull()?.let { l - it } ?: data.str("usage")?.toDoubleOrNull() }
            }
            val tomorrow = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
            return OpenRouterUsage(
                freeRequestsUsed = freeUsed,
                freeRequestsLimit = freeLimit,
                budgetUsedUsd = used?.coerceAtLeast(0.0),
                budgetLimitUsd = limit,
                spentTodayUsd = data.str("usage_daily")?.toDoubleOrNull(),
                spentMonthUsd = data.str("usage_monthly")?.toDoubleOrNull(),
                isFreeTier = data.str("is_free_tier")?.toBooleanStrictOrNull(),
                freeResetsAtMs = freeLimit?.let { LocalDate.from(tomorrow).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            )
        }
    }
}
