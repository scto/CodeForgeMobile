/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Parst und validiert `plugin.json` (v1 abwärtskompatibel, v2 mit `contributes`). Reines Kotlin, ohne Android-Abhängigkeit. */
object PluginManifestParser {

    private const val VALUE = "value"

    private val LANGUAGE_CODE = Regex("[a-z]{2,3}(-[A-Z]{2})?")

    private val ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    private val ENV_KEY = Regex("[A-Z_][A-Z0-9_]{0,63}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val TEMPLATE_LANGUAGES = setOf(ProjectTemplateContribution.LANGUAGE_KOTLIN, ProjectTemplateContribution.LANGUAGE_JAVA)
    private val TEMPLATE_DSL = setOf(ProjectTemplateContribution.DSL_KTS, ProjectTemplateContribution.DSL_GROOVY)
    private val MIN_SDK_RANGE = 21..36
    private val CATEGORY_REGEX = Regex("[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*){0,3}")
    private const val MAX_TAGS = 20

    /** Parst und validiert ein Plugin-Manifest (JSON); Fehler werden als Failure geliefert. */
    fun parse(text: String): Result<PluginManifest> = runCatching {
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("plugin.json muss ein JSON-Objekt sein")

        val id = root.string("id") ?: throw IllegalArgumentException("plugin.json: 'id' fehlt")
        require(ID_REGEX.matches(id) && !id.contains("..")) { "Ungültige Plugin-ID: $id" }
        val name = root.string("name")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("plugin.json: 'name' fehlt")
        val version = root.string("version")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("plugin.json: 'version' fehlt")

        val apiVersion = (root["apiVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        require(apiVersion in 1..PluginApiVersion.CURRENT) {
            "Plugin benötigt API-Version $apiVersion, diese App unterstützt bis ${PluginApiVersion.CURRENT}"
        }

        val permissions = root.stringList("permissions").toSet()
        val unknown = permissions - PluginPermissions.ALL
        require(unknown.isEmpty()) { "Unbekannte Berechtigungen: ${unknown.joinToString()}" }

        val minHostVersion = root.string("minHostVersion").orEmpty()
        require(minHostVersion.isEmpty() || Regex("\\d+(\\.\\d+)*([-+][A-Za-z0-9.]+)?").matches(minHostVersion)) {
            "Ungültige minHostVersion: $minHostVersion"
        }
        val dependsOn = root.stringList("dependsOn")
        require(dependsOn.all { ID_REGEX.matches(it) && !it.contains("..") }) { "dependsOn enthält eine ungültige Plugin-ID" }
        require(id !in dependsOn) { "Ein Plugin kann nicht von sich selbst abhängen" }

        val activationEvents = root.stringList("activationEvents")
        activationEvents.forEach { e -> require(ActivationEvents.isValid(e)) { "Ungültiges Aktivierungsereignis: $e" } }
        val categories = root.stringList("categories").map { it.trim().trim('/') }.filter { it.isNotEmpty() }
        require(categories.all { CATEGORY_REGEX.matches(it) }) { "Ungültige Kategorie (erlaubt: a-z, 0-9, -, / als Trenner)" }
        val tags = root.stringList("tags").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        require(tags.size <= MAX_TAGS && tags.all { it.length <= 30 }) { "Höchstens $MAX_TAGS Tags mit je bis zu 30 Zeichen" }

        val sandbox = (root["sandbox"] as? JsonPrimitive)?.booleanOrNull == true
        if (sandbox) {
            require(apiVersion >= 4) { "sandbox braucht apiVersion 4" }
            require(!root.string("entryPointClass").isNullOrBlank()) { "sandbox braucht entryPointClass" }
            require(permissions.all { it in SandboxPolicy.PERMISSIONS }) {
                "In der Sandbox sind nur diese Berechtigungen möglich: ${SandboxPolicy.PERMISSIONS.joinToString()}"
            }
            require(activationEvents.isEmpty() || activationEvents.all { !it.startsWith("onProject:") }) {
                "Sandbox-Plugins kennen kein Projekt (onProject nicht möglich)"
            }
        }

        val parsedContributes = parseContributes(root["contributes"] as? JsonObject)
        require(parsedContributes.tools.isEmpty() || PluginPermissions.SHELL in permissions) {
            "contributes.tools benötigt die Berechtigung '${PluginPermissions.SHELL}'"
        }
        require((parsedContributes.agentTools.isEmpty() && parsedContributes.mcpServers.isEmpty()) ||
            (PluginPermissions.AGENT in permissions && PluginPermissions.SHELL in permissions)) {
            "contributes.agentTools/mcpServers benötigen die Berechtigungen '${PluginPermissions.AGENT}' und '${PluginPermissions.SHELL}'"
        }
        PluginManifest(
            id = id,
            name = name,
            version = version,
            description = root.string("description").orEmpty(),
            publisher = root.string("publisher").orEmpty(),
            apiVersion = apiVersion,
            entryPointClass = root.string("entryPointClass")?.takeIf { it.isNotBlank() },
            permissions = permissions,
            minHostVersion = minHostVersion,
            dependsOn = dependsOn,
            activationEvents = activationEvents,
            categories = categories,
            tags = tags,
            sandbox = sandbox,
            contributes = parsedContributes
        )
    }

    private fun parseContributes(obj: JsonObject?): Contributes {
        if (obj == null) return Contributes()
        return Contributes(
            colorThemes = obj.objects("colorThemes").map {
                ColorThemeContribution(
                    id = it.req("id"),
                    label = it.req("label"),
                    isDark = when (it.string("uiTheme")?.lowercase()) {
                        "dark" -> true
                        "light" -> false
                        null -> (it["dark"] as? JsonPrimitive)?.booleanOrNull
                            ?: throw IllegalArgumentException("colorThemes: 'uiTheme' (dark|light) fehlt")
                        else -> throw IllegalArgumentException("colorThemes: 'uiTheme' muss dark oder light sein")
                    },
                    path = it.relPath("path")
                )
            },
            iconThemes = obj.objects("iconThemes").map {
                IconThemeContribution(it.req("id"), it.req("label"), it.relPath("path"))
            },
            terminalThemes = obj.objects("terminalThemes").map {
                TerminalThemeContribution(
                    id = it.req("id"),
                    label = it.req("label"),
                    isDark = it.string("uiTheme") != "light",
                    path = it.relPath("path")
                )
            },
            languagePacks = obj.objects("languagePacks").map {
                LanguagePackContribution(it.req("locale"), it.req("label"), it.relPath("path"))
            },
            grammars = obj.objects("grammars").map {
                GrammarContribution(it.req("languageId"), it.req("scopeName"), it.relPath("path"), it.stringList("extensions"))
            },
            snippets = obj.objects("snippets").map {
                SnippetContribution(it.req("languageId"), it.relPath("path"))
            },
            lspServers = obj.objects("lspServers").map {
                val command = it.stringList("command")
                require(command.isNotEmpty()) { "lspServers: 'command' darf nicht leer sein" }
                val languages = it.stringList("languages")
                require(languages.isNotEmpty()) { "lspServers: 'languages' darf nicht leer sein" }
                LspServerContribution(
                    id = it.req("id"),
                    label = it.string("label") ?: it.req("id"),
                    languages = languages,
                    command = command,
                    installScript = it.string("installScript"),
                    env = it.stringMap("env").also { env ->
                        require(env.keys.all { k -> ENV_KEY.matches(k) }) { "lspServers: ungültiger Name in 'env'" }
                    },
                    rootMarkers = it.stringList("rootMarkers").also { markers ->
                        require(markers.all { m -> m.isNotBlank() && '/' !in m && ".." !in m }) {
                            "lspServers: 'rootMarkers' sind Dateinamen ohne Pfad"
                        }
                    }
                )
            },
            extensionPack = obj.stringList("extensionPack"),
            features = obj.stringList("features").filter { it in AppFeature.ALL }.distinct(),
            formatters = obj.objects("formatters").map {
                val command = it.stringList("command")
                require(command.isNotEmpty()) { "formatters: 'command' darf nicht leer sein" }
                val extensions = it.stringList("extensions").map { e -> e.removePrefix(".") }.filter { e -> e.isNotBlank() }
                val fileNames = it.stringList("fileNames").filter { n -> n.isNotBlank() }
                require(extensions.isNotEmpty() || fileNames.isNotEmpty()) { "formatters: 'extensions' oder 'fileNames' fehlt" }
                val timeout = (it["timeoutSeconds"] as? JsonPrimitive)?.intOrNull ?: 15
                require(timeout in 1..120) { "formatters: 'timeoutSeconds' muss 1..120 sein" }
                FormatterContribution(
                    id = it.req("id"),
                    label = it.string("label") ?: it.req("id"),
                    extensions = extensions,
                    fileNames = fileNames,
                    command = command,
                    timeoutSeconds = timeout
                )
            },
            tools = obj.objects("tools").map {
                val command = it.stringList("command")
                require(command.isNotEmpty() && command.first().isNotBlank()) { "tools: 'command' darf nicht leer sein" }
                val timeout = (it["timeoutSeconds"] as? JsonPrimitive)?.intOrNull ?: 120
                require(timeout in 1..900) { "tools: 'timeoutSeconds' muss 1..900 sein" }
                val prompt = (it["prompt"] as? JsonObject)?.let { p ->
                    val options = (p["options"] as? JsonArray).orEmpty().map { o ->
                        val obj = o as? JsonObject ?: throw IllegalArgumentException("tools: 'options' erwartet Objekte {value,label}")
                        val value = obj.req("value")
                        require(value.isNotBlank() && value.none { c -> c.isWhitespace() }) { "tools: Option '$value' darf keine Leerzeichen enthalten" }
                        ToolOption(value, obj.string("label") ?: value)
                    }
                    require(options.map { o -> o.value }.toSet().size == options.size) { "tools: doppelte Option-Werte" }
                    ToolPrompt(
                        label = p.req("label"),
                        hint = p.string("hint").orEmpty(),
                        default = p.string("default").orEmpty(),
                        options = options,
                        textLabel = p.string("textLabel").orEmpty(),
                        textHint = p.string("textHint").orEmpty()
                    )
                }
                ToolContribution(
                    id = it.req("id").also { id -> require(ID_REGEX.matches(id)) { "tools: ungültige ID '$id'" } },
                    label = it.string("label") ?: it.req("id"),
                    description = it.string("description").orEmpty(),
                    command = command,
                    prompt = prompt,
                    confirm = it.string("confirm")?.takeIf { c -> c.isNotBlank() },
                    timeoutSeconds = timeout
                )
            },
            agentTools = obj.objects("agentTools").map {
                val command = it.stringList("command")
                require(command.isNotEmpty() && command.first().isNotBlank()) { "agentTools: 'command' darf nicht leer sein" }
                val timeout = (it["timeoutSeconds"] as? JsonPrimitive)?.intOrNull ?: 60
                require(timeout in 1..600) { "agentTools: 'timeoutSeconds' muss 1..600 sein" }
                val id = it.req("id")
                require(Regex("[a-z0-9_]{1,40}").matches(id)) { "agentTools: ID '$id' muss aus a-z, 0-9, _ bestehen (max. 40)" }
                AgentToolContribution(
                    id = id,
                    description = it.req("description"),
                    command = command,
                    kind = when (it.string("kind")?.lowercase()) {
                        null, "execute" -> AgentToolKind.EXECUTE
                        "read" -> AgentToolKind.READ
                        else -> throw IllegalArgumentException("agentTools: 'kind' muss read oder execute sein")
                    },
                    inputDescription = it.string("inputDescription").orEmpty(),
                    timeoutSeconds = timeout
                )
            }.also { list -> require(list.map { t -> t.id }.toSet().size == list.size) { "agentTools: doppelte IDs" } },
            mcpServers = obj.objects("mcpServers").map {
                val url = it.string("url")?.takeIf { u -> u.isNotBlank() }
                val cmd = it.string("command")?.takeIf { c -> c.isNotBlank() }
                require((url == null) != (cmd == null)) { "mcpServers: genau eines von 'url' oder 'command' angeben" }
                val name = it.req("name")
                require(Regex("[A-Za-z0-9_-]{1,32}").matches(name)) { "mcpServers: ungültiger Name '$name'" }
                require(url == null || url.startsWith("https://") || url.startsWith("http://")) { "mcpServers: 'url' muss http(s) sein" }
                McpServerContribution(name = name, url = url, command = cmd, args = it.stringList("args"))
            }.also { list -> require(list.map { s -> s.name }.toSet().size == list.size) { "mcpServers: doppelte Namen" } },
            requires = obj.objects("requires").map { parseRequirement(it) },
            projectTemplates = obj.objects("projectTemplates").map { parseProjectTemplate(it) }
                .also { list -> require(list.map { t -> t.id }.toSet().size == list.size) { "projectTemplates: doppelte IDs" } },
            settings = (obj["settings"] as? JsonObject)?.let { parseSettings(it) }
        )
    }

    private fun parseSettings(obj: JsonObject): PluginSettingsContribution {
        val items = obj.objects("items").map { parseSettingItem(it) }
        require(items.isNotEmpty() && items.size <= PluginSettingsRules.MAX_ITEMS) {
            "settings: 1 bis ${PluginSettingsRules.MAX_ITEMS} Einträge in 'items'"
        }
        require(items.map { it.key }.toSet().size == items.size) { "settings: doppelte Schlüssel" }
        return PluginSettingsContribution(title = obj.string("title"), items = items)
    }

    private fun parseSettingItem(obj: JsonObject): PluginSettingItem {
        val key = obj.req("key")
        require(PluginSettingsRules.KEY.matches(key)) { "settings: ungültiger Schlüssel '$key'" }
        val type = when (val t = obj.req("type")) {
            "text" -> PluginSettingType.TEXT
            "boolean" -> PluginSettingType.BOOLEAN
            "choice" -> PluginSettingType.CHOICE
            "number" -> PluginSettingType.NUMBER
            else -> throw IllegalArgumentException("settings: unbekannter Typ '$t' (text, boolean, choice, number)")
        }
        val options = obj.objects("options").map { o -> o.req(VALUE).let { v -> PluginSettingOption(v, o.string("label") ?: v) } }
        require(type != PluginSettingType.CHOICE || options.isNotEmpty()) { "settings: '$key' (choice) braucht 'options'" }
        val default = (obj["default"] as? JsonPrimitive)?.content
            ?: when (type) {
                PluginSettingType.BOOLEAN -> "false"
                PluginSettingType.CHOICE -> options.first().value
                PluginSettingType.NUMBER -> (obj.int("min") ?: 0).toString()
                PluginSettingType.TEXT -> ""
            }
        val item = PluginSettingItem(
            key = key,
            type = type,
            title = obj.string("title") ?: key,
            description = obj.string("description").orEmpty(),
            default = default,
            options = options,
            min = obj.int("min"),
            max = obj.int("max")
        )
        require(item.normalize(default) == default) { "settings: Vorgabe von '$key' ist ungültig" }
        return item
    }

    private fun parseProjectTemplate(obj: JsonObject): ProjectTemplateContribution {
        val id = obj.req("id")
        require(ID_REGEX.matches(id)) { "projectTemplates: ungültige ID '$id'" }
        val languages = obj.stringList("languages").ifEmpty { listOf(ProjectTemplateContribution.LANGUAGE_KOTLIN) }
        require(languages.all { it in TEMPLATE_LANGUAGES }) { "projectTemplates: 'languages' erlaubt nur kotlin, java" }
        val dsl = obj.stringList("gradleDsl").ifEmpty { listOf(ProjectTemplateContribution.DSL_KTS) }
        require(dsl.all { it in TEMPLATE_DSL }) { "projectTemplates: 'gradleDsl' erlaubt nur kts, groovy" }
        val minSdk = (obj["minSdk"] as? JsonPrimitive)?.intOrNull
        require(minSdk == null || minSdk in MIN_SDK_RANGE) { "projectTemplates: 'minSdk' außerhalb 21..36" }
        return ProjectTemplateContribution(
            id = id,
            label = obj.req("label"),
            description = obj.string("description").orEmpty(),
            path = obj.relPath("path"),
            languages = languages.distinct(),
            gradleDsl = dsl.distinct(),
            android = (obj["android"] as? JsonPrimitive)?.booleanOrNull ?: true,
            minSdk = minSdk,
            icon = if (obj.string("icon").isNullOrBlank()) null else obj.relPath("icon"),
            l10n = parseTemplateL10n(obj["l10n"] as? JsonObject)
        )
    }

    private fun parseTemplateL10n(obj: JsonObject?): Map<String, ProjectTemplateContribution.TemplateText> =
        obj?.entries.orEmpty().mapNotNull { (lang, value) ->
            val text = value as? JsonObject ?: return@mapNotNull null
            require(LANGUAGE_CODE.matches(lang)) { "projectTemplates: ungültiger Sprachcode '$lang' in 'l10n'" }
            lang to ProjectTemplateContribution.TemplateText(text.string("label").orEmpty(), text.string("description").orEmpty())
        }.toMap()

    private fun parseRequirement(obj: JsonObject): RequirementContribution {
        val manager = RequirementManager.of(obj.req("manager"))
            ?: throw IllegalArgumentException("requires: 'manager' muss pkg, npm, pip oder download sein")
        val name = obj.req("name")
        require(RequirementPolicy.isValidName(name)) { "requires: ungültiger Paketname '$name'" }
        if (manager != RequirementManager.DOWNLOAD) {
            val check = obj.string("check")?.takeIf { c -> c.isNotBlank() } ?: name.substringAfterLast('/')
            require(RequirementPolicy.isValidName(check)) { "requires: ungültiger 'check'-Name '$check'" }
            return RequirementContribution(manager, name, check)
        }
        require(!name.contains('/')) { "requires: Download-Name '$name' darf keinen '/' enthalten" }
        val url = obj.req("url")
        require(url.startsWith("https://")) { "requires: 'url' muss https sein" }
        val sha = obj.req("sha256").lowercase()
        require(SHA256.matches(sha)) { "requires: 'sha256' muss 64 Hex-Zeichen haben" }
        val archive = DownloadArchive.fromId(obj.req("archive"))
            ?: throw IllegalArgumentException("requires: 'archive' muss zip, tar.gz oder file sein")
        val arch = obj.string("arch")?.takeIf { it.isNotBlank() }
        require(arch == null || arch in RequirementPolicy.ARCHES) { "requires: 'arch' muss aarch64, x86_64, arm oder i686 sein" }
        val strip = (obj["stripTopDir"] as? JsonPrimitive)?.booleanOrNull ?: false
        require(!strip || archive != DownloadArchive.FILE) { "requires: 'stripTopDir' nur bei zip oder tar.gz" }
        return RequirementContribution(manager, name, obj.relPath("check"), url, sha, archive, arch, strip)
    }

    private fun JsonObject.stringMap(key: String): Map<String, String> =
        (this[key] as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { k to it } }
            .toMap()

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.req(key: String): String =
        string(key)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Pflichtfeld '$key' fehlt")

    /** Relativer Pfad innerhalb des Plugin-Verzeichnisses: kein führender '/', kein '..'. */
    private fun JsonObject.relPath(key: String): String {
        val p = req(key)
        require(!p.startsWith("/") && !p.startsWith("\\") && p.split('/', '\\').none { it == ".." }) {
            "Ungültiger Pfad '$p' (nur relative Pfade innerhalb des Plugins erlaubt)"
        }
        return p
    }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
