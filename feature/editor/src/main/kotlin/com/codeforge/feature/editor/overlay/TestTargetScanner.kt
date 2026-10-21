// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import androidx.compose.runtime.Immutable

@Immutable
data class TestTarget(
    val line: Int,
    val label: String,
    val filterTarget: String,
    val isClass: Boolean
)

object TestTargetScanner {

    private val TEST_ANNOTATION_NAMES = setOf(
        "Test",
        "org.junit.Test",
        "org.junit.jupiter.api.Test",
        "ParameterizedTest",
        "RepeatedTest",
        "TestFactory"
    )

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
        var testAnnotationLine = -1

        for ((lineIdx, line) in cleanedLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check for annotations
            val annotationNames = extractAnnotations(trimmed)
            for (anno in annotationNames) {
                val simpleAnno = anno.substringAfterLast('.')
                if (simpleAnno in TEST_ANNOTATION_NAMES || anno in TEST_ANNOTATION_NAMES) {
                    pendingTestAnnotation = true
                    testAnnotationLine = lineIdx
                }
            }

            // Check for class declaration
            val classMatch = extractClassName(trimmed, ext)
            if (classMatch != null) {
                currentClassName = classMatch
                currentClassLine = lineIdx
            }

            // Check for function/method declaration
            val funMatch = extractFunctionName(trimmed, ext)
            if (funMatch != null) {
                if (pendingTestAnnotation) {
                    val fullClass = if (packageName.isNotEmpty()) "$packageName.$currentClassName" else currentClassName
                    val filterTarget = if (fullClass.isNotEmpty()) "$fullClass.$funMatch" else funMatch
                    targets.add(
                        TestTarget(
                            line = lineIdx,
                            label = funMatch,
                            filterTarget = filterTarget,
                            isClass = false
                        )
                    )
                    pendingTestAnnotation = false
                }
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
                    isClass = true
                )
            )
        }

        return targets.sortedBy { it.line }
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
