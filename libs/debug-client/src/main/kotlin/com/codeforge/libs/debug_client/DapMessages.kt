package com.codeforge.libs.debug_client

import kotlinx.serialization.json.JsonObject

/**
 * Basis-Typen für das Debug Adapter Protocol (DAP).
 */
sealed interface DapMessage {
    /** Sequenznummer der Nachricht. */
    val seq: Int
}

/** Anfrage an den Debug-Adapter. */
data class DapRequest(
    override val seq: Int,
    val command: String,
    val arguments: JsonObject? = null
) : DapMessage

/** Antwort des Debug-Adapters. */
data class DapResponse(
    override val seq: Int,
    val requestSeq: Int,
    val success: Boolean,
    val command: String,
    val message: String? = null,
    val body: JsonObject? = null
) : DapMessage

/** Ereignis des Debug-Adapters. */
data class DapEvent(
    override val seq: Int,
    val event: String,
    val body: JsonObject? = null
) : DapMessage
