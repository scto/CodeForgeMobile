/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Werkzeug-SPI des Agenten und Hilfen zum Lesen der JSON-Argumente.
 */
package com.codeforge.libs.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Art der Wirkung eines Werkzeugs; entscheidet über die Freigabe durch die [AgentPermissionPolicy]. */
enum class ToolKind {
    /** Liest nur (Projektdateien, Diagnosen) – immer erlaubt. */
    READ,

    /** Ändert Projektdateien. */
    WRITE,

    /** Führt etwas aus (Build, Programm, Netzwerk). */
    EXECUTE,
}

/** Ergebnis eines Werkzeugaufrufs. [isError] wird dem Modell als Fehler gemeldet, damit es reagieren kann. */
data class ToolResult(val content: String, val isError: Boolean = false) {
    companion object {
        /** Fehlerergebnis mit [message]. */
        fun error(message: String) = ToolResult(message, isError = true)
    }
}

/** Ein vom Modell aufrufbares Werkzeug. */
interface AgentTool {
    /** Eindeutiger Name (a–z, 0–9, `_`). */
    val name: String

    /** Beschreibung für das Modell. */
    val description: String

    /** JSON-Schema der Parameter (`type: object`). */
    val parameters: JsonObject

    /** Wirkungsart für die Berechtigungsprüfung. */
    val kind: ToolKind

    /** Kurzer Satz für die Freigabeabfrage, z. B. „Datei app/Foo.kt ändern“. */
    fun describeCall(arguments: JsonObject): String = name

    /** Vorschau der geplanten Wirkung (z. B. Unified-Diff) für die Freigabe; `null`, wenn es keine gibt. Darf nichts ändern. */
    suspend fun preview(arguments: JsonObject): String? = null

    /** Führt das Werkzeug aus. Fehler dürfen als Exception oder [ToolResult.error] gemeldet werden. */
    suspend fun execute(arguments: JsonObject): ToolResult
}

/** Menge der dem Agenten bekannten Werkzeuge. */
class AgentToolRegistry(tools: List<AgentTool>) {
    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }.also {
        require(it.size == tools.size) { "Doppelte Werkzeugnamen" }
    }

    /** Werkzeug zu [name] oder `null`. */
    operator fun get(name: String): AgentTool? = byName[name]

    /** Beschreibungen aller Werkzeuge für die Modellanfrage. */
    fun specs(): List<ToolSpec> = byName.values.map { ToolSpec(it.name, it.description, it.parameters) }
}

/** Ungültige oder fehlende Werkzeugargumente; die Meldung geht an das Modell. */
class ToolArgumentException(message: String) : IllegalArgumentException(message)

/** Pflichtparameter [key] als Text. */
fun JsonObject.requireString(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw ToolArgumentException("Parameter '$key' fehlt oder ist kein Text")

/** Optionaler Textparameter. */
fun JsonObject.optString(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Optionaler Ganzzahlparameter. */
fun JsonObject.optInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** Optionaler Wahrheitswert. */
fun JsonObject.optBoolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Optionales Array. */
fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray

/** Schema-Baustein: Textparameter. */
fun stringProp(description: String): JsonObject = buildJsonObject { put("type", "string"); put("description", description) }

/** Schema-Baustein: Ganzzahlparameter. */
fun intProp(description: String): JsonObject = buildJsonObject { put("type", "integer"); put("description", description) }

/** Schema-Baustein: Wahrheitswert. */
fun boolProp(description: String): JsonObject = buildJsonObject { put("type", "boolean"); put("description", description) }

/** Objekt-Schema mit [properties] und den Pflichtfeldern [required]. */
fun objectSchema(required: List<String>, vararg properties: Pair<String, JsonObject>): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { properties.forEach { (k, v) -> put(k, v) } })
    putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
}
