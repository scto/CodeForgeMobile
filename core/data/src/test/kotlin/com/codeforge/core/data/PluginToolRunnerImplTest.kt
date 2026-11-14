package com.codeforge.core.data

import com.codeforge.core.common.io.FileDownloader
import com.codeforge.core.common.io.Sha256Utils
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
import java.io.File

class PluginToolRunnerImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private val jar = "fake-jar".toByteArray()
    private val jarSha = Sha256Utils.calculateSha256(jar)
    private val tool = """{"id":"t","command":["true"]}"""

    private val serving = FileDownloader { _, dest, onProgress ->
        dest.parentFile?.mkdirs()
        dest.writeBytes(jar)
        onProgress(jar.size.toLong(), jar.size.toLong())
        Result.success(dest)
    }

    private fun downloadReq(sha: String = jarSha) =
        """[{"manager":"download","name":"lemminx","url":"https://download.eclipse.org/x.jar","sha256":"$sha",""" +
            """"archive":"file","check":"lemminx.jar"}]"""

    private fun runner(
        toolJson: String,
        requires: String = "[]",
        downloader: FileDownloader = FileDownloader { _, _, _ -> Result.failure(java.io.IOException("offline")) }
    ): PluginToolRunnerImpl {
        val manifest = PluginManifestParser.parse(
            """{"id":"p.test","name":"T","version":"1","apiVersion":2,"permissions":["shell"],""" +
                """"contributes":{"tools":[$toolJson],"requires":$requires}}"""
        ).getOrThrow()
        val flow = MutableStateFlow(ActiveContributions.of(listOf(manifest to "/plugins/p.test")))
        return PluginToolRunnerImpl(
            object : ContributionRepository {
                override val contributions: StateFlow<ActiveContributions> = flow
            },
            downloader,
            File(tmp.root, "deps")
        )
    }

    @Test
    fun downloadIsVerifiedAndFound() = runBlocking {
        val r = runner(tool, downloadReq(), serving)
        val missing = r.missingRequirements("p.test")
        assertEquals(listOf("lemminx"), missing.map { it.name })
        val events = r.install(missing).toList()
        assertEquals(0, (events.last() as ToolRunEvent.Finished).exitCode)
        val file = File(tmp.root, "deps/lemminx/lemminx.jar")
        assertTrue(file.exists() && file.canExecute())
        assertTrue(r.missingRequirements("p.test").isEmpty())
    }

    @Test
    fun wrongShaLeavesNothing() = runBlocking {
        val r = runner(tool, downloadReq("0".repeat(64)), serving)
        val events = r.install(r.missingRequirements("p.test")).toList()
        assertEquals(1, (events.last() as ToolRunEvent.Finished).exitCode)
        assertTrue(File(tmp.root, "deps").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun uninstallReleasesDownload() = runBlocking {
        val r = runner(tool, downloadReq(), serving)
        r.install(r.missingRequirements("p.test")).toList()
        r.releaseDownloads("p.test")
        assertTrue(!File(tmp.root, "deps/lemminx").exists())
    }

    @Test
    fun newShaCountsAsMissing() = runBlocking {
        runner(tool, downloadReq(), serving).let { it.install(it.missingRequirements("p.test")).toList() }
        val updated = runner(tool, downloadReq("a".repeat(64)), serving)
        assertEquals(1, updated.missingRequirements("p.test").size)
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
