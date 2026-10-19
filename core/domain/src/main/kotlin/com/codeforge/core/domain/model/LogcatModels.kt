/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

enum class LogLevel(val letter: Char, val priority: Int) {
    VERBOSE('V', 2),
    DEBUG('D', 3),
    INFO('I', 4),
    WARN('W', 5),
    ERROR('E', 6),
    FATAL('F', 7);

    companion object {
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

data class StackFrame(
    val className: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int,
    val rawLine: String
)

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
    val fullFormattedText: String
        get() = "$timestamp $pid $tid ${level.letter} $tag: $message"
}

enum class LogcatSource {
    OWN_PROCESS,
    READ_LOGS_PERMISSION,
    ADB_LOOPBACK
}

data class LogcatSourceAvailability(
    val source: LogcatSource,
    val isAvailable: Boolean,
    val explanationResId: Int,
    val helpInstructionResId: Int? = null
)

data class LogcatFilter(
    val packageName: String = "",
    val targetPid: Int? = null,
    val minLevel: LogLevel = LogLevel.VERBOSE,
    val tagQuery: String = "",
    val textQuery: String = "",
    val crashesOnly: Boolean = false
) {
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
