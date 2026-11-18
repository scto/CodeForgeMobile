package com.codeforge.feature.composepreview

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class PreviewGradleOutputsTest {

    private lateinit var root: File
    private lateinit var appSource: File
    private lateinit var libSource: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("gradle-outputs").toFile()
        File(root, "settings.gradle.kts").writeText("include(\":app\", \":feature:home\")")
        appSource = module("app", "com/example/app/MainActivity.kt")
        libSource = module("feature/home", "com/example/home/HomeScreen.kt")
        // App-Modul (AGP 9): Klassen, R.jar, gelinkte Ressourcen, externe DEX
        classFile("app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/example/app/MainActivity.class")
        classFile("feature/home/build/tmp/kotlin-classes/debug/com/example/home/HomeScreenKt.class") // AGP 8
        file("app/build/intermediates/compile_and_runtime_r_class_jar/debug/processDebugResources/R.jar")
        file("app/build/intermediates/linked_resources_binary_format/debug/processDebugResources/linked-resources-binary-format-debug.ap_")
        file("app/build/intermediates/dex/debug/mergeExtDexDebug/classes.dex")
    }

    private fun module(path: String, source: String): File {
        File(root, "$path/build.gradle.kts").apply { parentFile.mkdirs(); writeText("") }
        File(root, "$path/src/main/res/values/strings.xml").apply { parentFile.mkdirs(); writeText("<resources/>") }
        return File(root, "$path/src/main/kotlin/$source").apply { parentFile.mkdirs(); writeText("package x") }.also { age(it) }
    }

    private fun file(path: String) = File(root, path).apply { parentFile.mkdirs(); writeText("x") }
    private fun classFile(path: String) = file(path)

    /** Quellen älter als der Build machen. */
    private fun age(f: File) = f.setLastModified(System.currentTimeMillis() - 60_000)

    @Test
    fun `aktueller Build wird mit allen Teilen gefunden`() {
        File(root, "app/src/main/res/values/strings.xml").let(::age)
        File(root, "feature/home/src/main/res/values/strings.xml").let(::age)
        val outputs = PreviewGradleOutputs.find(root, libSource)
        assertNotNull(outputs)
        outputs!!
        assertEquals("debug", outputs.variant)
        assertEquals(2, outputs.classDirs.size)
        assertEquals("R.jar", outputs.rJars.single().name)
        assertTrue(outputs.resourceApk!!.name.endsWith(".ap_"))
        assertEquals(1, outputs.externalDex.size)
    }

    @Test
    fun `geänderte Nachbardatei macht den Build ungültig`() {
        listOf("app", "feature/home").forEach { File(root, "$it/src/main/res/values/strings.xml").let(::age) }
        appSource.setLastModified(System.currentTimeMillis() + 60_000)
        assertNull(PreviewGradleOutputs.find(root, libSource))
    }

    @Test
    fun `die offene Datei selbst darf neuer sein`() {
        listOf("app", "feature/home").forEach { File(root, "$it/src/main/res/values/strings.xml").let(::age) }
        libSource.setLastModified(System.currentTimeMillis() + 60_000)
        assertNotNull(PreviewGradleOutputs.find(root, libSource))
    }

    @Test
    fun `geänderte Ressource macht den Build ungültig`() {
        File(root, "feature/home/src/main/res/values/strings.xml").setLastModified(System.currentTimeMillis() + 60_000)
        assertNull(PreviewGradleOutputs.find(root, libSource))
    }

    @Test
    fun `nie gebautes Modul mit Code liefert nichts`() {
        File(root, "feature/home/build").deleteRecursively()
        assertNull(PreviewGradleOutputs.find(root, appSource))
    }

    /** Gegen ein echtes, gebautes Projekt: `-Dcf.gradleSample=/pfad/zum/projekt`. */
    @Test
    fun `echtes Projekt`() {
        val sample = System.getProperty("cf.gradleSample")?.let(::File)
        assumeTrue(sample?.isDirectory == true)
        val outputs = PreviewGradleOutputs.find(sample, null)
        println("Gradle-Ergebnisse: $outputs")
        assertNotNull(outputs)
    }
}
