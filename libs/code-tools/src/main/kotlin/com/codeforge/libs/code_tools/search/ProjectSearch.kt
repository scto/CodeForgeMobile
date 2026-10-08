package com.codeforge.libs.code_tools.search

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class ProjectSearchOptions(
    val search: SearchOptions,
    /** Glob-Muster, z. B. Endung-Muster oder Ordner-Muster mit Doppelstern; leer = alle Dateien. */
    val includeGlobs: List<String> = emptyList(),
    /** Zusätzlich zu [DEFAULT_EXCLUDED_DIRS]. */
    val excludeGlobs: List<String> = emptyList(),
    val maxFileBytes: Long = 2L * 1024 * 1024,
    val maxMatches: Int = 5_000,
)

data class FileMatches(val path: String, val relativePath: String, val matches: List<TextMatch>)

data class ProjectSearchResult(
    val files: List<FileMatches>,
    val totalMatches: Int,
    val filesScanned: Int,
    /** true, wenn [ProjectSearchOptions.maxMatches] erreicht wurde. */
    val truncated: Boolean,
)

data class ProjectReplaceResult(val changedFiles: List<String>, val replacements: Int, val failedFiles: List<String>)

/**
 * Blockierende Projektsuche/-ersetzung (Aufrufer: IO-Dispatcher). Überspringt Build-/VCS-Ordner,
 * Binärdateien (NUL-Byte), zu große Dateien und Dateien, die nicht streng als UTF-8 dekodierbar sind —
 * Letzteres stellt sicher, dass „Alle ersetzen“ nie Dateien beschädigt.
 */
class ProjectSearcher(private val isCancelled: () -> Boolean = { false }) {

    fun search(root: File, options: ProjectSearchOptions): Result<ProjectSearchResult> = runCatching {
        options.search.compile().getOrThrow()
        val files = ArrayList<FileMatches>()
        var total = 0
        var scanned = 0
        var truncated = false
        for (file in walk(root, options)) {
            if (isCancelled()) break
            val text = readText(file, options.maxFileBytes) ?: continue
            scanned++
            val remaining = options.maxMatches - total
            val outcome = TextSearch.findAll(text, options.search, remaining)
            val error = outcome.exceptionOrNull()
            if (error != null) {
                if (error is SearchTimeoutException) continue else throw error
            }
            val matches = outcome.getOrThrow()
            if (matches.isNotEmpty()) {
                files += FileMatches(file.path, relative(root, file), matches)
                total += matches.size
                if (total >= options.maxMatches) { truncated = true; break }
            }
        }
        ProjectSearchResult(files, total, scanned, truncated)
    }

    /** Ersetzt in allen passenden Dateien (oder nur in [onlyFiles], absolute Pfade). */
    fun replaceAll(
        root: File,
        options: ProjectSearchOptions,
        replacement: String,
        onlyFiles: Set<String>? = null,
    ): Result<ProjectReplaceResult> = runCatching {
        options.search.compile().getOrThrow()
        // Ersetzungssyntax vorab prüfen, damit kein Teilergebnis entsteht.
        TextSearch.validateReplacement(options.search, replacement).getOrThrow()
        val changed = ArrayList<String>()
        val failed = ArrayList<String>()
        var count = 0
        for (file in walk(root, options)) {
            if (isCancelled()) break
            if (onlyFiles != null && file.path !in onlyFiles) continue
            val text = readText(file, options.maxFileBytes) ?: continue
            val result = TextSearch.replaceAll(text, options.search, replacement).getOrNull()
            if (result == null) { failed += file.path; continue }
            if (result.count == 0) continue
            runCatching { writeAtomic(file, result.text) }
                .onSuccess { changed += file.path; count += result.count }
                .onFailure { failed += file.path }
        }
        ProjectReplaceResult(changed, count, failed)
    }

    /** Ersetzt genau einen Treffer (Beginn [matchStart]) in [file]. */
    fun replaceOne(file: File, options: SearchOptions, replacement: String, matchStart: Int): Result<Boolean> = runCatching {
        val text = readText(file, Long.MAX_VALUE) ?: error("Datei nicht als UTF-8 lesbar: ${file.name}")
        val result = TextSearch.replaceAt(text, options, replacement, matchStart).getOrThrow()
        if (result.count == 0) false else { writeAtomic(file, result.text); true }
    }

    // --- Dateisystem ---

    private fun walk(root: File, options: ProjectSearchOptions): Sequence<File> {
        val include = options.includeGlobs.map(::globToRegex)
        val exclude = options.excludeGlobs.map(::globToRegex)
        return root.walkTopDown()
            .onEnter { dir -> dir == root || (dir.name !in DEFAULT_EXCLUDED_DIRS && !matchesAny(exclude, relative(root, dir), dir.name)) }
            .filter { it.isFile }
            .filter { f ->
                val rel = relative(root, f)
                (include.isEmpty() || matchesAny(include, rel, f.name)) && !matchesAny(exclude, rel, f.name)
            }
            .sortedBy { it.path }
    }

    private fun relative(root: File, f: File) = f.relativeTo(root).path.replace(File.separatorChar, '/')

    private fun matchesAny(globs: List<Regex>, rel: String, name: String) =
        globs.any { it.matches(rel) || it.matches(name) }

    private fun readText(file: File, maxBytes: Long): String? {
        if (file.length() > maxBytes) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val probe = minOf(bytes.size, 8_000)
        for (i in 0 until probe) if (bytes[i] == 0.toByte()) return null
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, ".${file.name}.cf-tmp")
        tmp.writeBytes(text.toByteArray(StandardCharsets.UTF_8))
        if (!tmp.renameTo(file)) {
            file.writeBytes(text.toByteArray(StandardCharsets.UTF_8))
            tmp.delete()
        }
    }

    companion object {
        val DEFAULT_EXCLUDED_DIRS = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".cxx", ".kotlin")

        /** `*` = beliebig ohne `/`, `**` = beliebig inkl. `/`, `?` = ein Zeichen. */
        fun globToRegex(glob: String): Regex {
            val sb = StringBuilder()
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                        i++
                        if (i + 1 < glob.length && glob[i + 1] == '/') { i++; sb.append("(?:.*/)?") } else sb.append(".*")
                    }
                    c == '*' -> sb.append("[^/]*")
                    c == '?' -> sb.append("[^/]")
                    c in ".+()[]{}^$|\\" -> sb.append('\\').append(c)
                    else -> sb.append(c)
                }
                i++
            }
            return Regex(sb.toString())
        }
    }
}
