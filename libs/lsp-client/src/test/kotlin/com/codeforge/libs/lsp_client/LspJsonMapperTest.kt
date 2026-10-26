// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.DiagnosticSeverity
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspFileOperation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspSymbolKind
import com.codeforge.core.domain.model.LspTextEdit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspJsonMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `lspPositionToCharOffset handles ascii lines`() {
        val text = "line 0\nline 1\nline 2"
        assertEquals(0, lspPositionToCharOffset(text, LspPosition(0, 0)))
        assertEquals(5, lspPositionToCharOffset(text, LspPosition(0, 5)))
        assertEquals(7, lspPositionToCharOffset(text, LspPosition(1, 0)))
        assertEquals(14, lspPositionToCharOffset(text, LspPosition(2, 0)))
    }

    @Test
    fun `lspPositionToCharOffset correctly handles german umlauts`() {
        val text = "Schöne Grüße\nÜberall Äpfel"
        // "Schöne Grüße\n": 'S'(0), 'c'(1), 'h'(2), 'ö'(3), 'n'(4), 'e'(5), ' '(6), 'G'(7), 'r'(8), 'ü'(9), 'ß'(10), 'e'(11), '\n'(12)
        assertEquals(3, lspPositionToCharOffset(text, LspPosition(0, 3)))
        assertEquals(9, lspPositionToCharOffset(text, LspPosition(0, 9)))
        // Zeile 1: 'Ü'(0, offset 13), 'b'(1, offset 14)... 'Ä'(8, offset 21)
        assertEquals(13, lspPositionToCharOffset(text, LspPosition(1, 0)))
        assertEquals(21, lspPositionToCharOffset(text, LspPosition(1, 8)))
    }

    @Test
    fun `lspPositionToCharOffset correctly handles emojis and surrogate pairs in UTF-16`() {
        // 🌍 ist ein Surrogate-Pair (\uD83C\uDF0D) mit UTF-16 Länge 2
        val text = "Hello 🌍 World\nEmoji 😀 end"
        // 'H'(0), 'e'(1), 'l'(2), 'l'(3), 'o'(4), ' '(5), 🌍(6, 7), ' '(8), 'W'(9)
        val posAfterGlobe = LspPosition(0, 8)
        val offset = lspPositionToCharOffset(text, posAfterGlobe)
        assertEquals(8, offset)
        assertEquals(' ', text[offset])
        assertEquals('W', text[offset + 1])

        // Zeile 1: 'E'(0), 'm'(1), 'o'(2), 'j'(3), 'i'(4), ' '(5), 😀(6, 7), ' '(8)
        val line1Start = text.indexOf('\n') + 1
        assertEquals(line1Start + 8, lspPositionToCharOffset(text, LspPosition(1, 8)))
    }

    @Test
    fun `applyTextEditsToString applies single edit correctly`() {
        val original = "fun foo() = 42"
        val edits = listOf(
            LspTextEdit(
                range = LspRange(LspPosition(0, 4), LspPosition(0, 7)),
                newText = "bar"
            )
        )
        val result = applyTextEditsToString(original, edits)
        assertEquals("fun bar() = 42", result)
    }

    @Test
    fun `applyTextEditsToString applies multiple non-overlapping edits in correct order`() {
        val original = "val a = 1\nval b = 2"
        val edits = listOf(
            LspTextEdit(LspRange(LspPosition(0, 4), LspPosition(0, 5)), "x"),
            LspTextEdit(LspRange(LspPosition(1, 4), LspPosition(1, 5)), "y")
        )
        val result = applyTextEditsToString(original, edits)
        assertEquals("val x = 1\nval y = 2", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `applyTextEditsToString rejects overlapping edits`() {
        val original = "abcdefghij"
        val edits = listOf(
            LspTextEdit(LspRange(LspPosition(0, 2), LspPosition(0, 6)), "123"),
            LspTextEdit(LspRange(LspPosition(0, 4), LspPosition(0, 8)), "456")
        )
        applyTextEditsToString(original, edits)
    }

    @Test
    fun `parseCodeActions parses full code action with edit and command`() {
        val raw = buildJsonArray {
            add(buildJsonObject {
                put("title", "Extract Function")
                put("kind", "refactor.extract")
                put("isPreferred", true)
                put("diagnostics", buildJsonArray {
                    add(buildJsonObject {
                        put("range", buildJsonObject {
                            put("start", buildJsonObject { put("line", 1); put("character", 0) })
                            put("end", buildJsonObject { put("line", 1); put("character", 5) })
                        })
                        put("severity", 2)
                        put("message", "Unused expression")
                    })
                })
                put("edit", buildJsonObject {
                    put("changes", buildJsonObject {
                        put("file:///test/Main.kt", buildJsonArray {
                            add(buildJsonObject {
                                put("range", buildJsonObject {
                                    put("start", buildJsonObject { put("line", 0); put("character", 0) })
                                    put("end", buildJsonObject { put("line", 0); put("character", 3) })
                                })
                                put("newText", "fun")
                            })
                        })
                    })
                })
                put("command", buildJsonObject {
                    put("title", "Run Extract")
                    put("command", "kotlin.extract")
                })
            })
        }

        val actions = parseCodeActions(raw)
        assertEquals(1, actions.size)
        val action = actions[0]
        assertEquals("Extract Function", action.title)
        assertEquals("refactor.extract", action.kind)
        assertTrue(action.isPreferred)
        assertEquals(1, action.diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, action.diagnostics[0].severity)

        assertNotNull(action.edit)
        assertEquals(1, action.edit!!.changes.size)
        assertNotNull(action.command)
        assertEquals("kotlin.extract", action.command!!.command)
    }

    @Test
    fun `parseDocumentSymbols parses hierarchical and flat symbols`() {
        val hierarchicalRaw = buildJsonArray {
            add(buildJsonObject {
                put("name", "MyClass")
                put("detail", "class MyClass")
                put("kind", 5) // CLASS
                put("range", buildJsonObject {
                    put("start", buildJsonObject { put("line", 0); put("character", 0) })
                    put("end", buildJsonObject { put("line", 10); put("character", 1) })
                })
                put("selectionRange", buildJsonObject {
                    put("start", buildJsonObject { put("line", 0); put("character", 6) })
                    put("end", buildJsonObject { put("line", 0); put("character", 13) })
                })
                put("children", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "myMethod")
                        put("kind", 6) // METHOD
                        put("range", buildJsonObject {
                            put("start", buildJsonObject { put("line", 2); put("character", 4) })
                            put("end", buildJsonObject { put("line", 4); put("character", 5) })
                        })
                        put("selectionRange", buildJsonObject {
                            put("start", buildJsonObject { put("line", 2); put("character", 8) })
                            put("end", buildJsonObject { put("line", 2); put("character", 16) })
                        })
                    })
                })
            })
        }

        val symbols = parseDocumentSymbols(hierarchicalRaw)
        assertEquals(1, symbols.size)
        val cls = symbols[0]
        assertEquals("MyClass", cls.name)
        assertEquals(LspSymbolKind.CLASS, cls.kind)
        assertEquals(1, cls.children.size)
        assertEquals("myMethod", cls.children[0].name)
        assertEquals(LspSymbolKind.METHOD, cls.children[0].kind)
    }

    @Test
    fun `parseWorkspaceEdit parses documentChanges with file operations`() {
        val raw = buildJsonObject {
            put("documentChanges", buildJsonArray {
                add(buildJsonObject {
                    put("kind", "create")
                    put("uri", "file:///test/NewFile.kt")
                })
                add(buildJsonObject {
                    put("kind", "rename")
                    put("oldUri", "file:///test/Old.kt")
                    put("newUri", "file:///test/Renamed.kt")
                })
                add(buildJsonObject {
                    put("kind", "delete")
                    put("uri", "file:///test/Obsolete.kt")
                })
            })
        }

        val edit = parseWorkspaceEdit(raw)
        assertNotNull(edit)
        assertEquals(3, edit!!.documentChanges.size)
        val ops = edit.allFileOperations
        assertEquals(3, ops.size)
        assertTrue(ops[0] is LspFileOperation.CreateFile)
        assertTrue(ops[1] is LspFileOperation.RenameFile)
        assertTrue(ops[2] is LspFileOperation.DeleteFile)
    }

    @Test
    fun `parseServerCapabilities parses boolean and object providers`() {
        val raw = buildJsonObject {
            put("capabilities", buildJsonObject {
                put("codeActionProvider", true)
                put("renameProvider", buildJsonObject { put("prepareProvider", true) })
                put("documentSymbolProvider", true)
                put("definitionProvider", true)
                put("referencesProvider", true)
                put("executeCommandProvider", buildJsonObject { })
            })
        }

        val caps = parseServerCapabilities(raw)
        assertTrue(caps.codeActionProvider)
        assertTrue(caps.renameProvider)
        assertTrue(caps.documentSymbolProvider)
        assertTrue(caps.definitionProvider)
        assertTrue(caps.referencesProvider)
        assertTrue(caps.executeCommandProvider)
    }

    @Test
    fun `parseSignatureHelp liest Signaturen, Parameter-Offsets und aktiven Parameter`() {
        val json = Json.parseToJsonElement(
            """{"signatures":[{"label":"foo(a: Int, b: String)","documentation":{"kind":"markdown","value":"Doku"},
              "parameters":[{"label":[4,10]},{"label":"b: String"}]}],"activeSignature":0,"activeParameter":1}"""
        )
        val help = parseSignatureHelp(json)
        assertNotNull(help)
        assertEquals(1, help!!.activeParameter)
        assertEquals("foo(a: Int, b: String)", help.signatures.single().label)
        assertEquals(listOf("a: Int", "b: String"), help.signatures.single().parameters)
        assertEquals("Doku", help.signatures.single().documentation)
    }

    @Test
    fun `parseSignatureHelp liefert null ohne Signaturen`() {
        assertNull(parseSignatureHelp(Json.parseToJsonElement("""{"signatures":[]}""")))
        assertNull(parseSignatureHelp(Json.parseToJsonElement("null")))
    }

    @Test
    fun `parseServerCapabilities erkennt typeDefinition, implementation und signatureHelp`() {
        val caps = parseServerCapabilities(
            Json.parseToJsonElement(
                """{"capabilities":{"typeDefinitionProvider":true,"implementationProvider":{},"signatureHelpProvider":{"triggerCharacters":["("]}}}"""
            )
        )
        assertTrue(caps.typeDefinitionProvider)
        assertTrue(caps.implementationProvider)
        assertTrue(caps.signatureHelpProvider)
    }
}
