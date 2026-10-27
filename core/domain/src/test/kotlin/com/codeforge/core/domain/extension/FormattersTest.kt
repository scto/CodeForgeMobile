package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {
    private fun manifest(formatters: String) = """
        {"id":"p","name":"P","version":"1","apiVersion":2,
         "contributes":{"formatters":[$formatters]}}
    """.trimIndent()

    @Test
    fun `Formatierer werden geparst`() {
        val m = PluginManifestParser.parse(
            manifest("""{"id":"prettier","label":"Prettier","extensions":[".js","ts"],"fileNames":["Justfile"],"command":["prettier","--stdin-filepath","${'$'}{file}"],"timeoutSeconds":30}""")
        ).getOrThrow()
        val f = m.contributes.formatters.single()
        assertEquals(listOf("js", "ts"), f.extensions)
        assertEquals(30, f.timeoutSeconds)
        assertTrue("formatters" in m.contributes.kinds())
        assertTrue(!m.contributes.isEmpty)
    }

    @Test
    fun `ungueltige Formatierer werden abgelehnt`() {
        assertTrue(PluginManifestParser.parse(manifest("""{"id":"x","extensions":["a"],"command":[]}""")).isFailure)
        assertTrue(PluginManifestParser.parse(manifest("""{"id":"x","command":["a"]}""")).isFailure)
        assertTrue(PluginManifestParser.parse(manifest("""{"id":"x","extensions":["a"],"command":["a"],"timeoutSeconds":0}""")).isFailure)
    }

    @Test
    fun `Zuordnung nach Endung und Dateiname, Platzhalter`() {
        val f = FormatterContribution("f", "F", listOf("sh"), listOf("Justfile"), listOf("shfmt", "-filename", "${'$'}{file}", "${'$'}{pluginDir}"))
        val active = ActiveContributions(formatters = listOf(Resolved("p", "/plug", f)))
        assertEquals("f", active.formatterForPath("/w/run.SH")?.item?.id)
        assertEquals("f", active.formatterForPath("/w/justfile")?.item?.id)
        assertNull(active.formatterForPath("/w/readme.md"))
        assertNull(active.formatterForPath("/w/noext"))
        assertEquals(listOf("shfmt", "-filename", "/w/run.sh", "/plug"), f.expandCommand("/w/run.sh", "/plug", "/home"))
    }
}
