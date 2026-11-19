/**
 * Plugin: SQLite-Betrachter
 */
package com.codeforge.plugin.sqlite

import java.io.File

/** Nur lesende Abfragen; die Datenbank wird zusätzlich schreibgeschützt geöffnet. */
object SqlGuard {
    private val ALLOWED = setOf("select", "pragma", "explain", "with", "values")
    private val COMMENT = Regex("--[^\\n]*|/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

    /** Fehlermeldung oder `null`, wenn [sql] erlaubt ist. */
    fun violation(sql: String): String? {
        val clean = sql.replace(COMMENT, " ").trim().trimEnd(';').trim()
        if (clean.isEmpty()) return "Leere Abfrage"
        if (';' in clean) return "Nur eine Anweisung"
        val first = clean.substringBefore(' ').substringBefore('(').lowercase()
        return if (first in ALLOWED) null else "Nur lesende Abfragen (SELECT, PRAGMA, EXPLAIN, WITH)"
    }

    /** Ergänzt ein `LIMIT`, wenn ein SELECT keins hat. */
    fun withLimit(sql: String, limit: Int): String {
        val clean = sql.trim().trimEnd(';')
        val lower = clean.lowercase()
        return if ((lower.startsWith("select") || lower.startsWith("with")) && !Regex("\\blimit\\b").containsMatchIn(lower)) "$clean LIMIT $limit" else clean
    }
}

/** SQLite-Dateien im Projekt (ohne Build-, Git- und Abhängigkeitsordner). */
object DatabaseFinder {
    private val SKIP = setOf("build", ".git", ".gradle", "node_modules", ".idea", ".cxx", "intermediates")
    private val EXT = setOf("db", "sqlite", "sqlite3", "db3")

    fun find(root: File, maxDepth: Int = 6, max: Int = 100): List<File> = root.walkTopDown()
        .maxDepth(maxDepth)
        .onEnter { it == root || it.name !in SKIP }
        .filter { it.isFile && it.extension.lowercase() in EXT && isSqlite(it) }
        .take(max)
        .toList()

    /** Prüft die Kennung „SQLite format 3“ am Dateianfang. */
    fun isSqlite(file: File): Boolean = runCatching {
        file.inputStream().use { s -> val b = ByteArray(16); s.read(b) == 16 && String(b, 0, 15, Charsets.US_ASCII) == "SQLite format 3" }
    }.getOrDefault(false)
}
