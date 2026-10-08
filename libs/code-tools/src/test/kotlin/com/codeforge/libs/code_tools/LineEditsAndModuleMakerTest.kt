package com.codeforge.libs.code_tools

import com.codeforge.libs.code_tools.edit.CommentStyle
import com.codeforge.libs.code_tools.edit.LineEdits
import com.codeforge.libs.code_tools.module.GradleModulePath
import com.codeforge.libs.code_tools.module.ModuleMaker
import com.codeforge.libs.code_tools.module.ModuleRequest
import com.codeforge.libs.code_tools.module.ModuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LineEditsAndModuleMakerTest {

    private val slash = CommentStyle("//")

    @Test fun toggleCommentAndBack() {
        val t = "a\n  b\n\n  c\nd"
        val e = LineEdits.toggleComment(t, 2, 9, slash)
        assertEquals("a\n  // b\n\n  // c\nd", e.applyTo(t))
        val back = LineEdits.toggleComment(e.applyTo(t), e.selStart, e.selEnd, slash)
        assertEquals(t, back.applyTo(e.applyTo(t)))
    }

    @Test fun cursorFollowsInsertedPrefix() {
        val t = "foo()"
        val e = LineEdits.toggleComment(t, 3, 3, slash)
        assertEquals("// foo()", e.applyTo(t))
        assertEquals(6, e.selStart)
    }

    @Test fun xmlBlockStyleComment() {
        val style = LineEdits.commentStyleFor("a/layout.xml")!!
        val t = "<a/>"
        val c = LineEdits.toggleComment(t, 0, 0, style)
        assertEquals("<!-- <a/> -->", c.applyTo(t))
        assertEquals(t, LineEdits.toggleComment(c.applyTo(t), 0, 0, style).applyTo(c.applyTo(t)))
    }

    @Test fun selectionEndingAtLineStartExcludesThatLine() {
        val t = "a\nb\nc"
        val e = LineEdits.toggleComment(t, 0, 2, slash)
        assertEquals("// a\nb\nc", e.applyTo(t))
    }

    @Test fun indentAndDedent() {
        val t = "a\n\nb"
        val i = LineEdits.indent(t, 0, 4)
        assertEquals("    a\n\n    b", i.applyTo(t))
        assertEquals(t, LineEdits.dedent(i.applyTo(t), 0, 12).applyTo(i.applyTo(t)))
        assertEquals("a", LineEdits.dedent("  a", 0, 0).applyTo("  a"))
    }

    @Test fun duplicateDeleteMove() {
        val t = "a\nb\nc"
        val d = LineEdits.duplicateLines(t, 2, 2)
        assertEquals("a\nb\nb\nc", d.applyTo(t)); assertEquals(4, d.selStart)
        assertEquals("a\nc", LineEdits.deleteLines(t, 2, 2).applyTo(t))
        assertEquals("a\nb", LineEdits.deleteLines(t, 4, 4).applyTo(t))
        val up = LineEdits.moveLines(t, 2, 2, up = true)!!
        assertEquals("b\na\nc", up.applyTo(t)); assertEquals(0, up.selStart)
        val down = LineEdits.moveLines(t, 2, 2, up = false)!!
        assertEquals("a\nc\nb", down.applyTo(t)); assertEquals(4, down.selStart)
        assertNull(LineEdits.moveLines(t, 0, 0, up = true)); assertNull(LineEdits.moveLines(t, 4, 4, up = false))
    }

    // --- Submodule Maker ---

    @Test fun parsePaths() {
        assertEquals(listOf("lib", "core", "libcoredu"), GradleModulePath.parse(":lib:core:libcoredu").getOrThrow().segments)
        assertEquals("lib/core/libcoredu", GradleModulePath.parse("lib:core:libcoredu").getOrThrow().relativeDir)
        assertTrue(GradleModulePath.parse("").isFailure)
        assertTrue(GradleModulePath.parse(":a::b").isFailure)
        assertTrue(GradleModulePath.parse(":a:b c").isFailure)
        assertTrue(GradleModulePath.parse(":a:build").isFailure)
    }

    @Test fun packageAndClassNames() {
        val p = GradleModulePath.parse(":lib:core:lib-core_du").getOrThrow()
        assertEquals("com.x.lib.core.libcoredu", ModuleMaker.packageFor("com.x", p))
        assertEquals("LibCoreDu", ModuleMaker.className(p))
        assertEquals("com.x.m1.int_", ModuleMaker.packageFor("com.x", GradleModulePath.parse(":1:int").getOrThrow()))
    }

    @Test fun addIncludeIsIdempotent() {
        val p = GradleModulePath.parse(":lib:a").getOrThrow()
        val (s1, c1) = ModuleMaker.addInclude("rootProject.name = \"x\"\ninclude(\":app\")\n", p, true)
        assertTrue(c1); assertEquals("rootProject.name = \"x\"\ninclude(\":app\")\ninclude(\":lib:a\")\n", s1)
        assertFalse(ModuleMaker.addInclude(s1, p, true).second)
        assertFalse(ModuleMaker.addInclude("include ':app', ':lib:a'\n", p, false).second)
        assertEquals("include ':app'\ninclude ':lib:a'\n", ModuleMaker.addInclude("include ':app'", p, false).first)
    }

    private fun project(kts: Boolean = true, catalog: Boolean = true): File {
        val root = File.createTempFile("cfmod", "").apply { delete(); mkdirs() }
        val ext = if (kts) ".kts" else ""
        File(root, "settings.gradle$ext").writeText(if (kts) "include(\":app\")\n" else "include ':app'\n")
        File(root, "build.gradle$ext").writeText("plugins {\n    alias(libs.plugins.android.application) apply false\n    alias(libs.plugins.android.library) apply false\n    alias(libs.plugins.kotlin.android) apply false\n}\n")
        File(root, "app").mkdirs()
        File(root, "app/build.gradle$ext").writeText(if (kts) "android {\n    namespace = \"com.demo.app\"\n    compileSdk = 34\n    defaultConfig { minSdk = 24 }\n}\n" else "android {\n    namespace 'com.demo.app'\n    compileSdk 34\n    defaultConfig { minSdk 24 }\n}\n")
        if (catalog) {
            File(root, "gradle").mkdirs()
            File(root, "gradle/libs.versions.toml").writeText("[versions]\nagp = \"8\"\n[plugins]\nandroid-application = { id = \"com.android.application\", version.ref = \"agp\" }\nandroid-library = { id = \"com.android.library\", version.ref = \"agp\" }\nkotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"k\" }\nkotlin-jvm = { id = \"org.jetbrains.kotlin.jvm\", version.ref = \"k\" }\n")
        }
        return root
    }

    @Test fun createsAndroidLibraryKts() {
        val root = project()
        val r = ModuleMaker.create(root, ModuleRequest(":lib:core:libcoredu")).getOrThrow()
        val dir = File(root, "lib/core/libcoredu")
        val build = File(dir, "build.gradle.kts").readText()
        assertTrue(build.contains("alias(libs.plugins.android.library)"))
        assertTrue(build.contains("alias(libs.plugins.kotlin.android)"))
        assertTrue(build.contains("namespace = \"com.demo.app.lib.core.libcoredu\""))
        assertTrue(build.contains("compileSdk = 34") && build.contains("minSdk = 24"))
        assertTrue(File(dir, "src/main/AndroidManifest.xml").isFile)
        val src = File(dir, "src/main/kotlin/com/demo/app/lib/core/libcoredu/Libcoredu.kt").readText()
        assertTrue(src.startsWith("package com.demo.app.lib.core.libcoredu\n"))
        assertTrue(File(root, "settings.gradle.kts").readText().contains("include(\":lib:core:libcoredu\")"))
        assertTrue(r.settingsChanged); assertFalse(r.rootBuildChanged); assertTrue(r.warnings.isEmpty())
    }

    @Test fun createsGroovyModuleAndPatchesRootForJvm() {
        val root = project(kts = false)
        val r = ModuleMaker.create(root, ModuleRequest(":util", ModuleType.KOTLIN_JVM_LIBRARY)).getOrThrow()
        val build = File(root, "util/build.gradle").readText()
        assertTrue(build.contains("alias(libs.plugins.kotlin.jvm)"))
        assertFalse(File(root, "util/src/main/AndroidManifest.xml").exists())
        assertTrue(File(root, "settings.gradle").readText().contains("include ':util'"))
        assertTrue(r.rootBuildChanged)
        assertTrue(File(root, "build.gradle").readText().contains("alias(libs.plugins.kotlin.jvm) apply false"))
    }

    @Test fun withoutCatalogUsesPluginIdsAndWarns() {
        val root = project(catalog = false)
        val r = ModuleMaker.create(root, ModuleRequest(":x")).getOrThrow()
        assertTrue(File(root, "x/build.gradle.kts").readText().contains("id(\"com.android.library\")"))
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun refusesExistingNonEmptyDirAndMissingSettings() {
        val root = project()
        File(root, "lib/a").mkdirs(); File(root, "lib/a/x.txt").writeText("x")
        val before = File(root, "settings.gradle.kts").readText()
        assertTrue(ModuleMaker.create(root, ModuleRequest(":lib:a")).isFailure)
        assertEquals(before, File(root, "settings.gradle.kts").readText())
        val empty = File.createTempFile("cfmod", "").apply { delete(); mkdirs() }
        assertTrue(ModuleMaker.create(empty, ModuleRequest(":a")).isFailure)
        assertTrue(ModuleMaker.create(root, ModuleRequest("::")).isFailure)
    }
}
