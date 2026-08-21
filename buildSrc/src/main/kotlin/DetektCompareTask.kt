import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.ConfigurableFileCollection
import java.io.File

/**
 * Vergleicht Detekt-XML-Reports (Checkstyle-Format) zweier Projektstände
 * anhand der Violation-Anzahl pro Rule-Set.
 */
abstract class DetektCompareTask : DefaultTask() {

    @get:InputFiles
    abstract val baselineReports: ConfigurableFileCollection

    @get:InputFiles
    abstract val compareReports: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        group = "verification"
        description = "Vergleicht Detekt-Violation-Counts zwischen Baseline und Compare-Projekt."
    }

    @TaskAction
    fun compare() {
        val mainCounts = aggregateCounts(baselineReports.files)
        val compareCounts = aggregateCounts(compareReports.files)

        val allRuleSets = (mainCounts.keys + compareCounts.keys).sorted()

        var totalMain = 0
        var totalCompare = 0

        val sb = StringBuilder()
        sb.appendLine("# Detekt-Vergleichsreport")
        sb.appendLine()
        sb.appendLine("| Rule-Set | Baseline | Compare | Delta |")
        sb.appendLine("|---|---|---|---|")

        allRuleSets.forEach { rule ->
            val m = mainCounts[rule] ?: 0
            val c = compareCounts[rule] ?: 0
            totalMain += m
            totalCompare += c
            val delta = c - m
            val marker = when {
                delta > 0 -> "+$delta ⚠️"
                delta < 0 -> "$delta ✅"
                else -> "0"
            }
            sb.appendLine("| $rule | $m | $c | $marker |")
        }

        val totalDelta = totalCompare - totalMain
        sb.appendLine("| **Total** | **$totalMain** | **$totalCompare** | **$totalDelta** |")

        val outFile = reportFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(sb.toString())

        logger.lifecycle(sb.toString())

        if (totalDelta > 0) {
            logger.warn("⚠️ Detekt-Regression: +$totalDelta neue Violations gegenüber Baseline.")
        }
    }

    private fun aggregateCounts(files: Set<File>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val regex = Regex("""source="detekt\.([\w.]+)\.\w+"""")

        files.filter { it.exists() }.forEach { file ->
            regex.findAll(file.readText()).forEach { match ->
                val ruleSet = match.groupValues[1]
                result[ruleSet] = (result[ruleSet] ?: 0) + 1
            }
        }
        return result
    }
}
