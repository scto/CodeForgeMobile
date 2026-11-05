package com.codeforge.feature.agent

import com.codeforge.core.domain.model.DiagnosticSeverity
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDiagnosticsStoreTest {
    private val root = File("/p")
    private fun diag(line: Int, sev: DiagnosticSeverity, msg: String) =
        LspDiagnostic(LspRange(LspPosition(line, 0), LspPosition(line, 1)), sev, msg)

    @Test
    fun `liefert relative Pfade, 1-basierte Zeilen und blendet Hinweise aus`() {
        val store = AgentDiagnosticsStore()
        store.update("/p/src/A.kt", listOf(diag(4, DiagnosticSeverity.ERROR, "e"), diag(0, DiagnosticSeverity.HINT, "h")))
        val out = store.query(root, null)
        assertEquals(1, out.size)
        assertEquals("src/A.kt", out[0].path)
        assertEquals(5, out[0].line)
    }

    @Test
    fun `filtert nach Datei oder Ordner und ignoriert fremde Projekte`() {
        val store = AgentDiagnosticsStore()
        store.update("/p/src/A.kt", listOf(diag(0, DiagnosticSeverity.WARNING, "w")))
        store.update("/p/other/B.kt", listOf(diag(0, DiagnosticSeverity.ERROR, "e")))
        store.update("/q/C.kt", listOf(diag(0, DiagnosticSeverity.ERROR, "x")))
        assertEquals(listOf("src/A.kt"), store.query(root, "src/A.kt").map { it.path })
        assertEquals(listOf("src/A.kt"), store.query(root, "src").map { it.path })
        assertEquals(2, store.query(root, null).size)
    }

    @Test
    fun `leere Liste entfernt die Datei, Limit wird eingehalten`() {
        val store = AgentDiagnosticsStore()
        store.update("/p/A.kt", listOf(diag(0, DiagnosticSeverity.ERROR, "e")))
        store.update("/p/A.kt", emptyList())
        assertTrue(store.query(root, null).isEmpty())
        store.update("/p/B.kt", List(5) { diag(it, DiagnosticSeverity.ERROR, "e$it") })
        assertEquals(3, store.query(root, null, limit = 3).size)
    }
}
