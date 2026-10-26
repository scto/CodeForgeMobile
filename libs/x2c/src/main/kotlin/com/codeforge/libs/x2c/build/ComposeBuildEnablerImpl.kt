package com.codeforge.libs.x2c.build

import com.codeforge.core.domain.repository.BuildEnableResult
import com.codeforge.core.domain.repository.ComposeBuildEnabler
import com.codeforge.core.domain.repository.PrerequisitesCheckResult
import java.io.File

/**
 * Task 52.4: Compose im Build ermöglichen.
 *
 * - Prüft Voraussetzungen (minSdk >= 21, Java 8+, AGP/Gradle).
 * - Groovy bleibt Groovy, Kotlin DSL bleibt Kotlin DSL.
 * - Ergänzt Kotlin-Plugin, falls nicht vorhanden.
 * - Aktiviert buildFeatures { compose = true }.
 * - Setzt Compose-Compiler je nach Kotlin-Version (ab Kotlin 2.0 Compose-Compiler-Plugin,
 *   davor composeOptions mit kotlinCompilerExtensionVersion).
 * - Ergänzt Compose-BOM, ui, ui-tooling-preview, material3, debugImplementation ui-tooling,
 *   optional activity-compose (Stufe 2) und constraintlayout-compose.
 * - Unterstützt Versionskatalog (libs.versions.toml) ohne Verlust bestehender Einträge/Kommentare.
 * - Keine doppelten Einträge und kein Eingriff in unbeteiligte Dateien.
 */
class ComposeBuildEnablerImpl(
    private val compatJsonProvider: (() -> String)? = null
) : ComposeBuildEnabler {

    companion object {
        const val OFFICIAL_MIN_SDK = 21
        const val DEFAULT_BOM = "2024.10.01"
        const val DEFAULT_KOTLIN = "2.0.21"
        const val DEFAULT_ACTIVITY_COMPOSE = "1.9.3"
        const val DEFAULT_CONSTRAINT_LAYOUT_COMPOSE = "1.1.0"
        const val OFFICIAL_SETUP_DOC_URL = "https://developer.android.com/jetpack/compose/setup (Stand: 2024-10-25)"
    }

    private val compatData: CompatData by lazy { loadCompatData() }

    override suspend fun checkPrerequisites(projectPath: String): Result<PrerequisitesCheckResult> {
        val projectDir = File(projectPath).canonicalFile
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return Result.failure(IllegalArgumentException("Projektverzeichnis existiert nicht: $projectPath"))
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val moduleBuildFiles = findAndroidModuleBuildFiles(projectDir)
        if (moduleBuildFiles.isEmpty()) {
            return Result.failure(IllegalStateException("Keine Android-Build-Dateien (build.gradle/build.gradle.kts) gefunden."))
        }

        var detectedMinSdk: Int? = null
        var isMinSdkSatisfied = true
        var detectedJavaVersion: String? = null
        var isJavaSatisfied = true
        var detectedKotlinVersion: String? = null
        var detectedAgpVersion: String? = null

        // Prüfe minSdk & Java in Modul-Build-Dateien
        for (file in moduleBuildFiles) {
            val text = file.readText()

            // minSdk Detection
            val minSdkMatch = Regex("""\b(?:minSdk|minSdkVersion)\s*(?:=|\(|\s)\s*(\d+)""").find(text)
            if (minSdkMatch != null) {
                val sdk = minSdkMatch.groupValues[1].toIntOrNull()
                if (sdk != null) {
                    detectedMinSdk = sdk
                    if (sdk < OFFICIAL_MIN_SDK) {
                        isMinSdkSatisfied = false
                        errors.add(
                            "Compose-Voraussetzung nicht erfüllt: minSdk ist $sdk in ${file.name}, erforderlich ist minSdk >= $OFFICIAL_MIN_SDK. Quelle: $OFFICIAL_SETUP_DOC_URL."
                        )
                    }
                }
            }

            // Java Version Detection
            val javaMatch = Regex("""\b(?:sourceCompatibility|targetCompatibility|jvmTarget)\s*(?:=|\(|\s)\s*(?:JavaVersion\.)?([A-Za-z0-9_"]+)""").find(text)
            if (javaMatch != null) {
                val javaVer = javaMatch.groupValues[1].replace("\"", "").replace("VERSION_", "")
                detectedJavaVersion = javaVer
                if (javaVer == "1_6" || javaVer == "1_7" || javaVer == "6" || javaVer == "7") {
                    isJavaSatisfied = false
                    errors.add("Compose-Voraussetzung nicht erfüllt: Java-Zielversion ist $javaVer in ${file.name}, erforderlich ist mindestens Java 8 (1.8).")
                }
            }
        }

        // Kotlin-Version im Projekt ermitteln
        detectedKotlinVersion = detectKotlinVersion(projectDir)
        detectedAgpVersion = detectAgpVersion(projectDir)

        return Result.success(
            PrerequisitesCheckResult(
                isMinSdkSatisfied = isMinSdkSatisfied,
                detectedMinSdk = detectedMinSdk,
                requiredMinSdk = OFFICIAL_MIN_SDK,
                isJavaVersionSatisfied = isJavaSatisfied,
                detectedJavaVersion = detectedJavaVersion,
                detectedKotlinVersion = detectedKotlinVersion,
                detectedAgpVersion = detectedAgpVersion,
                isAgpSatisfied = true,
                errors = errors,
                warnings = warnings
            )
        )
    }

    override suspend fun enableComposeInProject(
        projectPath: String,
        useVersionCatalog: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean,
        kotlinVersionOverride: String?
    ): Result<BuildEnableResult> {
        val projectDir = File(projectPath).canonicalFile
        val prereqResult = checkPrerequisites(projectPath)
        if (prereqResult.isFailure) {
            return Result.failure(prereqResult.exceptionOrNull()!!)
        }

        val prereq = prereqResult.getOrThrow()
        if (!prereq.isMinSdkSatisfied || !prereq.isJavaVersionSatisfied) {
            val errorMsg = prereq.errors.joinToString("\n")
            return Result.failure(IllegalStateException(errorMsg))
        }

        val effectiveKotlin = kotlinVersionOverride
            ?: prereq.detectedKotlinVersion
            ?: compatData.defaultKotlinVersion

        // Prüfe, ob Kotlin-Version ab 2.0 ist oder ComposeOptions-Mapping vorliegt
        val isKotlin2Plus = isVersionAtLeast(effectiveKotlin, "2.0.0")
        val compilerExtVersion = if (!isKotlin2Plus) {
            compatData.kotlinToCompiler[effectiveKotlin]?.extensionVersion
                ?: return Result.failure(
                    IllegalArgumentException(
                        "Unbekannte Kotlin-Version '$effectiveKotlin'. Bitte eine unterstützte Version auswählen oder konfigurieren."
                    )
                )
        } else {
            null
        }

        val modifiedFiles = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val addedDependencies = mutableListOf<String>()

        val moduleBuildFiles = findAndroidModuleBuildFiles(projectDir)
        if (moduleBuildFiles.isEmpty()) {
            return Result.failure(IllegalStateException("Keine Android-Modul-Build-Dateien gefunden."))
        }

        val catalogFile = File(projectDir, "gradle/libs.versions.toml")
        val hasCatalog = useVersionCatalog && (catalogFile.exists() || !moduleBuildFiles.first().readText().contains("libs."))

        // 1. Wenn Versionskatalog aktiv ist, libs.versions.toml aktualisieren
        if (useVersionCatalog) {
            val catalogResult = updateVersionCatalog(
                catalogFile = catalogFile,
                kotlinVersion = effectiveKotlin,
                isKotlin2Plus = isKotlin2Plus,
                wireActivities = wireActivities,
                hasConstraintLayout = hasConstraintLayout
            )
            if (catalogResult.modified) {
                modifiedFiles.add(catalogFile.relativeTo(projectDir).path.replace('\\', '/'))
                notes.addAll(catalogResult.notes)
            }
        }

        // 2. Jedes Android-Modul anpassen
        for (buildFile in moduleBuildFiles) {
            val relPath = buildFile.relativeTo(projectDir).path.replace('\\', '/')
            val isKts = buildFile.name.endsWith(".kts")
            var content = buildFile.readText()
            var modified = false

            // A. Kotlin-Plugin ergänzen, falls nicht vorhanden
            val (contentWithKotlin, kotlinAdded) = ensureKotlinPlugin(content, isKts)
            if (kotlinAdded) {
                content = contentWithKotlin
                modified = true
                notes.add("Kotlin-Android-Plugin in $relPath ergänzt.")
            }

            // B. Compose-Compiler-Plugin bei Kotlin >= 2.0
            if (isKotlin2Plus) {
                val (contentWithCompilerPlugin, pluginAdded) = ensureComposeCompilerPlugin(content, isKts)
                if (pluginAdded) {
                    content = contentWithCompilerPlugin
                    modified = true
                    notes.add("Compose-Compiler-Plugin (Kotlin 2.x) in $relPath ergänzt.")
                }
            }

            // C. buildFeatures { compose = true } und ggf. composeOptions
            val (contentWithFeatures, featuresModified) = ensureBuildFeaturesAndOptions(
                content,
                isKts,
                isKotlin2Plus,
                compilerExtVersion
            )
            if (featuresModified) {
                content = contentWithFeatures
                modified = true
                notes.add("buildFeatures { compose = true } in $relPath aktiviert.")
            }

            // D. Dependencies ergänzen
            val (contentWithDeps, depsAdded) = ensureDependencies(
                content = content,
                isKts = isKts,
                useVersionCatalog = useVersionCatalog,
                wireActivities = wireActivities,
                hasConstraintLayout = hasConstraintLayout
            )
            if (depsAdded.isNotEmpty()) {
                content = contentWithDeps
                modified = true
                addedDependencies.addAll(depsAdded)
                notes.add("${depsAdded.size} Compose-Abhängigkeiten in $relPath ergänzt.")
            }

            if (modified) {
                buildFile.writeText(content)
                if (!modifiedFiles.contains(relPath)) {
                    modifiedFiles.add(relPath)
                }
            }
        }

        // 3. Root-Build-Datei anpassen, falls Kotlin 2+ Plugin registriert werden muss
        val rootBuildKts = File(projectDir, "build.gradle.kts")
        val rootBuildGroovy = File(projectDir, "build.gradle")
        val rootBuildFile = if (rootBuildKts.exists()) rootBuildKts else if (rootBuildGroovy.exists()) rootBuildGroovy else null

        if (rootBuildFile != null && isKotlin2Plus) {
            val isKts = rootBuildFile.name.endsWith(".kts")
            val rootContent = rootBuildFile.readText()
            val (updatedRoot, rootModified) = ensureRootPluginRegistration(rootContent, isKts, useVersionCatalog, effectiveKotlin)
            if (rootModified) {
                rootBuildFile.writeText(updatedRoot)
                val rel = rootBuildFile.relativeTo(projectDir).path.replace('\\', '/')
                if (!modifiedFiles.contains(rel)) {
                    modifiedFiles.add(rel)
                }
                notes.add("Compose-Compiler-Plugin in Wurzel-Build-Datei $rel deklariert.")
            }
        }

        return Result.success(
            BuildEnableResult(
                modifiedFiles = modifiedFiles,
                notes = notes,
                addedDependencies = addedDependencies.distinct(),
                kotlinVersionUsed = effectiveKotlin,
                composeBomUsed = compatData.defaultComposeBom
            )
        )
    }

    private fun findAndroidModuleBuildFiles(projectDir: File): List<File> {
        val files = mutableListOf<File>()
        projectDir.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != "build" && name != ".gradle" && name != "Backup" && name != ".git" && name != ".cxx" && name != "node_modules"
            }
            .filter { it.isFile && (it.name == "build.gradle" || it.name == "build.gradle.kts") }
            .forEach { file ->
                val text = file.readText()
                if (text.contains("com.android.application") || text.contains("com.android.library") ||
                    text.contains("android {") || text.contains("android{")
                ) {
                    files.add(file)
                }
            }
        return files
    }

    private fun detectKotlinVersion(projectDir: File): String? {
        val catalog = File(projectDir, "gradle/libs.versions.toml")
        if (catalog.exists()) {
            val match = Regex("""\b(?:kotlin|org-jetbrains-kotlin-android)\s*=\s*"([^"]+)"""").find(catalog.readText())
            if (match != null) return match.groupValues[1]
        }

        val rootFiles = listOf(File(projectDir, "build.gradle.kts"), File(projectDir, "build.gradle"))
        for (rf in rootFiles) {
            if (!rf.exists()) continue
            val text = rf.readText()
            val pluginMatch = Regex("""id\(?["']org\.jetbrains\.kotlin\.android["']\)?\s+version\s+["']([^"']+)["']""").find(text)
            if (pluginMatch != null) return pluginMatch.groupValues[1]

            val classpathMatch = Regex("""org\.jetbrains\.kotlin:kotlin-gradle-plugin:([^"']+)""").find(text)
            if (classpathMatch != null) return classpathMatch.groupValues[1]
        }
        return null
    }

    private fun detectAgpVersion(projectDir: File): String? {
        val catalog = File(projectDir, "gradle/libs.versions.toml")
        if (catalog.exists()) {
            val match = Regex("""\b(?:agp|androidGradlePlugin|com-android-application)\s*=\s*"([^"]+)"""").find(catalog.readText())
            if (match != null) return match.groupValues[1]
        }

        val rootFiles = listOf(File(projectDir, "build.gradle.kts"), File(projectDir, "build.gradle"))
        for (rf in rootFiles) {
            if (!rf.exists()) continue
            val text = rf.readText()
            val match = Regex("""id\(?["']com\.android\.application["']\)?\s+version\s+["']([^"']+)["']""").find(text)
            if (match != null) return match.groupValues[1]

            val classpathMatch = Regex("""com\.android\.tools\.build:gradle:([^"']+)""").find(text)
            if (classpathMatch != null) return classpathMatch.groupValues[1]
        }
        return null
    }

    private fun ensureKotlinPlugin(content: String, isKts: Boolean): Pair<String, Boolean> {
        if (content.contains("kotlin.android") || content.contains("kotlin(\"android\")") || content.contains("kotlin-android")) {
            return content to false
        }

        val pluginsRegex = Regex("""plugins\s*\{""")
        val match = pluginsRegex.find(content)
        return if (match != null) {
            val insert = if (isKts) "    id(\"org.jetbrains.kotlin.android\")\n" else "    id 'org.jetbrains.kotlin.android'\n"
            val index = match.range.last + 1
            content.substring(0, index) + "\n" + insert + content.substring(index) to true
        } else {
            val apply = if (isKts) "apply(plugin = \"org.jetbrains.kotlin.android\")\n" else "apply plugin: 'org.jetbrains.kotlin.android'\n"
            apply + content to true
        }
    }

    private fun ensureComposeCompilerPlugin(content: String, isKts: Boolean): Pair<String, Boolean> {
        if (content.contains("kotlin.plugin.compose") || content.contains("compose.compiler")) {
            return content to false
        }

        val pluginsRegex = Regex("""plugins\s*\{""")
        val match = pluginsRegex.find(content)
        return if (match != null) {
            val insert = if (isKts) "    id(\"org.jetbrains.kotlin.plugin.compose\")\n" else "    id 'org.jetbrains.kotlin.plugin.compose'\n"
            val index = match.range.last + 1
            content.substring(0, index) + "\n" + insert + content.substring(index) to true
        } else {
            val apply = if (isKts) "apply(plugin = \"org.jetbrains.kotlin.plugin.compose\")\n" else "apply plugin: 'org.jetbrains.kotlin.plugin.compose'\n"
            apply + content to true
        }
    }

    private fun ensureBuildFeaturesAndOptions(
        content: String,
        isKts: Boolean,
        isKotlin2Plus: Boolean,
        compilerExtVersion: String?
    ): Pair<String, Boolean> {
        var modified = false
        var result = content

        // 1. buildFeatures { compose = true }
        if (!result.contains(Regex("""compose\s*=?\s*true"""))) {
            val buildFeaturesRegex = Regex("""buildFeatures\s*\{""")
            val bfMatch = buildFeaturesRegex.find(result)
            if (bfMatch != null) {
                val insert = if (isKts) "\n        compose = true" else "\n        compose = true"
                val idx = bfMatch.range.last + 1
                result = result.substring(0, idx) + insert + result.substring(idx)
                modified = true
            } else {
                val androidRegex = Regex("""android\s*\{""")
                val aMatch = androidRegex.find(result)
                if (aMatch != null) {
                    val bfBlock = if (isKts) {
                        "\n    buildFeatures {\n        compose = true\n    }\n"
                    } else {
                        "\n    buildFeatures {\n        compose = true\n    }\n"
                    }
                    val idx = aMatch.range.last + 1
                    result = result.substring(0, idx) + bfBlock + result.substring(idx)
                    modified = true
                }
            }
        }

        // 2. composeOptions { kotlinCompilerExtensionVersion = ... } für Kotlin < 2.0
        if (!isKotlin2Plus && compilerExtVersion != null && !result.contains("composeOptions")) {
            val androidRegex = Regex("""android\s*\{""")
            val aMatch = androidRegex.find(result)
            if (aMatch != null) {
                val coBlock = if (isKts) {
                    "\n    composeOptions {\n        kotlinCompilerExtensionVersion = \"$compilerExtVersion\"\n    }\n"
                } else {
                    "\n    composeOptions {\n        kotlinCompilerExtensionVersion = '$compilerExtVersion'\n    }\n"
                }
                val idx = aMatch.range.last + 1
                result = result.substring(0, idx) + coBlock + result.substring(idx)
                modified = true
            }
        }

        return result to modified
    }

    private fun ensureDependencies(
        content: String,
        isKts: Boolean,
        useVersionCatalog: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean
    ): Pair<String, List<String>> {
        val depsRegex = Regex("""dependencies\s*\{""")
        val match = depsRegex.find(content)
        val added = mutableListOf<String>()

        val newLines = StringBuilder()

        if (useVersionCatalog) {
            // Mit Versionskatalog
            if (!content.contains("androidx.compose.bom") && !content.contains("compose-bom") && !content.contains("compose.bom")) {
                newLines.append(if (isKts) "    implementation(platform(libs.androidx.compose.bom))\n" else "    implementation platform(libs.androidx.compose.bom)\n")
                added.add("androidx.compose:compose-bom (Katalog)")
            }
            if (!content.contains("libs.androidx.compose.ui") && !content.contains("compose:ui") && !content.contains("compose.ui")) {
                newLines.append(if (isKts) "    implementation(libs.androidx.compose.ui)\n" else "    implementation libs.androidx.compose.ui\n")
                added.add("androidx.compose.ui:ui (Katalog)")
            }
            if (!content.contains("androidx.compose.ui.tooling.preview") && !content.contains("ui-tooling-preview")) {
                newLines.append(if (isKts) "    implementation(libs.androidx.compose.ui.tooling.preview)\n" else "    implementation libs.androidx.compose.ui.tooling.preview\n")
                added.add("androidx.compose.ui:ui-tooling-preview (Katalog)")
            }
            if (!content.contains("androidx.compose.material3") && !content.contains("compose-material3") && !content.contains("material3")) {
                newLines.append(if (isKts) "    implementation(libs.androidx.compose.material3)\n" else "    implementation libs.androidx.compose.material3\n")
                added.add("androidx.compose.material3:material3 (Katalog)")
            }
            if (!content.contains("libs.androidx.compose.ui.tooling\"") && !content.contains("androidx.compose.ui.tooling)") && !content.contains("ui-tooling")) {
                newLines.append(if (isKts) "    debugImplementation(libs.androidx.compose.ui.tooling)\n" else "    debugImplementation libs.androidx.compose.ui.tooling\n")
                added.add("androidx.compose.ui:ui-tooling (Katalog)")
            }
            if (wireActivities && !content.contains("activity.compose") && !content.contains("activity-compose")) {
                newLines.append(if (isKts) "    implementation(libs.androidx.activity.compose)\n" else "    implementation libs.androidx.activity.compose\n")
                added.add("androidx.activity:activity-compose (Katalog)")
            }
            if (hasConstraintLayout && !content.contains("constraintlayout.compose") && !content.contains("constraintlayout-compose")) {
                newLines.append(if (isKts) "    implementation(libs.androidx.constraintlayout.compose)\n" else "    implementation libs.androidx.constraintlayout.compose\n")
                added.add("androidx.constraintlayout:constraintlayout-compose (Katalog)")
            }
        } else {
            // Direkt ohne Katalog
            val bom = compatData.defaultComposeBom
            if (!content.contains("compose-bom")) {
                newLines.append(
                    if (isKts) "    implementation(platform(\"androidx.compose:compose-bom:$bom\"))\n"
                    else "    implementation platform('androidx.compose:compose-bom:$bom')\n"
                )
                added.add("androidx.compose:compose-bom:$bom")
            }
            if (!content.contains("androidx.compose.ui:ui\"") && !content.contains("androidx.compose.ui:ui'")) {
                newLines.append(
                    if (isKts) "    implementation(\"androidx.compose.ui:ui\")\n"
                    else "    implementation 'androidx.compose.ui:ui'\n"
                )
                added.add("androidx.compose.ui:ui")
            }
            if (!content.contains("androidx.compose.ui:ui-tooling-preview")) {
                newLines.append(
                    if (isKts) "    implementation(\"androidx.compose.ui:ui-tooling-preview\")\n"
                    else "    implementation 'androidx.compose.ui:ui-tooling-preview'\n"
                )
                added.add("androidx.compose.ui:ui-tooling-preview")
            }
            if (!content.contains("androidx.compose.material3:material3")) {
                newLines.append(
                    if (isKts) "    implementation(\"androidx.compose.material3:material3\")\n"
                    else "    implementation 'androidx.compose.material3:material3'\n"
                )
                added.add("androidx.compose.material3:material3")
            }
            if (!content.contains(Regex("""androidx\.compose\.ui:ui-tooling["']"""))) {
                newLines.append(
                    if (isKts) "    debugImplementation(\"androidx.compose.ui:ui-tooling\")\n"
                    else "    debugImplementation 'androidx.compose.ui:ui-tooling'\n"
                )
                added.add("androidx.compose.ui:ui-tooling (debug)")
            }
            if (wireActivities && !content.contains("activity-compose")) {
                val actVer = compatData.defaultActivityComposeVersion
                newLines.append(
                    if (isKts) "    implementation(\"androidx.activity:activity-compose:$actVer\")\n"
                    else "    implementation 'androidx.activity:activity-compose:$actVer'\n"
                )
                added.add("androidx.activity:activity-compose:$actVer")
            }
            if (hasConstraintLayout && !content.contains("constraintlayout-compose")) {
                val clVer = compatData.defaultConstraintLayoutComposeVersion
                newLines.append(
                    if (isKts) "    implementation(\"androidx.constraintlayout:constraintlayout-compose:$clVer\")\n"
                    else "    implementation 'androidx.constraintlayout:constraintlayout-compose:$clVer'\n"
                )
                added.add("androidx.constraintlayout:constraintlayout-compose:$clVer")
            }
        }

        if (newLines.isEmpty()) {
            return content to emptyList()
        }

        return if (match != null) {
            val idx = match.range.last + 1
            content.substring(0, idx) + "\n" + newLines.toString() + content.substring(idx) to added
        } else {
            val block = "\ndependencies {\n$newLines}\n"
            content + block to added
        }
    }

    private data class CatalogUpdateResult(val modified: Boolean, val notes: List<String>)

    private fun updateVersionCatalog(
        catalogFile: File,
        kotlinVersion: String,
        isKotlin2Plus: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean
    ): CatalogUpdateResult {
        val notes = mutableListOf<String>()
        if (!catalogFile.exists()) {
            catalogFile.parentFile?.mkdirs()
            catalogFile.writeText(
                """
                [versions]
                compose-bom = "${compatData.defaultComposeBom}"
                ${if (isKotlin2Plus) "kotlin = \"$kotlinVersion\"\n" else ""}
                ${if (wireActivities) "activity-compose = \"${compatData.defaultActivityComposeVersion}\"\n" else ""}
                ${if (hasConstraintLayout) "constraintlayout-compose = \"${compatData.defaultConstraintLayoutComposeVersion}\"\n" else ""}

                [libraries]
                androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
                androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
                androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
                androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
                androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
                ${if (wireActivities) "androidx-activity-compose = { group = \"androidx.activity\", name = \"activity-compose\", version.ref = \"activity-compose\" }\n" else ""}
                ${if (hasConstraintLayout) "androidx-constraintlayout-compose = { group = \"androidx.constraintlayout\", name = \"constraintlayout-compose\", version.ref = \"constraintlayout-compose\" }\n" else ""}

                [plugins]
                ${if (isKotlin2Plus) "compose-compiler = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }\n" else ""}
                """.trimIndent() + "\n"
            )
            notes.add("gradle/libs.versions.toml neu angelegt mit Compose-Einträgen.")
            return CatalogUpdateResult(modified = true, notes = notes)
        }

        var content = catalogFile.readText()
        var modified = false

        // Versionen ergänzen
        val verSection = content.substringAfter("[versions]", "").substringBefore("\n[")
        val versionEntries = mutableMapOf<String, String>()
        if (!verSection.contains("compose-bom =") && !verSection.contains("composeBom =")) {
            versionEntries["compose-bom"] = compatData.defaultComposeBom
        }
        if (wireActivities && !verSection.contains("activity-compose =") && !verSection.contains("activityCompose =")) {
            versionEntries["activity-compose"] = compatData.defaultActivityComposeVersion
        }
        if (hasConstraintLayout && !verSection.contains("constraintlayout-compose =") && !verSection.contains("constraintlayoutCompose =")) {
            versionEntries["constraintlayout-compose"] = compatData.defaultConstraintLayoutComposeVersion
        }

        if (versionEntries.isNotEmpty()) {
            val verSectionMatch = Regex("""\[versions\]""").find(content)
            if (verSectionMatch != null) {
                val insert = StringBuilder("\n")
                for ((k, v) in versionEntries) {
                    insert.append("$k = \"$v\"\n")
                }
                val idx = verSectionMatch.range.last + 1
                content = content.substring(0, idx) + insert.toString() + content.substring(idx)
                modified = true
            }
        }

        // Libraries ergänzen
        val libSection = content.substringAfter("[libraries]", "").substringBefore("\n[")
        val libEntries = mutableMapOf<String, String>()
        if (!libSection.contains("androidx-compose-bom") && !libSection.contains("compose-bom =")) {
            libEntries["androidx-compose-bom"] = "{ group = \"androidx.compose\", name = \"compose-bom\", version.ref = \"compose-bom\" }"
        }
        if (!libSection.contains("androidx-compose-ui =") && !libSection.contains("compose-ui =")) {
            libEntries["androidx-compose-ui"] = "{ group = \"androidx.compose.ui\", name = \"ui\" }"
        }
        if (!libSection.contains("androidx-compose-ui-tooling-preview") && !libSection.contains("compose-ui-tooling-preview")) {
            libEntries["androidx-compose-ui-tooling-preview"] = "{ group = \"androidx.compose.ui\", name = \"ui-tooling-preview\" }"
        }
        if (!libSection.contains("androidx-compose-ui-tooling =") && !libSection.contains("compose-ui-tooling =")) {
            libEntries["androidx-compose-ui-tooling"] = "{ group = \"androidx.compose.ui\", name = \"ui-tooling\" }"
        }
        if (!libSection.contains("androidx-compose-material3") && !libSection.contains("compose-material3")) {
            libEntries["androidx-compose-material3"] = "{ group = \"androidx.compose.material3\", name = \"material3\" }"
        }
        if (wireActivities && !libSection.contains("androidx-activity-compose") && !libSection.contains("activity-compose =")) {
            libEntries["androidx-activity-compose"] = "{ group = \"androidx.activity\", name = \"activity-compose\", version.ref = \"activity-compose\" }"
        }
        if (hasConstraintLayout && !libSection.contains("androidx-constraintlayout-compose") && !libSection.contains("constraintlayout-compose =")) {
            libEntries["androidx-constraintlayout-compose"] = "{ group = \"androidx.constraintlayout\", name = \"constraintlayout-compose\", version.ref = \"constraintlayout-compose\" }"
        }

        if (libEntries.isNotEmpty()) {
            val libSectionMatch = Regex("""\[libraries\]""").find(content)
            if (libSectionMatch != null) {
                val insert = StringBuilder("\n")
                for ((k, v) in libEntries) {
                    insert.append("$k = $v\n")
                }
                val idx = libSectionMatch.range.last + 1
                content = content.substring(0, idx) + insert.toString() + content.substring(idx)
                modified = true
            }
        }

        // Plugins ergänzen (Compose Compiler für Kotlin 2+)
        if (isKotlin2Plus && !content.contains("compose-compiler") && !content.contains("org.jetbrains.kotlin.plugin.compose")) {
            val pluginSectionMatch = Regex("""\[plugins\]""").find(content)
            val kotlinRef = if (content.contains("kotlin =")) "kotlin" else null
            val pluginDef = if (kotlinRef != null) {
                "compose-compiler = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"$kotlinRef\" }\n"
            } else {
                "compose-compiler = { id = \"org.jetbrains.kotlin.plugin.compose\", version = \"$kotlinVersion\" }\n"
            }

            if (pluginSectionMatch != null) {
                val idx = pluginSectionMatch.range.last + 1
                content = content.substring(0, idx) + "\n" + pluginDef + content.substring(idx)
                modified = true
            } else {
                content += "\n[plugins]\n$pluginDef"
                modified = true
            }
        }

        if (modified) {
            catalogFile.writeText(content)
            notes.add("Compose-Einträge in gradle/libs.versions.toml ergänzt.")
        }

        return CatalogUpdateResult(modified = modified, notes = notes)
    }

    private fun ensureRootPluginRegistration(
        content: String,
        isKts: Boolean,
        useVersionCatalog: Boolean,
        kotlinVersion: String
    ): Pair<String, Boolean> {
        if (content.contains("kotlin.plugin.compose")) {
            return content to false
        }

        val pluginsRegex = Regex("""plugins\s*\{""")
        val match = pluginsRegex.find(content)
        if (match != null) {
            val insert = if (useVersionCatalog) {
                if (isKts) "    alias(libs.plugins.compose.compiler) apply false\n" else "    alias(libs.plugins.compose.compiler) apply false\n"
            } else {
                if (isKts) "    id(\"org.jetbrains.kotlin.plugin.compose\") version \"$kotlinVersion\" apply false\n"
                else "    id 'org.jetbrains.kotlin.plugin.compose' version '$kotlinVersion' apply false\n"
            }
            val idx = match.range.last + 1
            return content.substring(0, idx) + "\n" + insert + content.substring(idx) to true
        }
        return content to false
    }

    private fun isVersionAtLeast(version: String, target: String): Boolean {
        val vParts = version.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val tParts = target.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(vParts.size, tParts.size)
        for (i in 0 until maxLen) {
            val v = vParts.getOrElse(i) { 0 }
            val t = tParts.getOrElse(i) { 0 }
            if (v != t) return v > t
        }
        return true
    }

    private data class CompilerConfig(
        val extensionVersion: String? = null,
        val pluginId: String? = null
    )

    private data class CompatData(
        val minSdkRequirement: Int,
        val defaultComposeBom: String,
        val defaultKotlinVersion: String,
        val defaultActivityComposeVersion: String,
        val defaultConstraintLayoutComposeVersion: String,
        val kotlinToCompiler: Map<String, CompilerConfig>
    )

    private fun loadCompatData(): CompatData {
        val json = compatJsonProvider?.invoke() ?: run {
            val stream = javaClass.getResourceAsStream("/com/codeforge/libs/x2c/x2c-compose-compat.json")
                ?: javaClass.classLoader.getResourceAsStream("com/codeforge/libs/x2c/x2c-compose-compat.json")
            stream?.bufferedReader()?.use { it.readText() }
        } ?: ""

        val defaultData = CompatData(
            minSdkRequirement = OFFICIAL_MIN_SDK,
            defaultComposeBom = DEFAULT_BOM,
            defaultKotlinVersion = DEFAULT_KOTLIN,
            defaultActivityComposeVersion = DEFAULT_ACTIVITY_COMPOSE,
            defaultConstraintLayoutComposeVersion = DEFAULT_CONSTRAINT_LAYOUT_COMPOSE,
            kotlinToCompiler = mapOf(
                "1.9.24" to CompilerConfig(extensionVersion = "1.5.14"),
                "1.9.23" to CompilerConfig(extensionVersion = "1.5.11"),
                "1.9.22" to CompilerConfig(extensionVersion = "1.5.10"),
                "1.9.21" to CompilerConfig(extensionVersion = "1.5.7"),
                "1.9.20" to CompilerConfig(extensionVersion = "1.5.4"),
                "1.9.10" to CompilerConfig(extensionVersion = "1.5.3"),
                "1.9.0" to CompilerConfig(extensionVersion = "1.5.1"),
                "1.8.22" to CompilerConfig(extensionVersion = "1.4.8"),
                "1.8.21" to CompilerConfig(extensionVersion = "1.4.7"),
                "1.8.20" to CompilerConfig(extensionVersion = "1.4.6")
            )
        )

        if (json.isBlank()) return defaultData

        try {
            val compilerMap = mutableMapOf<String, CompilerConfig>()
            val entryRegex = Regex(""""([0-9.]+)":\s*\{([^}]+)\}""")
            for (match in entryRegex.findAll(json)) {
                val kotlinVer = match.groupValues[1]
                val body = match.groupValues[2]
                val extMatch = Regex(""""extensionVersion":\s*"([^"]+)"""").find(body)
                val plugMatch = Regex(""""plugin":\s*"([^"]+)"""").find(body)
                compilerMap[kotlinVer] = CompilerConfig(
                    extensionVersion = extMatch?.groupValues?.get(1),
                    pluginId = plugMatch?.groupValues?.get(1)
                )
            }
            return defaultData.copy(kotlinToCompiler = compilerMap)
        } catch (_: Exception) {
            return defaultData
        }
    }
}
