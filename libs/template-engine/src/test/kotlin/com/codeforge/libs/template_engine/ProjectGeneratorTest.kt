package com.codeforge.libs.template_engine

import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.ProjectTemplateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Läuft gegen die echten Assets (`src/main/assets`), Arbeitsverzeichnis = Modulverzeichnis. */
class ProjectGeneratorTest {

    private val assetRoot = listOf("src/main/assets", "libs/template-engine/src/main/assets")
        .map(::File).first { it.isDirectory }
    private val generator = ProjectGenerator(DirectoryAssetSource(assetRoot))

    private fun gen(kind: ProjectTemplateKind, lang: ProjectLanguage, kts: Boolean): File {
        val out = File.createTempFile("pwgen", "").apply { delete(); mkdirs() }
        val dir = File(out, "Demo").apply { mkdirs() }
        val req = ProjectRequest(
            ProjectTemplateDescriptor(kind.name.lowercase(), kind, kind != ProjectTemplateKind.COMPOSE_ACTIVITY),
            "Demo", "com.example.demo", out.path, language = lang, useKotlinDsl = kts
        )
        val handle = generator.generate(req, dir)
        assertEquals(dir.path, handle.rootPath)
        return dir
    }

    private fun File.rel(p: String) = File(this, p)
    private fun File.sources(): List<File> = walkTopDown().filter { it.extension in setOf("kt", "java") }.toList()

    @Test fun everyCombinationProducesCoreFiles() {
        for (kind in ProjectTemplateKind.values()) for (lang in ProjectLanguage.values()) for (kts in listOf(true, false)) {
            if (kind == ProjectTemplateKind.COMPOSE_ACTIVITY && lang == ProjectLanguage.JAVA) continue
            val d = gen(kind, lang, kts)
            val ext = if (kts) ".kts" else ""
            val tag = "$kind/$lang/kts=$kts"
            for (f in listOf("settings.gradle$ext", "build.gradle$ext", "app/build.gradle$ext", "gradle.properties",
                "gradlew", "gradle/wrapper/gradle-wrapper.properties", "app/src/main/AndroidManifest.xml",
                "app/src/main/res/values/strings.xml", "app/src/main/res/values/themes.xml")) {
                assertTrue("$tag: $f fehlt", d.rel(f).isFile)
            }
            val manifest = d.rel("app/src/main/AndroidManifest.xml").readText()
            assertFalse("$tag: package-Attribut im Manifest", manifest.contains("package="))
            if (kind != ProjectTemplateKind.NO_ACTIVITY) {
                assertTrue("$tag: keine Quelltexte", d.rel("app/src/main").sources().isNotEmpty())
            }
        }
    }

    @Test fun languageDecidesSourceExtension() {
        val kt = gen(ProjectTemplateKind.EMPTY_ACTIVITY, ProjectLanguage.KOTLIN, true)
        val java = gen(ProjectTemplateKind.EMPTY_ACTIVITY, ProjectLanguage.JAVA, true)
        assertTrue(kt.rel("app/src/main").sources().all { it.extension == "kt" })
        assertTrue(java.rel("app/src/main").sources().all { it.extension == "java" })
    }

    @Test fun noActivityHasNoSources() {
        val d = gen(ProjectTemplateKind.NO_ACTIVITY, ProjectLanguage.KOTLIN, true)
        assertTrue(d.rel("app/src/main").sources().isEmpty())
    }

    @Test fun layoutsReferencedByCodeExist() {
        val kinds = listOf(
            ProjectTemplateKind.EMPTY_ACTIVITY, ProjectTemplateKind.BASIC_ACTIVITY, ProjectTemplateKind.NAV_DRAWER_ACTIVITY,
            ProjectTemplateKind.BOTTOM_NAV_ACTIVITY, ProjectTemplateKind.TABBED_ACTIVITY, ProjectTemplateKind.CPP_ACTIVITY,
            ProjectTemplateKind.NO_ANDROIDX_ACTIVITY
        )
        for (kind in kinds) for (lang in ProjectLanguage.values()) {
            val d = gen(kind, lang, true)
            val res = d.rel("app/src/main/res")
            val code = d.rel("app/src/main").sources().joinToString("\n") { it.readText() }
            // R.layout.x und R.menu.x / R.navigation.x
            for (m in Regex("""R\.(layout|menu|navigation)\.([a-z0-9_]+)""").findAll(code)) {
                val (type, name) = m.destructured
                assertTrue("$kind/$lang: R.$type.$name ohne Datei", File(res, "$type/$name.xml").isFile)
            }
            // Binding-Klassen FooBarBinding -> layout/foo_bar.xml
            for (m in Regex("""\b([A-Z][A-Za-z0-9]*)Binding\b""").findAll(code)) {
                val snake = m.groupValues[1].replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
                assertTrue("$kind/$lang: ${m.value} ohne layout/$snake.xml", File(res, "layout/$snake.xml").isFile)
            }
        }
    }

    @Test fun stringReferencesResolve() {
        for (kind in ProjectTemplateKind.values()) {
            val d = gen(kind, ProjectLanguage.KOTLIN, true)
            val res = d.rel("app/src/main/res")
            val strings = res.walkTopDown().filter { it.name.startsWith("strings") && it.extension == "xml" }
                .joinToString("\n") { it.readText() }
            val names = Regex("""<string name="([^"]+)"""").findAll(strings).map { it.groupValues[1] }.toSet()
            val users = d.rel("app/src/main").walkTopDown().filter { it.extension in setOf("kt", "java", "xml") }
                .joinToString("\n") { it.readText() }
            // Strings, die Material/AndroidX selbst liefern
            val library = setOf("appbar_scrolling_view_behavior")
            for (m in Regex("""(?:@string/|R\.string\.)([A-Za-z0-9_]+)""").findAll(users)) {
                if (m.groupValues[1] in library) continue
                assertTrue("$kind: string ${m.groupValues[1]} undefiniert", m.groupValues[1] in names)
            }
        }
    }

    @Test fun stringsXmlEscapesAppName() {
        val xml = buildStringsXml("A&B <x>")
        assertTrue(xml.contains("A&amp;B &lt;x&gt;"))
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
    }
}
