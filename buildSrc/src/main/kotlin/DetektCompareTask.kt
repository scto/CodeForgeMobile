import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Vergleicht Detekt-XML-Reports (Baseline vs. aktueller Stand)
 * und erzeugt eine Markdown-Zusammenfassung der Delta-Findings.
 */
abstract class DetektCompareTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineReports: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compareReports: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun compare() {
        val baselineIssues = collectIssues(baselineReports.files)
        val compareIssues = collectIssues(compareReports.files)

        val newIssues = compareIssues - baselineIssues
        val resolvedIssues = baselineIssues - compareIssues

        val report = buildString {
            appendLine("# Detekt Compare Report")
            appendLine()
            appendLine("Baseline: ${baselineIssues.size} Findings")
            appendLine("Aktuell: ${compareIssues.size} Findings")
            appendLine()
            appendLine("## Neue Findings (${newIssues.size})")
            if (newIssues.isEmpty()) {
                appendLine("_Keine neuen Findings._")
            } else {
                newIssues.sortedBy { it.location }.forEach {
                    appendLine("- **${it.ruleId}** — `${it.location}`: ${it.message}")
                }
            }
            appendLine()
            appendLine("## Behobene Findings (${resolvedIssues.size})")
            if (resolvedIssues.isEmpty()) {
                appendLine("_Keine behobenen Findings._")
            } else {
                resolvedIssues.sortedBy { it.location }.forEach {
                    appendLine("- **${it.ruleId}** — `${it.location}`")
                }
            }
        }

        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report)
        }

        logger.lifecycle("Detekt Compare: +${newIssues.size} neue / -${resolvedIssues.size} behobene Findings")

        if (newIssues.isNotEmpty()) {
            logger.warn("⚠️ ${newIssues.size} neue Detekt-Findings gegenüber Baseline entdeckt.")
        }
    }

    private data class Issue(val ruleId: String, val location: String, val message: String)

    private fun collectIssues(files: Set<File>): Set<Issue> {
        val issues = mutableSetOf<Issue>()
        val factory = DocumentBuilderFactory.newInstance()

        files.filter { it.exists() }.forEach { file ->
            runCatching {
                val doc = factory.newDocumentBuilder().parse(file)
                val fileNodes = doc.getElementsByTagName("file")

                for (i in 0 until fileNodes.length) {
                    val fileNode = fileNodes.item(i)
                    val filePath = fileNode.attributes.getNamedItem("name")?.nodeValue ?: "unknown"
                    val errorNodes = (fileNode as org.w3c.dom.Element).getElementsByTagName("error")

                    for (j in 0 until errorNodes.length) {
                        val errorNode = errorNodes.item(j)
                        val attrs = errorNode.attributes
                        val ruleId = attrs.getNamedItem("source")?.nodeValue ?: "unknown"
                        val line = attrs.getNamedItem("line")?.nodeValue ?: "?"
                        val message = attrs.getNamedItem("message")?.nodeValue ?: ""
                        issues += Issue(ruleId, "$filePath:$line", message)
                    }
                }
            }.onFailure {
                logger.warn("Konnte Detekt-Report nicht parsen: ${file.absolutePath} (${it.message})")
            }
        }
        return issues
    }
}