/**
 * Modul: :libs:debug-client
 * @author Thomas Schmid
 */
package com.codeforge.libs.debug_client.mapping

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.ClassSymbolInfo
import com.codeforge.core.domain.model.SourceFileAnalysis
import com.codeforge.core.domain.repository.SourceToClassMapper
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceToClassMapperImpl @Inject constructor() : SourceToClassMapper {

    private val packageRegex = Regex("""^\s*package\s+([A-Za-z0-9_.]+)""")
    private val jvmNameRegex = Regex("""@file:\s*JvmName\s*\(\s*"([^"]+)"\s*\)""")
    private val kotlinClassDeclRegex = Regex("""\b(?:class|interface|object|enum\s+class|data\s+class|sealed\s+class|sealed\s+interface|value\s+class|annotation\s+class)\s+([A-Za-z0-9_]+)""")
    private val companionObjectRegex = Regex("""\bcompanion\s+object(?:\s+([A-Za-z0-9_]+))?""")
    private val javaClassDeclRegex = Regex("""\b(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+)*(?:class|interface|enum|record)\s+([A-Za-z0-9_]+)""")

    override fun analyzeSource(filePath: String, content: String): SourceFileAnalysis {
        val fileName = File(filePath).name
        val isKotlin = fileName.endsWith(".kt", ignoreCase = true)
        val fileSimpleName = fileName.substringBeforeLast('.')

        val lines = content.lines()
        var packageName = ""
        var jvmName: String? = null

        // 1. Paket & Dateianmerkungen ermitteln
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) continue

            val pkgMatch = packageRegex.find(trimmed)
            if (pkgMatch != null) {
                packageName = pkgMatch.groupValues[1].removeSuffix(";").trim()
            }

            if (isKotlin && jvmName == null) {
                val jvmMatch = jvmNameRegex.find(trimmed)
                if (jvmMatch != null) {
                    jvmName = jvmMatch.groupValues[1]
                }
            }
        }

        val facadeSimpleName = when {
            jvmName != null -> jvmName
            isKotlin -> "${fileSimpleName}Kt"
            else -> fileSimpleName
        }

        val facadeClassName = if (packageName.isNotEmpty()) "$packageName.$facadeSimpleName" else facadeSimpleName

        // 2. Klassen und ihre Zeilenbereiche ermitteln
        val detectedClasses = mutableListOf<ClassSymbolInfo>()
        val classStack = mutableListOf<ClassBuilder>()

        var inBlockComment = false

        lines.forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            var line = rawLine.trim()

            if (inBlockComment) {
                if (line.contains("*/")) {
                    line = line.substringAfter("*/").trim()
                    inBlockComment = false
                } else {
                    return@forEachIndexed
                }
            }

            if (line.contains("/*")) {
                val before = line.substringBefore("/*")
                val after = line.substringAfter("/*")
                if (after.contains("*/")) {
                    line = (before + " " + after.substringAfter("*/")).trim()
                } else {
                    line = before.trim()
                    inBlockComment = true
                }
            }

            if (line.startsWith("//")) return@forEachIndexed
            val cleanCode = line.substringBefore("//").trim()
            if (cleanCode.isEmpty()) return@forEachIndexed

            // Prüfe auf Klassendeklarationen
            val declMatch = if (isKotlin) {
                val compMatch = companionObjectRegex.find(cleanCode)
                if (compMatch != null) {
                    val compName = compMatch.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Companion"
                    compName
                } else {
                    kotlinClassDeclRegex.find(cleanCode)?.groupValues?.get(1)
                }
            } else {
                javaClassDeclRegex.find(cleanCode)?.groupValues?.get(1)
            }

            if (declMatch != null) {
                val parentClass = classStack.lastOrNull()
                val fqName = if (parentClass != null) {
                    "${parentClass.fullyQualifiedName}\$$declMatch"
                } else {
                    if (packageName.isNotEmpty()) "$packageName.$declMatch" else declMatch
                }

                val builder = ClassBuilder(
                    simpleName = declMatch,
                    fullyQualifiedName = fqName,
                    startLine = lineNumber,
                    isInner = parentClass != null,
                    braceDepthAtStart = classStack.sumOf { it.braceBalance }
                )
                classStack.add(builder)
            }

            // Geschweifte Klammern nachverfolgen
            val openCount = cleanCode.count { it == '{' }
            val closeCount = cleanCode.count { it == '}' }

            if (classStack.isNotEmpty()) {
                val current = classStack.last()
                current.braceBalance += (openCount - closeCount)

                // Wenn geschweifte Klammern geschlossen wurden
                while (classStack.isNotEmpty() && classStack.last().braceBalance <= 0 && classStack.last().sawOpeningBrace) {
                    val finished = classStack.removeAt(classStack.lastIndex)
                    detectedClasses.add(
                        ClassSymbolInfo(
                            simpleName = finished.simpleName,
                            fullyQualifiedName = finished.fullyQualifiedName,
                            startLine = finished.startLine,
                            endLine = lineNumber,
                            isFacade = false,
                            isInner = finished.isInner
                        )
                    )
                }

                if (openCount > 0 && classStack.isNotEmpty()) {
                    classStack.last().sawOpeningBrace = true
                }
            }
        }

        // Falls noch offene Klassen vorhanden sind (z. B. bis Dateiende)
        while (classStack.isNotEmpty()) {
            val unclosed = classStack.removeAt(classStack.lastIndex)
            detectedClasses.add(
                ClassSymbolInfo(
                    simpleName = unclosed.simpleName,
                    fullyQualifiedName = unclosed.fullyQualifiedName,
                    startLine = unclosed.startLine,
                    endLine = lines.size,
                    isFacade = false,
                    isInner = unclosed.isInner
                )
            )
        }

        // 3. Ausführbare Zeilen bestimmen (Keine Kommentare, Leerzeilen, Importe oder Paketdeklarationen)
        val executableLines = mutableSetOf<Int>()
        inBlockComment = false

        lines.forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val trimmed = rawLine.trim()

            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false
                }
                return@forEachIndexed
            }

            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true
                }
                return@forEachIndexed
            }

            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) {
                return@forEachIndexed
            }

            // Deklarationszeilen ohne ausführbare Anweisungen ausschließen
            if (trimmed.startsWith("package ") ||
                trimmed.startsWith("import ") ||
                trimmed == "{" || trimmed == "}" ||
                trimmed == "};" || trimmed == "});"
            ) {
                return@forEachIndexed
            }

            // Reine Annotationen ohne Anweisung ausschließen
            if (trimmed.startsWith("@") && !trimmed.contains(" ") && !trimmed.contains("(")) {
                return@forEachIndexed
            }

            executableLines.add(lineNumber)
        }

        // Wenn Datei Top-Level-Code hat, auch Facade-Klasse erfassen
        val hasTopLevelCode = executableLines.any { line ->
            detectedClasses.none { cls -> line in cls.startLine..cls.endLine }
        }

        if (hasTopLevelCode || detectedClasses.isEmpty()) {
            detectedClasses.add(
                ClassSymbolInfo(
                    simpleName = facadeSimpleName,
                    fullyQualifiedName = facadeClassName,
                    startLine = 1,
                    endLine = lines.size,
                    isFacade = true,
                    isInner = false
                )
            )
        }

        return SourceFileAnalysis(
            filePath = filePath,
            packageName = packageName,
            jvmName = jvmName,
            facadeClassName = facadeClassName,
            classes = detectedClasses,
            executableLines = executableLines
        )
    }

    override fun resolveClassName(filePath: String, content: String, line: Int): String? {
        val analysis = analyzeSource(filePath, content)
        if (!isLineVerifiable(analysis, line)) {
            return null
        }

        // Sucht nach der am tiefsten verschachtelten Klasse, die diese Zeile umschließt
        val enclosingClass = analysis.classes
            .filter { !it.isFacade && line in it.startLine..it.endLine }
            .maxByOrNull { it.startLine }

        return enclosingClass?.fullyQualifiedName ?: analysis.facadeClassName
    }

    override fun isLineVerifiable(analysis: SourceFileAnalysis, line: Int): Boolean {
        return line in analysis.executableLines
    }

    override fun verifyBreakpoints(
        filePath: String,
        content: String,
        breakpoints: List<Breakpoint>
    ): List<Breakpoint> {
        val analysis = analyzeSource(filePath, content)
        return breakpoints.map { bp ->
            val verifiable = isLineVerifiable(analysis, bp.line)
            bp.copy(verified = verifiable)
        }
    }

    override fun findSourceFileForClass(className: String, sourceFiles: List<File>): File? {
        val rawSimpleName = className.substringAfterLast('.')
        val topLevelSimpleName = rawSimpleName.substringBefore('$')
        val sourceBaseName = if (topLevelSimpleName.endsWith("Kt")) {
            topLevelSimpleName.removeSuffix("Kt")
        } else {
            topLevelSimpleName
        }

        val pkgPath = className.substringBeforeLast('.', "").replace('.', File.separatorChar)

        // 1. Suche nach exakter Übereinstimmung (Name + Paket-Pfad)
        val matchByPathAndName = sourceFiles.firstOrNull { file ->
            val matchesName = file.nameWithoutExtension.equals(sourceBaseName, ignoreCase = true) ||
                file.nameWithoutExtension.equals(topLevelSimpleName, ignoreCase = true)
            matchesName && (pkgPath.isEmpty() || file.absolutePath.contains(pkgPath))
        }
        if (matchByPathAndName != null) return matchByPathAndName

        // 2. Suche nach einfachem Dateinamen
        val matchByName = sourceFiles.firstOrNull { file ->
            file.nameWithoutExtension.equals(sourceBaseName, ignoreCase = true) ||
                file.nameWithoutExtension.equals(topLevelSimpleName, ignoreCase = true)
        }
        if (matchByName != null) return matchByName

        // 3. Suche nach @file:JvmName oder Klassendeklaration im Inhalt
        return sourceFiles.firstOrNull { file ->
            if (!file.exists() || !file.isFile) return@firstOrNull false
            val text = runCatching { file.readText() }.getOrNull() ?: return@firstOrNull false
            text.contains("JvmName(\"$topLevelSimpleName\")") ||
                text.contains("class $topLevelSimpleName") ||
                text.contains("interface $topLevelSimpleName") ||
                text.contains("object $topLevelSimpleName")
        }
    }

    private class ClassBuilder(
        val simpleName: String,
        val fullyQualifiedName: String,
        val startLine: Int,
        val isInner: Boolean,
        val braceDepthAtStart: Int,
        var braceBalance: Int = 0,
        var sawOpeningBrace: Boolean = false
    )
}
