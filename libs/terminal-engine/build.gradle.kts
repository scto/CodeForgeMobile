import java.net.URI

plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.terminal_engine"

    // HINWEIS: Der frühere externalNativeBuild-Block für die vendorte Termix-JNI-PTY-
    // Bridge (com.termux.terminal, src/main/jni/) wurde entfernt — dieser Code ist
    // gelöscht (siehe docs/sub/TERMUX-PORTING.md) und durch die Abhängigkeit auf
    // :libs:termux-emulator ersetzt, die ihre eigene native Bibliothek (libtermux.so)
    // mitbringt. Zwei Module mit derselben .so würden sonst beim APK-Packaging
    // kollidieren.
}

/**
 * Lädt beim Bauen die Release-Liste des SDK-Mirrors (`catalog.json` von scto/codeforge-tools) als eingebaute Fassung
 * in die Assets (`codeforge-tools-catalog.json`). [ScriptInstaller] legt sie neben `codeforge-env`; das Skript
 * nutzt sie, wenn weder Zwischenspeicher noch Netz da sind – der SDK-Manager zeigt so auch beim ersten Start und
 * offline sofort eine Liste (wie der Plugin-Store mit `store.json`). Ohne Netz bleibt die vorige Fassung.
 */
abstract class FetchBundledToolsCatalog : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val target = outputDir.get().asFile.resolve("codeforge-tools-catalog.json")
        target.parentFile.mkdirs()
        val body = runCatching {
            val conn = URI(url.get()).toURL().openConnection()
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.getInputStream().bufferedReader().use { it.readText() }
        }.getOrNull()
        if (body != null && body.trimStart().startsWith("[") && body.contains("\"tag_name\"")) {
            target.writeText(body)
        } else {
            logger.warn("SDK-Katalog nicht geladen – eingebaute Fassung bleibt ${if (target.isFile) "unverändert" else "leer"}.")
            if (!target.isFile) target.writeText("[]")
        }
    }
}

val fetchBundledToolsCatalog = tasks.register<FetchBundledToolsCatalog>("fetchBundledToolsCatalog") {
    url.set("https://raw.githubusercontent.com/scto/codeforge-tools/main/catalog.json")
    outputDir.set(layout.buildDirectory.dir("generated/toolsCatalog"))
    // Höchstens alle 12 Stunden neu laden; Release-Builds in der CI starten ohnehin frisch.
    outputs.upToDateWhen {
        val file = outputDir.get().asFile.resolve("codeforge-tools-catalog.json")
        file.isFile && System.currentTimeMillis() - file.lastModified() < 12 * 60 * 60 * 1000L
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchBundledToolsCatalog, FetchBundledToolsCatalog::outputDir)
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))
    implementation(project(":core:datastore"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.annotation)

    // Termux-Vendoring: ersetzt die bisherige Termix-com.termux.terminal-Kopie in
    // diesem Modul als Backing für TerminalSessionRepositoryImpl. Siehe docs/sub/TERMUX-PORTING.md.
    implementation(project(":libs:termux-emulator"))
    implementation(project(":libs:termux-shared"))

    testImplementation(libs.junit)
}
