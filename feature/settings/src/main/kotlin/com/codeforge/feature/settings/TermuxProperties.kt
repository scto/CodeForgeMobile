/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import android.annotation.SuppressLint
import java.io.File

/** Reine Textfunktionen für `termux.properties` (Kommentare und fremde Zeilen bleiben unverändert). */
object TermuxPropertiesText {
    private fun keyOf(line: String): String? {
        val t = line.trimStart()
        if (t.isEmpty() || t.startsWith("#")) return null
        val idx = t.indexOf('=')
        return if (idx > 0) t.substring(0, idx).trim() else null
    }

    fun get(text: String, key: String): String? =
        text.lineSequence().lastOrNull { keyOf(it) == key }?.substringAfter('=')?.trim()

    /** Setzt [key] auf [value]; `null` entfernt den Schlüssel (Termux-Standard greift). */
    fun set(text: String, key: String, value: String?): String {
        val lines = if (text.isEmpty()) mutableListOf() else text.split('\n').toMutableList()
        val indexes = lines.indices.filter { keyOf(lines[it]) == key }
        if (value == null) {
            indexes.reversed().forEach { lines.removeAt(it) }
        } else if (indexes.isEmpty()) {
            if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
            lines += "$key=$value"
            lines += ""
        } else {
            lines[indexes.last()] = "$key=$value"
            indexes.dropLast(1).reversed().forEach { lines.removeAt(it) }
        }
        return lines.joinToString("\n")
    }
}

/** Liest/schreibt `~/.termux/termux.properties`; Änderungen gelten für neue Terminal-Sitzungen. */
class TermuxPropertiesStore(private val file: File = defaultFile()) {
    fun read(key: String): String? =
        runCatching { if (file.isFile) TermuxPropertiesText.get(file.readText(), key) else null }.getOrNull()

    fun write(key: String, value: String?): Boolean = runCatching {
        val old = if (file.isFile) file.readText() else ""
        file.parentFile?.mkdirs()
        file.writeText(TermuxPropertiesText.set(old, key, value))
    }.isSuccess

    companion object {
        const val KEY_TRANSCRIPT_ROWS = "terminal-transcript-rows"
        const val KEY_CURSOR_STYLE = "terminal-cursor-style"
        const val KEY_EXTRA_KEYS = "extra-keys"

        @SuppressLint("SdCardPath")
        fun defaultFile(): File {
            val home = System.getenv("HOME")?.takeIf { it.isNotBlank() } ?: "/data/data/com.codeforge.app/files/home"
            return File(home, ".termux/termux.properties")
        }
    }
}
