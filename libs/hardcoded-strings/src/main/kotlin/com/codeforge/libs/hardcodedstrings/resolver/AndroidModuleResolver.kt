package com.codeforge.libs.hardcodedstrings.resolver

import com.codeforge.libs.hardcodedstrings.model.ProjectModuleInfo
import java.io.File

/**
 * Resolves Android module boundaries, target strings.xml files, existing string keys, and package namespaces.
 */
object AndroidModuleResolver {

    /**
     * Finds the nearest module directory enclosing the given file or directory.
     */
    fun findEnclosingModule(file: File, projectRoot: File): ProjectModuleInfo {
        var current: File? = if (file.isDirectory) file else file.parentFile
        while (current != null) {
            val buildKts = File(current, "build.gradle.kts")
            val buildGroovy = File(current, "build.gradle")
            if (buildKts.exists() || buildGroovy.exists()) {
                val stringsXml = findStringsXml(current)
                val namespace = resolveNamespace(current)
                val moduleName = current.name
                return ProjectModuleInfo(
                    moduleDir = current,
                    moduleName = moduleName,
                    stringsXmlFile = stringsXml,
                    resNamespace = namespace
                )
            }
            if (current.canonicalPath == projectRoot.canonicalPath) {
                break
            }
            current = current.parentFile
        }

        // Fallback: treat projectRoot as the module
        val fallbackStringsXml = findStringsXml(projectRoot)
        val fallbackNamespace = resolveNamespace(projectRoot)
        return ProjectModuleInfo(
            moduleDir = projectRoot,
            moduleName = projectRoot.name,
            stringsXmlFile = fallbackStringsXml,
            resNamespace = fallbackNamespace
        )
    }

    /**
     * Finds all Android modules within a project root.
     */
    fun findAllModules(projectRoot: File): List<ProjectModuleInfo> {
        val modules = mutableListOf<ProjectModuleInfo>()

        fun walk(dir: File) {
            val name = dir.name
            if (name in setOf("build", ".gradle", ".git", "node_modules", "bin", "out", ".idea")) {
                return
            }

            val buildKts = File(dir, "build.gradle.kts")
            val buildGroovy = File(dir, "build.gradle")
            val srcMain = File(dir, "src/main")

            if ((buildKts.exists() || buildGroovy.exists()) && srcMain.exists()) {
                val stringsXml = findStringsXml(dir)
                val namespace = resolveNamespace(dir)
                modules.add(
                    ProjectModuleInfo(
                        moduleDir = dir,
                        moduleName = dir.name,
                        stringsXmlFile = stringsXml,
                        resNamespace = namespace
                    )
                )
            }

            dir.listFiles()?.filter { it.isDirectory }?.forEach { walk(it) }
        }

        walk(projectRoot)
        return modules
    }

    /**
     * Finds or defaults the primary `strings.xml` for an Android module.
     */
    fun findStringsXml(moduleDir: File): File? {
        val candidates = listOf(
            File(moduleDir, "src/main/res/values/strings.xml"),
            File(moduleDir, "res/values/strings.xml")
        )
        return candidates.firstOrNull { it.exists() } ?: File(moduleDir, "src/main/res/values/strings.xml")
    }

    /**
     * Reads all existing `<string name="key">value</string>` entries from strings.xml into a map.
     */
    fun loadExistingStringsXml(file: File?): Map<String, String> {
        if (file == null || !file.exists()) return emptyMap()

        val text = try {
            file.readText()
        } catch (_: Exception) {
            return emptyMap()
        }

        val map = mutableMapOf<String, String>()
        val regex = Regex("""<string\s+[^>]*name\s*=\s*"([^"]+)"[^>]*>([\s\S]*?)<\/string>""")
        regex.findAll(text).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim()
            map[key] = value
        }
        return map
    }

    /**
     * Resolves the R class package namespace for a module (e.g. `com.example.app`).
     */
    fun resolveNamespace(moduleDir: File): String? {
        val buildKts = File(moduleDir, "build.gradle.kts")
        if (buildKts.exists()) {
            val content = buildKts.readText()
            val match = Regex("""namespace\s*=\s*"([^"]+)"""").find(content)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        val manifest = File(moduleDir, "src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val content = manifest.readText()
            val match = Regex("""package\s*=\s*"([^"]+)"""").find(content)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }
}
