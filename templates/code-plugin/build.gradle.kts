plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

// compileOnly: SDK und Coroutines sind zur Laufzeit im Host-Prozess vorhanden (Parent-Delegation in PluginRuntime).
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(libs.kotlinx.coroutines.core)
}

kotlin {
    jvmToolchain(17)
}

/** Konvertiert die .class-Dateien via `d8` zu classes.dex (benötigt ANDROID_HOME mit build-tools). */
val dexClasses by tasks.registering(Exec::class) {
    dependsOn("compileKotlin")

    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val ownClassesDir = layout.buildDirectory.dir("classes/kotlin/main")
    val dexOutputDir = layout.buildDirectory.dir("dex")

    doFirst {
        requireNotNull(androidHome) { "ANDROID_HOME oder ANDROID_SDK_ROOT muss gesetzt sein." }
        dexOutputDir.get().asFile.mkdirs()

        val buildToolsRoot = file("$androidHome/build-tools")
        val latestBuildTools = buildToolsRoot.listFiles()?.maxByOrNull { it.name }
            ?: error("Keine Build-Tools unter $buildToolsRoot gefunden.")
        val d8Binary = File(latestBuildTools, "d8")
        check(d8Binary.isFile) { "d8 nicht gefunden unter ${d8Binary.path}" }

        val classFiles = fileTree(ownClassesDir).matching { include("**/*.class") }.files
        check(classFiles.isNotEmpty()) { "Keine .class-Dateien gefunden — vorher compileKotlin ausführen." }

        commandLine(
            listOf(d8Binary.absolutePath, "--output", dexOutputDir.get().asFile.absolutePath) +
                classFiles.map { it.absolutePath }
        )
    }
}

val packagePlugin by tasks.registering(Zip::class) {
    dependsOn(dexClasses)
    archiveFileName.set("template-code-plugin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin-package"))

    from(projectDir) { include("plugin.json") }
    from(layout.buildDirectory.dir("dex")) { include("classes.dex") }
}
