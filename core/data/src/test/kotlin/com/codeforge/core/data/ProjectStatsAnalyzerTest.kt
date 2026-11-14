package com.codeforge.core.data

import com.codeforge.core.data.repository.ProjectStatsAnalyzer
import com.codeforge.core.domain.model.CreationSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectStatsAnalyzerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun write(root: File, path: String, vararg lines: String) =
        File(root, path).apply {
            parentFile.mkdirs()
            writeText(lines.joinToString(System.lineSeparator()))
        }

    private fun sample(): File {
        val root = tmp.newFolder("Demo")
        write(root, "settings.gradle.kts", """include(":app", ":core")""")
        write(root, "build.gradle.kts", "plugins { }")
        write(root, "gradle/wrapper/gradle-wrapper.properties", """distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.3-bin.zip""")
        write(
            root, "gradle/libs.versions.toml",
            "[versions]", """agp = "8.13.0"""", """kotlin = "2.1.21"""", "",
            "[libraries]", """a = { module = "x:a" }""", """b = { module = "x:b" }""", "",
            "[plugins]", """c = { id = "c" }""",
        )
        write(
            root, "app/build.gradle.kts",
            "plugins { alias(libs.plugins.android.application) }", "android {", "    compileSdk = 36",
            "    defaultConfig { minSdk = 26", " targetSdk = 35 }", "}",
        )
        write(root, "app/src/main/AndroidManifest.xml", "<manifest/>")
        write(
            root, "app/src/main/kotlin/Main.kt",
            "package a", "", "/**", " * Doku", " */", "@Composable", "fun Screen() {", "    // Kommentar", "    val x = 1 /* inline */", "}",
        )
        write(root, "core/build.gradle.kts", "plugins { }")
        write(root, "core/src/main/java/A.java", "class A {", "  /* a */", "  int b;", "}")
        write(root, "core/src/main/res/values/strings.xml", "<resources>", "<!-- c", " d -->", """<string name="x">y</string>""", "</resources>")
        write(root, "build/generated/Gen.kt", *Array(100) { "fun gen() = 1" }) // ignoriert
        return root
    }

    @Test fun countsSample() {
        val s = ProjectStatsAnalyzer.analyze(sample())
        assertEquals("Kotlin", s.primaryLanguage)
        assertEquals(listOf("Gradle (Kotlin DSL)"), s.buildSystems)
        assertTrue(s.projectKinds.containsAll(listOf("Android-App", "Jetpack Compose")))
        assertTrue(s.usesCompose)

        val kotlin = s.languages.single { it.language == "Kotlin" }
        // Main.kt: 10 Zeilen = 1 leer, 4 Kommentar (3 KDoc + //), 5 Code; dazu die vier .kts-Dateien (1 + 1 + 6 + 1).
        assertEquals(5L + 1 + 1 + 6 + 1, kotlin.codeLines)
        val java = s.languages.single { it.language == "Java" }
        assertEquals(3L, java.codeLines)
        val xml = s.languages.single { it.language == "XML" }
        assertEquals(4L, xml.codeLines) // Manifest + 3 Zeilen strings.xml, 2 Kommentarzeilen
        assertFalse(xml.isProgramming)
        assertTrue(s.languages.none { it.language == "Kotlin" && it.lines > 100 }) // build/ ignoriert
        assertEquals(kotlin.codeLines + java.codeLines, s.codeLines) // XML zählt nicht als Code

        assertEquals(setOf(":", ":app", ":core"), s.modules.map { it.path }.toSet())
        assertEquals(5L + 6, s.modules.single { it.path == ":app" }.codeLines) // Main.kt + app/build.gradle.kts
        assertEquals("8.14.3", s.gradleWrapper)
        assertEquals("8.13.0", s.agpVersion)
        assertEquals("2.1.21", s.kotlinVersion)
        assertEquals(26, s.minSdk)
        assertEquals(35, s.targetSdk)
        assertEquals(36, s.compileSdk)
        assertEquals(2, s.dependencyCount)
        assertNotNull(s.createdMillis)
        assertTrue(s.createdSource == CreationSource.FILESYSTEM || s.createdSource == CreationSource.OLDEST_FILE)
        assertFalse(s.truncated)
    }

    @Test fun moduleLookup() {
        val dirs = setOf("", "app", "libs/net")
        assertEquals("app", ProjectStatsAnalyzer.moduleOf("app/src/main/kotlin", dirs))
        assertEquals("libs/net", ProjectStatsAnalyzer.moduleOf("libs/net/src", dirs))
        assertEquals("", ProjectStatsAnalyzer.moduleOf("docs", dirs))
    }
}
