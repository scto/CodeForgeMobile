/**
 * Plugin: Projekt-Skripte
 *
 * Findet Aufgaben im Projektwurzelordner. Reine Funktionen (ohne Android), daher direkt testbar.
 */
package com.codeforge.plugin.scripts

import java.io.File

/** Gefundene Aufgabe: [command] läuft per `bash -lc` im Projektordner. */
data class ScriptTask(val id: String, val title: String, val command: String, val isTest: Boolean = false)

object TaskDiscovery {
    private val MAKE_TARGET = Regex("^([A-Za-z0-9][A-Za-z0-9_.-]*)\\s*:(?![=:])")
    private val JUST_RECIPE = Regex("^@?([A-Za-z0-9_-]+)(\\s+[^:]*)?:(?!=)")
    private val NPM_SCRIPT = Regex("\"([^\"\\\\]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private const val MAX = 40

    fun discover(root: File): List<ScriptTask> = (npm(root) + make(root) + cargo(root) + go(root) + just(root)).take(MAX * 5)

    fun npm(root: File): List<ScriptTask> {
        val pkg = File(root, "package.json").takeIf { it.isFile } ?: return emptyList()
        val runner = when {
            File(root, "pnpm-lock.yaml").isFile -> "pnpm"
            File(root, "yarn.lock").isFile -> "yarn"
            File(root, "bun.lockb").isFile || File(root, "bun.lock").isFile -> "bun"
            else -> "npm"
        }
        val scripts = scriptsBlock(pkg.readText()) ?: return emptyList()
        return NPM_SCRIPT.findAll(scripts).map { it.groupValues[1] }.distinct().take(MAX).map { name ->
            ScriptTask("npm:$name", "$runner run $name", "$runner run ${shellQuote(name)}", isTest = name == "test" || name.startsWith("test:"))
        }.toList()
    }

    /** Inhalt des Objekts `"scripts": { … }` (ohne JSON-Bibliothek; Strings mit Klammern werden beachtet). */
    internal fun scriptsBlock(json: String): String? {
        val key = Regex("\"scripts\"\\s*:\\s*\\{").find(json) ?: return null
        var depth = 1
        var i = key.range.last + 1
        var inString = false
        while (i < json.length && depth > 0) {
            val c = json[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> depth--
            }
            i++
        }
        return json.substring(key.range.last + 1, (i - 1).coerceAtLeast(key.range.last + 1))
    }

    fun make(root: File): List<ScriptTask> {
        val file = listOf("Makefile", "makefile", "GNUmakefile").map { File(root, it) }.firstOrNull { it.isFile } ?: return emptyList()
        return file.readLines().asSequence()
            .mapNotNull { MAKE_TARGET.find(it)?.groupValues?.get(1) }
            .filter { !it.startsWith(".") && '%' !in it }
            .distinct().take(MAX)
            .map { ScriptTask("make:$it", "make $it", "make ${shellQuote(it)}", isTest = it == "test" || it == "check") }
            .toList()
    }

    fun cargo(root: File): List<ScriptTask> {
        if (!File(root, "Cargo.toml").isFile) return emptyList()
        return listOf(
            ScriptTask("cargo:build", "cargo build", "cargo build"),
            ScriptTask("cargo:run", "cargo run", "cargo run"),
            ScriptTask("cargo:test", "cargo test", "cargo test", isTest = true),
            ScriptTask("cargo:clippy", "cargo clippy", "cargo clippy"),
        )
    }

    fun go(root: File): List<ScriptTask> {
        if (!File(root, "go.mod").isFile) return emptyList()
        return listOf(
            ScriptTask("go:build", "go build ./...", "go build ./..."),
            ScriptTask("go:test", "go test ./...", "go test ./...", isTest = true),
            ScriptTask("go:vet", "go vet ./...", "go vet ./..."),
        )
    }

    fun just(root: File): List<ScriptTask> {
        val file = listOf("justfile", "Justfile", ".justfile").map { File(root, it) }.firstOrNull { it.isFile } ?: return emptyList()
        return file.readLines().asSequence()
            .filter { it.isNotEmpty() && !it[0].isWhitespace() && !it.startsWith("#") && !it.startsWith("set ") && !it.startsWith("alias ") }
            .mapNotNull { JUST_RECIPE.find(it)?.groupValues?.get(1) }
            .distinct().take(MAX)
            .map { ScriptTask("just:$it", "just $it", "just ${shellQuote(it)}", isTest = it == "test") }
            .toList()
    }

    fun shellQuote(s: String): String = if (Regex("[A-Za-z0-9_./:@=+-]+").matches(s)) s else "'" + s.replace("'", "'\\''") + "'"
}
