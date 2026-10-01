package com.nyamux.shared.termux.extrakeys

import org.json.JSONArray

data class ExtraKeyButton(
    val key: String,
    val display: String = key,
    val popup: ExtraKeyButton? = null
)

class ExtraKeysInfo(
    matrixString: String,
    displayMap: Map<String, String>? = null,
    aliases: Map<String, String>? = null
) {
    val matrix: Array<Array<ExtraKeyButton>>

    init {
        val parsedMatrix = mutableListOf<Array<ExtraKeyButton>>()
        runCatching {
            val rows = JSONArray(matrixString)
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                val rowList = mutableListOf<ExtraKeyButton>()
                for (j in 0 until row.length()) {
                    val keyStr = row.getString(j)
                    val display = displayMap?.get(keyStr) ?: keyStr
                    rowList.add(ExtraKeyButton(key = keyStr, display = display))
                }
                parsedMatrix.add(rowList.toTypedArray())
            }
        }
        matrix = parsedMatrix.toTypedArray()
    }
}

object ExtraKeysConstants {
    object EXTRA_KEY_DISPLAY_MAPS {
        @JvmField
        val DEFAULT_CHAR_DISPLAY = mapOf(
            "ESC" to "ESC",
            "TAB" to "TAB",
            "CTRL" to "CTRL",
            "ALT" to "ALT",
            "UP" to "▲",
            "DOWN" to "▼",
            "LEFT" to "◀",
            "RIGHT" to "▶"
        )
    }

    @JvmField
    val CONTROL_CHARS_ALIASES = emptyMap<String, String>()

    @JvmField
    val PRIMARY_REPETITIVE_KEYS = listOf("UP", "DOWN", "LEFT", "RIGHT", "BACKSPACE", "DELETE")
}
