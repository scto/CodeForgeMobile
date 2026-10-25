package com.codeforge.libs.j2k.scanner

import com.codeforge.core.domain.model.J2kPlan
import com.codeforge.core.domain.model.J2kScanResult
import com.codeforge.core.domain.repository.ProjectLanguageScanner
import java.io.File

class ProjectLanguageScannerImpl : ProjectLanguageScanner {

    companion object {
        const val AUTO_PROMPT_PERCENTAGE_THRESHOLD = 70.0f
        val EXCLUDED_DIRS = setOf(
            "build", ".gradle", "Backup", ".git", ".cxx",
            "node_modules", "out", ".idea", ".agents"
        )
        val EXCLUDED_JAVA_FILES = setOf(
            "module-info.java",
            "package-info.java"
        )
    }

    override suspend fun scanProject(projectPath: String): J2kScanResult {
        val root = File(projectPath)
        if (!root.exists() || !root.isDirectory) {
            return J2kScanResult(
                projectPath = projectPath,
                javaFilesCount = 0,
                javaLinesCount = 0,
                kotlinFilesCount = 0,
                kotlinLinesCount = 0,
                javaPercentage = 0f,
                hasGroovyGradle = false,
                hasKotlinDsl = false,
                hasVersionCatalog = false,
                qualifiesForAutoPrompt = false
            )
        }

        var javaFiles = 0
        var javaLines = 0
        var kotlinFiles = 0
        var kotlinLines = 0
        var hasGroovy = false
        var hasKts = false

        root.walkTopDown()
            .onEnter { dir -> !EXCLUDED_DIRS.contains(dir.name) }
            .filter { it.isFile }
            .forEach { file ->
                val name = file.name
                when {
                    name.endsWith(".java") && !EXCLUDED_JAVA_FILES.contains(name) && !file.path.contains("/generated/") -> {
                        javaFiles++
                        javaLines += countLines(file)
                    }
                    name.endsWith(".kt") && !file.path.contains("/generated/") -> {
                        kotlinFiles++
                        kotlinLines += countLines(file)
                    }
                    name.endsWith(".gradle") -> {
                        hasGroovy = true
                    }
                    name.endsWith(".gradle.kts") -> {
                        hasKts = true
                    }
                }
            }

        val totalLines = javaLines + kotlinLines
        val percentage = if (totalLines > 0) {
            (javaLines.toFloat() / totalLines.toFloat()) * 100.0f
        } else if (javaFiles > 0) {
            (javaFiles.toFloat() / (javaFiles + kotlinFiles).toFloat()) * 100.0f
        } else {
            0.0f
        }

        val catalogFile = File(root, "gradle/libs.versions.toml")
        val qualifies = percentage >= AUTO_PROMPT_PERCENTAGE_THRESHOLD && javaFiles > 0

        return J2kScanResult(
            projectPath = projectPath,
            javaFilesCount = javaFiles,
            javaLinesCount = javaLines,
            kotlinFilesCount = kotlinFiles,
            kotlinLinesCount = kotlinLines,
            javaPercentage = percentage,
            hasGroovyGradle = hasGroovy,
            hasKotlinDsl = hasKts,
            hasVersionCatalog = catalogFile.exists(),
            qualifiesForAutoPrompt = qualifies
        )
    }

    override suspend fun createPlan(projectPath: String): J2kPlan {
        val root = File(projectPath)
        val javaFileList = mutableListOf<String>()
        val groovyFileList = mutableListOf<String>()
        val ktsFileList = mutableListOf<String>()
        var totalJavaLines = 0
        var totalKotlinLines = 0
        var totalKotlinFiles = 0
        val obstacles = mutableListOf<String>()

        if (root.exists() && root.isDirectory) {
            root.walkTopDown()
                .onEnter { dir -> !EXCLUDED_DIRS.contains(dir.name) }
                .filter { it.isFile }
                .forEach { file ->
                    val relPath = file.relativeTo(root).path
                    val name = file.name
                    when {
                        name.endsWith(".java") && !EXCLUDED_JAVA_FILES.contains(name) && !file.path.contains("/generated/") -> {
                            javaFileList.add(relPath)
                            totalJavaLines += countLines(file)
                        }
                        name.endsWith(".kt") && !file.path.contains("/generated/") -> {
                            totalKotlinFiles++
                            totalKotlinLines += countLines(file)
                        }
                        name.endsWith(".gradle") -> {
                            groovyFileList.add(relPath)
                        }
                        name.endsWith(".gradle.kts") -> {
                            ktsFileList.add(relPath)
                        }
                    }
                }
        }

        val totalLines = totalJavaLines + totalKotlinLines
        val percentage = if (totalLines > 0) {
            (totalJavaLines.toFloat() / totalLines.toFloat()) * 100.0f
        } else if (javaFileList.isNotEmpty()) {
            100.0f
        } else {
            0.0f
        }

        val hasCatalog = File(root, "gradle/libs.versions.toml").exists()

        if (groovyFileList.any { it.contains("buildSrc/src/main/groovy") }) {
            obstacles.add("buildSrc enthält Groovy-Quellklassen (müssen manuell migriert werden)")
        }

        return J2kPlan(
            projectPath = projectPath,
            totalJavaFiles = javaFileList.size,
            totalJavaLines = totalJavaLines,
            totalKotlinFiles = totalKotlinFiles,
            totalKotlinLines = totalKotlinLines,
            javaPercentage = percentage,
            javaFiles = javaFileList,
            groovyFiles = groovyFileList,
            ktsFiles = ktsFileList,
            hasExistingVersionCatalog = hasCatalog,
            detectedObstacles = obstacles
        )
    }

    private fun countLines(file: File): Int {
        return try {
            file.bufferedReader().useLines { lines -> lines.count { it.isNotBlank() } }
        } catch (_: Exception) {
            0
        }
    }
}
