/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Schmale Schnittstelle, über die der Agent das Projekt berührt. Pfade sind relativ zum Projektstamm; die
 * Implementierung (in der App über Dateibaum, LSP und Gradle-Anbindung) verantwortet, dass nichts außerhalb des
 * Projekts erreichbar ist.
 */
package com.codeforge.libs.agent

/** Eintrag eines Verzeichnisses. */
data class DirEntry(val name: String, val isDirectory: Boolean)

/** Treffer der Textsuche (1-basierte [line]). */
data class SearchHit(val path: String, val line: Int, val text: String)

/** Schweregrad einer Diagnose. */
enum class DiagnosticSeverity { ERROR, WARNING, INFO }

/** Diagnose (Fehler/Warnung) des Language Servers oder Builds (1-basierte [line]). */
data class AgentDiagnostic(val path: String, val line: Int, val severity: DiagnosticSeverity, val message: String)

/** Projektzugriff des Agenten. */
interface AgentWorkspace {
    /** Inhalt der Datei oder `null`, wenn sie nicht existiert. */
    suspend fun readFile(path: String): String?

    /** Schreibt die Datei (legt fehlende Ordner an). */
    suspend fun writeFile(path: String, content: String)

    /** Löscht Datei oder Ordner; `false`, wenn der Pfad nicht existiert. */
    suspend fun deletePath(path: String): Boolean

    /** Einträge des Ordners oder `null`, wenn er nicht existiert. */
    suspend fun listDir(path: String): List<DirEntry>?

    /** Sucht [query] (wörtlich, ohne Beachtung der Groß-/Kleinschreibung) in Textdateien unter [path]. */
    suspend fun searchText(query: String, path: String, maxResults: Int): List<SearchHit>

    /** Diagnosen für [path] oder, bei `null`, für das ganze Projekt. */
    suspend fun diagnostics(path: String?): List<AgentDiagnostic>
}

/**
 * Normalisiert einen vom Modell angegebenen Pfad: relativ, mit `/`, ohne `.`- und `..`-Segmente.
 * Wirft [ToolArgumentException] bei absoluten Pfaden und Versuchen, den Projektstamm zu verlassen.
 */
fun normalizeProjectPath(raw: String): String {
    val p = raw.trim().replace('\\', '/')
    if (p.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(p)) {
        throw ToolArgumentException("Pfad muss relativ zum Projektstamm sein: $raw")
    }
    val parts = p.split('/').filter { it.isNotEmpty() && it != "." }
    if (parts.any { it == ".." }) throw ToolArgumentException("Pfad verlässt das Projekt: $raw")
    return parts.joinToString("/")
}
