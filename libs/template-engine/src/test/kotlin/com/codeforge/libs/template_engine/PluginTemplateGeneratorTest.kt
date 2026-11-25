package com.codeforge.libs.template_engine

import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.PluginTemplateRef
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
        template = ProjectTemplateDescriptor("t", PluginTemplateRef("p", "t", "", "")),
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
        // 5 Compose-Vorlagen (je Kotlin, KTS) + jvm-cli (2 × 2) + mitgeliefert: 8 × (2 Sprachen × 2 DSL) + Compose (Kotlin × 2 DSL)
        // + 13 Plugin-Autoren-Vorlagen (je Kotlin, KTS)
        assertEquals(9 + 34 + 13, projects.size)
        projects.forEach { p ->
            assertTrue(p.name, File(p, "gradlew").canExecute())
            assertTrue(p.name, File(p, "gradle/wrapper/gradle-wrapper.properties").isFile)
            assertTrue(p.name, File(p, "gradle/libs.versions.toml").isFile)
            assertTrue(p.name, File(p, "settings.gradle.kts").isFile || File(p, "settings.gradle").isFile)
            val leftovers = p.walkTopDown()
                .filter { it.isFile && it.extension !in BINARY_EXT }
                // Die Vorlage „Plugin · Projektvorlage“ erzeugt selbst eine Vorlage – deren Platzhalter bleiben stehen.
                .filterNot { it.invariantSeparatorsPath.contains("/plugin/template/") }
                .filterNot { p.name.startsWith("plugin-project-template") && it.name == "README.md" }
                .filter { "{{" in it.path || "{{" in it.readText() }
                .toList()
            assertTrue("${p.name}: $leftovers", leftovers.isEmpty())
            if (p.walkTopDown().any { it.extension in SOURCE_EXT && !it.invariantSeparatorsPath.contains("/plugin/template/") }) {
                assertTrue(p.name, p.walkTopDown().any { it.path.contains("com/example/demo") })
            }
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
    fun escapesAndTplSuffix() {
        val tpl = tmp.newFolder("esc")
        File(tpl, "common/{{{packagePath}}}/A.kt").apply { parentFile.mkdirs() }.writeText("{{{packageName}}} {{packageName}}")
        File(tpl, "common/run.sh.tpl").writeText("echo {{projectName}}")
        val out = tmp.newFolder("escout")
        generator.generate(request(ProjectLanguage.KOTLIN, true, "X"), tpl, out)
        assertEquals("{{packageName}} com.example.demo", File(out, "{{packagePath}}/A.kt").readText())
        assertEquals("echo X", File(out, "run.sh").readText())
    }

    @Test
    fun pluginAuthorTemplatesPack() {
        val src = File(repoRoot, "project-templates/src")
        val authors = src.listFiles().orEmpty().filter { it.name.startsWith("com.codeforge.plugin.plugin-") }
        assertEquals(13, authors.size)
        authors.forEach { dir ->
            val out = tmp.newFolder(dir.name)
            generator.generate(request(ProjectLanguage.KOTLIN, true, "MyPlugin").copy(packageName = "com.example.myplugin"), File(dir, "template"), out)
            val manifest = listOf("plugin/plugin.json", "plugin.json").map { File(out, it) }.first { it.isFile }
            val m = PluginManifestParser.parse(manifest.readText()).getOrThrow()
            assertEquals(dir.name, "com.example.myplugin", m.id)
            assertEquals(dir.name, "MyPlugin", m.name)
            assertTrue(dir.name, out.walkTopDown().none { it.name.endsWith(".tpl") })
        }
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

    @Test
    fun builtinsMatchBundledList() {
        val src = File(repoRoot, "project-templates/src")
        val bundled = src.listFiles().orEmpty().map { it.name }.filter { it.startsWith("com.codeforge.plugin.android-") }.sorted()
        assertEquals(BuiltinTemplates.IDS.sorted(), bundled)
        for (id in BuiltinTemplates.IDS) {
            val m = PluginManifestParser.parse(File(src, "$id/plugin.json").readText()).getOrThrow()
            val t = m.contributes.projectTemplates.single()
            assertTrue(id, t.l10n["en"]?.label?.isNotBlank() == true)
            assertTrue(id, File(src, "$id/${t.icon}").isFile)
        }
    }

    @Test
    fun nativeTemplateUsesJniSymbol() {
        val src = File(repoRoot, "project-templates/src/com.codeforge.plugin.android-native-cpp.template/template")
        val out = tmp.newFolder("cpp")
        generator.generate(request(ProjectLanguage.KOTLIN, true, "X").copy(packageName = "com.my_app.demo"), src, out)
        val cpp = out.walkTopDown().first { it.extension == "cpp" }.readText()
        assertTrue(cpp, cpp.contains("Java_com_my_1app_demo_MainActivity_sayHello"))
    }

    @Test
    fun seedingRespectsUninstall() {
        assertTrue(BuiltinTemplateInstaller.needsInstall(null, null, "1.0.0"))
        assertFalse(BuiltinTemplateInstaller.needsInstall(null, "1.0.0", "1.0.0"))
        assertFalse(BuiltinTemplateInstaller.needsInstall("1.0.0", null, "1.0.0"))
        assertFalse(BuiltinTemplateInstaller.needsInstall("1.0.0", null, "1.1.0"))
        assertTrue(BuiltinTemplateInstaller.needsInstall("1.0.0", "1.0.0", "1.1.0"))
        assertFalse(BuiltinTemplateInstaller.needsInstall("1.0.0", "2.0.0", "1.1.0"))
    }

    private companion object {
        val SOURCE_EXT = setOf("kt", "java")
        val BINARY_EXT = setOf("jar", "png", "webp")
    }
}
