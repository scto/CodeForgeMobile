package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Code-Plugins unter `code-plugins/` müssen mit dem echten Parser gültig sein und API 4 nutzen. */
class CodePluginsValidTest {
    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "code-plugins") }.first { it.isDirectory }

    private val manifests = dir.listFiles { f -> File(f, "plugin.json").isFile }.orEmpty().sortedBy { it.name }
        .associate { it.name to PluginManifestParser.parse(File(it, "plugin.json").readText()).getOrThrow() }

    @Test
    fun `alle Manifeste sind gültig`() {
        assertEquals(7, manifests.size)
        manifests.forEach { (name, m) ->
            assertEquals(name, 4, m.apiVersion)
            assertNotNull(name, m.entryPointClass)
            assertTrue(name, m.id.startsWith("com.codeforge.plugin.$name"))
            assertTrue(name, m.categories.isNotEmpty() && m.tags.isNotEmpty())
            // Code-Plugins gibt es nur per Sideload.
            assertNotNull(name, DeclarativeOnly.violation(listOf("plugin.json", "classes.dex"), m))
        }
    }

    @Test
    fun `Einstiegsklasse existiert im Quelltext`() {
        manifests.forEach { (name, m) ->
            val path = "src/main/kotlin/" + m.entryPointClass!!.replace('.', '/') + ".kt"
            assertTrue("$name: $path", File(dir, "$name/$path").isFile)
        }
    }

    @Test
    fun `Manifest mit Katalogangaben und ungültigen Werten`() {
        val ok = PluginManifestParser.parse(
            """{"id":"a.b","name":"A","version":"1","apiVersion":4,"categories":["languages/jvm/kotlin"],"tags":["Kotlin"," kotlin "],"activationEvents":["onLanguage:kotlin"]}"""
        ).getOrThrow()
        assertEquals(listOf("kotlin"), ok.tags)
        assertEquals(listOf("languages/jvm/kotlin"), ok.categories)
        assertTrue(PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","categories":["Sprachen"]}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","activationEvents":["onBoot"]}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","apiVersion":5}""").isFailure)
    }
}
