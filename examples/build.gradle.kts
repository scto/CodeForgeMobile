/**
 * Sammel-Task aller Beispiel-Plugins: `./gradlew :examples:packageAll`
 *
 * Baut jedes Code-Plugin (`packagePlugin`) und die deklarativen Plugins unter `examples/guide/` und legt alles nach
 * `examples/out/<modul>/` (`<modul>.zip` zum Installieren, daneben `plugin.json`; Code-Plugins zusätzlich `classes.dex`).
 * Benötigt ANDROID_HOME (d8) und `python3` (Prüfen und Packen der deklarativen Plugins mit `templates/build.py`).
 */
val outDir = layout.projectDirectory.dir("out")

/** Deklarative Guide-Beispiele (ohne Code): Ordner unter examples/guide mit plugin.json, aber ohne build.gradle.kts. */
val declarativeGuides: List<File> = layout.projectDirectory.dir("guide").asFile
    .listFiles { f -> File(f, "plugin.json").isFile && !File(f, "build.gradle.kts").isFile }
    .orEmpty().sortedBy { it.name }

val packageDeclarativeGuides by tasks.registering(Exec::class) {
    description = "Prüft und packt die deklarativen Guide-Beispiele nach examples/out/guide-<name>/."
    inputs.files(declarativeGuides)
    outputs.dir(outDir)
    commandLine(
        listOf("python3", rootProject.file("templates/build.py").absolutePath, "--out", outDir.asFile.absolutePath, "--prefix", "guide-") +
            declarativeGuides.map { it.absolutePath }
    )
}

tasks.register("packageAll") {
    group = "build"
    description = "Baut alle Beispiel-Plugins nach examples/out."
    dependsOn(packageDeclarativeGuides)
    dependsOn(subprojects.filter { it.buildFile.isFile }.map { "${it.path}:packagePlugin" }.filterNot { it.startsWith(":examples:lsp-plugin-common") })
}
