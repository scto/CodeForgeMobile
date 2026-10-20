// Modul: :core:domain
package com.codeforge.core.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Year
import java.util.UUID

/**
 * Modell für ein Code-Snippet / Live-Template.
 *
 * @param id Eindeutige ID zur Identifikation.
 * @param prefix Das Tastatur-Kürzel in der Autovervollständigung (z. B. "fori", "composable").
 * @param name Anzeigename des Snippets.
 * @param description Optionale Beschreibung der Vorlage.
 * @param scope Sprach-Gültigkeitsbereich (z. B. "kotlin", "java", "xml", "json"). Leer = alle Sprachen.
 * @param body Der Vorlagentext im VS-Code-/LSP-Snippet-Format mit Tabstopps und Variablen.
 * @param isBuiltin Ob es sich um ein mitgeliefertes Standard-Snippet handelt.
 * @param isEnabled Ob das Snippet aktiv ist und in der Autovervollständigung vorgeschlagen wird.
 */
data class Snippet(
    val id: String = UUID.randomUUID().toString(),
    val prefix: String,
    val name: String,
    val description: String = "",
    val scope: Set<String> = emptySet(),
    val body: String,
    val isBuiltin: Boolean = false,
    val isEnabled: Boolean = true
)

/**
 * Ergebnis einer Snippet-Syntaxprüfung.
 */
sealed interface SnippetValidationResult {
    data object Valid : SnippetValidationResult
    data class Invalid(val error: String, val line: Int, val column: Int) : SnippetValidationResult
}

/**
 * Kontext für die Variablenauflösung eines Snippets beim Einfügen.
 */
data class SnippetVariableContext(
    val filePath: String? = null,
    val projectRoot: String? = null,
    val clipboardContent: String? = null,
    val currentYear: String = Year.now().value.toString(),
    val packageName: String? = null,
    val className: String? = null
)

/**
 * Reiner Kotlin-Parser und Validator für VS-Code-/TextMate-/LSP-Snippet-Syntax.
 *
 * Unterstützt:
 * - Tabstopps: `$0`, `$1`, `${1}`, etc.
 * - Placeholders: `${1:default}`, verschachtelt `${1:foo ${2:bar}}`
 * - Choices: `${1|one,two,three|}`
 * - Variablen: `$TM_FILENAME`, `${TM_FILENAME}`, `$PACKAGE`, `${PACKAGE:com.example}`
 * - Escapes: `\$`, `\\`, `\}`
 * - Fehlererkennung mit Zeilen- und Spaltenangabe ohne Abstürze.
 */
object SnippetParser {

    private val KNOWN_VARIABLES = setOf(
        "TM_FILENAME",
        "TM_FILENAME_BASE",
        "TM_DIRECTORY",
        "TM_FILEPATH",
        "RELATIVE_PATH",
        "CURRENT_YEAR",
        "CLIPBOARD",
        "PACKAGE",
        "CLASSNAME",
        "WORKSPACE_NAME",
        "WORKSPACE_FOLDER"
    )

    fun validate(body: String): SnippetValidationResult {
        if (body.isEmpty()) return SnippetValidationResult.Valid

        var line = 1
        var col = 1
        var i = 0
        val len = body.length

        fun advance() {
            if (i < len) {
                if (body[i] == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
                i++
            }
        }

        while (i < len) {
            val c = body[i]
            if (c == '\\') {
                val startLine = line
                val startCol = col
                advance()
                if (i >= len) {
                    return SnippetValidationResult.Invalid("Dangling escape character at end of snippet", startLine, startCol)
                }
                // Escaped character: \$, \}, \\ etc.
                advance()
                continue
            }

            if (c == '$') {
                val dollarLine = line
                val dollarCol = col
                advance()

                if (i >= len) {
                    return SnippetValidationResult.Invalid("Dangling '$' at end of snippet", dollarLine, dollarCol)
                }

                val next = body[i]
                if (next.isDigit()) {
                    // Simple tabstop $1, $2, $0
                    while (i < len && body[i].isDigit()) {
                        advance()
                    }
                    continue
                }

                if (next.isLetter() || next == '_') {
                    // Simple variable $VAR
                    while (i < len && (body[i].isLetterOrDigit() || body[i] == '_')) {
                        advance()
                    }
                    continue
                }

                if (next == '{') {
                    val braceLine = dollarLine
                    val braceCol = dollarCol
                    advance() // consume '{'

                    if (i >= len) {
                        return SnippetValidationResult.Invalid("Unclosed '\${' at end of snippet", braceLine, braceCol)
                    }

                    // Check for tabstop / choice / placeholder / variable
                    // Could be:
                    // ${1}
                    // ${1:default}
                    // ${1|a,b,c|}
                    // ${VAR}
                    // ${VAR:default}
                    // ${VAR/regex/format/options}
                    val tokenStart = i
                    val tokenLine = line
                    val tokenCol = col

                    // Read tabstop id or variable name
                    while (i < len && body[i] != ':' && body[i] != '|' && body[i] != '/' && body[i] != '}') {
                        if (body[i] == '\n') break
                        advance()
                    }

                    val identifier = body.substring(tokenStart, i).trim()
                    if (identifier.isEmpty() && (i >= len || body[i] != '}')) {
                        return SnippetValidationResult.Invalid("Empty placeholder identifier in '\${'", tokenLine, tokenCol)
                    }

                    val isTabstopNumber = identifier.all { it.isDigit() } && identifier.isNotEmpty()
                    val isValidVarName = identifier.isNotEmpty() &&
                        (identifier[0].isLetter() || identifier[0] == '_') &&
                        identifier.all { it.isLetterOrDigit() || it == '_' }

                    if (!isTabstopNumber && !isValidVarName) {
                        return SnippetValidationResult.Invalid(
                            "Invalid placeholder identifier '$identifier'. Must be a number or valid variable name",
                            tokenLine,
                            tokenCol
                        )
                    }

                    if (i >= len) {
                        return SnippetValidationResult.Invalid("Unclosed '\${$identifier' missing '}'", braceLine, braceCol)
                    }

                    val delimiter = body[i]
                    when (delimiter) {
                        '}' -> {
                            advance() // simple ${1} or ${VAR}
                        }
                        ':' -> {
                            advance() // default value start
                            // Parse inner until matching unescaped '}' (handling nested braces)
                            var braceDepth = 1
                            while (i < len && braceDepth > 0) {
                                val ch = body[i]
                                if (ch == '\\') {
                                    advance()
                                    if (i < len) advance()
                                    continue
                                }
                                if (ch == '$' && i + 1 < len && body[i + 1] == '{') {
                                    braceDepth++
                                    advance()
                                    advance()
                                    continue
                                }
                                if (ch == '}') {
                                    braceDepth--
                                    advance()
                                    continue
                                }
                                advance()
                            }
                            if (braceDepth > 0) {
                                return SnippetValidationResult.Invalid("Unclosed '\${$identifier:' missing '}'", braceLine, braceCol)
                            }
                        }
                        '|' -> {
                            advance() // choice list start
                            // Read choices until '|}'
                            var closed = false
                            while (i < len) {
                                if (body[i] == '\\') {
                                    advance()
                                    if (i < len) advance()
                                    continue
                                }
                                if (body[i] == '|' && i + 1 < len && body[i + 1] == '}') {
                                    advance() // '|'
                                    advance() // '}'
                                    closed = true
                                    break
                                }
                                advance()
                            }
                            if (!closed) {
                                return SnippetValidationResult.Invalid(
                                    "Unclosed choice '\${$identifier|...' missing '|}'",
                                    braceLine,
                                    braceCol
                                )
                            }
                        }
                        '/' -> {
                            advance() // regex transform start: /regex/format/options}
                            var slashCount = 1
                            var closed = false
                            while (i < len) {
                                if (body[i] == '\\') {
                                    advance()
                                    if (i < len) advance()
                                    continue
                                }
                                if (body[i] == '/') {
                                    slashCount++
                                    advance()
                                    continue
                                }
                                if (slashCount >= 3 && body[i] == '}') {
                                    advance()
                                    closed = true
                                    break
                                }
                                advance()
                            }
                            if (!closed) {
                                return SnippetValidationResult.Invalid(
                                    "Unclosed regex transformation for '$identifier'",
                                    braceLine,
                                    braceCol
                                )
                            }
                        }
                        else -> {
                            return SnippetValidationResult.Invalid(
                                "Unexpected character '$delimiter' after '\${$identifier'",
                                line,
                                col
                            )
                        }
                    }
                    continue
                }

                return SnippetValidationResult.Invalid("Unexpected character after '$': '$next'", dollarLine, dollarCol)
            }

            advance()
        }

        return SnippetValidationResult.Valid
    }

    /**
     * Extrahiert alle verwendeten Tabstopp-Nummern aus dem Snippet-Body.
     */
    fun extractTabstops(body: String): List<Int> {
        val tabstops = mutableSetOf<Int>()
        val regex = Regex("""\$(?:\{(\d+)(?::|\||/|\})|(\d+))""")
        for (match in regex.findAll(body)) {
            val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
            numStr.toIntOrNull()?.let { tabstops.add(it) }
        }
        return tabstops.sorted()
    }
}

/**
 * Löst projekt- und dateibezogene Variablen in Snippets auf.
 */
object SnippetVariableResolver {

    /**
     * Ersetzt bekannte Variablen wie `$TM_FILENAME`, `$TM_FILENAME_BASE`, `$PACKAGE`, `$CLASSNAME`,
     * `$CURRENT_YEAR`, `$CLIPBOARD` durch ihre konkreten Werte im [context].
     * Behält Tabstopps ($0, $1, ${1:default}) und unbekannte Variablen unverändert bei.
     */
    fun resolve(body: String, context: SnippetVariableContext): String {
        val fileName = context.filePath?.substringAfterLast('/') ?: ""
        val fileNameBase = fileName.substringBeforeLast('.')
        val className = context.className ?: deriveClassName(fileName)
        val packageName = context.packageName ?: derivePackageName(context.filePath)
        val currentYear = context.currentYear
        val clipboard = context.clipboardContent ?: ""

        val values = mapOf(
            "TM_FILENAME" to fileName,
            "TM_FILENAME_BASE" to fileNameBase,
            "CLASSNAME" to className,
            "PACKAGE" to packageName,
            "CURRENT_YEAR" to currentYear,
            "CLIPBOARD" to clipboard,
            "WORKSPACE_NAME" to (context.projectRoot?.substringAfterLast('/') ?: ""),
            "WORKSPACE_FOLDER" to (context.projectRoot ?: "")
        )

        // 1. Simple variables: $VAR
        var result = body
        for ((key, value) in values) {
            val simpleRegex = Regex("""\$$key\b""")
            result = result.replace(simpleRegex, value)
        }

        // 2. Braced variables without default: ${VAR}
        for ((key, value) in values) {
            val bracedRegex = Regex("""\$\{$key\}""")
            result = result.replace(bracedRegex, value)
        }

        // 3. Braced variables with default: ${VAR:default}
        val defaultRegex = Regex("""\$\{([A-Z_]+):([^}]*)\}""")
        result = defaultRegex.replace(result) { match ->
            val varName = match.groupValues[1]
            val defaultVal = match.groupValues[2]
            val resolved = values[varName]
            if (!resolved.isNullOrEmpty()) resolved else defaultVal
        }

        return result
    }

    /**
     * Leitet den Klassennamen aus dem Dateinamen ab (z. B. "MainActivity.kt" -> "MainActivity").
     */
    fun deriveClassName(filePath: String?): String {
        if (filePath.isNullOrBlank()) return "MyClass"
        val fileName = filePath.substringAfterLast('/')
        val base = fileName.substringBeforeLast('.')
        return if (base.isNotEmpty()) base else "MyClass"
    }

    /**
     * Leitet den Paketnamen aus dem Dateipfad ab (z. B. "src/main/kotlin/com/codeforge/app/Main.kt" -> "com.codeforge.app").
     */
    fun derivePackageName(filePath: String?): String {
        if (filePath.isNullOrBlank()) return ""
        val normalized = filePath.replace('\\', '/')
        val sourceRoots = listOf(
            "/src/main/kotlin/",
            "/src/main/java/",
            "/src/test/kotlin/",
            "/src/test/java/",
            "/src/androidTest/kotlin/",
            "/src/androidTest/java/",
            "src/main/kotlin/",
            "src/main/java/",
            "src/test/kotlin/",
            "src/test/java/",
            "src/androidTest/kotlin/",
            "src/androidTest/java/"
        )

        for (root in sourceRoots) {
            val idx = normalized.indexOf(root)
            if (idx != -1) {
                val rel = normalized.substring(idx + root.length)
                val dir = rel.substringBeforeLast('/', "")
                if (dir.isNotEmpty()) {
                    return dir.replace('/', '.')
                }
            }
        }
        return ""
    }
}

/**
 * Serialisierer und Deserialisierer für das Standard-VS-Code-Snippet-Format (JSON).
 */
object VsCodeSnippetJsonSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    /**
     * Serialisiert eine Liste von Snippets in ein gültiges VS-Code-Snippet-JSON.
     */
    fun serialize(snippets: List<Snippet>): String {
        val root = buildJsonObject {
            for (snippet in snippets) {
                put(
                    snippet.name,
                    buildJsonObject {
                        put("prefix", JsonPrimitive(snippet.prefix))
                        if (snippet.scope.isNotEmpty()) {
                            put("scope", JsonPrimitive(snippet.scope.sorted().joinToString(",")))
                        }
                        if (snippet.description.isNotBlank()) {
                            put("description", JsonPrimitive(snippet.description))
                        }
                        val bodyLines = snippet.body.split('\n')
                        if (bodyLines.size > 1) {
                            put(
                                "body",
                                JsonArray(bodyLines.map { JsonPrimitive(it) })
                            )
                        } else {
                            put("body", JsonPrimitive(snippet.body))
                        }
                    }
                )
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Parst ein VS-Code-Snippet-JSON in eine Liste von [Snippet]-Objekten.
     */
    fun deserialize(jsonContent: String): Result<List<Snippet>> = runCatching {
        if (jsonContent.isBlank()) return@runCatching emptyList()
        val parsed = json.parseToJsonElement(jsonContent).jsonObject
        val list = mutableListOf<Snippet>()

        for ((name, element) in parsed) {
            val obj = element.jsonObject
            val prefix = when (val p = obj["prefix"]) {
                is JsonPrimitive -> p.content
                is JsonArray -> p.firstOrNull()?.jsonPrimitive?.content ?: name.lowercase()
                else -> name.lowercase()
            }

            val scopeSet = when (val s = obj["scope"]) {
                is JsonPrimitive -> s.content.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                is JsonArray -> s.map { it.jsonPrimitive.content.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                else -> emptySet()
            }

            val description = obj["description"]?.jsonPrimitive?.content.orEmpty()

            val body = when (val b = obj["body"]) {
                is JsonPrimitive -> b.content
                is JsonArray -> b.joinToString("\n") { it.jsonPrimitive.content }
                else -> ""
            }

            list.add(
                Snippet(
                    id = UUID.randomUUID().toString(),
                    prefix = prefix,
                    name = name,
                    description = description,
                    scope = scopeSet,
                    body = body,
                    isBuiltin = false,
                    isEnabled = true
                )
            )
        }
        list
    }
}
