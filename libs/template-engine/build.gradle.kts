import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.template_engine"
}

/**
 * Packt die mitgelieferten Projektvorlagen (`project-templates/src/com.codeforge.plugin.android-*.template`) als
 * Plugin-ZIPs in die Assets (`builtin-templates/<id>.zip`); `BuiltinTemplateInstaller` installiert sie beim Start.
 * Einträge sortiert, feste Zeitstempel – reproduzierbar.
 */
abstract class PackBuiltinTemplates : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val target = outputDir.get().asFile.resolve("builtin-templates")
        target.deleteRecursively()
        target.mkdirs()
        sources.files.filter { it.isDirectory }.sortedBy { it.name }.forEach { folder ->
            ZipOutputStream(target.resolve("${folder.name}.zip").outputStream()).use { zip ->
                folder.walkTopDown().filter { it.isFile }.map { it.relativeTo(folder).invariantSeparatorsPath to it }
                    .sortedBy { it.first }.forEach { (rel, file) ->
                        zip.putNextEntry(ZipEntry(rel).apply { time = FIXED_TIME })
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
        }
    }

    private companion object {
        const val FIXED_TIME = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    }
}

val packBuiltinTemplates = tasks.register<PackBuiltinTemplates>("packBuiltinTemplates") {
    sources.from(
        rootProject.file("project-templates/src").listFiles { f -> f.isDirectory && f.name.matches(Regex("com\\.codeforge\\.plugin\\.android-[a-z0-9-]+\\.template")) }
            .orEmpty().sortedBy { it.name }
    )
    outputDir.set(layout.buildDirectory.dir("generated/builtinTemplates"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(packBuiltinTemplates, PackBuiltinTemplates::outputDir)
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
