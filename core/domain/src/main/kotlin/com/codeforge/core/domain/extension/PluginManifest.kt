/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

/** Versionen des Manifest-/SDK-Vertrags. v1 = nur id/name/version/entryPointClass, v2 = + contributes/permissions. */
object PluginApiVersion {
    const val CURRENT: Int = 2
}

/** Berechtigungen, die ein Code-Plugin im Manifest anfordern muss (rein deklarative Plugins brauchen keine). */
object PluginPermissions {
    const val COMMANDS = "commands"
    const val MENUS = "menus"
    const val FILE_EVENTS = "file-events"
    const val GIT_EVENTS = "git-events"
    const val SHELL = "shell"
    const val LSP = "lsp"

    val ALL: Set<String> = setOf(COMMANDS, MENUS, FILE_EVENTS, GIT_EVENTS, SHELL, LSP)
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val publisher: String = "",
    val apiVersion: Int = 1,
    val entryPointClass: String? = null,
    val permissions: Set<String> = emptySet(),
    val contributes: Contributes = Contributes()
) {
    /** true, wenn das Plugin keinen ausführbaren Code mitbringt (im Store erlaubt). */
    val isDeclarativeOnly: Boolean get() = entryPointClass == null
}

data class Contributes(
    val colorThemes: List<ColorThemeContribution> = emptyList(),
    val iconThemes: List<IconThemeContribution> = emptyList(),
    val languagePacks: List<LanguagePackContribution> = emptyList(),
    val grammars: List<GrammarContribution> = emptyList(),
    val snippets: List<SnippetContribution> = emptyList(),
    val lspServers: List<LspServerContribution> = emptyList(),
    /** IDs weiterer Plugins, die zusammen mit diesem Bundle installiert werden sollen. */
    val extensionPack: List<String> = emptyList(),
    val formatters: List<FormatterContribution> = emptyList(),
    /** Aufrufbare Werkzeuge im „PT"-Menü des Dateibaums (führen Programme aus → nur Sideload, Berechtigung `shell`). */
    val tools: List<ToolContribution> = emptyList(),
    /** Programme, die dieses Plugin benötigt und die die App nach Bestätigung installieren kann. */
    val requires: List<RequirementContribution> = emptyList()
) {
    val isEmpty: Boolean
        get() = colorThemes.isEmpty() && iconThemes.isEmpty() && languagePacks.isEmpty() &&
            grammars.isEmpty() && snippets.isEmpty() && lspServers.isEmpty() && extensionPack.isEmpty() &&
            formatters.isEmpty() && tools.isEmpty() && requires.isEmpty()
}

/** Farbschema: [path] zeigt auf eine JSON-Datei (siehe docs/extensions.md) relativ zum Plugin-Verzeichnis. */
data class ColorThemeContribution(
    val id: String,
    val label: String,
    val isDark: Boolean,
    val path: String
)

/** Icon-Theme: [path] zeigt auf eine JSON-Datei, die Dateiendungen/Dateinamen/Ordner auf Icon-Dateien abbildet. */
data class IconThemeContribution(
    val id: String,
    val label: String,
    val path: String
)

/** Sprachpaket: [path] zeigt auf eine JSON-Datei mit Schlüssel → übersetzter Text. [locale] als BCP-47-Tag (z. B. "fr", "pt-BR"). */
data class LanguagePackContribution(
    val locale: String,
    val label: String,
    val path: String
)

data class GrammarContribution(
    val languageId: String,
    val scopeName: String,
    val path: String,
    val extensions: List<String> = emptyList()
)

data class SnippetContribution(
    val languageId: String,
    val path: String
)

/**
 * Deklarativer Language Server. [command] darf die Platzhalter `${pluginDir}` und `${home}` enthalten.
 * [installScript] ist reserviert und wird **noch nicht ausgeführt**; benötigte Programme müssen vorab installiert sein.
 */
data class LspServerContribution(
    val id: String,
    val label: String,
    val languages: List<String>,
    val command: List<String>,
    val installScript: String? = null
)

/**
 * Externer Code-Formatierer: Der Editor startet [command], schreibt den Dateiinhalt auf stdin und ersetzt ihn durch stdout.
 * Zuordnung über [extensions] (ohne Punkt, case-insensitiv) oder exakte [fileNames]. Platzhalter im Kommando:
 * `${file}` (absoluter Pfad der Datei), `${fileName}`, `${pluginDir}`, `${home}`. Das Programm muss bereits installiert sein.
 */
data class FormatterContribution(
    val id: String,
    val label: String,
    val extensions: List<String>,
    val fileNames: List<String> = emptyList(),
    val command: List<String>,
    val timeoutSeconds: Int = 15
)

/** Eingabefeld, das ein Werkzeug vor dem Start anzeigt; der Wert steht im Kommando als `${input}`. */
data class ToolPrompt(val label: String, val hint: String = "", val default: String = "")

/**
 * Werkzeug im „PT"-Menü. [command] wird ohne Shell gestartet (jedes Element ist ein Argument), Arbeitsverzeichnis ist das Projekt.
 * Platzhalter: `${projectDir}`, `${pluginDir}`, `${home}`, `${input}`. [confirm] zeigt vor dem Start einen Bestätigungstext.
 */
data class ToolContribution(
    val id: String,
    val label: String,
    val description: String = "",
    val command: List<String>,
    val prompt: ToolPrompt? = null,
    val confirm: String? = null,
    val timeoutSeconds: Int = 120
)

enum class RequirementManager(val id: String) {
    PKG("pkg"), NPM("npm"), PIP("pip");

    companion object {
        fun of(id: String): RequirementManager? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Benötigtes Programm: [name] ist der Paketname beim [manager]; [check] der Programmname, dessen Vorhandensein geprüft wird
 * (Standard: [name]). Installation erst nach Bestätigung durch den Nutzer.
 */
data class RequirementContribution(
    val manager: RequirementManager,
    val name: String,
    val check: String = name
)

/** Namen der nicht leeren Contribution-Arten, wie sie in `contributes` heißen. */
fun Contributes.kinds(): List<String> = buildList {
    if (colorThemes.isNotEmpty()) add("colorThemes")
    if (iconThemes.isNotEmpty()) add("iconThemes")
    if (languagePacks.isNotEmpty()) add("languagePacks")
    if (grammars.isNotEmpty()) add("grammars")
    if (snippets.isNotEmpty()) add("snippets")
    if (lspServers.isNotEmpty()) add("lspServers")
    if (extensionPack.isNotEmpty()) add("extensionPack")
    if (formatters.isNotEmpty()) add("formatters")
    if (tools.isNotEmpty()) add("tools")
    if (requires.isNotEmpty()) add("requires")
}
