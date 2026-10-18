import com.android.build.api.attributes.BuildTypeAttr
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

val composeBomVersion: String = libs.versions.composeBom.get()
val kotlinVersion: String = libs.versions.kotlin.get()
val coroutinesVersion: String = libs.versions.coroutines.get()
val lifecycleVersion: String = libs.versions.lifecycle.get()
val compilerRuntimeTag = "compiler-runtime-$composeBomVersion-k$kotlinVersion"
val compilerRuntimeZipName = "$compilerRuntimeTag.zip"
val compilerRuntimeBaseUrl = "https://github.com/scto/codeforge-tools/releases/download/$compilerRuntimeTag"

android {
    namespace = "com.codeforge.feature.composepreview"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "COMPOSE_BOM_VERSION", "\"$composeBomVersion\"")
        buildConfigField("String", "KOTLIN_VERSION", "\"$kotlinVersion\"")
        buildConfigField("String", "COMPILER_RUNTIME_TAG", "\"$compilerRuntimeTag\"")
        buildConfigField("String", "COMPILER_RUNTIME_ZIP_NAME", "\"$compilerRuntimeZipName\"")
        buildConfigField("String", "COMPILER_RUNTIME_BASE_URL", "\"$compilerRuntimeBaseUrl\"")
    }
}

val compilerRuntimeConfig: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.ANDROID)
        )
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
        attribute(BuildTypeAttr.ATTRIBUTE, objects.named("release"))
    }
}

val withExtendedIcons = providers.gradleProperty("withExtendedIcons")
    .map { it.equals("true", ignoreCase = true) || it == "1" }
    .orElse(false)

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":libs:terminal-engine"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    // Für ComposeViewBitmapRenderer: Compose-Hosting außerhalb einer Activity.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // --- Compiler-Runtime-Konfiguration für packCompilerRuntime (Task 26.1 / 27a) ---
    compilerRuntimeConfig(platform(libs.androidx.compose.bom))
    compilerRuntimeConfig("androidx.compose.runtime:runtime")
    compilerRuntimeConfig("androidx.compose.runtime:runtime-saveable")
    compilerRuntimeConfig("androidx.compose.ui:ui")
    compilerRuntimeConfig("androidx.compose.ui:ui-graphics")
    compilerRuntimeConfig("androidx.compose.ui:ui-text")
    compilerRuntimeConfig("androidx.compose.ui:ui-unit")
    compilerRuntimeConfig("androidx.compose.ui:ui-geometry")
    compilerRuntimeConfig("androidx.compose.ui:ui-util")
    compilerRuntimeConfig("androidx.compose.foundation:foundation")
    compilerRuntimeConfig("androidx.compose.foundation:foundation-layout")
    compilerRuntimeConfig("androidx.compose.animation:animation")
    compilerRuntimeConfig("androidx.compose.animation:animation-core")
    compilerRuntimeConfig("androidx.compose.material3:material3")
    compilerRuntimeConfig("androidx.compose.material:material-icons-core")
    compilerRuntimeConfig("androidx.compose.ui:ui-tooling-preview")
    compilerRuntimeConfig("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compilerRuntimeConfig("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
    if (withExtendedIcons.get()) {
        compilerRuntimeConfig("androidx.compose.material:material-icons-extended")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

val packCompilerRuntime = tasks.register("packCompilerRuntime") {
    group = "build"
    description =
        "Packs the Compose & Kotlin compiler runtime JARs, manifest.json, and release ZIP for on-device Compose Preview compilation."

    val outDirProvider = layout.buildDirectory.dir("compiler-runtime")
    val bomVer = composeBomVersion
    val ktVer = kotlinVersion
    val zipName = compilerRuntimeZipName

    inputs.property("bomVersion", bomVer)
    inputs.property("kotlinVersion", ktVer)
    inputs.property("withExtendedIcons", withExtendedIcons.get())
    inputs.files(compilerRuntimeConfig)
    outputs.dir(outDirProvider)

    doLast {
        val outDir = outDirProvider.get().asFile
        if (outDir.exists()) {
            outDir.deleteRecursively()
        }
        outDir.mkdirs()

        val allowedGroups = setOf(
            "androidx.compose.runtime",
            "androidx.compose.ui",
            "androidx.compose.foundation",
            "androidx.compose.animation",
            "androidx.compose.material",
            "androidx.compose.material3",
            "androidx.annotation",
            "androidx.collection",
            "androidx.lifecycle",
            "androidx.arch.core",
            "org.jetbrains.kotlin",
            "org.jetbrains.kotlinx",
            "org.jetbrains"
        )

        val resolvedArtifacts = compilerRuntimeConfig.resolvedConfiguration.resolvedArtifacts
        val seenNames = mutableSetOf<String>()

        for (artifact in resolvedArtifacts) {
            val id = artifact.moduleVersion.id
            if (id.group !in allowedGroups) continue
            val file = artifact.file
            if (!file.exists()) continue

            val targetJarName = "${id.group.replace('.', '_')}-${id.name}-${id.version}.jar"
            if (!seenNames.add(targetJarName)) continue
            val targetJar = File(outDir, targetJarName)

            when (file.extension.lowercase()) {
                "aar" -> {
                    ZipFile(file).use { aarZip ->
                        val classesEntry = aarZip.getEntry("classes.jar")
                        if (classesEntry != null && classesEntry.size > 0) {
                            aarZip.getInputStream(classesEntry).use { input ->
                                targetJar.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                "jar" -> {
                    // Leere Multiplatform-Root-Stub-Jars (nur META-INF/MANIFEST.MF without classes) überspringen
                    val hasClassOrMeta = ZipFile(file).use { jarZip ->
                        jarZip.entries().asSequence().any { !it.isDirectory && it.name != "META-INF/MANIFEST.MF" }
                    }
                    if (hasClassOrMeta) {
                        file.copyTo(targetJar, overwrite = true)
                    }
                }
            }
        }

        val jarFiles = outDir.listFiles { f -> f.isFile && f.extension == "jar" }
            ?.sortedBy { it.name }
            .orEmpty()
        check(jarFiles.isNotEmpty()) { "packCompilerRuntime produced no JAR files" }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        // Jede Jar-Datei prüfen (analog unzip -t) und Manifest-Einträge sammeln
        val entriesJson = StringBuilder()
        var totalUnpackedBytes = 0L
        jarFiles.forEachIndexed { idx, jar ->
            ZipFile(jar).use { zf ->
                val entries = zf.entries()
                var count = 0
                val buf = ByteArray(4096)
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!e.isDirectory) {
                        zf.getInputStream(e).use { s ->
                            while (s.read(buf) != -1) {
                                // Stream vollständig lesen zur CRC/Integritätsprüfung
                            }
                        }
                        count++
                    }
                }
                check(count > 0) { "JAR file ${jar.name} contains no entries" }
            }
            val sha = sha256Hex(jar)
            val size = jar.length()
            totalUnpackedBytes += size
            if (idx > 0) entriesJson.append(",\n")
            entriesJson.append("    { \"name\": \"${jar.name}\", \"sha256\": \"$sha\", \"size\": $size }")
        }

        val manifestFile = File(outDir, "manifest.json")
        val manifestContent = buildString {
            appendLine("{")
            appendLine("  \"bom\": \"$bomVer\",")
            appendLine("  \"kotlin\": \"$ktVer\",")
            appendLine("  \"files\": [")
            appendLine(entriesJson.toString())
            appendLine("  ]")
            appendLine("}")
        }
        manifestFile.writeText(manifestContent, Charsets.UTF_8)

        val zipFile = File(outDir, zipName)
        ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            val filesToPack = jarFiles + manifestFile
            val buf = ByteArray(8192)
            for (f in filesToPack) {
                val entry = ZipEntry(f.name).apply { time = 0L }
                zos.putNextEntry(entry)
                f.inputStream().buffered().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        zos.write(buf, 0, n)
                    }
                }
                zos.closeEntry()
            }
        }
        val zipSha = sha256Hex(zipFile)
        File(outDir, "$zipName.sha256").writeText("$zipSha  $zipName\n", Charsets.UTF_8)

        logger.lifecycle(
            "packCompilerRuntime: ${jarFiles.size} JARs (${totalUnpackedBytes / 1024} KiB unpacked), " +
                "${zipFile.name} = ${zipFile.length() / 1024} KiB (sha256=$zipSha)"
        )
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(packCompilerRuntime)
    val runtimeBuildDir = layout.buildDirectory.dir("compiler-runtime")
    doFirst {
        systemProperty("compilerRuntimeBuildDir", runtimeBuildDir.get().asFile.absolutePath)
    }
}
