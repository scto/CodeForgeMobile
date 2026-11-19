/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Aktivierungsereignisse (wie `activationEvents` in VS Code): Ein Code-Plugin wird erst geladen, wenn eines seiner
 * Ereignisse eintritt. Ohne Angabe (oder mit `*`/`onStartup`) lädt es beim Start wie bisher.
 *
 * * `onStartup` / `*` – beim Start der App
 * * `onLanguage:<id>` – beim Öffnen einer Datei der Sprache (z. B. `onLanguage:markdown`)
 * * `onFileOpen:<muster>` – beim Öffnen einer passenden Datei (`*.csv`, `*.{db,sqlite}`, `Makefile`)
 * * `onProject:<datei>` – beim Öffnen eines Projekts, das die Datei im Wurzelordner enthält (`package.json`)
 */
package com.codeforge.core.domain.extension

import java.io.File

object ActivationEvents {
    const val STARTUP = "onStartup"
    const val ANY = "*"
    private const val LANGUAGE = "onLanguage:"
    private const val FILE_OPEN = "onFileOpen:"
    private const val PROJECT = "onProject:"
    private val ARG = Regex("[A-Za-z0-9._*?{},\\-]{1,80}")

    /** Ob [event] gültig geschrieben ist. */
    fun isValid(event: String): Boolean = when {
        event == STARTUP || event == ANY -> true
        event.startsWith(LANGUAGE) -> ARG.matches(event.removePrefix(LANGUAGE))
        event.startsWith(FILE_OPEN) -> ARG.matches(event.removePrefix(FILE_OPEN))
        event.startsWith(PROJECT) -> ARG.matches(event.removePrefix(PROJECT)) && '/' !in event
        else -> false
    }

    /** Lädt sofort beim Start (keine Ereignisse, `*` oder `onStartup`). */
    fun isEager(events: List<String>): Boolean = events.isEmpty() || events.any { it == STARTUP || it == ANY }

    /**
     * Ob [event] eines der [events] auslöst. [languageOf] liefert die Sprach-ID einer Datei (z. B. `kotlin`).
     */
    fun triggers(events: List<String>, event: HostEvent, languageOf: (String) -> String?): Boolean {
        if (isEager(events)) return true
        val opened = when (event) {
            is HostEvent.FileOpened -> event.path
            is HostEvent.EditorActivated -> event.path
            else -> null
        }
        return events.any { e ->
            when {
                opened != null && e.startsWith(LANGUAGE) -> languageOf(opened)?.equals(e.removePrefix(LANGUAGE), ignoreCase = true) == true
                opened != null && e.startsWith(FILE_OPEN) -> globMatches(e.removePrefix(FILE_OPEN), File(opened).name)
                event is HostEvent.ProjectOpened && e.startsWith(PROJECT) -> File(event.rootPath, e.removePrefix(PROJECT)).exists()
                else -> false
            }
        }
    }

    /** Sprach-ID einer Datei nach Endung (Bezeichner wie in VS Code). */
    fun languageOf(path: String): String? {
        val name = File(path).name.lowercase()
        if (name == "makefile" || name == "gnumakefile") return "makefile"
        if (name == "dockerfile") return "dockerfile"
        return LANGUAGES[name.substringAfterLast('.', "")]
    }

    private val LANGUAGES = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "java" to "java", "md" to "markdown", "markdown" to "markdown",
        "py" to "python", "js" to "javascript", "mjs" to "javascript", "ts" to "typescript", "tsx" to "typescriptreact",
        "jsx" to "javascriptreact", "json" to "json", "xml" to "xml", "yaml" to "yaml", "yml" to "yaml", "toml" to "toml",
        "sh" to "shellscript", "bash" to "shellscript", "c" to "c", "h" to "c", "cpp" to "cpp", "cc" to "cpp", "hpp" to "cpp",
        "rs" to "rust", "go" to "go", "dart" to "dart", "html" to "html", "htm" to "html", "css" to "css", "scss" to "scss",
        "csv" to "csv", "tsv" to "tsv", "sql" to "sql", "http" to "http", "rest" to "http", "svg" to "svg", "gradle" to "groovy",
        "swift" to "swift", "lua" to "lua", "rb" to "ruby", "php" to "php", "zig" to "zig",
    )

    /** Einfaches Glob für Dateinamen: `*`, `?` und `{a,b}`; Groß-/Kleinschreibung egal. */
    fun globMatches(glob: String, fileName: String): Boolean {
        val regex = buildString {
            var inBraces = false
            for (c in glob) when (c) {
                '*' -> append(".*")
                '?' -> append('.')
                '{' -> { inBraces = true; append("(?:") }
                '}' -> { inBraces = false; append(')') }
                ',' -> append(if (inBraces) "|" else ",")
                else -> append(Regex.escape(c.toString()))
            }
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(fileName)
    }
}
