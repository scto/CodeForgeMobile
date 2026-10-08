package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.RepositoryScope
import org.junit.Assert.assertEquals
import org.junit.Test

class GradleRepositoryParserTest {

    @Test
    fun settingsFile_scopesAndForms() {
        val repos = GradleRepositoryParser.parse(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); google() }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                    maven { url = uri("https://jitpack.io") }
                    maven("https://repo.gradle.org/gradle/libs-releases")
                    maven { url 'https://example.com/m2/' }
                    // maven { url = uri("https://commented.example") }
                    maven { url = uri(someVariable) }
                }
            }
            """.trimIndent(),
            "settings.gradle.kts"
        )
        val plugins = repos.filter { it.scope == RepositoryScope.PLUGINS }.map { it.repository.name }
        val deps = repos.filter { it.scope == RepositoryScope.DEPENDENCIES }.map { it.repository.url }
        assertEquals(listOf("gradlePluginPortal", "google"), plugins)
        assertEquals(
            listOf(
                "https://dl.google.com/dl/android/maven2/",
                "https://repo.maven.apache.org/maven2/",
                "https://jitpack.io/",
                "https://repo.gradle.org/gradle/libs-releases/",
                "https://example.com/m2/"
            ),
            deps
        )
    }

    @Test
    fun buildscriptIsPluginScope() {
        val repos = GradleRepositoryParser.parse(
            "buildscript { repositories { mavenCentral() } }\nallprojects { repositories { google() } }",
            "build.gradle"
        )
        assertEquals(RepositoryScope.PLUGINS, repos[0].scope)
        assertEquals(RepositoryScope.DEPENDENCIES, repos[1].scope)
    }
}
