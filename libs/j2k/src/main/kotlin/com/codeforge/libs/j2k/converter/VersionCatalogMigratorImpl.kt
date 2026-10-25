package com.codeforge.libs.j2k.converter

import com.codeforge.core.domain.repository.VersionCatalogMigrationResult
import com.codeforge.core.domain.repository.VersionCatalogMigrator
import java.io.File

class VersionCatalogMigratorImpl : VersionCatalogMigrator {

    override suspend fun migrateToCatalog(
        projectPath: String,
        gradleFiles: List<String>
    ): VersionCatalogMigrationResult {
        val projectDir = File(projectPath)
        val versions = sortedMapOf<String, String>()
        val libraries = sortedMapOf<String, String>() // alias -> "group = \"...\", name = \"...\", version.ref = \"...\""
        val plugins = sortedMapOf<String, String>()
        val updatedFiles = mutableMapOf<String, String>()
        val notes = mutableListOf<String>()

        val depRegex = Regex("""(implementation|api|testImplementation|androidTestImplementation|kapt|ksp|annotationProcessor|classpath)\s*\(?["']([^"':]+):([^"':]+):([^"':]+)["']\)?""")

        for (relFile in gradleFiles) {
            val file = File(projectDir, relFile)
            if (!file.exists()) continue
            var content = file.readText()

            val isKts = relFile.endsWith(".kts")

            content = depRegex.replace(content) { m ->
                val config = m.groupValues[1]
                val group = m.groupValues[2]
                val artifact = m.groupValues[3]
                val version = m.groupValues[4]

                val alias = makeAlias(group, artifact)
                val versionRef = makeVersionRef(group, artifact)

                versions[versionRef] = version
                libraries[alias] = "group = \"$group\", name = \"$artifact\", version.ref = \"$versionRef\""

                val dotAlias = alias.replace("-", ".")
                if (isKts) {
                    "$config(libs.$dotAlias)"
                } else {
                    "$config libs.$dotAlias"
                }
            }

            updatedFiles[relFile] = content
        }

        val tomlBuilder = StringBuilder()
        tomlBuilder.append("[versions]\n")
        for ((verRef, verVal) in versions) {
            tomlBuilder.append("$verRef = \"$verVal\"\n")
        }

        tomlBuilder.append("\n[libraries]\n")
        for ((libAlias, libDef) in libraries) {
            tomlBuilder.append("$libAlias = { $libDef }\n")
        }

        tomlBuilder.append("\n[plugins]\n")
        for ((plugAlias, plugDef) in plugins) {
            tomlBuilder.append("$plugAlias = { $plugDef }\n")
        }

        return VersionCatalogMigrationResult(
            catalogContent = tomlBuilder.toString(),
            updatedGradleFiles = updatedFiles,
            notes = notes
        )
    }

    private fun makeAlias(group: String, artifact: String): String {
        val raw = if (group.startsWith("androidx") || group.startsWith("org.jetbrains")) {
            val shortGroup = group.substringAfterLast('.')
            "$shortGroup-$artifact"
        } else {
            artifact
        }
        return raw.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
    }

    private fun makeVersionRef(group: String, artifact: String): String {
        return makeAlias(group, artifact).replace("-", "")
    }
}
