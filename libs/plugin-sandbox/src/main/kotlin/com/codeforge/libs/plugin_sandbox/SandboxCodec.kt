/**
 * Modul: :libs:plugin-sandbox
 * @author Thomas Schmid
 *
 * JSON-Kodierung der Daten, die zwischen App und Sandbox-Prozess hin- und hergehen. Nur einfache Datenklassen der
 * Plugin-API; kein Objekt verlässt seinen Prozess.
 */
package com.codeforge.libs.plugin_sandbox

import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginCompletionItem
import com.codeforge.libs.plugin_api.PluginDiagnostic
import com.codeforge.libs.plugin_api.PluginFileEdit
import com.codeforge.libs.plugin_api.PluginLocation
import com.codeforge.libs.plugin_api.TextEdit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal object SandboxCodec {
    /** Größte Textmenge, die in die Sandbox geschickt wird (Binder-Grenze ~1 MB). */
    const val MAX_TEXT = 400_000

    fun context(c: EditorContext): JsonObject = buildJsonObject {
        put("filePath", c.filePath); put("languageId", c.languageId); put("text", c.text.take(MAX_TEXT))
        put("cursor", c.cursorOffset); put("selStart", c.selectionStart); put("selEnd", c.selectionEnd)
    }

    fun context(o: JsonObject): EditorContext = EditorContext(
        filePath = o.s("filePath"), languageId = o.s("languageId"), text = o.s("text"),
        cursorOffset = o.i("cursor"), selectionStart = o.i("selStart"), selectionEnd = o.i("selEnd")
    )

    fun effects(list: List<EditorEffect>): JsonArray = buildJsonArray {
        list.forEach { e ->
            add(
                when (e) {
                    is EditorEffect.ApplyEdits -> buildJsonObject {
                        put("type", "edits")
                        put("edits", buildJsonArray { e.edits.forEach { t -> add(buildJsonObject { put("s", t.start); put("e", t.end); put("t", t.newText) }) } })
                    }
                    is EditorEffect.MoveCaret -> buildJsonObject { put("type", "caret"); put("offset", e.offset) }
                    is EditorEffect.Select -> buildJsonObject { put("type", "select"); put("s", e.start); put("e", e.end) }
                    is EditorEffect.CreateFile -> buildJsonObject { put("type", "create"); put("path", e.path); put("content", e.content) }
                    is EditorEffect.OpenFile -> buildJsonObject { put("type", "open"); put("path", e.path) }
                    is EditorEffect.ShowMessage -> buildJsonObject { put("type", "message"); put("text", e.text) }
                }
            )
        }
    }

    fun effects(a: JsonArray): List<EditorEffect> = a.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        when (o.s("type")) {
            "edits" -> EditorEffect.ApplyEdits(o.arr("edits").map { t -> val x = t.jsonObject; TextEdit(x.i("s"), x.i("e"), x.s("t")) })
            "caret" -> EditorEffect.MoveCaret(o.i("offset"))
            "select" -> EditorEffect.Select(o.i("s"), o.i("e"))
            "create" -> EditorEffect.CreateFile(o.s("path"), o.s("content"))
            "open" -> EditorEffect.OpenFile(o.s("path"))
            "message" -> EditorEffect.ShowMessage(o.s("text").take(500))
            else -> null
        }
    }

    fun completions(list: List<PluginCompletionItem>): JsonArray = buildJsonArray {
        list.take(500).forEach { c -> add(buildJsonObject { put("label", c.label); put("insert", c.insertText); c.detail?.let { put("detail", it) } }) }
    }

    fun completions(a: JsonArray): List<PluginCompletionItem> = a.map { val o = it.jsonObject; PluginCompletionItem(o.s("label"), o.s("insert"), o.sn("detail")) }

    fun locations(list: List<PluginLocation>): JsonArray = buildJsonArray { list.take(1000).forEach { add(location(it)) } }
    fun locations(a: JsonArray): List<PluginLocation> = a.map { location(it.jsonObject) }

    fun fileEdits(list: List<PluginFileEdit>?): JsonElement = list?.let { l ->
        buildJsonArray { l.forEach { e -> add(buildJsonObject { put("range", location(e.range)); put("text", e.newText) }) } }
    } ?: JsonNull

    fun fileEdits(el: JsonElement?): List<PluginFileEdit>? =
        (el as? JsonArray)?.map { val o = it.jsonObject; PluginFileEdit(location(o["range"]!!.jsonObject), o.s("text")) }

    fun diagnostics(list: List<PluginDiagnostic>): JsonArray = buildJsonArray {
        list.forEach { d ->
            add(buildJsonObject {
                put("m", d.message); put("sev", d.severity); put("s", d.startOffset); put("e", d.endOffset)
                d.code?.let { put("code", it) }; d.source?.let { put("src", it) }
            })
        }
    }

    fun diagnostics(a: JsonArray): List<PluginDiagnostic> = a.map {
        val o = it.jsonObject
        PluginDiagnostic(o.s("m"), o.i("sev"), o.i("s"), o.i("e"), o.sn("code"), o.sn("src"))
    }

    private fun location(l: PluginLocation) = buildJsonObject {
        put("f", l.filePath); put("l", l.line); put("c", l.column); put("el", l.endLine); put("ec", l.endColumn)
    }

    private fun location(o: JsonObject) = PluginLocation(o.s("f"), o.i("l"), o.i("c"), o.i("el"), o.i("ec"))

    fun JsonObject.s(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun JsonObject.sn(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.i(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
    fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
    fun JsonObject.obj(key: String): JsonObject = (this[key] as? JsonObject) ?: JsonObject(emptyMap())
}
