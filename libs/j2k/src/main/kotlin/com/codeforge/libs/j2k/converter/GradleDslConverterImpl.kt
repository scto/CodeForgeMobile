package com.codeforge.libs.j2k.converter

import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.repository.GradleDslConverter
import com.codeforge.core.domain.repository.J2kFileResultWithContent

class GradleDslConverterImpl : GradleDslConverter {

    override suspend fun convertFile(groovySource: String, filePath: String): J2kFileResultWithContent {
        val notes = mutableListOf<String>()
        val originalLines = groovySource.lines().size

        try {
            var kts = groovySource

            // 1. Strings: Einfache Anführungszeichen '...' zu "..."
            kts = kts.replace(Regex("""'([^'\\]*(?:\\.[^'\\]*)*)'""")) { m ->
                val content = m.groupValues[1]
                val escaped = content.replace("$", "\\$")
                "\"$escaped\""
            }

            // 2. Plugins Block & apply plugin
            kts = kts.replace(Regex("""apply\s+plugin:\s*["']([^"']+)["']""")) { m ->
                "apply(plugin = \"${m.groupValues[1]}\")"
            }
            kts = kts.replace(Regex("""plugins\s*\{([^}]+)\}""")) { m ->
                val body = m.groupValues[1]
                val convertedBody = body.replace(Regex("""id\s+["']([^"']+)["'](?:\s+version\s+["']([^"']+)["'])?(?:\s+apply\s+(true|false))?""")) { idMatch ->
                    val id = idMatch.groupValues[1]
                    val ver = idMatch.groupValues[2]
                    val appl = idMatch.groupValues[3]
                    var line = "id(\"$id\")"
                    if (ver.isNotEmpty()) line += " version \"$ver\""
                    if (appl.isNotEmpty()) line += " apply $appl"
                    line
                }
                "plugins {$convertedBody}"
            }

            // 3. Dependencies Konfiguration
            val depConfigs = listOf(
                "implementation", "testImplementation", "androidTestImplementation",
                "api", "compileOnly", "runtimeOnly", "kapt", "ksp", "annotationProcessor",
                "debugImplementation", "releaseImplementation", "classpath"
            )
            for (cfg in depConfigs) {
                // cfg "group:artifact:version"
                kts = kts.replace(Regex("""\b$cfg\s+["']([^"']+)["']""")) { m ->
                    "$cfg(\"${m.groupValues[1]}\")"
                }
                // cfg project(':module')
                kts = kts.replace(Regex("""\b$cfg\s+project\(["']([^"']+)["']\)""")) { m ->
                    "$cfg(project(\"${m.groupValues[1]}\"))"
                }
                // cfg platform("...")
                kts = kts.replace(Regex("""\b$cfg\s+platform\(["']([^"']+)["']\)""")) { m ->
                    "$cfg(platform(\"${m.groupValues[1]}\"))"
                }
            }

            // fileTree
            kts = kts.replace(Regex("""fileTree\s*\(\s*dir:\s*["']([^"']+)["'],\s*include:\s*\[["']([^"']+)["']\]\s*\)""")) { m ->
                "fileTree(mapOf(\"dir\" to \"${m.groupValues[1]}\", \"include\" to listOf(\"${m.groupValues[2]}\")))"
            }

            // 4. Repositories
            kts = kts.replace(Regex("""maven\s*\{\s*url\s+["']([^"']+)["']\s*\}""")) { m ->
                "maven { url = uri(\"${m.groupValues[1]}\") }"
            }

            // 5. Android DSL
            kts = kts.replace(Regex("""compileSdkVersion\s+(\d+)"""), "compileSdk = $1")
            kts = kts.replace(Regex("""minSdkVersion\s+(\d+)"""), "minSdk = $1")
            kts = kts.replace(Regex("""targetSdkVersion\s+(\d+)"""), "targetSdk = $1")
            kts = kts.replace(Regex("""versionCode\s+(\d+)"""), "versionCode = $1")
            kts = kts.replace(Regex("""versionName\s+["']([^"']+)["']"""), "versionName = \"$1\"")
            kts = kts.replace(Regex("""applicationId\s+["']([^"']+)["']"""), "applicationId = \"$1\"")
            kts = kts.replace("minifyEnabled true", "isMinifyEnabled = true")
            kts = kts.replace("minifyEnabled false", "isMinifyEnabled = false")
            kts = kts.replace("shrinkResources true", "isShrinkResources = true")
            kts = kts.replace("shrinkResources false", "isShrinkResources = false")

            // buildTypes release
            kts = kts.replace(Regex("""buildTypes\s*\{\s*release\s*\{""")) {
                "buildTypes {\n        getByName(\"release\") {"
            }
            kts = kts.replace(Regex("""buildTypes\s*\{\s*debug\s*\{""")) {
                "buildTypes {\n        getByName(\"debug\") {"
            }

            // 6. Variablen & ext
            kts = kts.replace(Regex("""def\s+(\w+)\s*="""), "val $1 =")
            kts = kts.replace(Regex("""ext\.(\w+)\s*="""), "extra[\"$1\"] =")

            val convertedLines = kts.lines().size
            return J2kFileResultWithContent(
                result = J2kFileResult(
                    filePath = filePath,
                    status = if (notes.isNotEmpty()) J2kFileStatus.CONVERTED_WITH_NOTES else J2kFileStatus.CONVERTED,
                    originalLines = originalLines,
                    convertedLines = convertedLines,
                    notes = notes
                ),
                convertedContent = kts
            )
        } catch (e: Exception) {
            return J2kFileResultWithContent(
                result = J2kFileResult(
                    filePath = filePath,
                    status = J2kFileStatus.FAILED,
                    originalLines = originalLines,
                    convertedLines = 0,
                    errorMessage = e.message
                ),
                convertedContent = null
            )
        }
    }
}
