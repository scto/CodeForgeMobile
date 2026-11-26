import java.net.URI

plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.settings"
}

/**
 * Lädt den Store-Katalog beim Bauen als eingebaute Fassung in die Assets (`store/store.json`), damit der
 * Store auch beim ersten Start und offline sofort Inhalt zeigt. Ohne Netz bleibt die vorige Fassung;
 * gibt es keine, startet der Store wie bisher ohne eingebauten Katalog.
 */
abstract class FetchBundledStoreCatalog : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val target = outputDir.get().asFile.resolve("store/store.json")
        target.parentFile.mkdirs()
        val body = runCatching {
            val conn = URI(url.get()).toURL().openConnection()
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.getInputStream().bufferedReader().use { it.readText() }
        }.getOrNull()
        if (body != null && body.trimStart().startsWith("{") && body.contains("\"items\"")) {
            target.writeText(body)
        } else {
            logger.warn("Store-Katalog nicht geladen – eingebaute Fassung bleibt ${if (target.isFile) "unverändert" else "leer"}.")
        }
    }
}

val fetchBundledStoreCatalog = tasks.register<FetchBundledStoreCatalog>("fetchBundledStoreCatalog") {
    url.set("https://scto.github.io/codeforge-store/store.json")
    outputDir.set(layout.buildDirectory.dir("generated/storeCatalog"))
    // Höchstens alle 12 Stunden neu laden; Release-Builds in der CI starten ohnehin frisch.
    outputs.upToDateWhen {
        val file = outputDir.get().asFile.resolve("store/store.json")
        file.isFile && System.currentTimeMillis() - file.lastModified() < 12 * 60 * 60 * 1000L
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchBundledStoreCatalog, FetchBundledStoreCatalog::outputDir)
    }
}

dependencies {
    implementation(project(":libs:termux-shared"))
    implementation(project(":core:resources"))
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":libs:terminal-engine"))
    implementation(project(":feature:plugins"))
    implementation(project(":libs:ai-client"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
