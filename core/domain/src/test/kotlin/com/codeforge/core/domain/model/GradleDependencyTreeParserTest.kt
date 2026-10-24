package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleDependencyTreeParserTest {

    @Test
    fun parseSimpleTree_extractsRootAndChildren() {
        val input = """
            compileClasspath - Compile classpath
            +--- com.google.guava:guava:31.0-jre
            |    \--- com.google.guava:failureaccess:1.0.1
            \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.21
        """.trimIndent()

        val graph = GradleDependencyTreeParser.parse(input, ":test", "compileClasspath")

        assertEquals(2, graph.rootNodes.size)
        val guava = graph.rootNodes[0]
        assertEquals("com.google.guava", guava.group)
        assertEquals("guava", guava.name)
        assertEquals("31.0-jre", guava.resolvedVersion)
        assertEquals(1, guava.children.size)

        val failureaccess = guava.children[0]
        assertEquals("failureaccess", failureaccess.name)
        assertEquals("1.0.1", failureaccess.resolvedVersion)

        val kotlinStdlib = graph.rootNodes[1]
        assertEquals("kotlin-stdlib", kotlinStdlib.name)
        assertEquals(0, kotlinStdlib.children.size)
    }

    @Test
    fun parseRepeatedAndConstraint_flagsSetCorrectly() {
        val input = """
            compileClasspath - Compile classpath
            +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0 (c)
            \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.21 (*)
        """.trimIndent()

        val graph = GradleDependencyTreeParser.parse(input)
        assertEquals(2, graph.rootNodes.size)

        val c1 = graph.rootNodes[0]
        assertTrue(c1.isConstraint)
        assertFalse(c1.isRepeated)

        val c2 = graph.rootNodes[1]
        assertTrue(c2.isRepeated)
        assertFalse(c2.isConstraint)
    }

    @Test
    fun parseConflictResolution_arrowParsedAndConflictDetected() {
        val input = """
            compileClasspath - Compile classpath
            \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.0 -> 2.1.21
                 \--- org.jetbrains:annotations:13.0 -> 23.0.0
        """.trimIndent()

        val graph = GradleDependencyTreeParser.parse(input)
        assertEquals(1, graph.rootNodes.size)
        val root = graph.rootNodes[0]
        assertEquals("2.0.0", root.requestedVersion)
        assertEquals("2.1.21", root.resolvedVersion)
        assertTrue(root.hasConflict)

        assertEquals(2, graph.conflicts.size)
        val conflict1 = graph.conflicts.first { it.name == "kotlin-stdlib" }
        assertEquals("2.0.0", conflict1.requestedVersion)
        assertEquals("2.1.21", conflict1.resolvedVersion)
        assertNull(conflict1.directRequester)

        val conflict2 = graph.conflicts.first { it.name == "annotations" }
        assertEquals("13.0", conflict2.requestedVersion)
        assertEquals("23.0.0", conflict2.resolvedVersion)
        assertNotNull(conflict2.directRequester)
    }

    @Test
    fun parseProjectDependency_recognizedProperly() {
        val input = """
            compileClasspath - Compile classpath
            +--- project :core:domain
            \--- project :core:resources -> project :core:resources
        """.trimIndent()

        val graph = GradleDependencyTreeParser.parse(input)
        assertEquals(2, graph.rootNodes.size)
        assertTrue(graph.rootNodes[0].isProject)
        assertEquals(":core:domain", graph.rootNodes[0].name)
        assertTrue(graph.rootNodes[1].isProject)
        assertEquals(":core:resources", graph.rootNodes[1].name)
    }

    @Test
    fun parseFailedDependency_markedAsFailed() {
        val input = """
            compileClasspath - Compile classpath
            \--- com.example:broken-lib:1.0.0 FAILED
        """.trimIndent()

        val graph = GradleDependencyTreeParser.parse(input)
        assertEquals(1, graph.rootNodes.size)
        val node = graph.rootNodes[0]
        assertTrue(node.failed)
        assertEquals("FAILED", node.failureReason)
        assertEquals("broken-lib", node.name)
    }

    @Test
    fun parseRealFixture_dependencyUpdaterApi() {
        val fixtureStream = javaClass.getResourceAsStream("/fixtures/dependency_updater_api_compileClasspath.txt")
        assertNotNull("Fixture must exist", fixtureStream)
        val content = fixtureStream!!.bufferedReader().readText()

        val graph = GradleDependencyTreeParser.parse(content, ":libs:dependency-updater-api", "compileClasspath")
        assertEquals(2, graph.rootNodes.size)

        // Conflict should be detected: annotations:13.0 -> 23.0.0 and kotlin-stdlib:2.0.0 -> 2.1.21
        assertTrue(graph.conflicts.isNotEmpty())
        val stdlibConflict = graph.conflicts.find { it.name == "kotlin-stdlib" }
        assertNotNull(stdlibConflict)
        assertEquals("2.0.0", stdlibConflict!!.requestedVersion)
        assertEquals("2.1.21", stdlibConflict.resolvedVersion)

        assertTrue(graph.totalDependencies > 0)
        assertTrue(graph.maxDepth >= 3)
    }

    @Test
    fun parseRealFixture_coreCommonDebugCompileClasspath() {
        val fixtureStream = javaClass.getResourceAsStream("/fixtures/core_common_debugCompileClasspath.txt")
        assertNotNull("Fixture must exist", fixtureStream)
        val content = fixtureStream!!.bufferedReader().readText()

        val graph = GradleDependencyTreeParser.parse(content, ":core:common", "debugCompileClasspath")
        assertTrue(graph.rootNodes.isNotEmpty())
        assertTrue(graph.conflicts.size > 5)
        assertTrue(graph.totalDependencies > 10)
        assertTrue(graph.maxDepth >= 5)
    }

    @Test
    fun parseVeryDeepTree_doesNotCauseStackOverflow() {
        // Erzeuge Baum mit 500 Ebenen
        val sb = StringBuilder("compileClasspath - Compile\n")
        for (i in 0 until 500) {
            val indent = "|    ".repeat(i)
            sb.appendLine("${indent}+--- com.example:level$i:1.0.0")
        }
        val graph = GradleDependencyTreeParser.parse(sb.toString())
        assertEquals(1, graph.rootNodes.size)
        assertEquals(500, graph.maxDepth)
    }
}
