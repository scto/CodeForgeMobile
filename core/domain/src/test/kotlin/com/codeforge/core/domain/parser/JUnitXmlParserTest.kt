package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JUnitXmlParserTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$name")
            ?: error("Fixture not found: fixtures/$name")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun parsePassedSuite() {
        val xml = loadFixture("junit_passed.xml")
        val suites = JUnitXmlParser.parseXml(xml)

        assertEquals(1, suites.size)
        val suite = suites.first()
        assertEquals("com.codeforge.core.domain.parser.AaptPermissionsParserTest", suite.className)
        assertEquals("com.codeforge.core.domain.parser", suite.packageName)
        assertEquals("AaptPermissionsParserTest", suite.simpleClassName)
        assertEquals(2, suite.totalCount)
        assertEquals(2, suite.passedCount)
        assertEquals(0, suite.failedCount)
        assertEquals(0, suite.skippedCount)
        assertTrue(suite.durationSeconds > 0.0)

        val firstCase = suite.cases.first()
        assertEquals("parseEmptyOutputReturnsNullPackageAndEmptyList", firstCase.name)
        assertEquals(TestStatus.PASSED, firstCase.status)
        assertTrue(firstCase.isSuccess)
        assertNull(firstCase.failureDetail)
    }

    @Test
    fun parseFailedSuiteWithStacktraceAndComparison() {
        val xml = loadFixture("junit_failed.xml")
        val suites = JUnitXmlParser.parseXml(xml)

        assertEquals(1, suites.size)
        val suite = suites.first()
        assertEquals(2, suite.totalCount)
        assertEquals(1, suite.passedCount)
        assertEquals(1, suite.failedCount)

        val failedCase = suite.cases.first { it.status == TestStatus.FAILED }
        assertEquals("testAssertionFailure", failedCase.name)
        val failure = failedCase.failureDetail
        assertNotNull(failure)
        assertEquals("expected:<hello> but was:<world>", failure?.message)
        assertEquals("hello", failure?.expected)
        assertEquals("world", failure?.actual)
        assertTrue(failure?.isComparisonFailure == true)

        assertNotNull(failedCase.stdout)
        assertTrue(failedCase.stdout!!.contains("Testing assertion failure logic"))
        assertNotNull(failedCase.stderr)
        assertTrue(failedCase.stderr!!.contains("Some warning on stderr"))

        // Verify stack frames
        val frames = failure!!.stackFrames
        assertTrue("Stack frames should not be empty", frames.isNotEmpty())
        val sampleFrame = frames.find { it.className == "com.codeforge.core.domain.parser.SampleFailedTest" }
        assertNotNull("Project stack frame should be found", sampleFrame)
        assertEquals("testAssertionFailure", sampleFrame?.methodName)
        assertEquals("SampleFailedTest.kt", sampleFrame?.fileName)
        assertEquals(42, sampleFrame?.lineNumber)
    }

    @Test
    fun parseErrorSuite() {
        val xml = loadFixture("junit_error.xml")
        val suites = JUnitXmlParser.parseXml(xml)

        assertEquals(1, suites.size)
        val suite = suites.first()
        assertEquals(1, suite.totalCount)
        assertEquals(0, suite.passedCount)
        assertEquals(1, suite.failedCount)

        val errorCase = suite.cases.first()
        assertEquals(TestStatus.ERROR, errorCase.status)
        assertEquals("testCrash", errorCase.name)
        val failure = errorCase.failureDetail
        assertNotNull(failure)
        assertEquals("Crash occurred: null pointer", failure?.message)
        assertEquals("java.lang.NullPointerException", failure?.type)

        val frame = failure!!.stackFrames.first()
        assertEquals("com.codeforge.core.domain.parser.SampleErrorTest", frame.className)
        assertEquals("testCrash", frame.methodName)
        assertEquals("SampleErrorTest.kt", frame.fileName)
        assertEquals(18, frame.lineNumber)
    }

    @Test
    fun parseSkippedSuite() {
        val xml = loadFixture("junit_skipped.xml")
        val suites = JUnitXmlParser.parseXml(xml)

        assertEquals(1, suites.size)
        val suite = suites.first()
        assertEquals(2, suite.totalCount)
        assertEquals(1, suite.passedCount)
        assertEquals(0, suite.failedCount)
        assertEquals(1, suite.skippedCount)

        val skippedCase = suite.cases.first { it.status == TestStatus.SKIPPED }
        assertEquals("testIgnored", skippedCase.name)
        assertTrue(skippedCase.isSkipped)
    }

    @Test
    fun parseParameterizedSuite() {
        val xml = loadFixture("junit_parameterized.xml")
        val suites = JUnitXmlParser.parseXml(xml)

        assertEquals(1, suites.size)
        val suite = suites.first()
        assertEquals(3, suite.totalCount)
        assertEquals(3, suite.passedCount)
        assertEquals("testCalculation[0: 1 + 1 = 2]", suite.cases[0].name)
        assertEquals("testCalculation[1: 2 + 2 = 4]", suite.cases[1].name)
        assertEquals("testCalculation[2: 3 + 3 = 6]", suite.cases[2].name)
    }

    @Test
    fun parseMultiSuitesInSingleDocument() {
        val xml = loadFixture("junit_multi_suites.xml")
        val suites = JUnitXmlParser.parseXml(xml)

        assertEquals(2, suites.size)
        assertEquals("com.codeforge.core.domain.parser.SuiteA", suites[0].className)
        assertEquals("com.codeforge.core.domain.parser.SuiteB", suites[1].className)

        val suiteB = suites[1]
        val caseB = suiteB.cases.first()
        assertEquals(TestStatus.FAILED, caseB.status)
        assertEquals("true", caseB.failureDetail?.expected)
        assertEquals("false", caseB.failureDetail?.actual)
    }

    @Test
    fun buildTestRunResultCombinesModulesAndFiles() {
        val tempDir = File.createTempFile("junit_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val fileA = File(tempDir, "TEST-A.xml").apply { writeText(loadFixture("junit_passed.xml")) }
            val fileB = File(tempDir, "TEST-B.xml").apply { writeText(loadFixture("junit_failed.xml")) }

            val moduleMap = mapOf(
                ":core:domain" to listOf(fileA, fileB),
                ":core:data" to listOf(fileA)
            )

            val result = JUnitXmlParser.buildTestRunResult(moduleMap)

            assertEquals(2, result.moduleSuites.size)
            assertEquals(6, result.totalCount)
            assertEquals(5, result.passedCount)
            assertEquals(1, result.failedCount)
            assertTrue(result.hasFailures)
            assertEquals(1, result.allFailedCases.size)
            assertEquals("testAssertionFailure", result.allFailedCases.first().name)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
