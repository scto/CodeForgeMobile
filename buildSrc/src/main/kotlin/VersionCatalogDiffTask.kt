import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Vergleicht zwei Gradle Version Catalog (.toml) Dateien und listet
 * hinzugefügte, entfernte und geänderte Library-/Plugin-Versionen auf.
 */
abstract class VersionCatalogDiffTask : DefaultTask() {

    @get:InputFile
    abstract val baselineCatalog: Property<File>

    @get:InputFile
    abstract val compareCatalog: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    val baselineExists: Boolean
        get() = baselineCatalog.orNull?.exists() == true

    @TaskAction
    fun diff() {
        val baselineFile = baselineCatalog.orNull
        if (baselineFile == null || !baselineFile.exists()) {
            reportFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText("# Version Catalog Diff\n\n⚠️ Baseline-Datei nicht gefunden: ${baselineFile?.absolutePath}\n")
            }
            logger.warn("Baseline Version Catalog nicht gefunden, Diff übersprungen.")
            return
        }

        val baselineVersions = parseVersions(baselineFile.readText())
        val compareVersions = parseVersions(compareCatalog.get().asFile.readText())

        val added = compareVersions.keys - baselineVersions.keys
        val removed = baselineVersions.keys - compareVersions.keys
        val changed = compareVersions.keys.intersect(baselineVersions.keys)
            .filter { baselineVersions[it] != compareVersions[it] }

        val report = buildString {
            appendLine("# Version Catalog Diff")
            appendLine()
            appendLine("## Hinzugefügt (${added.size})")
            added.sorted().forEach { key ->
                appendLine("- `$key` = ${compareVersions[key]}")
            }
            appendLine()
            appendLine("## Entfernt (${removed.size})")
            removed.sorted().forEach { key ->
                appendLine("- `$key` (war: ${baselineVersions[key]})")
            }
            appendLine()
            appendLine("## Geändert (${changed.size})")
            changed.sorted().forEach { key ->
                appendLine("- `$key`: ${baselineVersions[key]} → ${compareVersions[key]}")
            }
        }

        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report)
        }

        logger.lifecycle("Version Catalog Diff: +${added.size} / -${removed.size} / ~${changed.size}")
    }

    private fun parseVersions(tomlContent: String): Map<String, String> {
        val versionRegex = Regex("""^\s*([a-zA-Z0-9_-]+)\s*=\s*"([^"]+)"""")
        val result = mutableMapOf<String, String>()
        var inVersionsSection = false

        tomlContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("[") -> inVersionsSection = line == "[versions]"
                inVersionsSection && line.isNotEmpty() && !line.startsWith("#") -> {
                    versionRegex.find(line)?.let { match ->
                        result[match.groupValues[1]] = match.groupValues[2]
                    }
                }
            }
        }
        return result
    }
}