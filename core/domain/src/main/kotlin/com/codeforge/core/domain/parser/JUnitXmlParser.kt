// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.StackFrame
import com.codeforge.core.domain.model.TestCaseResult
import com.codeforge.core.domain.model.TestClassSuite
import com.codeforge.core.domain.model.TestFailureDetail
import com.codeforge.core.domain.model.TestModuleSuite
import com.codeforge.core.domain.model.TestRunResult
import com.codeforge.core.domain.model.TestStatus
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

object JUnitXmlParser {

    private val STACK_FRAME_REGEX = Regex("""^\s*at\s+([a-zA-Z0-9_$]+(?:\.[a-zA-Z0-9_$]+)*)\.([a-zA-Z0-9_$<>]+)\(([^:)]+):(\d+)\)""")

    private val EXPECTED_ACTUAL_PATTERNS = listOf(
        Regex("""expected:\s*<([\s\S]*?)>\s*but was:\s*<([\s\S]*?)>"""),
        Regex("""expected:\s*"([\s\S]*?)"\s*but was:\s*"([\s\S]*?)""""),
        Regex("""expected\s*\[([\s\S]*?)\]\s*but was\s*\[([\s\S]*?)\]"""),
        Regex("""Expected:\s*<([\s\S]*?)>\s*but:\s*was\s*<([\s\S]*?)>""")
    )

    private fun createDocumentBuilderFactory(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (_: Throwable) {
            // Best effort feature disabling
        }
        return factory
    }

    fun parseXml(xmlContent: String): List<TestClassSuite> {
        if (xmlContent.isBlank()) return emptyList()
        return runCatching {
            parseInputStream(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        }.getOrDefault(emptyList())
    }

    fun parseFile(file: File): List<TestClassSuite> {
        if (!file.exists() || !file.isFile || file.length() == 0L) return emptyList()
        return runCatching {
            file.inputStream().use { parseInputStream(it) }
        }.getOrDefault(emptyList())
    }

    fun parseInputStream(inputStream: InputStream): List<TestClassSuite> {
        val factory = createDocumentBuilderFactory()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(inputStream)
        val root = document.documentElement ?: return emptyList()

        val suites = mutableListOf<TestClassSuite>()
        if (root.nodeName == "testsuite") {
            parseTestSuiteElement(root)?.let { suites.add(it) }
        } else if (root.nodeName == "testsuites") {
            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node is Element && node.nodeName == "testsuite") {
                    parseTestSuiteElement(node)?.let { suites.add(it) }
                }
            }
        }
        return suites
    }

    private fun parseTestSuiteElement(suiteElement: Element): TestClassSuite? {
        val suiteName = suiteElement.getAttribute("name").trim()
        val testCases = mutableListOf<TestCaseResult>()
        val suiteOut = getChildTextContent(suiteElement, "system-out")
        val suiteErr = getChildTextContent(suiteElement, "system-err")

        val children = suiteElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.nodeName == "testcase") {
                val testCase = parseTestCaseElement(node, suiteName, suiteOut, suiteErr)
                testCases.add(testCase)
            }
        }

        val className = if (testCases.isNotEmpty()) {
            testCases.first().className.ifBlank { suiteName }
        } else {
            suiteName
        }

        val packageName = if (className.contains('.')) {
            className.substringBeforeLast('.')
        } else {
            ""
        }

        return TestClassSuite(
            className = className,
            packageName = packageName,
            cases = testCases
        )
    }

    private fun parseTestCaseElement(
        testCaseElement: Element,
        fallbackClassName: String,
        suiteOut: String?,
        suiteErr: String?
    ): TestCaseResult {
        val name = testCaseElement.getAttribute("name").trim()
        val classNameAttr = testCaseElement.getAttribute("classname").trim()
        val className = if (classNameAttr.isNotBlank()) classNameAttr else fallbackClassName
        val timeAttr = testCaseElement.getAttribute("time").trim()
        val durationSeconds = timeAttr.toDoubleOrNull() ?: 0.0

        var status = TestStatus.PASSED
        var failureDetail: TestFailureDetail? = null

        val caseOut = getChildTextContent(testCaseElement, "system-out") ?: suiteOut
        val caseErr = getChildTextContent(testCaseElement, "system-err") ?: suiteErr

        val children = testCaseElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                when (node.nodeName) {
                    "failure" -> {
                        status = TestStatus.FAILED
                        failureDetail = parseFailureOrError(node)
                    }
                    "error" -> {
                        status = TestStatus.ERROR
                        failureDetail = parseFailureOrError(node)
                    }
                    "skipped" -> {
                        status = TestStatus.SKIPPED
                    }
                }
            }
        }

        return TestCaseResult(
            name = name,
            className = className,
            durationSeconds = durationSeconds,
            status = status,
            failureDetail = failureDetail,
            stdout = caseOut?.takeIf { it.isNotBlank() },
            stderr = caseErr?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseFailureOrError(element: Element): TestFailureDetail {
        val message = element.getAttribute("message").takeIf { it.isNotBlank() }
        val type = element.getAttribute("type").takeIf { it.isNotBlank() }
        val rawStackTrace = element.textContent ?: ""

        val (expected, actual) = extractExpectedAndActual(message, rawStackTrace)
        val stackFrames = extractStackFrames(rawStackTrace)

        return TestFailureDetail(
            message = message,
            type = type,
            stackTrace = rawStackTrace,
            expected = expected,
            actual = actual,
            stackFrames = stackFrames
        )
    }

    internal fun extractExpectedAndActual(message: String?, stackTrace: String): Pair<String?, String?> {
        val candidates = buildList {
            if (!message.isNullOrBlank()) add(message)
            val firstLines = stackTrace.lines().take(5).joinToString("\n")
            if (firstLines.isNotBlank()) add(firstLines)
        }

        for (candidate in candidates) {
            for (pattern in EXPECTED_ACTUAL_PATTERNS) {
                val match = pattern.find(candidate)
                if (match != null && match.groupValues.size >= 3) {
                    return match.groupValues[1] to match.groupValues[2]
                }
            }
        }
        return null to null
    }

    internal fun extractStackFrames(stackTrace: String): List<StackFrame> {
        if (stackTrace.isBlank()) return emptyList()
        val frames = mutableListOf<StackFrame>()
        for (line in stackTrace.lines()) {
            val match = STACK_FRAME_REGEX.find(line) ?: continue
            val className = match.groupValues[1]
            val methodName = match.groupValues[2]
            val fileName = match.groupValues[3]
            val lineNumber = match.groupValues[4].toIntOrNull() ?: continue
            frames.add(
                StackFrame(
                    className = className,
                    methodName = methodName,
                    fileName = fileName,
                    lineNumber = lineNumber,
                    rawLine = line.trim()
                )
            )
        }
        return frames
    }

    private fun getChildTextContent(parent: Element, tagName: String): String? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.nodeName == tagName) {
                val text = node.textContent?.trim()
                if (!text.isNullOrEmpty()) return text
            }
        }
        return null
    }

    /**
     * Baut ein zusammenfassendes [TestRunResult] aus einer Sammlung von Modul-Ergebnisdateien.
     */
    fun buildTestRunResult(
        moduleFiles: Map<String, List<File>>,
        timestamp: Long = System.currentTimeMillis()
    ): TestRunResult {
        val moduleSuites = mutableListOf<TestModuleSuite>()

        for ((gradlePath, files) in moduleFiles) {
            val classSuites = mutableListOf<TestClassSuite>()
            for (file in files) {
                classSuites.addAll(parseFile(file))
            }

            // Gruppiere nach Klassenname falls mehrere Dateien dieselbe Klasse betreffen
            val groupedByClass = classSuites.groupBy { it.className }
            val mergedClasses = groupedByClass.map { (className, suites) ->
                if (suites.size == 1) {
                    suites.first()
                } else {
                    TestClassSuite(
                        className = className,
                        packageName = suites.first().packageName,
                        cases = suites.flatMap { it.cases }
                    )
                }
            }.sortedBy { it.className }

            val moduleName = if (gradlePath == ":") "root" else gradlePath.removePrefix(":").replace(':', '-')
            moduleSuites.add(
                TestModuleSuite(
                    moduleGradlePath = gradlePath,
                    moduleName = moduleName,
                    classes = mergedClasses
                )
            )
        }

        return TestRunResult(
            moduleSuites = moduleSuites.sortedBy { it.moduleGradlePath },
            timestamp = timestamp
        )
    }
}
