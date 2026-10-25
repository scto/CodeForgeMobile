package com.codeforge.core.common

data class FileHeaderContext(
    val filename: String,
    val moduleName: String? = null,
    val packageName: String? = null,
    val author: String? = null,
    val date: String? = null,
    val year: String? = null,
    val projectName: String? = null
)

object FileHeaderRenderer {

    fun getDefaultTemplate(extension: String): String? {
        val ext = extension.lowercase().removePrefix(".")
        return when (ext) {
            "kt", "java" -> """
/**
 * Modul: {{module}}
 * @author {{author}}
 */
""".trimIndent()
            "sh", "bash" -> """
# Modul: {{module}}
# @author {{author}}
""".trimIndent()
            "xml", "html" -> """
<!--
  Modul: {{module}}
  @author {{author}}
-->
""".trimIndent()
            "yaml", "yml", "toml", "properties", "py" -> """
# Modul: {{module}}
# @author {{author}}
""".trimIndent()
            "kts", "gradle", "c", "cpp", "h" -> """
/**
 * Modul: {{module}}
 * @author {{author}}
 */
""".trimIndent()
            "json" -> null
            else -> null
        }
    }

    fun render(extension: String, context: FileHeaderContext, customTemplate: String? = null): String? {
        val rawTemplate = customTemplate ?: getDefaultTemplate(extension) ?: return null

        val lines = rawTemplate.lines().mapNotNull { line ->
            var l = line
            if (l.contains("{{module}}")) {
                if (context.moduleName.isNullOrBlank()) return@mapNotNull null
                l = l.replace("{{module}}", context.moduleName)
            }
            if (l.contains("{{author}}")) {
                if (context.author.isNullOrBlank()) return@mapNotNull null
                l = l.replace("{{author}}", context.author)
            }
            if (l.contains("{{filename}}")) {
                l = l.replace("{{filename}}", context.filename)
            }
            if (l.contains("{{package}}")) {
                if (context.packageName.isNullOrBlank()) return@mapNotNull null
                l = l.replace("{{package}}", context.packageName)
            }
            if (l.contains("{{date}}")) {
                if (context.date.isNullOrBlank()) return@mapNotNull null
                l = l.replace("{{date}}", context.date)
            }
            if (l.contains("{{year}}")) {
                if (context.year.isNullOrBlank()) return@mapNotNull null
                l = l.replace("{{year}}", context.year)
            }
            if (l.contains("{{project}}")) {
                if (context.projectName.isNullOrBlank()) return@mapNotNull null
                l = l.replace("{{project}}", context.projectName)
            }
            l
        }

        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    fun applyHeaderToContent(content: String, extension: String, context: FileHeaderContext, customTemplate: String? = null): String {
        val header = render(extension, context, customTemplate) ?: return content
        val ext = extension.lowercase().removePrefix(".")

        return when (ext) {
            "xml" -> {
                if (content.startsWith("<?xml")) {
                    val xmlEnd = content.indexOf("?>")
                    if (xmlEnd != -1) {
                        val decl = content.substring(0, xmlEnd + 2)
                        val rest = content.substring(xmlEnd + 2)
                        "$decl\n$header$rest"
                    } else "$header\n$content"
                } else "$header\n$content"
            }
            "sh", "bash" -> {
                if (content.startsWith("#!")) {
                    val firstNewline = content.indexOf("\n")
                    if (firstNewline != -1) {
                        val shebang = content.substring(0, firstNewline)
                        val rest = content.substring(firstNewline)
                        "$shebang\n$header$rest"
                    } else "$content\n$header"
                } else "$header\n$content"
            }
            "kt", "java" -> {
                val pkgIndex = content.indexOf("package ")
                if (pkgIndex != -1) {
                    val beforePkg = content.substring(0, pkgIndex)
                    val afterPkg = content.substring(pkgIndex)
                    "$beforePkg$header\n$afterPkg"
                } else "$header\n$content"
            }
            else -> "$header\n$content"
        }
    }
}
