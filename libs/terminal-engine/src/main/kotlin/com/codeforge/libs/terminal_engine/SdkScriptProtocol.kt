/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 *
 * Parser für die `--machine`-Ausgabe von `codeforge-env` (siehe assets/codeforge-env).
 * Bewusst ohne Android-Abhängigkeiten, damit er auf der JVM testbar ist.
 */
package com.codeforge.libs.terminal_engine

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res

sealed interface SdkScriptLine {
    data class Item(val id: String, val version: String, val installed: Boolean, val path: String?) : SdkScriptLine
    data class Progress(val percent: Int, val message: String) : SdkScriptLine
    data class Ok(val id: String) : SdkScriptLine
    data class Error(val message: String) : SdkScriptLine
}

object SdkScriptProtocol {
    private const val PREFIX = "CFSDK|"

    /** `null` für Zeilen ohne Protokoll-Präfix (normale Ausgabe) und für fehlerhafte Zeilen. */
    fun parse(line: String): SdkScriptLine? {
        val l = line.trim()
        if (!l.startsWith(PREFIX)) return null
        val parts = l.removePrefix(PREFIX).split('|')
        return when (parts.firstOrNull()) {
            "ITEM" -> if (parts.size >= 5) SdkScriptLine.Item(
                id = parts[1], version = parts[2], installed = parts[3] == "1",
                path = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
            ) else null
            "PROGRESS" -> if (parts.size >= 3) SdkScriptLine.Progress(
                (parts[1].toIntOrNull() ?: return null).coerceIn(0, 100), parts.drop(2).joinToString("|"),
            ) else null
            "OK" -> parts.getOrNull(1)?.let { SdkScriptLine.Ok(it) }
            "ERR" -> SdkScriptLine.Error(parts.drop(1).joinToString("|").ifBlank { Res.string(R.string.terminal_engine_unbekannter_fehler) })
            else -> null
        }
    }

    /** Paket-ID muss ins Schema passen, bevor sie an die Shell geht. */
    private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._;\\-]{0,63}$")
    fun isSafeId(id: String): Boolean = SAFE_ID.matches(id)
}
