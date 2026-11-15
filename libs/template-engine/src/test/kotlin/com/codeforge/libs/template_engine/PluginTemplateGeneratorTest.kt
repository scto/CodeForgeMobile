package com.codeforge.libs.template_engine

import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.ProjectTemplateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Erzeugt Projekte aus den echten Plugin-Vorlagen unter `project-templates/src`. */
class PluginTemplateGeneratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "project-templates").isDirectory }
    private val assets = File(repoRoot, "libs/template-engine/src/main/assets")
    private val generator = PluginTemplateGenerator(DirectoryAssetSource(assets))

    // Zum Bauen außerhalb der Tests: CF_TEMPLATE_OUT=<ordner> behält die erzeugten Projekte.
    private val keepDir = System.getenv("CF_TEMPLATE_OUT")?.let(::File)

    private fun request(lang: ProjectLanguage, kts: Boolean, name: String) = ProjectRequest(
        template = ProjectTemplateDescriptor("t", ProjectTemplateKind.PLUGIN),
        projectName = name,
        packageName = "com.example.demo",
        targetDir = tmp.root.path,
        minSdk = 26,
        language = lang,
        useKotlinDsl = kts
    )

    private fun generateAll(): List<File> {
        val src = File(repoRoot, "project-templates/src")
        return src.listFiles().orEmpty().filter { File(it, "plugin.json").isFile }.flatMap { plugin ->
            val manifest = PluginManifestParser.parse(File(plugin, "plugin.json").readText()).getOrThrow()
            manifest.contributes.projectTemplates.flatMap { t ->
                t.languages.flatMap { lang ->
                    t.gradleDsl.map { dsl ->
                        val language = if (lang == "java") ProjectLanguage.JAVA else ProjectLanguage.KOTLIN
                        val name = "${t.id}-$lang-$dsl"
                        val out = File(keepDir ?: tmp.root, name).apply { deleteRecursively(); mkdirs() }
                        generator.generate(request(language, dsl == "kts", name), File(plugin, t.path), out)
                        out
                    }
                }
            }
        }
    }

    @Test
    fun everyCombinationIsComplete() {
        val projects = generateAll()
        assertEquals(7, projects.size) // compose-clean, compose-drawer, compose-bottomnav (je Kotlin, KTS) + jvm-cli (2 Sprachen x 2 DSL)
        projects.forEach { p ->
            assertTrue(p.name, File(p, "gradlew").canExecute())
            assertTrue(p.name, File(p, "gradle/wrapper/gradle-wrapper.properties").isFile)
            assertTrue(p.name, File(p, "gradle/libs.versions.toml").isFile)
            assertTrue(p.name, File(p, "settings.gradle.kts").isFile || File(p, "settings.gradle").isFile)
            val leftovers = p.walkTopDown()
                .filter { it.isFile && it.name != "gradle-wrapper.jar" }
                .filter { "{{" in it.path || "{{" in it.readText() }
                .toList()
            assertTrue("${p.name}: $leftovers", leftovers.isEmpty())
            assertTrue(p.name, p.walkTopDown().any { it.path.contains("com/example/demo") })
        }
    }

    @Test
    fun layersOverrideInOrder() {
        val tpl = tmp.newFolder("tpl")
        File(tpl, "common/a.txt").apply { parentFile.mkdirs() }.writeText("common")
        File(tpl, "kotlin/a.txt").apply { parentFile.mkdirs() }.writeText("kotlin {{packageName}}")
        File(tpl, "kotlin-kts/b.txt").apply { parentFile.mkdirs() }.writeText("{{minSdk}} {{unknown}}")
        val out = tmp.newFolder("out")
        generator.generate(request(ProjectLanguage.KOTLIN, true, "X"), tpl, out)
        assertEquals("kotlin com.example.demo", File(out, "a.txt").readText())
        assertEquals("26 {{unknown}}", File(out, "b.txt").readText())
    }

    @Test
    fun binaryFilesAreCopiedUnchanged() {
        val tpl = tmp.newFolder("bin")
        val bytes = byteArrayOf(0, 1, 2, '{'.code.toByte(), '{'.code.toByte(), -1)
        File(tpl, "common/x.bin").apply { parentFile.mkdirs() }.writeBytes(bytes)
        val out = tmp.newFolder("binout")
        generator.generate(request(ProjectLanguage.KOTLIN, true, "X"), tpl, out)
        assertTrue(File(out, "x.bin").readBytes().contentEquals(bytes))
    }

    @Test
    fun missingLayersFail() {
        val tpl = tmp.newFolder("empty")
        val result = runCatching { generator.generate(request(ProjectLanguage.KOTLIN, true, "X"), tpl, tmp.newFolder("o")) }
        assertFalse(result.isSuccess)
    }
}
