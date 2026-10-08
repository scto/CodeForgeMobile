package com.codeforge.libs.dependency_updater_impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TomlCatalogParserTest {

    private val toml = """
        [versions]
        agp = "8.6.0"
        kotlin = { strictly = "2.0.20" } # comment
        compose = "1.7.0"
        # skipped = "9.9.9"

        [libraries]
        androidx-ui = { group = "androidx.compose.ui", name = "ui", version.ref = "compose" }
        androidx-material = { module = "androidx.compose.material3:material3", version = "1.3.0" }
        kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
        short = "com.squareup.okhttp3:okhttp:4.12.0"
        no-version = { module = "x:y" }
        bom-managed = { module = "a:b" }

        [plugins]
        android-app = { id = "com.android.application", version.ref = "agp" }
        ksp = "com.google.devtools.ksp:2.0.20-1.0.25"
        kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }

        [bundles]
        x = ["a", "b"]
    """.trimIndent()

    @Test fun parsesAllForms() {
        val usages = TomlCatalogParser.parse("/p/libs.versions.toml", toml)
        val byCoord = usages.groupBy { it.coordinate.key }
        assertEquals("1.7.0", byCoord["androidx.compose.ui:ui"]!!.single().literal.value)
        assertEquals("1.3.0", byCoord["androidx.compose.material3:material3"]!!.single().literal.value)
        assertEquals("4.12.0", byCoord["com.squareup.okhttp3:okhttp"]!!.single().literal.value)
        assertEquals("8.6.0", byCoord["com.android.application:com.android.application.gradle.plugin"]!!.single().literal.value)
        assertEquals("2.0.20-1.0.25", byCoord["com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin"]!!.single().literal.value)
        assertEquals("2.0.20", byCoord["org.jetbrains.kotlin:kotlin-stdlib"]!!.single().literal.value)
        assertTrue("x:y" !in byCoord)
    }

    @Test fun offsetsPointAtVersionText() {
        for (u in TomlCatalogParser.parse("/p/libs.versions.toml", toml)) {
            assertEquals(u.literal.value, toml.substring(u.literal.start, u.literal.end))
        }
    }

    @Test fun refUsagesShareLiteralAndGroup() {
        val usages = TomlCatalogParser.parse("/p/libs.versions.toml", toml)
        val candidates = DependencyScanner.group(usages)
        val kotlin = candidates.single { it.currentVersion == "2.0.20" }
        assertEquals(2, kotlin.usages.size)
        assertEquals(1, kotlin.locations.size)
    }

    @Test fun crlfAndLine() {
        val text = "[versions]\r\na = \"1.0\"\r\n[libraries]\r\nl = { module = \"g:n\", version.ref = \"a\" }\r\n"
        val u = TomlCatalogParser.parse("f", text).single()
        assertEquals(1, u.literal.line)
        assertEquals("1.0", text.substring(u.literal.start, u.literal.end))
    }
}
