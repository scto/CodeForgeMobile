package com.codeforge.core.data

import com.codeforge.core.data.repository.PluginToolRunnerImpl
import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.extension.RequirementManager
import com.codeforge.core.domain.extension.ToolRunEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginToolRunnerImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun runner(toolJson: String, requires: String = "[]"): PluginToolRunnerImpl {
        val manifest = PluginManifestParser.parse(
            """{"id":"p.test","name":"T","version":"1","apiVersion":2,"permissions":["shell"],
               "contributes":{"tools":[$toolJson],"requires":$requires}}"""
        ).getOrThrow()
        val flow = MutableStateFlow(ActiveContributions.of(listOf(manifest to "/plugins/p.test")))
        return PluginToolRunnerImpl(object : ContributionRepository {
            override val contributions: StateFlow<ActiveContributions> = flow
        })
    }

    @Test
    fun `Ausgabe wird gestreamt und Exit-Code gemeldet`() = runBlocking {
        val r = runner("""{"id":"t","command":["sh","-c","echo hallo; echo \"in=$0\"; exit 3","${'$'}{input}"],"timeoutSeconds":10}""")
        val events = r.run("p.test", "t", tmp.root.absolutePath, "x y").toList()
        val lines = events.filterIsInstance<ToolRunEvent.Line>().map { it.text }
        assertTrue(lines.toString(), lines.contains("hallo"))
        assertTrue(lines.toString(), lines.contains("in=x y")) // Eingabe bleibt EIN Argument
        assertEquals(3, (events.last() as ToolRunEvent.Finished).exitCode)
    }

    @Test
    fun `Timeout beendet den Prozess`() = runBlocking {
        val r = runner("""{"id":"t","command":["sleep","30"],"timeoutSeconds":1}""")
        val fin = r.run("p.test", "t", tmp.root.absolutePath, "").toList().last() as ToolRunEvent.Finished
        assertTrue(fin.timedOut)
    }

    @Test
    fun `unbekanntes Programm und Werkzeug liefern Fehler statt Absturz`() = runBlocking {
        val r = runner("""{"id":"t","command":["gibt-es-nicht-xyz"]}""")
        assertEquals(127, (r.run("p.test", "t", tmp.root.absolutePath, "").toList().last() as ToolRunEvent.Finished).exitCode)
        assertEquals(-1, (r.run("p.test", "nope", tmp.root.absolutePath, "").toList().last() as ToolRunEvent.Finished).exitCode)
    }

    @Test
    fun `fehlende Voraussetzungen werden erkannt`() = runBlocking {
        val r = runner(
            """{"id":"t","command":["sh"]}""",
            """[{"manager":"pkg","name":"sh"},{"manager":"pkg","name":"gibt-es-nicht-xyz"}]"""
        )
        val missing: List<RequirementContribution> = r.missingRequirements("p.test")
        assertEquals(listOf("gibt-es-nicht-xyz"), missing.map { it.name })
        assertEquals(RequirementManager.PKG, missing.single().manager)
    }
}
