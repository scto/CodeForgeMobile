package com.codeforge.libs.code_tools.module

import java.io.File

enum class ModuleType { ANDROID_LIBRARY, KOTLIN_JVM_LIBRARY }

/** Gradle-Projektpfad wie `:lib:core:libcoredu`. */
data class GradleModulePath(val segments: List<String>) {
    val path: String get() = segments.joinToString(separator = ":", prefix = ":")
    val relativeDir: String get() = segments.joinToString("/")
    val name: String get() = segments.last()

    companion object {
        private val SEGMENT = Regex("^[A-Za-z0-9][A-Za-z0-9_\\-]*$")

        /** Akzeptiert `:a:b`, `a:b` und tolerant Leerzeichen am Rand. */
        fun parse(input: String): Result<GradleModulePath> {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed == ":") return Result.failure(IllegalArgumentException("Modulpfad ist leer"))
            val segments = trimmed.removePrefix(":").split(':')
            segments.firstOrNull { !SEGMENT.matches(it) }?.let {
                return Result.failure(IllegalArgumentException(
                    if (it.isEmpty()) "Leeres Segment im Modulpfad (doppeltes ':')" else "Ungültiges Segment „$it“ (erlaubt: Buchstaben, Ziffern, _ und -)"
                ))
            }
            if (segments.any { it.equals("build", ignoreCase = true) }) {
                return Result.failure(IllegalArgumentException("„build“ ist als Modulname reserviert"))
            }
            return Result.success(GradleModulePath(segments))
        }
    }
}

data class ModuleRequest(
    val gradlePath: String,
    val type: ModuleType = ModuleType.ANDROID_LIBRARY,
    /** Basis-Package; `null` = aus dem Projekt ermitteln. */
    val basePackage: String? = null,
)

data class ModuleResult(
    val gradlePath: String,
    val moduleDir: String,
    val createdFiles: List<String>,
    val settingsFile: String,
    val settingsChanged: Boolean,
    val rootBuildChanged: Boolean,
    val warnings: List<String>,
)

/** Was im Projekt erkannt wurde — Grundlage für passende Build-Dateien. */
data class ProjectProbe(
    val settingsFile: File?,
    val useKotlinDsl: Boolean,
    val basePackage: String?,
    val compileSdk: Int,
    val minSdk: Int,
    /** Plugin-ID → Katalog-Alias (z. B. `libs.plugins.android.library`). */
    val catalogPluginAliases: Map<String, String>,
)

/** Legt Gradle-Submodule an und trägt sie in `settings.gradle(.kts)` ein. */
object ModuleMaker {

    private val PLUGIN_LINE = Regex("""^\s*([A-Za-z0-9_\-]+)\s*=\s*\{[^}]*\bid\s*=\s*"([^"]+)"""")
    private val NAMESPACE = Regex("""namespace\s*=?\s*["']([^"']+)["']""")
    private val APP_ID = Regex("""applicationId\s*=?\s*["']([^"']+)["']""")
    private val COMPILE_SDK = Regex("""compileSdk\s*=?\s*(\d+)""")
    private val MIN_SDK = Regex("""minSdk\s*=?\s*(\d+)""")

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
    )

    fun probe(root: File): ProjectProbe {
        val kts = File(root, "settings.gradle.kts")
        val groovy = File(root, "settings.gradle")
        val settings = if (kts.isFile) kts else if (groovy.isFile) groovy else null
        val buildFiles = root.walkTopDown().maxDepth(3)
            .onEnter { it == root || it.name !in setOf("build", ".gradle", ".git", "node_modules") }
            .filter { it.isFile && (it.name == "build.gradle" || it.name == "build.gradle.kts") && it.parentFile != root }
            .sortedBy { it.path.length }
            .toList()
        val texts = buildFiles.map { it.readText() }
        val base = texts.firstNotNullOfOrNull { NAMESPACE.find(it)?.groupValues?.get(1) }
            ?: texts.firstNotNullOfOrNull { APP_ID.find(it)?.groupValues?.get(1) }
        val compileSdk = texts.firstNotNullOfOrNull { COMPILE_SDK.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 35
        val minSdk = texts.firstNotNullOfOrNull { MIN_SDK.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 21
        return ProjectProbe(settings, settings?.name?.endsWith(".kts") ?: true, base, compileSdk, minSdk, readPluginAliases(root))
    }

    private fun readPluginAliases(root: File): Map<String, String> {
        val toml = File(root, "gradle/libs.versions.toml")
        if (!toml.isFile) return emptyMap()
        var inPlugins = false
        val map = LinkedHashMap<String, String>()
        for (line in toml.readLines()) {
            val t = line.trim()
            if (t.startsWith("[")) { inPlugins = t == "[plugins]"; continue }
            if (!inPlugins) continue
            PLUGIN_LINE.find(line)?.let { m ->
                map.putIfAbsent(m.groupValues[2], "libs.plugins." + m.groupValues[1].replace('-', '.').replace('_', '.'))
            }
        }
        return map
    }

    fun packageFor(base: String, path: GradleModulePath): String {
        val parts = path.segments.map { seg ->
            var s = seg.lowercase().filter { it.isLetterOrDigit() }
            if (s.isEmpty() || s[0].isDigit()) s = "m$s"
            if (s in JAVA_KEYWORDS) s += "_"
            s
        }
        return (base.split('.').filter { it.isNotEmpty() } + parts).joinToString(".")
    }

    fun className(path: GradleModulePath): String {
        val camel = path.name.split('-', '_').filter { it.isNotEmpty() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        return if (camel.firstOrNull()?.isDigit() == true) "M$camel" else camel
    }

    /** Fügt `include(":x")` bzw. `include ':x'` hinzu; idempotent. Zweites Ergebnis: wurde geändert? */
    fun addInclude(settings: String, path: GradleModulePath, kotlinDsl: Boolean): Pair<String, Boolean> {
        val quoted = Regex("""["']${Regex.escape(path.path)}["']""")
        if (quoted.containsMatchIn(settings)) return settings to false
        val line = if (kotlinDsl) "include(\"${path.path}\")" else "include '${path.path}'"
        val body = settings.trimEnd('\n', ' ', '\t', '\r')
        return (if (body.isEmpty()) "$line\n" else "$body\n$line\n") to true
    }

    /** Stellt sicher, dass der Root-Build das Plugin (`alias(...) apply false`) deklariert. */
    fun ensureRootPlugin(rootBuild: String, aliasExpr: String): Pair<String, Boolean> {
        if (rootBuild.contains(aliasExpr)) return rootBuild to false
        val m = Regex("""^plugins\s*\{[ \t]*\n""", RegexOption.MULTILINE).find(rootBuild) ?: return rootBuild to false
        val insert = "    alias($aliasExpr) apply false\n"
        return (rootBuild.substring(0, m.range.last + 1) + insert + rootBuild.substring(m.range.last + 1)) to true
    }

    fun buildFile(
        type: ModuleType,
        pkg: String,
        kotlinDsl: Boolean,
        probe: ProjectProbe,
    ): String {
        fun plugin(id: String): String = probe.catalogPluginAliases[id]?.let { "alias($it)" } ?: "id(\"$id\")"
        val kts = kotlinDsl
        return when (type) {
            ModuleType.ANDROID_LIBRARY -> if (kts) """
                plugins {
                    ${plugin("com.android.library")}
                    ${plugin("org.jetbrains.kotlin.android")}
                }

                android {
                    namespace = "$pkg"
                    compileSdk = ${probe.compileSdk}

                    defaultConfig {
                        minSdk = ${probe.minSdk}
                        consumerProguardFiles("consumer-rules.pro")
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }

                    kotlinOptions { jvmTarget = "17" }
                }

                dependencies {
                }
            """.trimIndent() + "\n" else """
                plugins {
                    ${plugin("com.android.library")}
                    ${plugin("org.jetbrains.kotlin.android")}
                }

                android {
                    namespace '$pkg'
                    compileSdk ${probe.compileSdk}

                    defaultConfig {
                        minSdk ${probe.minSdk}
                        consumerProguardFiles 'consumer-rules.pro'
                    }

                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_17
                        targetCompatibility JavaVersion.VERSION_17
                    }

                    kotlinOptions { jvmTarget = '17' }
                }

                dependencies {
                }
            """.trimIndent() + "\n"

            ModuleType.KOTLIN_JVM_LIBRARY -> """
                plugins {
                    ${plugin("org.jetbrains.kotlin.jvm")}
                }

                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                dependencies {
                }
            """.trimIndent() + "\n"
        }
    }

    fun dummySource(pkg: String, className: String, path: GradleModulePath): String = """
        package $pkg

        /** Platzhalter für das Modul `${path.path}` — gerne ersetzen. */
        object $className {
            fun hello(): String = "Hallo aus ${path.path}"
        }
    """.trimIndent() + "\n"

    fun create(root: File, request: ModuleRequest): Result<ModuleResult> = runCatching {
        val path = GradleModulePath.parse(request.gradlePath).getOrThrow()
        val probe = probe(root)
        val settingsFile = probe.settingsFile ?: error("Keine settings.gradle(.kts) im Projekt gefunden")
        val moduleDir = File(root, path.relativeDir)
        require(!moduleDir.exists() || moduleDir.list().isNullOrEmpty()) { "Ordner existiert bereits: ${path.relativeDir}" }

        val warnings = ArrayList<String>()
        val base = request.basePackage?.takeIf { it.isNotBlank() } ?: probe.basePackage ?: "com.example".also {
            warnings += "Kein Basis-Package im Projekt gefunden – verwende com.example"
        }
        val pkg = packageFor(base, path)
        val kts = probe.useKotlinDsl

        val files = LinkedHashMap<String, String>()
        files[if (kts) "build.gradle.kts" else "build.gradle"] = buildFile(request.type, pkg, kts, probe)
        val srcDir = "src/main/kotlin/" + pkg.replace('.', '/')
        files["$srcDir/${className(path)}.kt"] = dummySource(pkg, className(path), path)
        files[".gitignore"] = "/build\n"
        if (request.type == ModuleType.ANDROID_LIBRARY) {
            files["src/main/AndroidManifest.xml"] = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest />\n"
            files["consumer-rules.pro"] = ""
            files["proguard-rules.pro"] = ""
        }

        // Alles vorbereiten, bevor etwas geschrieben wird (Root-Build/Settings zuerst berechnen).
        val newSettings = addInclude(settingsFile.readText(), path, kts)

        var rootBuildChanged = false
        var newRootBuild: Pair<File, String>? = null
        val rootBuild = listOf("build.gradle.kts", "build.gradle").map { File(root, it) }.firstOrNull { it.isFile }
        val needed = when (request.type) {
            ModuleType.ANDROID_LIBRARY -> listOf("com.android.library", "org.jetbrains.kotlin.android")
            ModuleType.KOTLIN_JVM_LIBRARY -> listOf("org.jetbrains.kotlin.jvm")
        }
        if (rootBuild != null) {
            var text = rootBuild.readText()
            for (id in needed) {
                val alias = probe.catalogPluginAliases[id]
                if (alias == null) {
                    warnings += "Plugin $id ist nicht im Versionskatalog – ggf. im Root-Build mit Version deklarieren"
                    continue
                }
                val (updated, changed) = ensureRootPlugin(text, alias)
                if (changed) { text = updated; rootBuildChanged = true }
            }
            if (rootBuildChanged) newRootBuild = rootBuild to text
        }

        moduleDir.mkdirs()
        files.forEach { (rel, content) ->
            val f = File(moduleDir, rel)
            f.parentFile.mkdirs()
            f.writeText(content)
        }
        if (newSettings.second) settingsFile.writeText(newSettings.first)
        newRootBuild?.let { (f, t) -> f.writeText(t) }

        ModuleResult(
            gradlePath = path.path,
            moduleDir = moduleDir.path,
            createdFiles = files.keys.map { "${path.relativeDir}/$it" },
            settingsFile = settingsFile.path,
            settingsChanged = newSettings.second,
            rootBuildChanged = rootBuildChanged,
            warnings = warnings,
        )
    }
}
