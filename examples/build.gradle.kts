/**
 * Sammel-Task aller Beispiel-Plugins: `./gradlew :examples:packageAll`
 *
 * Baut jedes Code-Plugin (`packagePlugin`) und die deklarativen Plugins unter `examples/guide/` und legt alles nach
 * `examples/out/<modul>/` (`<modul>.zip` zum Installieren, daneben `plugin.json`; Code-Plugins zusätzlich `classes.dex`).
 * Benötigt ANDROID_HOME (d8) und `python3` (Prüfen und Packen der deklarativen Plugins mit `templates/build.py`).
 */
val outDir = layout.projectDirectory.dir("out")

/** Deklarative Plugins (ohne Code): Ordner mit plugin.json, aber ohne build.gradle.kts. */
fun declarativeIn(dir: File): List<File> =
    dir.listFiles { f -> File(f, "plugin.json").isFile && !File(f, "build.gradle.kts").isFile }.orEmpty().sortedBy { it.name }

val declarativeGuides: List<File> = declarativeIn(layout.projectDirectory.dir("guide").asFile)
val declarativeExamples: List<File> = declarativeIn(layout.projectDirectory.asFile)

val packageDeclarativeGuides by tasks.registering(Exec::class) {
    description = "Prüft und packt die deklarativen Guide-Beispiele nach examples/out/guide-<name>/."
    inputs.files(declarativeGuides)
    outputs.dir(outDir)
    commandLine(
        listOf("python3", rootProject.file("templates/build.py").absolutePath, "--out", outDir.asFile.absolutePath, "--prefix", "guide-") +
            declarativeGuides.map { it.absolutePath }
    )
}

val packageDeclarativeExamples by tasks.registering(Exec::class) {
    description = "Prüft und packt die deklarativen Beispiele (z. B. web-search-plugin) nach examples/out/<name>/."
    inputs.files(declarativeExamples)
    outputs.dir(outDir)
    commandLine(
        listOf("python3", rootProject.file("templates/build.py").absolutePath, "--out", outDir.asFile.absolutePath) +
            declarativeExamples.map { it.absolutePath }
    )
}

tasks.register("packageAll") {
    group = "build"
    description = "Baut alle Beispiel-Plugins nach examples/out."
    dependsOn(packageDeclarativeGuides, packageDeclarativeExamples)
    dependsOn(subprojects.filter { it.buildFile.isFile }.map { "${it.path}:packagePlugin" }.filterNot { it.startsWith(":examples:lsp-plugin-common") })
}
