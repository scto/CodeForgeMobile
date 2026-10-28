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

    private val ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

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

        val parsedContributes = parseContributes(root["contributes"] as? JsonObject)
        require(parsedContributes.tools.isEmpty() || PluginPermissions.SHELL in permissions) {
            "contributes.tools benötigt die Berechtigung '${PluginPermissions.SHELL}'"
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
                    installScript = it.string("installScript")
                )
            },
            extensionPack = obj.stringList("extensionPack"),
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
                    ToolPrompt(label = p.req("label"), hint = p.string("hint").orEmpty(), default = p.string("default").orEmpty())
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
            requires = obj.objects("requires").map {
                val manager = RequirementManager.of(it.req("manager"))
                    ?: throw IllegalArgumentException("requires: 'manager' muss pkg, npm oder pip sein")
                val name = it.req("name")
                require(RequirementPolicy.isValidName(name)) { "requires: ungültiger Paketname '$name'" }
                val check = it.string("check")?.takeIf { c -> c.isNotBlank() } ?: name.substringAfterLast('/')
                require(RequirementPolicy.isValidName(check)) { "requires: ungültiger 'check'-Name '$check'" }
                RequirementContribution(manager, name, check)
            }
        )
    }

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
