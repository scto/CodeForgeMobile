/**
 * Gemeinsames Packen der Code-Plugins unter `code-plugins/` (per `apply(from = "../code-plugin.gradle.kts")`).
 *
 * Android-Library-Module (Compose möglich). Gedext werden nur die eigenen Klassen: SDK, Compose, Coroutines und
 * Kotlin kommen zur Laufzeit aus der App (DexClassLoader mit App-Classloader als Parent). Ergebnis:
 * `code-plugins/out/<modul>/<plugin-id>.zip` mit plugin.json, classes.dex, build-info.json und – wenn
 * `CF_PLUGIN_SIGNING_KEY` auf einen EC-Schlüssel (PEM) zeigt – signature.json.
 *
 * Benötigt ANDROID_HOME (build-tools mit d8, eine Plattform) und für die Signatur `python3` + `openssl`.
 */
import org.gradle.api.attributes.Attribute

val pluginId: String = groovy.json.JsonSlurper().parse(file("plugin.json")).let { (it as Map<*, *>)["id"] as String }
val variant = "release"
val kotlinClasses = layout.buildDirectory.dir("tmp/kotlin-classes/$variant")
val dexDir = layout.buildDirectory.dir("plugin-dex")
val stageDir = layout.buildDirectory.dir("plugin-stage")
val outDir = rootProject.layout.projectDirectory.dir("code-plugins/out/${project.name}")

val dexPluginClasses by tasks.registering(Exec::class) {
    group = "codeforge plugin"
    description = "Dext die eigenen Klassen des Plugins (d8)."
    dependsOn("compile${variant.replaceFirstChar { it.uppercase() }}Kotlin")
    inputs.dir(kotlinClasses)
    outputs.dir(dexDir)
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    // Klassen-Jars des Compile-Classpaths (AARs entpackt), damit d8 Lambdas und Schnittstellen auflösen kann.
    val classpathJars = configurations.named("${variant}CompileClasspath").map { cfg ->
        cfg.incoming.artifactView { attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar") } }.files
    }
    doFirst {
        requireNotNull(androidHome) { "ANDROID_HOME oder ANDROID_SDK_ROOT muss gesetzt sein." }
        val out = dexDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val buildTools = file("$androidHome/build-tools").listFiles()?.filter { File(it, "d8").isFile }?.maxByOrNull { it.name }
            ?: error("Keine Build-Tools mit d8 unter $androidHome/build-tools")
        val platformJar = file("$androidHome/platforms").listFiles()?.sortedBy { it.name }?.lastOrNull()?.let { File(it, "android.jar") }
        val classes = fileTree(kotlinClasses).matching { include("**/*.class") }.files
        check(classes.isNotEmpty()) { "Keine .class-Dateien unter ${kotlinClasses.get().asFile}" }
        val args = mutableListOf(File(buildTools, "d8").absolutePath, "--release", "--min-api", "26", "--output", out.absolutePath)
        platformJar?.takeIf { it.isFile }?.let { args += listOf("--lib", it.absolutePath) }
        classpathJars.get().files.filter { it.isFile }.forEach { args += listOf("--classpath", it.absolutePath) }
        args += classes.map { it.absolutePath }
        commandLine(args)
    }
}

val stagePlugin by tasks.registering(Sync::class) {
    dependsOn(dexPluginClasses)
    from(projectDir) { include("plugin.json", "README.md") }
    from(dexDir) { include("classes.dex") }
    into(stageDir)
    doLast {
        val stage = stageDir.get().asFile
        // Herkunft (wie scripts/build_info.py): letzter Commit des Plugin-Ordners.
        fun git(vararg a: String) = runCatching {
            ProcessBuilder("git", *a).directory(projectDir).redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")
        val (commit, date) = git("log", "-1", "--format=%h|%cI", "--", projectDir.absolutePath).split("|").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        val dirty = git("status", "--porcelain", "--", projectDir.absolutePath).isNotEmpty()
        File(stage, "build-info.json").writeText(
            """{"commit": "${commit.ifEmpty { "unknown" }}", "dirty": $dirty, "builtAt": "$date", "builder": "gradle", "apiVersion": 4}""" + "\n"
        )
        System.getenv("CF_PLUGIN_SIGNING_KEY")?.takeIf { File(it).isFile }?.let { key ->
            val sign = rootProject.file("scripts/plugin-sign.py").absolutePath
            val p = ProcessBuilder("python3", sign, stage.absolutePath, key).inheritIO().start()
            check(p.waitFor() == 0) { "Signieren fehlgeschlagen" }
        }
    }
}

val packagePlugin by tasks.registering(Zip::class) {
    group = "codeforge plugin"
    description = "Baut das installierbare Plugin-ZIP nach code-plugins/out/<modul>/."
    dependsOn(stagePlugin)
    from(stageDir)
    archiveFileName.set("$pluginId.zip")
    destinationDirectory.set(outDir)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
