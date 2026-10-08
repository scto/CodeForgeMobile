package com.codeforge.libs.indexing_impl

import org.junit.Assert.assertEquals
import org.junit.Test

class GradleSettingsParserTest {

    @Test
    fun kotlinDsl_includesRootNameAndCatalog() {
        val parsed = GradleSettingsParser.parse(
            """
            rootProject.name = "CodeForgeMobile"
            // include(":ignored")
            include(":app", ":core:domain")
            include(
                ":feature:editor",
                ":feature:git"
            )
            /* include(":blocked") */
            includeBuild("../other")
            dependencyResolutionManagement {
                versionCatalogs { create("libs") { from(files("gradle/my.versions.toml")) } }
            }
            project(":core:domain").projectDir = File(settingsDir, "modules/domain")
            """.trimIndent()
        )
        assertEquals("CodeForgeMobile", parsed.rootProjectName)
        assertEquals(listOf(":app", ":core:domain", ":feature:editor", ":feature:git"), parsed.includes)
        assertEquals(listOf("gradle/my.versions.toml"), parsed.versionCatalogFiles)
        assertEquals(mapOf(":core:domain" to "modules/domain"), parsed.projectDirOverrides)
    }

    @Test
    fun groovyDsl_includesWithoutParens() {
        val parsed = GradleSettingsParser.parse(
            "rootProject.name = 'Demo'\ninclude ':app', 'lib'\ninclude(':x')\n"
        )
        assertEquals("Demo", parsed.rootProjectName)
        assertEquals(listOf(":app", ":lib", ":x"), parsed.includes)
    }

    @Test
    fun defaultDirectory() {
        assertEquals("feature/editor", GradleSettingsParser.defaultDirectory(":feature:editor"))
    }
}
