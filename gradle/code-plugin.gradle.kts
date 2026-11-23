/**
 * Gemeinsames Packen aller Code-Plugins (`code-plugins/`, Beispiele, Vorlage) per
 * `apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))`.
 *
 * Unterstützt Android-Library-Module (Compose möglich) und reine Kotlin-Module. Gedext werden nur die eigenen Klassen:
 * SDK, Compose, Coroutines und Kotlin kommen zur Laufzeit aus der App (DexClassLoader mit App-Classloader als Parent).
 * Das ZIP enthält plugin.json, classes.dex, build-info.json (Herkunft) und – wenn `CF_PLUGIN_SIGNING_KEY` auf einen
 * EC-Schlüssel (PEM) zeigt – signature.json.
 *
 * Anpassbar über Eigenschaften, die vor dem `apply` gesetzt werden:
 * - `extra["pluginOutDir"]`: Ausgabeordner relativ zum Projekt (Standard `code-plugins/out/<modul>`).
 * - `extra["pluginArchiveName"]`: Dateiname des ZIP (Standard `<plugin-id>.zip`).
 * - `extra["pluginUnpackedCopy"] = true`: zusätzlich plugin.json und classes.dex entpackt neben das ZIP legen.
 *
 * Benötigt ANDROID_HOME (build-tools mit d8, eine Plattform) und für die Signatur `python3` + `openssl`.
 */
import org.gradle.api.attributes.Attribute

val manifest = groovy.json.JsonSlurper().parse(file("plugin.json")) as Map<*, *>
val pluginId: String = manifest["id"] as String
val pluginApiVersion: Int = (manifest["apiVersion"] as? Number)?.toInt() ?: 1
val isAndroidLibrary = plugins.hasPlugin("com.android.library")
val compileTaskName = if (isAndroidLibrary) "compileReleaseKotlin" else "compileKotlin"
val kotlinClasses = layout.buildDirectory.dir(if (isAndroidLibrary) "tmp/kotlin-classes/release" else "classes/kotlin/main")
val dexDir = layout.buildDirectory.dir("plugin-dex")
val stageDir = layout.buildDirectory.dir("plugin-stage")
val outDir = rootProject.layout.projectDirectory.dir(
    (findProperty("pluginOutDir") as? String) ?: "code-plugins/out/${project.name}",
)
val archiveName = (findProperty("pluginArchiveName") as? String) ?: "$pluginId.zip"
val unpackedCopy = findProperty("pluginUnpackedCopy") == true

val dexPluginClasses by tasks.registering(Exec::class) {
    group = "codeforge plugin"
    description = "Dext die eigenen Klassen des Plugins (d8)."
    dependsOn(compileTaskName)
    inputs.dir(kotlinClasses)
    outputs.dir(dexDir)
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    // Klassen des Compile-Classpaths (bei Android die entpackten AARs), damit d8 Lambdas und Schnittstellen auflöst.
    val classpath = if (isAndroidLibrary) {
        configurations.named("releaseCompileClasspath").map { cfg ->
            cfg.incoming.artifactView {
                attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar") }
            }.files
        }
    } else {
        configurations.named("compileClasspath").map { it as FileCollection }
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
        classpath.get().files.filter { it.exists() }.forEach { args += listOf("--classpath", it.absolutePath) }
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
        val (commit, date) = git("log", "-1", "--format=%h|%cI", "--", projectDir.absolutePath).split("|")
            .let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        val dirty = git("status", "--porcelain", "--", projectDir.absolutePath).isNotEmpty()
        File(stage, "build-info.json").writeText(
            """{"commit": "${commit.ifEmpty { "unknown" }}", "dirty": $dirty, "builtAt": "$date", "builder": "gradle", "apiVersion": $pluginApiVersion}""" + "\n",
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
    description = "Baut das installierbare Plugin-ZIP."
    dependsOn(stagePlugin)
    from(stageDir)
    archiveFileName.set(archiveName)
    destinationDirectory.set(outDir)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    if (unpackedCopy) {
        doLast {
            copy {
                from(stageDir) { include("plugin.json", "classes.dex") }
                into(outDir)
            }
        }
    }
}
