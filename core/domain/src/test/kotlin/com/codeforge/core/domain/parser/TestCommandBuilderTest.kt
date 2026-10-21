package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.TestCaseResult
import com.codeforge.core.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TestCommandBuilderTest {

    @Test
    fun defaultRootProjectCommandWithoutFilter() {
        val cmd = TestCommandBuilder.buildTestTaskCommand()
        assertEquals("testDebugUnitTest", cmd)
    }

    @Test
    fun specificModuleCommandWithoutFilter() {
        val cmd = TestCommandBuilder.buildTestTaskCommand(moduleGradlePath = ":core:domain")
        assertEquals(":core:domain:testDebugUnitTest", cmd)
    }

    @Test
    fun specificModuleWithReleaseVariant() {
        val cmd = TestCommandBuilder.buildTestTaskCommand(moduleGradlePath = ":feature:build", variant = "Release")
        assertEquals(":feature:build:testReleaseUnitTest", cmd)
    }

    @Test
    fun specificClassFilter() {
        val cmd = TestCommandBuilder.buildTestTaskCommand(
            moduleGradlePath = ":core:domain",
            testFilter = "com.codeforge.core.domain.parser.JUnitXmlParserTest"
        )
        assertEquals(
            ":core:domain:testDebugUnitTest --tests \"com.codeforge.core.domain.parser.JUnitXmlParserTest\"",
            cmd
        )
    }

    @Test
    fun specificMethodFilter() {
        val cmd = TestCommandBuilder.buildTestTaskCommand(
            moduleGradlePath = ":core:domain",
            testFilter = "com.codeforge.core.domain.parser.JUnitXmlParserTest.parsePassedSuite"
        )
        assertEquals(
            ":core:domain:testDebugUnitTest --tests \"com.codeforge.core.domain.parser.JUnitXmlParserTest.parsePassedSuite\"",
            cmd
        )
    }

    @Test
    fun specialCharactersInFilter() {
        // Parameterized tests or wildcards
        val cmd = TestCommandBuilder.buildTestTaskCommand(
            moduleGradlePath = ":core:domain",
            testFilter = "SampleParameterizedTest.test*[\"param\"]"
        )
        assertEquals(
            ":core:domain:testDebugUnitTest --tests \"SampleParameterizedTest.test*[\\\"param\\\"]\"",
            cmd
        )
    }

    @Test
    fun failedCasesRerunCommand() {
        val failedCases = listOf(
            TestCaseResult("testA", "com.example.FooTest", 0.1, TestStatus.FAILED),
            TestCaseResult("testB", "com.example.BarTest", 0.2, TestStatus.ERROR),
            // Duplicate case should be deduplicated
            TestCaseResult("testA", "com.example.FooTest", 0.1, TestStatus.FAILED)
        )

        val cmd = TestCommandBuilder.buildTestTaskCommand(
            moduleGradlePath = ":feature:editor",
            failedCases = failedCases
        )

        assertEquals(
            ":feature:editor:testDebugUnitTest --tests \"com.example.FooTest.testA\" --tests \"com.example.BarTest.testB\"",
            cmd
        )
    }
}
