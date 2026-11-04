/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/** Log-Level wie in Logcat (Buchstabe und Priorität). */
enum class LogLevel(val letter: Char, val priority: Int) {
    VERBOSE('V', 2),
    DEBUG('D', 3),
    INFO('I', 4),
    WARN('W', 5),
    ERROR('E', 6),
    FATAL('F', 7);

    companion object {
        /** Ordnet einen Level-Buchstaben zu (`A` zählt als FATAL, Unbekanntes als DEBUG). */
        fun fromLetter(c: Char): LogLevel = when (c.uppercaseChar()) {
            'V' -> VERBOSE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E' -> ERROR
            'F', 'A' -> FATAL
            else -> DEBUG
        }
    }
}

/** Ein Stack-Frame einer Ausnahme mit Klasse, Methode, Datei, Zeile und Originaltext. */
data class StackFrame(
    val className: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int,
    val rawLine: String
)

/** Ein Logcat-Eintrag; [isCrash] markiert Abstürze, [stackFrames] die geparsten Frames. */
data class LogEntry(
    val id: Long,
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val isCrash: Boolean = false,
    val stackFrames: List<StackFrame> = emptyList()
) {
    /** Eintrag als eine Textzeile im Logcat-Format. */
    val fullFormattedText: String
        get() = "$timestamp $pid $tid ${level.letter} $tag: $message"
}

/** Quelle der Logs: eigener Prozess, `READ_LOGS`-Berechtigung oder ADB über Loopback. */
enum class LogcatSource {
    OWN_PROCESS,
    READ_LOGS_PERMISSION,
    ADB_LOOPBACK
}

/** Ob eine [LogcatSource] nutzbar ist, samt Ressourcen-IDs für Erklärung und Hilfetext. */
data class LogcatSourceAvailability(
    val source: LogcatSource,
    val isAvailable: Boolean,
    val explanationResId: Int,
    val helpInstructionResId: Int? = null
)

/** Filter für Logcat-Einträge (Paket, PID, Mindest-Level, Tag, Text, nur Abstürze). */
data class LogcatFilter(
    val packageName: String = "",
    val targetPid: Int? = null,
    val minLevel: LogLevel = LogLevel.VERBOSE,
    val tagQuery: String = "",
    val textQuery: String = "",
    val crashesOnly: Boolean = false
) {
    /** Ob [entry] alle gesetzten Filterkriterien erfüllt. */
    fun matches(entry: LogEntry): Boolean {
        if (entry.level.priority < minLevel.priority) return false
        if (crashesOnly && !entry.isCrash) return false

        if (targetPid != null && entry.pid != targetPid) {
            return false
        }

        if (packageName.isNotBlank()) {
            val matchesPackage = (targetPid != null && entry.pid == targetPid) ||
                entry.message.contains(packageName, ignoreCase = true) ||
                entry.tag.contains(packageName, ignoreCase = true) ||
                entry.stackFrames.any { it.className.startsWith(packageName, ignoreCase = true) }
            if (!matchesPackage) return false
        }

        if (tagQuery.isNotBlank() && !entry.tag.contains(tagQuery, ignoreCase = true)) {
            return false
        }

        if (textQuery.isNotBlank()) {
            val matchesText = entry.message.contains(textQuery, ignoreCase = true) ||
                entry.tag.contains(textQuery, ignoreCase = true) ||
                entry.stackFrames.any { it.rawLine.contains(textQuery, ignoreCase = true) }
            if (!matchesText) return false
        }

        return true
    }
}
