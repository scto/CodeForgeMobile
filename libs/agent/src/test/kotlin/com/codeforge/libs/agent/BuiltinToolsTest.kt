package com.codeforge.libs.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinToolsTest {
    private val ws = FakeWorkspace(
        mapOf(
            "src/Main.kt" to "fun main() {\n    println(\"a\")\n    println(\"a\")\n}\n",
            "src/util/Util.kt" to "object Util\n",
            "README.md" to "Titel\nzweite Zeile\n",
        ),
    )
    private val todos = TodoStore()
    private val tools = BuiltinTools.create(ws, todos).associateBy { it.name }
    private suspend fun run(name: String, vararg a: Pair<String, String>) = tools.getValue(name).execute(args(*a))

    @Test
    fun `read_file liest ganz und zeilenweise`() = runTest {
        assertEquals("Titel\nzweite Zeile\n", run("read_file", "path" to "README.md").content)
        val range = tools.getValue("read_file").execute(buildJsonObject { put("path", "README.md"); put("start_line", 2); put("end_line", 2) })
        assertEquals("zweite Zeile", range.content)
        assertTrue(run("read_file", "path" to "fehlt.txt").isError)
    }

    @Test
    fun `list_dir sortiert Ordner zuerst`() = runTest {
        assertEquals("src/\nREADME.md", run("list_dir").content)
        assertEquals("util/\nMain.kt", run("list_dir", "path" to "src").content)
        assertTrue(run("list_dir", "path" to "nix").isError)
    }

    @Test
    fun `search_text findet Treffer mit Zeile`() = runTest {
        assertEquals("src/Main.kt:2:     println(\"a\")\nsrc/Main.kt:3:     println(\"a\")".replace(":     ", ": "), run("search_text", "query" to "PRINTLN").content.replace(":     ", ": "))
        assertEquals("Keine Treffer", run("search_text", "query" to "xyz").content)
    }

    @Test
    fun `edit_file verlangt eindeutigen Treffer`() = runTest {
        val ambiguous = run("edit_file", "path" to "src/Main.kt", "old_string" to "println(\"a\")", "new_string" to "println(\"b\")")
        assertTrue(ambiguous.isError)
        assertTrue(ambiguous.content.contains("2-mal"))

        val ok = run("edit_file", "path" to "src/Main.kt", "old_string" to "fun main()", "new_string" to "fun start()")
        assertFalse(ok.isError)
        assertTrue(ws.files.getValue("src/Main.kt").startsWith("fun start()"))

        val all = tools.getValue("edit_file").execute(
            buildJsonObject { put("path", "src/Main.kt"); put("old_string", "println(\"a\")"); put("new_string", "println(\"b\")"); put("replace_all", true) },
        )
        assertFalse(all.isError)
        assertEquals(2, Regex("\"b\"").findAll(ws.files.getValue("src/Main.kt")).count())

        assertTrue(run("edit_file", "path" to "src/Main.kt", "old_string" to "gibt es nicht", "new_string" to "x").isError)
        assertTrue(run("edit_file", "path" to "fehlt.kt", "old_string" to "a", "new_string" to "b").isError)
    }

    @Test
    fun `create_file ueberschreibt nur mit overwrite`() = runTest {
        assertFalse(run("create_file", "path" to "neu/A.kt", "content" to "x").isError)
        assertEquals("x", ws.files["neu/A.kt"])
        assertTrue(run("create_file", "path" to "neu/A.kt", "content" to "y").isError)
        assertEquals("x", ws.files["neu/A.kt"])
        val over = tools.getValue("create_file").execute(buildJsonObject { put("path", "neu/A.kt"); put("content", "y"); put("overwrite", true) })
        assertFalse(over.isError)
        assertEquals("y", ws.files["neu/A.kt"])
    }

    @Test
    fun `delete_path loescht und schuetzt den Projektstamm`() = runTest {
        assertFalse(run("delete_path", "path" to "src/util").isError)
        assertFalse(ws.files.containsKey("src/util/Util.kt"))
        assertTrue(run("delete_path", "path" to "gibt/es/nicht").isError)
        assertThrows(ToolArgumentException::class.java) { kotlinx.coroutines.runBlocking { run("delete_path", "path" to ".") } }
    }

    @Test
    fun `Pfade werden normalisiert und begrenzt`() {
        assertEquals("a/b.kt", normalizeProjectPath("./a//b.kt"))
        assertEquals("a/b.kt", normalizeProjectPath("a\\b.kt"))
        assertEquals("", normalizeProjectPath("."))
        assertThrows(ToolArgumentException::class.java) { normalizeProjectPath("/etc/passwd") }
        assertThrows(ToolArgumentException::class.java) { normalizeProjectPath("C:/x") }
        assertThrows(ToolArgumentException::class.java) { normalizeProjectPath("a/../../b") }
    }

    @Test
    fun `get_diagnostics formatiert`() = runTest {
        assertEquals("Keine Diagnosen", run("get_diagnostics").content)
        ws.diagnosticsResult = listOf(AgentDiagnostic("src/Main.kt", 3, DiagnosticSeverity.ERROR, "Unresolved reference: foo"))
        assertEquals("src/Main.kt:3: error: Unresolved reference: foo", run("get_diagnostics", "path" to "src/Main.kt").content)
    }

    @Test
    fun `todo_write speichert die Liste`() = runTest {
        val a: JsonObject = buildJsonObject {
            put("todos", JsonArray(listOf(
                buildJsonObject { put("content", "A"); put("status", "done") },
                buildJsonObject { put("content", "B"); put("status", "in_progress") },
            )))
        }
        val res = tools.getValue("todo_write").execute(a)
        assertEquals("Aufgabenliste aktualisiert (1/2 erledigt)", res.content)
        assertEquals(listOf(TodoStore.Item("A", "done"), TodoStore.Item("B", "in_progress")), todos.items())
        assertThrows(ToolArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                tools.getValue("todo_write").execute(buildJsonObject { put("todos", JsonArray(listOf(buildJsonObject { put("content", "x"); put("status", "kaputt") }))) })
            }
        }
    }

    @Test
    fun `Schemas sind gueltige Objekt-Schemas`() {
        for (t in tools.values) {
            assertEquals(JsonPrimitive("object"), t.parameters["type"])
            assertTrue("${t.name} ohne properties", t.parameters.containsKey("properties"))
            assertTrue(t.description.isNotBlank())
        }
        assertEquals(8, tools.size)
    }
}
