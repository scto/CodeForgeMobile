/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Werkzeuge für Rückfragen (`ask_user`), das Projektgedächtnis (`read_memory`, `write_memory`) und Bilder
 * (`view_image`, `screenshot_preview`). Vorbild: die Agenten-Werkzeuge von CodeAssist (GPL-3.0).
 */
package com.codeforge.libs.agent

import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Rückfrage des Agenten mit Antwortknöpfen; [allowOther] erlaubt eine eigene Antwort. */
data class AgentQuestion(val question: String, val options: List<String>, val allowOther: Boolean = true)

/** Stellt dem Nutzer eine Frage und wartet auf die Antwort; `null` = Frage verworfen. */
fun interface AgentQuestioner {
    /** Antwort auf [question]. */
    suspend fun ask(question: AgentQuestion): String?
}

/** Gedächtnis des Agenten für ein Projekt (Notizen über mehrere Unterhaltungen). */
interface AgentMemory {
    /** Aktueller Inhalt (leer, wenn nichts gespeichert ist). */
    suspend fun read(): String

    /** Ersetzt den Inhalt. */
    suspend fun write(content: String)
}

/** [AgentMemory] in einer Datei. */
class FileAgentMemory(private val file: File) : AgentMemory {
    override suspend fun read(): String = withContext(Dispatchers.IO) { if (file.isFile) file.readText() else "" }

    override suspend fun write(content: String) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        if (content.isBlank()) {
            file.delete()
            Unit
        } else {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) { file.writeText(content); tmp.delete() }
            Unit
        }
    }
}

/** Liest Bilddateien des Projekts für `view_image`. */
fun interface AgentImageReader {
    /** Bild unter [path] (relativ zum Projektstamm) oder `null`, wenn es keine unterstützte Bilddatei ist. */
    suspend fun read(path: String): ImageData?
}

/** [AgentImageReader] auf dem Dateisystem; Pfade bleiben im Projekt, höchstens [maxBytes]. */
class FileImageReader(root: File, private val maxBytes: Long = 3_500_000) : AgentImageReader {
    private val rootCanonical = root.canonicalFile

    override suspend fun read(path: String): ImageData? = withContext(Dispatchers.IO) {
        val rel = normalizeProjectPath(path)
        val f = File(rootCanonical, rel).canonicalFile
        if (!f.path.startsWith(rootCanonical.path + File.separator)) throw ToolArgumentException("Pfad verlässt das Projekt: $path")
        if (!f.isFile) return@withContext null
        if (f.length() > maxBytes) throw ToolArgumentException("Bild zu groß (${f.length() / 1024} KB, Limit ${maxBytes / 1024} KB)")
        val bytes = f.readBytes()
        val type = ImageTypes.detect(bytes) ?: return@withContext null
        ImageData(type, Base64.getEncoder().encodeToString(bytes))
    }
}

/** Erkennt Bildformate am Dateianfang. */
object ImageTypes {
    /** MIME-Typ von [bytes] (`image/png`, `image/jpeg`, `image/gif`, `image/webp`) oder `null`. */
    fun detect(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF8") -> "image/gif"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }
}

/** Rendert eine Compose-Vorschau als PNG. */
fun interface AgentPreviewRenderer {
    /** PNG der `@Preview`-Funktion [function] in [path] (relativ) oder ein Fehler mit Meldung. */
    suspend fun render(path: String, function: String): Result<ByteArray>
}

/** Fabrik der Interaktionswerkzeuge; fehlende Bausteine lassen das jeweilige Werkzeug weg. */
object InteractionTools {
    /** Obergrenze des Gedächtnisses in Zeichen. */
    const val MEMORY_MAX_CHARS = 16_000

    /** Werkzeuge für die vorhandenen Bausteine. */
    fun create(
        questioner: AgentQuestioner? = null,
        memory: AgentMemory? = null,
        images: AgentImageReader? = null,
        preview: AgentPreviewRenderer? = null,
    ): List<AgentTool> = listOfNotNull(
        questioner?.let { AskUser(it) },
        memory?.let { ReadMemory(it) },
        memory?.let { WriteMemory(it) },
        images?.let { ViewImage(it) },
        preview?.let { ScreenshotPreview(it) },
    )

    private class AskUser(private val questioner: AgentQuestioner) : AgentTool {
        override val name = "ask_user"
        override val kind = ToolKind.READ
        override val description = "Asks the user a multiple-choice question when a decision is genuinely theirs or the task is " +
            "ambiguous. The user taps an option or types an own answer. Do not use it for things you can find out yourself."
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("question", stringProp("The question, one or two sentences"))
                put("options", buildJsonObject {
                    put("type", "array")
                    put("description", "2 to 5 short answer options")
                    put("items", buildJsonObject { put("type", "string") })
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("question"), JsonPrimitive("options"))))
        }

        override fun describeCall(arguments: JsonObject) = "Rückfrage: ${arguments.optString("question")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val question = arguments.requireString("question").trim()
            val options = arguments.optArray("options").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }.distinct().take(MAX_OPTIONS)
            if (question.isEmpty()) throw ToolArgumentException("'question' ist leer")
            val answer = questioner.ask(AgentQuestion(question, options)) ?: return ToolResult("Der Nutzer hat die Frage nicht beantwortet.")
            return ToolResult("Antwort des Nutzers: $answer")
        }

        private companion object {
            const val MAX_OPTIONS = 5
        }
    }

    private class ReadMemory(private val memory: AgentMemory) : AgentTool {
        override val name = "read_memory"
        override val kind = ToolKind.READ
        override val description = "Reads your notes for this project from earlier conversations (decisions, conventions, open points)."
        override val parameters = objectSchema(emptyList())

        override suspend fun execute(arguments: JsonObject): ToolResult =
            ToolResult(memory.read().ifBlank { "Noch keine Notizen gespeichert." })
    }

    private class WriteMemory(private val memory: AgentMemory) : AgentTool {
        override val name = "write_memory"
        override val kind = ToolKind.READ
        override val description = "Saves notes for future conversations in this project: durable facts only (architecture decisions, " +
            "conventions, how to build/test, user preferences). mode 'append' adds a line, 'replace' rewrites all notes."
        override val parameters = objectSchema(
            listOf("content"),
            "content" to stringProp("Text to save"),
            "mode" to stringProp("append (default) or replace"),
        )

        override fun describeCall(arguments: JsonObject) = "Notiz speichern"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val content = arguments.requireString("content").trim()
            val mode = arguments.optString("mode") ?: "append"
            val next = when (mode) {
                "replace" -> content
                "append" -> (memory.read().trimEnd() + "\n" + content).trim()
                else -> throw ToolArgumentException("mode muss append oder replace sein")
            }
            if (next.length > MEMORY_MAX_CHARS) {
                return ToolResult.error("Notizen zu lang (${next.length} Zeichen, Limit $MEMORY_MAX_CHARS) – mit mode=replace zusammenfassen")
            }
            memory.write(next)
            return ToolResult("Gespeichert (${next.length} Zeichen)")
        }
    }

    private class ViewImage(private val reader: AgentImageReader) : AgentTool {
        override val name = "view_image"
        override val kind = ToolKind.READ
        override val description = "Shows you an image file of the project (PNG, JPEG, GIF, WebP), e.g. a screenshot or an icon."
        override val parameters = objectSchema(listOf("path"), "path" to stringProp("Image path relative to the project root"))

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val image = reader.read(path) ?: return ToolResult.error("$path ist keine unterstützte Bilddatei (PNG, JPEG, GIF, WebP)")
            return ToolResult("Bild $path (${image.mediaType})", images = listOf(image))
        }
    }

    private class ScreenshotPreview(private val renderer: AgentPreviewRenderer) : AgentTool {
        override val name = "screenshot_preview"
        override val kind = ToolKind.EXECUTE
        override val description = "Compiles and renders a Jetpack Compose @Preview function and shows you the image. " +
            "Use it to check UI changes visually."
        override val parameters = objectSchema(
            listOf("path", "function"),
            "path" to stringProp("Kotlin file relative to the project root"),
            "function" to stringProp("Name of the @Preview (or @Composable) function"),
        )

        override fun describeCall(arguments: JsonObject) = "Vorschau rendern: ${arguments.optString("function")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val function = arguments.requireString("function").trim()
            val png = renderer.render(path, function).getOrElse { return ToolResult.error("Vorschau fehlgeschlagen: ${it.message ?: it}") }
            return ToolResult(
                "Vorschau $function aus $path",
                images = listOf(ImageData(ImageTypes.detect(png) ?: "image/png", Base64.getEncoder().encodeToString(png))),
            )
        }
    }
}
