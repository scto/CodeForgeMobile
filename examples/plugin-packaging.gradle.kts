/**
 * Gemeinsames Packaging aller LSP-Beispiel-Plugins (per `apply(from = …)` eingebunden).
 * Erzeugt classes.dex (d8) und legt Ergebnis nach `examples/out/<modulname>/`:
 *   plugin.json, classes.dex und <modulname>.zip (das installierbare Archiv).
 * Benötigt ANDROID_HOME/ANDROID_SDK_ROOT (build-tools mit d8) und die android.jar einer Plattform.
 */
val commonProject = project(":examples:lsp-plugin-common")

val dexClasses by tasks.registering(Exec::class) {
    dependsOn("compileKotlin", ":examples:lsp-plugin-common:compileKotlin")
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val classDirs = listOf(
        layout.buildDirectory.dir("classes/kotlin/main"),
        commonProject.layout.buildDirectory.dir("classes/kotlin/main")
    )
    val dexOutputDir = layout.buildDirectory.dir("dex")

    doFirst {
        requireNotNull(androidHome) { "ANDROID_HOME oder ANDROID_SDK_ROOT muss gesetzt sein." }
        dexOutputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val buildTools = file("$androidHome/build-tools").listFiles()?.maxByOrNull { it.name }
            ?: error("Keine Build-Tools unter $androidHome/build-tools")
        val d8 = File(buildTools, "d8")
        check(d8.isFile) { "d8 nicht gefunden: ${d8.path}" }
        val platformJar = file("$androidHome/platforms").listFiles()?.sortedBy { it.name }?.lastOrNull()
            ?.let { File(it, "android.jar") }?.takeIf { it.isFile }
        val classFiles = classDirs.flatMap { dir -> fileTree(dir).matching { include("**/*.class") }.files }
        check(classFiles.isNotEmpty()) { "Keine .class-Dateien gefunden." }
        // Host-Typen (plugin-api, domain, coroutines, stdlib) liegen zur Laufzeit im App-Prozess → nur als --classpath.
        val libs = configurations.getByName("compileClasspath").files.filter { it.exists() }
        val args = mutableListOf(d8.absolutePath, "--min-api", "26", "--output", dexOutputDir.get().asFile.absolutePath)
        platformJar?.let { args += listOf("--lib", it.absolutePath) }
        libs.forEach { args += listOf("--classpath", it.absolutePath) }
        args += classFiles.map { it.absolutePath }
        commandLine(args)
    }
}

val outDir = rootProject.layout.projectDirectory.dir("examples/out/${project.name}")

val packagePlugin by tasks.registering(Zip::class) {
    dependsOn(dexClasses)
    archiveFileName.set("${project.name}.zip")
    destinationDirectory.set(outDir)
    from(projectDir) { include("plugin.json") }
    from(layout.buildDirectory.dir("dex")) { include("classes.dex") }
    doLast {
        // Entpackte Kopie neben dem ZIP — zum Prüfen/Kopieren einzelner Dateien.
        copy {
            from(projectDir) { include("plugin.json") }
            from(layout.buildDirectory.dir("dex")) { include("classes.dex") }
            into(outDir)
        }
    }
}
