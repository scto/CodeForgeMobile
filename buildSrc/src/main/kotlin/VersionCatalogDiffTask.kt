import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.LocalDateTime

/**
 * Vergleicht zwei Gradle Version Catalogs (libs.versions.toml).
 * Nutzung: register<VersionCatalogDiffTask>("diffVersionCatalogs") { ... }
 */
abstract class VersionCatalogDiffTask : DefaultTask() {

    @get:InputFile
    abstract val baselineCatalog: RegularFileProperty

    @get:InputFile
    abstract val compareCatalog: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        group = "verification"
        description = "Vergleicht zwei Version-Catalog TOML-Dateien und listet Diffs auf."
    }

    @TaskAction
    fun diff() {
        val baselineFile = baselineCatalog.get().asFile
        val compareFile = compareCatalog.get().asFile

        require(baselineFile.exists()) { "Baseline-Catalog nicht gefunden: $baselineFile" }
        require(compareFile.exists()) { "Compare-Catalog nicht gefunden: $compareFile" }

        val baseline = parseSections(baselineFile)
        val compare = parseSections(compareFile)

        val report = buildString {
            appendLine("# Version-Catalog-Diff-Report")
            appendLine("Generiert: ${LocalDateTime.now()}")
            appendLine("Baseline: ${baselineFile.path}")
            appendLine("Compare:  ${compareFile.path}")
            appendLine()
            listOf("versions", "libraries", "bundles", "plugins").forEach { section ->
                appendLine("## [$section]")
                appendDiff(baseline[section].orEmpty(), compare[section].orEmpty(), this)
                appendLine()
            }
        }

        val outFile = reportFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(report)

        logger.lifecycle(report)
        logger.lifecycle("Report geschrieben: ${outFile.path}")
    }

    private fun parseSections(file: File): Map<String, Map<String, String>> {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null

        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val sectionMatch = Regex("""^\[(\w+)]$""").find(line)
            if (sectionMatch != null) {
                current = sections.getOrPut(sectionMatch.groupValues[1]) { mutableMapOf() }
                return@forEach
            }

            val eq = line.indexOf('=')
            if (eq > 0 && current != null) {
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim().removeSurrounding("\"")
                current!![key] = value
            }
        }
        return sections
    }

    private fun appendDiff(
        baseline: Map<String, String>,
        compare: Map<String, String>,
        sb: StringBuilder
    ) {
        val added = compare.keys - baseline.keys
        val removed = baseline.keys - compare.keys
        val changed = baseline.keys.intersect(compare.keys)
            .filter { baseline[it] != compare[it] }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            sb.appendLine("Keine Unterschiede.")
            return
        }
        added.sorted().forEach { sb.appendLine("+ `$it = ${compare[it]}`") }
        removed.sorted().forEach { sb.appendLine("- `$it = ${baseline[it]}`") }
        changed.sorted().forEach {
            sb.appendLine("~ `$it`: `${baseline[it]}` → `${compare[it]}`")
        }
    }
}
