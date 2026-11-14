/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

/** Versionen des Manifest-/SDK-Vertrags. v1 = nur id/name/version/entryPointClass, v2 = + contributes/permissions. */
object PluginApiVersion {
    /** Aktuelle Version des Plugin-API-Vertrags. */
    const val CURRENT: Int = 2
}

/** Berechtigungen, die ein Code-Plugin im Manifest anfordern muss (rein deklarative Plugins brauchen keine). */
object PluginPermissions {
    /** Befehle registrieren. */
    const val COMMANDS = "commands"
    /** Menüeinträge beisteuern. */
    const val MENUS = "menus"
    /** Dateiereignisse empfangen. */
    const val FILE_EVENTS = "file-events"
    /** Git-Ereignisse empfangen. */
    const val GIT_EVENTS = "git-events"
    /** Shell-Befehle ausführen. */
    const val SHELL = "shell"
    /** Auf Language-Server zugreifen. */
    const val LSP = "lsp"
    /** Werkzeuge und MCP-Server für den KI-Agenten beisteuern (zusätzlich zu [SHELL], da sie Programme starten). */
    const val AGENT = "agent"

    /** Menge aller bekannten Berechtigungen. */
    val ALL: Set<String> = setOf(COMMANDS, MENUS, FILE_EVENTS, GIT_EVENTS, SHELL, LSP, AGENT)
}

/**
 * Manifest eines Plugins (`plugin.json`): Identität, Version, API-Version, optionaler Einstiegspunkt, Berechtigungen und Contributions.
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val publisher: String = "",
    val apiVersion: Int = 1,
    val entryPointClass: String? = null,
    val permissions: Set<String> = emptySet(),
    /** Mindestversion der App (`versionName`); leer = keine Einschränkung. */
    val minHostVersion: String = "",
    /** IDs von Plugins, die installiert und aktiviert sein müssen. */
    val dependsOn: List<String> = emptyList(),
    val contributes: Contributes = Contributes()
) {
    /** true, wenn das Plugin keinen ausführbaren Code mitbringt (im Store erlaubt). */
    val isDeclarativeOnly: Boolean get() = entryPointClass == null
}

/** Beiträge eines Plugins zur IDE (Themes, Sprachpakete, Grammatiken, Snippets, LSP-Server, Formatter, Werkzeuge, Voraussetzungen). */
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
    val requires: List<RequirementContribution> = emptyList(),
    /** Werkzeuge, die der KI-Agent aufrufen darf (Berechtigungen `agent` + `shell`, nur Sideload). */
    val agentTools: List<AgentToolContribution> = emptyList(),
    /** MCP-Server, die der Agent nach Freigabe durch den Nutzer verbindet (Berechtigungen `agent` + `shell`). */
    val mcpServers: List<McpServerContribution> = emptyList()
) {
    /** Ob das Plugin keinerlei Contributions enthält. */
    val isEmpty: Boolean
        get() = colorThemes.isEmpty() && iconThemes.isEmpty() && languagePacks.isEmpty() &&
            grammars.isEmpty() && snippets.isEmpty() && lspServers.isEmpty() && extensionPack.isEmpty() &&
            formatters.isEmpty() && tools.isEmpty() && requires.isEmpty() &&
            agentTools.isEmpty() && mcpServers.isEmpty()
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

/** Beitrag einer TextMate-Grammatik für eine Sprache. */
data class GrammarContribution(
    val languageId: String,
    val scopeName: String,
    val path: String,
    val extensions: List<String> = emptyList()
)

/** Beitrag einer Snippet-Datei. */
data class SnippetContribution(
    val languageId: String,
    val path: String
)

/**
 * Deklarativer Language Server. [command] und die Werte von [env] dürfen `${pluginDir}`, `${home}` und `${deps}`
 * (Ordner der Downloads aus `requires`, Manager `download`) enthalten. JVM-Server markieren die Stelle für die
 * JVM-Optionen des Nutzers: Ein Kommando-Element mit `${jvmArg}` wird je Option wiederholt (z. B. `--jvm-arg=${jvmArg}`),
 * `${jvmArgs}` in einem [env]-Wert steht für alle Optionen, durch Leerzeichen getrennt.
 * [installScript] ist reserviert und wird **noch nicht ausgeführt**; benötigte Programme kommen über `requires`.
 */
data class LspServerContribution(
    val id: String,
    val label: String,
    val languages: List<String>,
    val command: List<String>,
    val installScript: String? = null,
    val env: Map<String, String> = emptyMap()
) {
    /** Ob der Server JVM-Optionen annimmt (Platzhalter `${jvmArg}`/`${jvmArgs}` in Kommando oder Umgebung). */
    val acceptsJvmOptions: Boolean
        get() = (command + env.values).any { it.contains(JVM_ARG) || it.contains(JVM_ARGS) }

    /** Platzhalter für Kommandos und Umgebung. */
    companion object {
        const val PLUGIN_DIR = "${'$'}{pluginDir}"
        const val HOME = "${'$'}{home}"
        const val DEPS = "${'$'}{deps}"
        const val JVM_ARG = "${'$'}{jvmArg}"
        const val JVM_ARGS = "${'$'}{jvmArgs}"
    }
}

/**
 * Setzt die Platzhalter eines [LspServerContribution] ein. Elemente mit `${jvmArg}` werden je JVM-Option wiederholt
 * (ohne Optionen entfallen sie); jedes Element bleibt ein einzelnes Argument (keine Shell).
 */
fun LspServerContribution.expand(
    pluginDir: String,
    home: String,
    deps: String,
    jvmArgs: List<String>
): Pair<List<String>, Map<String, String>> {
    fun paths(v: String) = v.replace(LspServerContribution.PLUGIN_DIR, pluginDir)
        .replace(LspServerContribution.HOME, home)
        .replace(LspServerContribution.DEPS, deps)
    val jvmArg = LspServerContribution.JVM_ARG
    val cmd = command.flatMap { arg ->
        if (arg.contains(jvmArg)) jvmArgs.map { paths(arg).replace(jvmArg, it) } else listOf(paths(arg))
    }
    val environment = env.mapValues { (_, v) -> paths(v).replace(LspServerContribution.JVM_ARGS, jvmArgs.joinToString(" ")).trim() }
    return cmd to environment
}

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
/** Auswahlwert eines Werkzeug-Dialogs (Dropdown); [value] wird als erstes Wort der Eingabe übergeben. */
data class ToolOption(val value: String, val label: String)

/**
 * Eingabe eines Werkzeugs. Ohne [options] ein Textfeld. Mit [options] ein Dropdown; ist zusätzlich [textLabel]
 * gesetzt, folgt ein Textfeld. Das Werkzeug erhält `"<auswahl> <text>"` (bzw. nur `"<auswahl>"`).
 */
data class ToolPrompt(
    val label: String,
    val hint: String = "",
    val default: String = "",
    val options: List<ToolOption> = emptyList(),
    val textLabel: String = "",
    val textHint: String = ""
) {
    /** Ob das Werkzeug eine Auswahlliste anbietet. */
    val hasOptions: Boolean get() = options.isNotEmpty()
    /** Ob zusätzlich ein Textfeld angezeigt wird. */
    val hasText: Boolean get() = !hasOptions || textLabel.isNotBlank()

    /** Gewählter Wert; unbekannte oder leere Vorgaben fallen auf die erste Option zurück. */
    fun optionOf(input: String): String {
        val first = input.trim().substringBefore(' ')
        return options.firstOrNull { it.value == first }?.value ?: options.firstOrNull()?.value.orEmpty()
    }

    /** Text hinter dem Auswahlwert (bzw. die ganze Eingabe ohne Optionen). */
    fun textOf(input: String): String {
        if (!hasOptions) return input
        if (!hasText) return ""
        val trimmed = input.trimStart()
        val first = trimmed.substringBefore(' ')
        return if (options.any { it.value == first }) trimmed.substringAfter(' ', "").trim() else trimmed.trim()
    }

    /** Setzt Auswahl und Text zum Aufrufwert `"<auswahl> <text>"` zusammen. */
    fun compose(option: String, text: String): String = if (text.isBlank()) option else "$option ${text.trim()}"
}

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

/** Wirkungsart eines Agent-Werkzeugs aus einem Plugin; EXECUTE verlangt die Freigabe im Agenten. */
enum class AgentToolKind { READ, EXECUTE }

/**
 * Werkzeug für den KI-Agenten. Das Modell liefert genau einen Text-Parameter `input`, der `${input}` im [command] ersetzt
 * (jedes Listenelement bleibt ein Argument, keine Shell). Weitere Platzhalter: `${projectDir}`, `${pluginDir}`, `${home}`.
 * [inputDescription] erklärt dem Modell, was in `input` stehen soll.
 */
data class AgentToolContribution(
    val id: String,
    val description: String,
    val command: List<String>,
    val kind: AgentToolKind = AgentToolKind.EXECUTE,
    val inputDescription: String = "",
    val timeoutSeconds: Int = 60
) {
    /** Dasselbe Werkzeug als [ToolContribution], damit der gemeinsame Runner es starten kann. */
    fun asTool(): ToolContribution = ToolContribution(
        id = id, label = id, description = description, command = command, timeoutSeconds = timeoutSeconds
    )
}

/** MCP-Server eines Plugins: entweder [url] (HTTP) oder [command] + [args] (stdio). Läuft erst nach Freigabe. */
data class McpServerContribution(
    val name: String,
    val url: String? = null,
    val command: String? = null,
    val args: List<String> = emptyList()
)

/**
 * Paketmanager, über den ein Plugin Voraussetzungen anfordern kann. [DOWNLOAD] lädt eine Datei mit SHA-256-Prüfung
 * in den gemeinsamen Download-Ordner (`${deps}/<name>`) und entpackt sie dort.
 */
enum class RequirementManager(val id: String) {
    PKG("pkg"), NPM("npm"), PIP("pip"), DOWNLOAD("download");

    companion object {
        /** Liefert den Manager zur [id] oder `null`. */
        fun of(id: String): RequirementManager? = entries.firstOrNull { it.id == id }
    }
}

/** Format einer Download-Voraussetzung: [FILE] wird unverändert gespeichert (z. B. ein JAR). */
enum class DownloadArchive(val id: String) {
    ZIP("zip"), TAR_GZ("tar.gz"), FILE("file");

    companion object {
        /** Liefert das Format zur [id] oder `null`. */
        fun fromId(id: String): DownloadArchive? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Benötigtes Programm: [name] ist der Paketname beim [manager]; [check] der Programmname, dessen Vorhandensein geprüft wird
 * (Standard: [name]). Installation erst nach Bestätigung durch den Nutzer.
 *
 * Bei [RequirementManager.DOWNLOAD] ist [name] der Zielordner unter `${deps}`, [check] der relative Pfad der Datei,
 * die danach existieren muss (wird ausführbar gemacht), und [url]/[sha256]/[archive] sind Pflicht. Ändert sich [sha256]
 * (neue Version im Plugin-Update), gilt die Voraussetzung wieder als fehlend.
 */
data class RequirementContribution(
    val manager: RequirementManager,
    val name: String,
    val check: String = name,
    val url: String? = null,
    val sha256: String? = null,
    val archive: DownloadArchive? = null
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
    if (agentTools.isNotEmpty()) add("agentTools")
    if (mcpServers.isNotEmpty()) add("mcpServers")
}
