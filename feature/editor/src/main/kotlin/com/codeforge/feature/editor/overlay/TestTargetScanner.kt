// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import androidx.compose.runtime.Immutable

/** Ausführbarer Test (Klasse oder Methode). */
@Immutable
data class TestTarget(
    val line: Int,
    val label: String,
    val filterTarget: String,
    val isClass: Boolean,
    /** Quelle `pluginId/providerId`, wenn der Test von einem Plugin stammt; sonst Gradle-Test. */
    val pluginRef: String? = null,
    /** Ergebnis des letzten Laufs (`null` = noch nicht gelaufen). */
    val passed: Boolean? = null,
    /** Screenshot-Test (`@PreviewTest` oder Roborazzi `captureRoboImage`). */
    val screenshot: Boolean = false
)

/** Findet Tests im Quelltext. */
object TestTargetScanner {

    private val TEST_ANNOTATION_NAMES = setOf(
        "Test",
        "org.junit.Test",
        "org.junit.jupiter.api.Test",
        "ParameterizedTest",
        "RepeatedTest",
        "TestFactory"
    )
    private const val PREVIEW_TEST = "PreviewTest"

    /** Tests der Datei. */
    fun scan(path: String, content: String): List<TestTarget> {
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext != "kt" && ext != "kts" && ext != "java") {
            return emptyList()
        }

        val cleanedLines = sanitizeCode(content)
        val packageName = extractPackage(cleanedLines)
        val targets = mutableListOf<TestTarget>()

        var currentClassName = ""
        var currentClassLine = -1
        var pendingTestAnnotation = false
        var pendingScreenshot = false
        // Roborazzi: jeder Test einer Datei mit captureRoboImage nimmt Screenshots auf.
        val roborazzi = content.contains("captureRoboImage")

        for ((lineIdx, line) in cleanedLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Annotationen: Test bzw. Screenshot-Test
            when (testKind(extractAnnotations(trimmed))) {
                AnnotationKind.TEST -> {
                    pendingTestAnnotation = true
                    pendingScreenshot = pendingScreenshot || roborazzi
                }
                AnnotationKind.PREVIEW_TEST -> {
                    pendingTestAnnotation = true
                    pendingScreenshot = true
                }
                AnnotationKind.NONE -> {}
            }

            // Check for class declaration
            val classMatch = extractClassName(trimmed, ext)
            if (classMatch != null) {
                currentClassName = classMatch
                currentClassLine = lineIdx
            }

            // Check for function/method declaration
            val funMatch = extractFunctionName(trimmed, ext)
            if (funMatch != null && pendingTestAnnotation) {
                val fullClass = if (packageName.isNotEmpty()) "$packageName.$currentClassName" else currentClassName
                val filterTarget = if (fullClass.isNotEmpty()) "$fullClass.$funMatch" else funMatch
                targets += TestTarget(lineIdx, funMatch, filterTarget, isClass = false, screenshot = pendingScreenshot)
                pendingTestAnnotation = false
                pendingScreenshot = false
            }
        }

        // If class contains test methods, add class target at classLine
        if (targets.any { !it.isClass } && currentClassName.isNotEmpty() && currentClassLine >= 0) {
            val fullClass = if (packageName.isNotEmpty()) "$packageName.$currentClassName" else currentClassName
            targets.add(
                0,
                TestTarget(
                    line = currentClassLine,
                    label = currentClassName,
                    filterTarget = fullClass,
                    isClass = true,
                    screenshot = targets.any { it.screenshot }
                )
            )
        }

        return targets.sortedBy { it.line }
    }

    private enum class AnnotationKind { NONE, TEST, PREVIEW_TEST }

    // Stärkste Test-Annotation der Zeile: @PreviewTest vor @Test.
    private fun testKind(annotations: List<String>): AnnotationKind {
        var kind = AnnotationKind.NONE
        for (anno in annotations) {
            val simple = anno.substringAfterLast('.')
            when {
                simple == PREVIEW_TEST -> return AnnotationKind.PREVIEW_TEST
                simple in TEST_ANNOTATION_NAMES || anno in TEST_ANNOTATION_NAMES -> kind = AnnotationKind.TEST
            }
        }
        return kind
    }

    private fun extractPackage(lines: List<String>): String {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                return trimmed.removePrefix("package ")
                    .substringBefore(';')
                    .trim()
            }
        }
        return ""
    }

    private fun extractAnnotations(line: String): List<String> {
        val annotations = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '@') {
                i++
                val start = i
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '.')) {
                    i++
                }
                if (i > start) {
                    annotations.add(line.substring(start, i))
                }
            } else {
                i++
            }
        }
        return annotations
    }

    private fun extractClassName(line: String, ext: String): String? {
        val words = line.split("\\s+".toRegex())
        val classIndex = words.indexOfFirst { it == "class" || it == "object" }
        if (classIndex >= 0 && classIndex + 1 < words.size) {
            val name = words[classIndex + 1].substringBefore('(').substringBefore('{').substringBefore(':')
            return name.takeIf { it.isNotBlank() && it[0].isJavaIdentifierStart() }
        }
        return null
    }

    private fun extractFunctionName(line: String, ext: String): String? {
        if (ext == "kt" || ext == "kts") {
            val funIdx = line.indexOf("fun ")
            if (funIdx >= 0) {
                val afterFun = line.substring(funIdx + 4).trimStart()
                val name = afterFun.substringBefore('(').substringBefore('<').trim()
                return name.takeIf { it.isNotBlank() && it[0].isJavaIdentifierStart() }
            }
        } else {
            // Java method: returnType methodName(...)
            if (line.contains('(') && !line.startsWith("if") && !line.startsWith("while") && !line.startsWith("for")) {
                val beforeParen = line.substringBefore('(').trim()
                val lastWord = beforeParen.substringAfterLast(' ').trim()
                if (lastWord.isNotBlank() && lastWord[0].isJavaIdentifierStart()) {
                    return lastWord
                }
            }
        }
        return null
    }

    /**
     * Entfernt Kommentare sowie Stringliterale, behält aber alle Zeilenumbrüche exakt bei.
     */
    internal fun sanitizeCode(source: String): List<String> {
        val sb = StringBuilder()
        var i = 0
        val n = source.length
        var inBlockComment = false
        var inLineComment = false
        var inString = false
        var inTripleString = false

        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else '\u0000'
            val next2 = if (i + 2 < n) source[i + 2] else '\u0000'

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false
                    sb.append(c)
                } else {
                    sb.append(' ')
                }
                i++
            } else if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false
                    sb.append("  ")
                    i += 2
                } else {
                    if (c == '\n') sb.append('\n') else sb.append(' ')
                    i++
                }
            } else if (inTripleString) {
                if (c == '"' && next == '"' && next2 == '"') {
                    inTripleString = false
                    sb.append("   ")
                    i += 3
                } else {
                    if (c == '\n') sb.append('\n') else sb.append(' ')
                    i++
                }
            } else if (inString) {
                if (c == '\\') {
                    sb.append("  ")
                    i += 2
                } else if (c == '"') {
                    inString = false
                    sb.append(' ')
                    i++
                } else {
                    if (c == '\n') sb.append('\n') else sb.append(' ')
                    i++
                }
            } else {
                if (c == '/' && next == '/') {
                    inLineComment = true
                    sb.append("  ")
                    i += 2
                } else if (c == '/' && next == '*') {
                    inBlockComment = true
                    sb.append("  ")
                    i += 2
                } else if (c == '"' && next == '"' && next2 == '"') {
                    inTripleString = true
                    sb.append("   ")
                    i += 3
                } else if (c == '"') {
                    inString = true
                    sb.append(' ')
                    i++
                } else {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString().lines()
    }
}
