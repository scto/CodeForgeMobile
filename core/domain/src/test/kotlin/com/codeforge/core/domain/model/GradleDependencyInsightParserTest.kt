package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleDependencyInsightParserTest {

    @Test
    fun parseRealInsightFixture() {
        val fixtureStream = javaClass.getResourceAsStream("/fixtures/dependency_updater_api_insight.txt")
        assertNotNull("Fixture must exist", fixtureStream)
        val content = fixtureStream!!.bufferedReader().readText()

        val insight = GradleDependencyInsightParser.parse(content, "org.jetbrains.kotlin:kotlin-stdlib")

        assertEquals("org.jetbrains.kotlin:kotlin-stdlib", insight.coordinate)
        assertEquals("2.1.21", insight.resolvedVersion)

        assertTrue("Should contain conflict resolution reason", insight.selectionReasons.any { it.contains("By conflict resolution") })
        assertTrue("Should extract paths to root", insight.pathsToRoot.isNotEmpty())
    }
}
