package com.codeforge.libs.dependency_updater_impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.codeforge.libs.indexing_api.RepositoryScope

class GradleBuildParserTest {

    @Test fun kotlinDslForms() {
        val text = """
            plugins {
                id("com.android.application") version "8.6.0" apply false
                kotlin("jvm") version "2.0.20"
            }
            val ktor = "2.3.12"
            buildscript { dependencies { classpath("com.android.tools.build:gradle:8.5.0") } }
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                api("io.ktor:ktor-client-core:${'$'}ktor")
                implementation(group = "a.b", name = "c", version = "3.1")
                // implementation("old:commented:1.0")
                implementation("x:dynamic:1.+")
                implementation(libs.some)
            }
        """.trimIndent()
        val vars = VariableIndex.build(mapOf("/p/build.gradle.kts" to text))
        val usages = GradleBuildParser.parse("/p/build.gradle.kts", text, vars)
        val m = usages.associateBy { it.coordinate.key }
        assertEquals("8.6.0", m["com.android.application:com.android.application.gradle.plugin"]!!.literal.value)
        assertEquals("2.0.20", m["org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin"]!!.literal.value)
        assertEquals(RepositoryScope.PLUGINS, m["com.android.tools.build:gradle"]!!.scope)
        assertEquals("1.13.1", m["androidx.core:core-ktx"]!!.literal.value)
        assertEquals("2.3.12", m["io.ktor:ktor-client-core"]!!.literal.value)
        assertEquals("3.1", m["a.b:c"]!!.literal.value)
        assertTrue("old:commented" !in m)
        assertTrue(m.values.all { text.substring(it.literal.start, it.literal.end) == it.literal.value })
        // dynamic wird erst beim Gruppieren verworfen
        assertTrue(DependencyScanner.group(usages).none { it.currentVersion == "1.+" })
    }

    @Test fun groovyFormsAndProperties() {
        val gradle = """
            ext { retrofit = '2.9.0' }
            dependencies {
                implementation 'com.squareup.retrofit2:retrofit:${'$'}retrofit'
                implementation "com.google.guava:guava:${'$'}{guavaVersion}"
                implementation group: 'org.x', name: 'y', version: '0.5'
            }
        """.trimIndent()
        val props = "guavaVersion=32.1.2-jre\n# comment=1\n"
        val files = mapOf("/p/build.gradle" to gradle, "/p/gradle.properties" to props)
        val usages = GradleBuildParser.parse("/p/build.gradle", gradle, VariableIndex.build(files))
        val m = usages.associateBy { it.coordinate.key }
        assertEquals("2.9.0", m["com.squareup.retrofit2:retrofit"]!!.literal.value)
        val guava = m["com.google.guava:guava"]!!.literal
        assertEquals("/p/gradle.properties", guava.filePath)
        assertEquals("32.1.2-jre", props.substring(guava.start, guava.end))
        assertEquals("0.5", m["org.x:y"]!!.literal.value)
    }

    @Test fun sameDependencyInSeveralModulesMergesToOneCandidate() {
        val a = "dependencies { implementation(\"g:n:1.0\") }"
        val b = "dependencies { implementation(\"g:n:1.0\") }"
        val raw = GradleBuildParser.parse("/a/build.gradle.kts", a) + GradleBuildParser.parse("/b/build.gradle.kts", b)
        val c = DependencyScanner.group(raw).single()
        assertEquals(2, c.locations.size)
    }
}
