plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

// compileOnly: CodeForgePlugin, PluginContext, LspClientRepository, kotlinx-coroutines
// sind zur Laufzeit bereits im Host-App-Prozess vorhanden (siehe DexClassLoader-Parent-
// Delegation in PluginRuntime, :libs:plugin-api).
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":core:domain"))
    compileOnly(libs.kotlinx.coroutines.core)

    // implementation (nicht compileOnly): landet im eigenen classes.dex.
    implementation(project(":examples:lsp-plugin-common"))
}

kotlin {
    jvmToolchain(17)
}

/**
 * Konvertiert die kompilierten .class-Dateien via `d8` zu classes.dex. Siehe KDoc im
 * gleichnamigen Task in :examples:kotlin-lsp-plugin/build.gradle.kts — identisches
 * Vorgehen, hier nicht erneut kommentiert.
 */
val dexClasses by tasks.registering(Exec::class) {
    dependsOn("compileKotlin", ":examples:lsp-plugin-common:compileKotlin")

    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val ownClassesDir = layout.buildDirectory.dir("classes/kotlin/main")
    val commonClassesDir = project(":examples:lsp-plugin-common").layout.buildDirectory.dir("classes/kotlin/main")
    val dexOutputDir = layout.buildDirectory.dir("dex")

    doFirst {
        requireNotNull(androidHome) { "ANDROID_HOME oder ANDROID_SDK_ROOT muss gesetzt sein." }
        dexOutputDir.get().asFile.mkdirs()

        val buildToolsRoot = file("$androidHome/build-tools")
        val latestBuildTools = buildToolsRoot.listFiles()?.maxByOrNull { it.name }
            ?: error("Keine Build-Tools unter $buildToolsRoot gefunden.")
        val d8Binary = File(latestBuildTools, "d8")
        check(d8Binary.isFile) { "d8 nicht gefunden unter ${d8Binary.path}" }

        val classFiles = fileTree(ownClassesDir).matching { include("**/*.class") }.files +
            fileTree(commonClassesDir).matching { include("**/*.class") }.files
        check(classFiles.isNotEmpty()) { "Keine .class-Dateien gefunden — vorher compileKotlin ausführen." }

        commandLine(
            listOf(d8Binary.absolutePath, "--output", dexOutputDir.get().asFile.absolutePath) +
                classFiles.map { it.absolutePath }
        )
    }
}

val packagePlugin by tasks.registering(Zip::class) {
    dependsOn(dexClasses)
    archiveFileName.set("java-lsp-plugin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin-package"))

    from(projectDir) { include("plugin.json") }
    from(layout.buildDirectory.dir("dex")) { include("classes.dex") }
}
