package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.applier.CodeReplacementApplier
import com.codeforge.libs.hardcodedstrings.applier.StringsXmlWriter
import com.codeforge.libs.hardcodedstrings.backup.BackupManager
import com.codeforge.libs.hardcodedstrings.extractor.KotlinStringExtractor
import com.codeforge.libs.hardcodedstrings.extractor.XmlStringExtractor
import com.codeforge.libs.hardcodedstrings.generator.AndroidStringEscaper
import com.codeforge.libs.hardcodedstrings.model.ExtractionPlan
import com.codeforge.libs.hardcodedstrings.model.ExtractionResult
import com.codeforge.libs.hardcodedstrings.model.FileReplacementPlan
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringsConfig
import com.codeforge.libs.hardcodedstrings.model.ProjectModuleInfo
import com.codeforge.libs.hardcodedstrings.resolver.AndroidModuleResolver
import java.io.File

/**
 * Main coordinator engine for finding and extracting hardcoded strings.
 */
class HardcodedStringsEngine {

    private val ignoredDirNames = setOf(
        "build", ".gradle", ".git", "node_modules", "bin", "out",
        ".idea", "target", ".cxx", ".externalNativeBuild"
    )

    /**
     * Scans a target path (file or directory) for hardcoded strings.
     */
    fun scan(
        targetPath: File,
        projectRoot: File,
        config: HardcodedStringsConfig = loadConfig(projectRoot)
    ): List<HardcodedStringFinding> {
        val kotlinExtractor = KotlinStringExtractor(config)
        val xmlExtractor = XmlStringExtractor(config)

        // Find module information
        val moduleInfo = AndroidModuleResolver.findEnclosingModule(targetPath, projectRoot)
        val existingXmlKeys = AndroidModuleResolver.loadExistingStringsXml(moduleInfo.stringsXmlFile)

        val filesToScan = mutableListOf<File>()
        if (targetPath.isFile) {
            filesToScan.add(targetPath)
        } else {
            collectSourceFiles(targetPath, filesToScan)
        }

        val allFindings = mutableListOf<HardcodedStringFinding>()

        for (file in filesToScan) {
            val relPath = file.relativeTo(projectRoot).path

            // Check if ignored by file pattern
            if (config.ignoredFilePatterns.any { pattern -> relPath.contains(pattern) }) {
                continue
            }

            // Skip test sources by default
            if (file.path.contains("/src/test/") || file.path.contains("/src/androidTest/") ||
                file.path.contains("\\src\\test\\") || file.path.contains("\\src\\androidTest\\")
            ) {
                continue
            }

            val ext = file.extension.lowercase()
            val content = try {
                file.readText()
            } catch (_: Exception) {
                continue
            }

            val fileModule = AndroidModuleResolver.findEnclosingModule(file, projectRoot)
            val fileXmlKeys = if (fileModule.moduleDir == moduleInfo.moduleDir) {
                existingXmlKeys
            } else {
                AndroidModuleResolver.loadExistingStringsXml(fileModule.stringsXmlFile)
            }

            val findings = when (ext) {
                "kt", "java" -> kotlinExtractor.extract(
                    file = file,
                    content = content,
                    relativePath = relPath,
                    moduleName = fileModule.moduleName,
                    existingXmlKeys = fileXmlKeys,
                    targetStringsXmlFile = fileModule.stringsXmlFile,
                    resNamespace = fileModule.resNamespace
                )
                "xml" -> xmlExtractor.extract(
                    file = file,
                    content = content,
                    relativePath = relPath,
                    moduleName = fileModule.moduleName,
                    existingXmlKeys = fileXmlKeys,
                    targetStringsXmlFile = fileModule.stringsXmlFile,
                    resNamespace = fileModule.resNamespace
                )
                else -> emptyList()
            }

            allFindings.addAll(findings)
        }

        return allFindings
    }

    /**
     * Creates an extraction plan with file diffs and strings.xml updates without modifying files.
     */
    fun createPlan(
        findings: List<HardcodedStringFinding>,
        projectRoot: File
    ): ExtractionPlan {
        val extractable = findings.filter { it.isExtractable }

        // 1. Group findings by source file to create replacement plans
        val byFile = extractable.groupBy { it.file }
        val filePlans = mutableListOf<FileReplacementPlan>()

        for ((file, fileFindings) in byFile) {
            val plan = CodeReplacementApplier.createPlanForFile(file, fileFindings)
            filePlans.add(plan)
        }

        // 2. Group findings by target strings.xml file
        val byStringsXml = extractable.groupBy { finding ->
            finding.targetStringsXmlFile ?: AndroidModuleResolver.findStringsXml(
                AndroidModuleResolver.findEnclosingModule(finding.file, projectRoot).moduleDir
            ) ?: File(projectRoot, "app/src/main/res/values/strings.xml")
        }

        val stringsXmlUpdates = mutableMapOf<File, String>()
        for ((xmlFile, xmlFindings) in byStringsXml) {
            val existingContent = if (xmlFile.exists()) xmlFile.readText() else null
            val newEntries = xmlFindings.map { finding ->
                val escaped = AndroidStringEscaper.escapeForXml(finding.rawValue, finding.isTemplate)
                finding.selectedKey to escaped.xmlValue
            }
            val merged = StringsXmlWriter.mergeEntries(existingContent, newEntries)
            stringsXmlUpdates[xmlFile] = merged
        }

        return ExtractionPlan(
            findings = findings,
            filePlans = filePlans,
            stringsXmlUpdates = stringsXmlUpdates,
            totalStringsCount = extractable.size
        )
    }

    /**
     * Atomically executes the extraction plan with automatic rollback on error.
     */
    fun applyPlan(
        plan: ExtractionPlan,
        projectRoot: File
    ): ExtractionResult {
        val backupManager = BackupManager(projectRoot)

        val filesToModify = mutableSetOf<File>()
        for (fp in plan.filePlans) {
            filesToModify.add(fp.file)
        }
        for (xmlFile in plan.stringsXmlUpdates.keys) {
            filesToModify.add(xmlFile)
        }

        // Create atomic backup before writing
        val backupDir = try {
            backupManager.createBackup(filesToModify)
        } catch (e: Exception) {
            return ExtractionResult(
                success = false,
                modifiedFiles = emptyList(),
                extractedCount = 0,
                errorMessage = "Backup failed: ${e.message}"
            )
        }

        val modifiedFiles = mutableListOf<File>()

        try {
            // Write strings.xml updates
            for ((xmlFile, content) in plan.stringsXmlUpdates) {
                xmlFile.parentFile?.mkdirs()
                xmlFile.writeText(content)
                modifiedFiles.add(xmlFile)
            }

            // Write code replacements
            for (fp in plan.filePlans) {
                fp.file.writeText(fp.newContent)
                modifiedFiles.add(fp.file)
            }

            return ExtractionResult(
                success = true,
                modifiedFiles = modifiedFiles,
                extractedCount = plan.totalStringsCount,
                backupDir = backupDir
            )
        } catch (e: Exception) {
            // Rollback immediately on failure
            backupManager.restoreBackup(backupDir)
            return ExtractionResult(
                success = false,
                modifiedFiles = emptyList(),
                extractedCount = 0,
                backupDir = backupDir,
                errorMessage = "Execution failed, rolled back: ${e.message}"
            )
        }
    }

    /**
     * Restores the most recent backup.
     */
    fun undoLastExtraction(projectRoot: File): Boolean {
        val backupManager = BackupManager(projectRoot)
        val latest = backupManager.getLatestBackup() ?: return false
        return backupManager.restoreBackup(latest)
    }

    /**
     * Loads project config from `.codeforge/hardcoded-strings.json` if it exists.
     */
    fun loadConfig(projectRoot: File): HardcodedStringsConfig {
        val configFile = File(projectRoot, ".codeforge/hardcoded-strings.json")
        if (!configFile.exists()) {
            return HardcodedStringsConfig()
        }

        return try {
            val text = configFile.readText()
            parseConfigJson(text)
        } catch (_: Exception) {
            HardcodedStringsConfig()
        }
    }

    /**
     * Saves project config to `.codeforge/hardcoded-strings.json`.
     */
    fun saveConfig(projectRoot: File, config: HardcodedStringsConfig) {
        val configFile = File(projectRoot, ".codeforge/hardcoded-strings.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText(serializeConfigJson(config))
    }

    private fun collectSourceFiles(dir: File, result: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!ignoredDirNames.contains(file.name)) {
                    collectSourceFiles(file, result)
                }
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext == "kt" || ext == "java" || ext == "xml") {
                    result.add(file)
                }
            }
        }
    }

    internal fun parseConfigJson(json: String): HardcodedStringsConfig {
        fun extractStringList(key: String): Set<String> {
            val pattern = Regex(""""$key"\s*:\s*\[([^\]]*)\]""")
            val match = pattern.find(json) ?: return emptySet()
            val items = match.groupValues[1].split(",")
            return items.map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }.toSet()
        }

        val ignoredKeys = extractStringList("ignoredKeys")
        val ignoredStrings = extractStringList("ignoredStrings")
        val ignoredPatterns = extractStringList("ignoredFilePatterns")

        return HardcodedStringsConfig(
            ignoredKeys = ignoredKeys,
            ignoredStrings = ignoredStrings,
            ignoredFilePatterns = ignoredPatterns
        )
    }

    internal fun serializeConfigJson(config: HardcodedStringsConfig): String {
        fun formatList(items: Set<String>): String {
            return items.joinToString(",\n    ") { "\"$it\"" }
        }

        return buildString {
            appendLine("{")
            appendLine("  \"ignoredKeys\": [")
            if (config.ignoredKeys.isNotEmpty()) {
                appendLine("    ${formatList(config.ignoredKeys)}")
            }
            appendLine("  ],")
            appendLine("  \"ignoredStrings\": [")
            if (config.ignoredStrings.isNotEmpty()) {
                appendLine("    ${formatList(config.ignoredStrings)}")
            }
            appendLine("  ],")
            appendLine("  \"ignoredFilePatterns\": [")
            if (config.ignoredFilePatterns.isNotEmpty()) {
                appendLine("    ${formatList(config.ignoredFilePatterns)}")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
}
