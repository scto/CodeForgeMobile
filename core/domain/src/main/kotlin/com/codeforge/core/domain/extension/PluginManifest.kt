/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

/**
 * Versionen des Manifest-/SDK-Vertrags. v1 = nur id/name/version/entryPointClass, v2 = + contributes/permissions,
 * v3 = Editor/UI/Run, v4 = Projekt-/Build-Dienst, Plugin-Dienste, Vorschauen, Aktivierungsereignisse, Katalogangaben,
 * v5 = IDE-Zugriff (`PluginContext.ide()`: Befehle, Ereignisbus, App-Einstellungen, Editor, Dateien, Git, Navigation,
 * Statusleiste) und Compose-Oberflächen (`:libs:plugin-ui-api`: Werkzeugfenster, Bildschirme, Einstellungsseiten).
 */
object PluginApiVersion {
    /** Aktuelle Version des Plugin-API-Vertrags. */
    const val CURRENT: Int = 5

    /**
     * Kleinste API-Version für Code-Plugins. API 1 (`shell` und `lsp` ohne Berechtigung im Manifest) wurde in 3.3
     * abgekündigt und ist seit [API1_REMOVED_IN] entfernt: Installation wird abgelehnt, installierte werden nicht geladen.
     * Deklarative Plugins betrifft das nicht (für sie macht die Version keinen Unterschied).
     */
    const val MIN_CODE_API: Int = 2

    /** App-Version, seit der Code-Plugins mit API 1 nicht mehr geladen werden. */
    const val API1_REMOVED_IN: String = "3.4.0"

    /** Ob ein Code-Plugin eine nicht mehr unterstützte API-Version (1) hat. */
    fun isUnsupported(apiVersion: Int, entryPointClass: String?): Boolean =
        !entryPointClass.isNullOrBlank() && apiVersion < MIN_CODE_API
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
    /** Seit API 3: Inhalt der aktiven Datei lesen und ändern (Editor-Aktionen, Schnellkorrekturen). */
    const val EDITOR = "editor"
    /** Seit API 3: Build-Ereignisse (gestartet, beendet). */
    const val BUILD_EVENTS = "build-events"
    /** Seit API 3: Projekt-Ereignisse (geöffnet, geschlossen). */
    const val PROJECT_EVENTS = "project-events"
    /** Seit API 3: eigene Oberflächen (Compose) – läuft mit allen Rechten der App, nur von vertrauenswürdigen Quellen. */
    const val UI = "ui"
    /** Seit API 3: Ausführen- und Test-Einträge beisteuern. */
    const val RUN = "run"
    /** Seit API 3: Systembenachrichtigungen zeigen. */
    const val NOTIFICATIONS = "notifications"
    /** Seit API 4: Projektmodell lesen (Module, Build-Ergebnisse eines Moduls). */
    const val PROJECT = "project"
    /** Seit API 4: Gradle-Aufgaben ausführen. */
    const val BUILD = "build"
    /**
     * Seit API 4: Netzwerkzugriff. Nur eine Angabe für den Nutzer (Code-Plugins laufen im App-Prozess und können
     * technisch ohnehin ins Netz); der Store und die Plugin-Ansicht zeigen sie an.
     */
    const val NETWORK = "network"
    /** Seit API 5: Befehle der IDE und anderer Plugins auflisten und ausführen. */
    const val COMMANDS_EXECUTE = "commands.execute"
    /** Seit API 5: Ereignisbus abonnieren und eigene Ereignisse veröffentlichen. */
    const val EVENTS = "events"
    /** Seit API 5: App-Einstellungen lesen. */
    const val SETTINGS_READ = "settings.read"
    /** Seit API 5: App-Einstellungen ändern. */
    const val SETTINGS_WRITE = "settings.write"
    /** Seit API 5: Projektdateien lesen. */
    const val FILES_READ = "files.read"
    /** Seit API 5: Projektdateien anlegen, ändern, umbenennen, löschen. */
    const val FILES_WRITE = "files.write"
    /** Seit API 5: Git-Status, Diff und Branches lesen. */
    const val GIT_READ = "git.read"
    /** Seit API 5: stagen, committen, Branch wechseln. */
    const val GIT_WRITE = "git.write"

    /** Menge aller bekannten Berechtigungen. */
    val ALL: Set<String> = setOf(
        COMMANDS, MENUS, FILE_EVENTS, GIT_EVENTS, SHELL, LSP, AGENT, EDITOR, BUILD_EVENTS, PROJECT_EVENTS, UI, RUN,
        NOTIFICATIONS, PROJECT, BUILD, NETWORK, COMMANDS_EXECUTE, EVENTS, SETTINGS_READ, SETTINGS_WRITE, FILES_READ,
        FILES_WRITE, GIT_READ, GIT_WRITE
    )
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
    /** Seit API 4: wann der Code geladen wird (leer = beim Start); siehe [ActivationEvents]. */
    val activationEvents: List<String> = emptyList(),
    /** Seit API 4: Store-Kategorien als Pfade, z. B. `languages/jvm/kotlin` (siehe Store-Doku). */
    val categories: List<String> = emptyList(),
    /** Seit API 4: Schlagwörter für Suche und Filter im Store. */
    val tags: List<String> = emptyList(),
    /**
     * Seit API 4: Code läuft in der Sandbox (eigener, isolierter Prozess ohne Berechtigungen, Dateien und Netz).
     * Erlaubt nur reine Editor-Beiträge (Berechtigung `editor`); dafür auch aus dem Store installierbar.
     */
    val sandbox: Boolean = false,
    val contributes: Contributes = Contributes()
) {
    /** true, wenn das Plugin keinen ausführbaren Code mitbringt (im Store erlaubt). */
    val isDeclarativeOnly: Boolean get() = entryPointClass == null
}

/** Beiträge eines Plugins zur IDE (Themes, Sprachpakete, Grammatiken, Snippets, LSP-Server, Formatter, Werkzeuge, Voraussetzungen). */
data class Contributes(
    val colorThemes: List<ColorThemeContribution> = emptyList(),
    val iconThemes: List<IconThemeContribution> = emptyList(),
    /** Farbschemata des Terminals (Termux-`colors.properties`). */
    val terminalThemes: List<TerminalThemeContribution> = emptyList(),
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
    val mcpServers: List<McpServerContribution> = emptyList(),
    /** Projektvorlagen für den Projekt-Assistenten (Dateibäume mit Platzhaltern, kein Code wird ausgeführt). */
    val projectTemplates: List<ProjectTemplateContribution> = emptyList(),
    /** Einstellungsseite des Plugins (Plugins-Bildschirm → „Einstellungen“). */
    val settings: PluginSettingsContribution? = null,
    /** Eingebaute App-Funktionen, die dieses Plugin freischaltet (siehe [AppFeature], z. B. `debugger`). */
    val features: List<String> = emptyList()
) {
    /** Ob das Plugin keinerlei Contributions enthält. */
    val isEmpty: Boolean
        get() = colorThemes.isEmpty() && iconThemes.isEmpty() && terminalThemes.isEmpty() && languagePacks.isEmpty() &&
            grammars.isEmpty() && snippets.isEmpty() && lspServers.isEmpty() && extensionPack.isEmpty() &&
            formatters.isEmpty() && tools.isEmpty() && requires.isEmpty() &&
            agentTools.isEmpty() && mcpServers.isEmpty() && projectTemplates.isEmpty() && features.isEmpty()
}

/**
 * Eingebaute App-Funktionen, die erst mit einem Plugin (`contributes.features`) sichtbar werden.
 * Der Code liegt in der App; das Plugin schaltet die Oberfläche frei und kann jederzeit deaktiviert werden.
 */
object AppFeature {
    /** Debugger: Haltepunkte im Editor, Debug-Leiste (JVM/Android/DAP), Variablen, Aufrufstapel, Auswertung. */
    const val DEBUGGER = "debugger"

    /** Alle bekannten Kennungen; unbekannte Einträge in Manifesten werden ignoriert. */
    val ALL: Set<String> = setOf(DEBUGGER)
}

/**
 * Projektvorlage: [path] ist ein Ordner im Plugin mit den Ebenen `common/` (immer), `kotlin/` bzw. `java/`
 * (gewählte Sprache), `kts/` bzw. `groovy/` (Gradle-DSL) und der Kombination (z. B. `kotlin-kts/`); spätere Ebenen
 * überschreiben gleichnamige Dateien.
 * In Pfaden und Textdateien werden `{{projectName}}`, `{{packageName}}`, `{{packagePath}}`, `{{packageJni}}` (JNI-Symbolname) und `{{minSdk}}` ersetzt.
 * Fehlen Gradle-Wrapper oder `gradle/libs.versions.toml`, ergänzt die App ihre eigenen.
 */
data class ProjectTemplateContribution(
    val id: String,
    val label: String,
    val description: String = "",
    val path: String,
    /** Wählbare Sprachen: `kotlin`, `java`. */
    val languages: List<String> = listOf(LANGUAGE_KOTLIN),
    /** Wählbare Gradle-DSL: `kts`, `groovy`. */
    val gradleDsl: List<String> = listOf(DSL_KTS),
    /** `false` für Nicht-Android-Projekte (kein Mindest-SDK). */
    val android: Boolean = true,
    /** Kleinstes erlaubtes Mindest-SDK. */
    val minSdk: Int? = null,
    /** Vorschaubild (PNG/WebP) relativ zum Plugin. */
    val icon: String? = null,
    /** Übersetzungen (`l10n`): Sprachcode (z. B. `en`) → Anzeigename/Beschreibung. */
    val l10n: Map<String, TemplateText> = emptyMap()
) {
    /** Übersetzter Text einer Vorlage; leere Felder fallen auf `label`/`description` zurück. */
    data class TemplateText(val label: String = "", val description: String = "")

    /** Erlaubte Werte. */
    companion object {
        const val LANGUAGE_KOTLIN = "kotlin"
        const val LANGUAGE_JAVA = "java"
        const val DSL_KTS = "kts"
        const val DSL_GROOVY = "groovy"
    }
}

/** Farbschema: [path] zeigt auf eine JSON-Datei (siehe docs/extensions.md) relativ zum Plugin-Verzeichnis. */
data class ColorThemeContribution(
    val id: String,
    val label: String,
    val isDark: Boolean,
    val path: String
)

/**
 * Terminal-Farbschema: [path] zeigt auf eine Datei im Format von Termux `colors.properties`
 * (`foreground`, `background`, `cursor`, `color0`…`color15` als `#RRGGBB`), siehe [TerminalColorsFile].
 */
data class TerminalThemeContribution(
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
    val env: Map<String, String> = emptyMap(),
    /** Dateinamen, die den Arbeitsordner markieren (z. B. `settings.gradle.kts`, `pyproject.toml`); leer = Projektordner. */
    val rootMarkers: List<String> = emptyList()
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
 * (neue Version im Plugin-Update), gilt die Voraussetzung wieder als fehlend. [arch] beschränkt einen Eintrag auf eine
 * CPU-Architektur (`aarch64`, `x86_64`, `arm`, `i686`; mehrere Einträge gleichen Namens je Architektur möglich),
 * [stripTopDir] entfernt beim Entpacken den einzigen obersten Ordner des Archivs.
 */
data class RequirementContribution(
    val manager: RequirementManager,
    val name: String,
    val check: String = name,
    val url: String? = null,
    val sha256: String? = null,
    val archive: DownloadArchive? = null,
    val arch: String? = null,
    val stripTopDir: Boolean = false
)

/** Ob der Eintrag für die Architektur [cpuArch] gilt (ohne [RequirementContribution.arch]: immer). */
fun RequirementContribution.appliesTo(cpuArch: String): Boolean = arch == null || arch == cpuArch

/** Namen der nicht leeren Contribution-Arten, wie sie in `contributes` heißen. */
fun Contributes.kinds(): List<String> = listOf(
    "colorThemes" to colorThemes.isNotEmpty(),
    "iconThemes" to iconThemes.isNotEmpty(),
    "terminalThemes" to terminalThemes.isNotEmpty(),
    "languagePacks" to languagePacks.isNotEmpty(),
    "grammars" to grammars.isNotEmpty(),
    "snippets" to snippets.isNotEmpty(),
    "lspServers" to lspServers.isNotEmpty(),
    "extensionPack" to extensionPack.isNotEmpty(),
    "formatters" to formatters.isNotEmpty(),
    "tools" to tools.isNotEmpty(),
    "requires" to requires.isNotEmpty(),
    "agentTools" to agentTools.isNotEmpty(),
    "mcpServers" to mcpServers.isNotEmpty(),
    "projectTemplates" to projectTemplates.isNotEmpty(),
    "settings" to (settings != null),
    "features" to features.isNotEmpty()
).filter { it.second }.map { it.first }

