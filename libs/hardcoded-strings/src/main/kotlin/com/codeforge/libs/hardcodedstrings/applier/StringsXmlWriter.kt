package com.codeforge.libs.hardcodedstrings.applier

import java.io.File

/**
 * Safely updates or creates Android `res/values/strings.xml` files.
 * Preserves existing comments, structure, and formatting.
 */
object StringsXmlWriter {

    /**
     * Generates updated content for strings.xml with new string entries added.
     */
    fun mergeEntries(existingContent: String?, newEntries: List<Pair<String, String>>): String {
        if (newEntries.isEmpty()) {
            return existingContent ?: ""
        }

        if (existingContent.isNullOrBlank()) {
            val sb = StringBuilder()
            sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            sb.appendLine("<resources>")
            for ((key, value) in newEntries) {
                sb.appendLine("    <string name=\"$key\">$value</string>")
            }
            sb.appendLine("</resources>")
            return sb.toString()
        }

        // Find existing keys to avoid duplicates
        val existingKeys = mutableSetOf<String>()
        val keyRegex = Regex("""<string\s+[^>]*name\s*=\s*"([^"]+)"""")
        keyRegex.findAll(existingContent).forEach {
            existingKeys.add(it.groupValues[1])
        }

        val entriesToAdd = newEntries.filter { (k, _) -> !existingKeys.contains(k) }
        if (entriesToAdd.isEmpty()) {
            return existingContent
        }

        val closingTag = "</resources>"
        val index = existingContent.lastIndexOf(closingTag)
        if (index == -1) {
            // No </resources> tag, append clean resources block
            val sb = StringBuilder(existingContent.trimEnd())
            sb.appendLine()
            sb.appendLine("<resources>")
            for ((key, value) in entriesToAdd) {
                sb.appendLine("    <string name=\"$key\">$value</string>")
            }
            sb.appendLine("</resources>")
            return sb.toString()
        }

        val before = existingContent.substring(0, index).trimEnd()
        val after = existingContent.substring(index)

        val sb = StringBuilder()
        sb.appendLine(before)
        for ((key, value) in entriesToAdd) {
            sb.appendLine("    <string name=\"$key\">$value</string>")
        }
        sb.append(after)
        return sb.toString()
    }

    /**
     * Writes new entries to the target file.
     */
    fun writeToFile(targetFile: File, newEntries: List<Pair<String, String>>) {
        val existingContent = if (targetFile.exists()) targetFile.readText() else null
        val updated = mergeEntries(existingContent, newEntries)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(updated)
    }
}
