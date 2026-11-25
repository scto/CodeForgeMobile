package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prüft `contributes.projectTemplates` und die Vorlagen-Plugins unter `project-templates/src`. */
class ProjectTemplatePluginsTest {

    private val src: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "project-templates/src") }
        .first { it.isDirectory }

    private fun parse(templates: String) = PluginManifestParser.parse(
        """{"id":"p.t","name":"T","version":"1","apiVersion":2,"contributes":{"projectTemplates":$templates}}"""
    )

    @Test
    fun defaultsAreKotlinKtsAndroid() {
        val t = parse("""[{"id":"a","label":"A","path":"tpl"}]""").getOrThrow().contributes.projectTemplates.single()
        assertEquals(listOf("kotlin"), t.languages)
        assertEquals(listOf("kts"), t.gradleDsl)
        assertTrue(t.android)
        assertNull(t.icon)
    }

    @Test
    fun l10nIsParsed() {
        val t = parse("""[{"id":"a","label":"Leer","path":"t","l10n":{"en":{"label":"Empty"}}}]""").getOrThrow()
            .contributes
            .projectTemplates
            .single()
        assertEquals("Empty", t.l10n.getValue("en").label)
        assertTrue(parse("""[{"id":"a","label":"A","path":"t","l10n":{"EN!":{"label":"x"}}}]""").isFailure)
    }

    @Test
    fun invalidTemplatesAreRejected() {
        assertTrue(parse("""[{"id":"a","label":"A","path":"../x"}]""").isFailure)
        assertTrue(parse("""[{"id":"a","label":"A","path":"t","languages":["rust"]}]""").isFailure)
        assertTrue(parse("""[{"id":"a","label":"A","path":"t","gradleDsl":["maven"]}]""").isFailure)
        assertTrue(parse("""[{"id":"a","label":"A","path":"t","minSdk":5}]""").isFailure)
        assertTrue(parse("""[{"id":"a","label":"A","path":"t"},{"id":"a","label":"B","path":"u"}]""").isFailure)
    }

    @Test
    fun shippedTemplatePluginsAreValid() {
        val plugins = src.listFiles().orEmpty().filter { File(it, "plugin.json").isFile }
        assertEquals(28, plugins.size) // 6 aus dem Store + 9 mitgelieferte (android-*) + 13 für Plugin-Autoren (plugin-*)
        plugins.forEach { dir ->
            val m = PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
            assertEquals(dir.name, m.id)
            assertTrue(m.id, m.id.endsWith(".template"))
            val files = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toList()
            assertNull(m.id, DeclarativeOnly.violation(files, m))
            assertTrue(m.contributes.kinds().contains("projectTemplates"))
            m.contributes.projectTemplates.forEach { t -> checkTemplate(dir, t) }
        }
    }

    // Jede wählbare Kombination braucht eine Build-Datei (gemeinsam, je DSL oder je Kombination).
    private fun checkTemplate(dir: File, t: ProjectTemplateContribution) {
        val root = File(dir, t.path)
        assertTrue(t.id, File(root, "common").isDirectory)
        t.icon?.let { assertTrue(it, File(dir, it).isFile) }
        val combos = t.languages.flatMap { lang -> t.gradleDsl.map { dsl -> lang to dsl } }
        combos.forEach { (lang, dsl) ->
            val build = if (dsl == "kts") "build.gradle.kts" else "build.gradle"
            val layers = listOf("common", lang, dsl, "$lang-$dsl")
            assertTrue("${t.id} $lang/$dsl", layers.any { File(root, "$it/$build").isFile })
        }
    }
}
