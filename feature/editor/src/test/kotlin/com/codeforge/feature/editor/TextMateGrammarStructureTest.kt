// Modul: :feature:editor
package com.codeforge.feature.editor

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Strukturprüfung der gebündelten TextMate-Grammatiken. tm4e wirft bei kaputten Regeln erst beim Tokenisieren
 * (ClassCastException) – der Editor zeigt dann stillschweigend gar keine Farben (so war es bei XML:
 * `end`/`name` lagen versehentlich innerhalb von `captures`).
 */
class TextMateGrammarStructureTest {

    private val dir: File = listOf(
        File("src/main/assets/textmate/grammars"),
        File("feature/editor/src/main/assets/textmate/grammars")
    ).first { it.isDirectory }

    private val grammars: List<Pair<String, JsonObject>> =
        dir.listFiles { f -> f.name.endsWith(".tmLanguage.json") }!!
            .sortedBy { it.name }
            .map { it.name to JsonParser.parseString(it.readText()).asJsonObject }

    @Test
    fun `captures only contain capture objects`() {
        val problems = mutableListOf<String>()
        grammars.forEach { (file, root) ->
            walk(root, "") { path, key, value ->
                if (key in CAPTURE_KEYS && value.isJsonObject) {
                    value.asJsonObject.entrySet().forEach { (index, capture) ->
                        if (!capture.isJsonObject) problems += "$file $path/$key/$index"
                    }
                }
            }
        }
        assertTrue("Ungültige captures: $problems", problems.isEmpty())
    }

    @Test
    fun `rules with begin have an end`() {
        val problems = mutableListOf<String>()
        grammars.forEach { (file, root) ->
            walkRules(root, "") { path, rule ->
                if (rule.has("begin") && !rule.has("end") && !rule.has("while")) problems += "$file $path"
            }
        }
        assertTrue("begin ohne end: $problems", problems.isEmpty())
    }

    @Test
    fun `local includes resolve to repository entries`() {
        val problems = mutableListOf<String>()
        grammars.forEach { (file, root) ->
            val repo = root.getAsJsonObject("repository")?.keySet().orEmpty()
            walk(root, "") { path, key, value ->
                if (key == "include" && value.isJsonPrimitive) {
                    val target = value.asString
                    if (target.startsWith("#") && target.substring(1) !in repo) problems += "$file $path -> $target"
                }
            }
        }
        assertTrue("Nicht auflösbare Includes: $problems", problems.isEmpty())
    }

    private fun walk(element: JsonElement, path: String, visit: (String, String, JsonElement) -> Unit) {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                visit(path, key, value)
                walk(value, "$path/$key", visit)
            }
            element.isJsonArray -> element.asJsonArray.forEachIndexed { i, value -> walk(value, "$path[$i]", visit) }
        }
    }

    private fun walkRules(element: JsonElement, path: String, visit: (String, JsonObject) -> Unit) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                visit(path, obj)
                obj.entrySet().forEach { (key, value) -> walkRules(value, "$path/$key", visit) }
            }
            element.isJsonArray -> element.asJsonArray.forEachIndexed { i, value -> walkRules(value, "$path[$i]", visit) }
        }
    }

    private companion object {
        val CAPTURE_KEYS = setOf("captures", "beginCaptures", "endCaptures")
    }
}
