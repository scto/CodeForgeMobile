/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * [AgentWorkspace] auf dem Dateisystem: alle Pfade bleiben unterhalb des Projektstamms.
 */
package com.codeforge.libs.agent

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dateisystem-Arbeitsbereich für den Agenten.
 *
 * @param root Projektstamm.
 * @param ignoredDirs Ordnernamen, die Suche und Listing überspringen (Build-Ausgaben, `.git` …).
 * @param maxFileBytes Größere Dateien werden weder gelesen noch durchsucht.
 * @param diagnosticsProvider Liefert Diagnosen (LSP/Build); Pfade sind relativ zum Projektstamm.
 */
class FileAgentWorkspace(
    private val root: File,
    private val ignoredDirs: Set<String> = DEFAULT_IGNORED,
    private val maxFileBytes: Long = 1_000_000,
    private val diagnosticsProvider: suspend (path: String?) -> List<AgentDiagnostic> = { emptyList() },
) : AgentWorkspace {

    private val rootCanonical: File = root.canonicalFile

    private fun resolve(path: String): File {
        val rel = normalizeProjectPath(path)
        val file = File(rootCanonical, rel).canonicalFile
        // Symlinks dürfen nicht aus dem Projekt herausführen.
        if (file != rootCanonical && !file.path.startsWith(rootCanonical.path + File.separator)) {
            throw ToolArgumentException("Pfad verlässt das Projekt: $path")
        }
        return file
    }

    private fun relative(file: File): String = file.relativeTo(rootCanonical).path.replace(File.separatorChar, '/')

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val f = resolve(path)
        when {
            !f.isFile -> null
            f.length() > maxFileBytes -> throw ToolArgumentException("Datei zu groß (${f.length()} Bytes, Limit $maxFileBytes): $path")
            else -> f.readText()
        }
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val f = resolve(path)
        if (f == rootCanonical || f.isDirectory) throw ToolArgumentException("Pfad ist ein Ordner: $path")
        f.parentFile?.mkdirs()
        // Atomar schreiben: erst Temp-Datei, dann umbenennen – ein Abbruch hinterlässt keine halbe Datei.
        val tmp = File(f.parentFile, ".${f.name}.cf-agent.tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(f)) {
                f.writeText(content)
            }
        } finally {
            tmp.delete()
        }
    }

    override suspend fun deletePath(path: String): Boolean = withContext(Dispatchers.IO) {
        val f = resolve(path)
        if (f == rootCanonical) throw ToolArgumentException("Der Projektstamm kann nicht gelöscht werden")
        if (!f.exists()) false else f.deleteRecursively()
    }

    override suspend fun listDir(path: String): List<DirEntry>? = withContext(Dispatchers.IO) {
        val f = resolve(path)
        if (!f.isDirectory) return@withContext null
        f.listFiles().orEmpty()
            .filterNot { it.isDirectory && it.name in ignoredDirs }
            .map { DirEntry(it.name, it.isDirectory) }
            .sortedWith(compareByDescending<DirEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun searchText(query: String, path: String, maxResults: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val start = resolve(path)
            if (query.isEmpty() || !start.exists()) return@withContext emptyList()
            val hits = ArrayList<SearchHit>()
            val files = if (start.isFile) sequenceOf(start) else walk(start)
            for (file in files) {
                if (hits.size >= maxResults) break
                if (file.length() > maxFileBytes || looksBinary(file)) continue
                val rel = relative(file)
                file.useLines { lines ->
                    for ((i, line) in lines.withIndex()) {
                        if (line.contains(query, ignoreCase = true)) {
                            hits += SearchHit(rel, i + 1, line.trim().take(MAX_LINE))
                            if (hits.size >= maxResults) break
                        }
                    }
                }
            }
            hits
        }

    override suspend fun diagnostics(path: String?): List<AgentDiagnostic> =
        diagnosticsProvider(path?.let { normalizeProjectPath(it).ifEmpty { null } })

    private fun walk(dir: File): Sequence<File> = sequence {
        val children = dir.listFiles().orEmpty().sortedBy { it.name }
        for (c in children) {
            if (c.isDirectory) {
                if (c.name !in ignoredDirs) yieldAll(walk(c))
            } else {
                yield(c)
            }
        }
    }

    private fun looksBinary(file: File): Boolean = runCatching {
        file.inputStream().use { s ->
            val buf = ByteArray(512)
            val n = s.read(buf)
            (0 until maxOf(n, 0)).any { buf[it] == 0.toByte() }
        }
    }.getOrDefault(true)

    /** Standardwerte. */
    companion object {
        /** Ordner, die der Agent nicht durchsucht oder auflistet. */
        val DEFAULT_IGNORED: Set<String> = setOf(
            ".git", ".cfm", ".gradle", ".idea", "build", "node_modules", ".cxx", ".externalNativeBuild",
        )
        private const val MAX_LINE = 240
    }
}
