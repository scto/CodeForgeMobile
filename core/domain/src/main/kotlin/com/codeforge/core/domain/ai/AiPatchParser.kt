// Modul: :core:domain
package com.codeforge.core.domain.ai

import com.codeforge.core.domain.model.DiffLineType
import com.codeforge.core.domain.model.UnifiedDiffParser

object AiPatchParser {

    private val FILE_PATH_HEADER_REGEX = Regex(
        """(?:^|\n)(?:#{1,6}\s*(?:(?:File|Datei|Path|Pfad):\s*)?|(?:File|Datei|Path|Pfad):\s*)[`"']?([a-zA-Z0-9_\-./\\]+\.[a-zA-Z0-9]+)[`"']?\s*\n```[a-zA-Z0-9_-]*\s*\n([\s\S]*?)```"""
    )

    private val CODE_BLOCK_WITH_PATH_COMMENT = Regex(
        """```[a-zA-Z0-9_-]*\s*\n(?://|#|<!--)\s*(?:file|filepath|datei|pfad):\s*([a-zA-Z0-9_\-./\\]+)\s*(?:-->)?\n([\s\S]*?)```"""
    )

    private val DIFF_BLOCK_REGEX = Regex(
        """```(?:diff|patch)?\n([\s\S]*?)```"""
    )

    fun parseProposedChanges(
        assistantText: String,
        currentFilePath: String? = null,
        currentFileContent: String? = null
    ): List<AiProposedChange> {
        val changes = mutableListOf<AiProposedChange>()

        // 1. Try unified diff blocks first
        val diffMatches = DIFF_BLOCK_REGEX.findAll(assistantText)
        for (match in diffMatches) {
            var blockContent = match.groupValues[1]
            if (blockContent.contains("--- ") && blockContent.contains("+++ ") && blockContent.contains("@@ ")) {
                if (!blockContent.contains("diff --git ")) {
                    val filePathMatch = Regex("""\+\+\+ (?:b/)?([^\s\n\r]+)""").find(blockContent)
                    val path = filePathMatch?.groupValues?.get(1) ?: currentFilePath ?: "file"
                    blockContent = "diff --git a/$path b/$path\n" + blockContent
                }
                val parsedFiles = UnifiedDiffParser.parse(blockContent)
                for (file in parsedFiles) {
                    val targetPath = if (file.newPath != "/dev/null") file.newPath else file.oldPath
                    val original = if (targetPath == currentFilePath) (currentFileContent ?: "") else ""
                    val proposed = applyDiffToFile(original, file)
                    changes.add(
                        AiProposedChange(
                            filePath = targetPath,
                            originalContent = original,
                            proposedContent = proposed,
                            unifiedDiff = blockContent
                        )
                    )
                }
            }
        }

        if (changes.isNotEmpty()) return changes

        // 2. Try code blocks with // file: path
        val commentMatches = CODE_BLOCK_WITH_PATH_COMMENT.findAll(assistantText)
        for (match in commentMatches) {
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2]
            val original = if (path == currentFilePath) (currentFileContent ?: "") else ""
            changes.add(
                AiProposedChange(
                    filePath = path,
                    originalContent = original,
                    proposedContent = content,
                    unifiedDiff = null
                )
            )
        }

        if (changes.isNotEmpty()) return changes

        // 3. Try header matches (### Datei: path \n ``` ... ```)
        val headerMatches = FILE_PATH_HEADER_REGEX.findAll(assistantText)
        for (match in headerMatches) {
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2]
            val original = if (path == currentFilePath) (currentFileContent ?: "") else ""
            changes.add(
                AiProposedChange(
                    filePath = path,
                    originalContent = original,
                    proposedContent = content,
                    unifiedDiff = null
                )
            )
        }

        // 4. If nothing else and there's a single code block and active file is known
        if (changes.isEmpty() && currentFilePath != null && currentFileContent != null) {
            val allBlocks = DIFF_BLOCK_REGEX.findAll(assistantText).toList()
            if (allBlocks.size == 1) {
                val singleContent = allBlocks[0].groupValues[1]
                if (singleContent.isNotBlank() && !singleContent.startsWith("--- ")) {
                    changes.add(
                        AiProposedChange(
                            filePath = currentFilePath,
                            originalContent = currentFileContent,
                            proposedContent = singleContent,
                            unifiedDiff = null
                        )
                    )
                }
            }
        }

        return changes
    }

    private fun applyDiffToFile(original: String, diffFile: com.codeforge.core.domain.model.DiffFile): String {
        val origLines = if (original.isEmpty()) emptyList() else original.lines()
        val result = mutableListOf<String>()
        var origIndex = 0

        for (hunk in diffFile.hunks) {
            val match = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*""").matchEntire(hunk.header)
            val hunkOldStart = match?.groupValues?.get(1)?.toIntOrNull() ?: (origIndex + 1)
            val targetZeroIndex = (hunkOldStart - 1).coerceAtLeast(0)

            while (origIndex < targetZeroIndex && origIndex < origLines.size) {
                result.add(origLines[origIndex])
                origIndex++
            }

            for (line in hunk.lines) {
                when (line.type) {
                    DiffLineType.CONTEXT -> {
                        if (origIndex < origLines.size) {
                            result.add(origLines[origIndex])
                            origIndex++
                        } else {
                            result.add(line.text)
                        }
                    }
                    DiffLineType.REMOVED -> {
                        if (origIndex < origLines.size) {
                            origIndex++
                        }
                    }
                    DiffLineType.ADDED -> {
                        result.add(line.text)
                    }
                    DiffLineType.NO_NEWLINE -> Unit
                }
            }
        }

        while (origIndex < origLines.size) {
            result.add(origLines[origIndex])
            origIndex++
        }

        return result.joinToString("\n")
    }
}
