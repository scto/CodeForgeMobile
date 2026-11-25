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

    /** Liest den letzten Wert von [key] oder `null`. */
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
    /** Pfad der Datei (Anzeige). */
    val path: String get() = file.path

    /** Liest den Wert von [key] aus der Datei. */
    fun read(key: String): String? =
        runCatching { if (file.isFile) TermuxPropertiesText.get(file.readText(), key) else null }.getOrNull()

    /** Schreibt [key] (`null` entfernt ihn); `true` bei Erfolg. */
    fun write(key: String, value: String?): Boolean = runCatching {
        val old = if (file.isFile) file.readText() else ""
        file.parentFile?.mkdirs()
        file.writeText(TermuxPropertiesText.set(old, key, value))
    }.isSuccess

    /** Ganzer Inhalt; leer, wenn die Datei fehlt. */
    fun readText(): String = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")

    /**
     * Ersetzt den ganzen Inhalt durch [text]. Eine vorhandene Datei wird vorher nach
     * `~/.termux/backup/termux.properties.<zeit>.bak` gesichert (die neuesten [MAX_BACKUPS] bleiben).
     *
     * @return Sicherungsdatei oder `null`, wenn es noch keine Datei gab.
     */
    fun replaceAll(text: String, now: java.util.Date = java.util.Date()): Result<File?> = runCatching {
        val dir = requireNotNull(file.parentFile) { "kein Ordner: ${file.path}" }
        val backup = if (file.isFile) {
            val backups = File(dir, BACKUP_DIR).apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(now)
            File(backups, "${file.name}.$stamp.bak").also { file.copyTo(it, overwrite = true) }
                .also { prune(backups) }
        } else {
            dir.mkdirs()
            null
        }
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(if (text.isEmpty() || text.endsWith("\n")) text else text + "\n")
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        backup
    }

    private fun prune(backups: File) {
        backups.listFiles { f -> f.name.startsWith(file.name) && f.name.endsWith(".bak") }.orEmpty()
            .sortedByDescending { it.name }
            .drop(MAX_BACKUPS)
            .forEach { it.delete() }
    }

    companion object {
        /** Unterordner der Sicherungen (neben termux.properties). */
        const val BACKUP_DIR = "backup"
        /** So viele Sicherungen bleiben erhalten. */
        const val MAX_BACKUPS = 10
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
