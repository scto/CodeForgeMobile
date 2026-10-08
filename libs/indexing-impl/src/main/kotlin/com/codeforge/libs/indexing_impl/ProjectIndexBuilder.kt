/**
 * Modul: :libs:indexing-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_impl

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.indexing_api.GradleModule
import com.codeforge.libs.indexing_api.IndexedFile
import com.codeforge.libs.indexing_api.IndexedFileKind
import com.codeforge.libs.indexing_api.ProjectIndex
import com.codeforge.libs.indexing_api.RepositoryDeclaration
import java.io.File

/** Baut einen [ProjectIndex] (blockierend, ohne Coroutines — direkt unit-testbar). */
object ProjectIndexBuilder {

    fun build(rootPath: String, nowMillis: Long = System.currentTimeMillis()): ProjectIndex {
        val root = File(rootPath).absoluteFile.normalize()
        require(root.isDirectory) { Res.string(R.string.index_projektordner_existiert_nicht, root.path) }

        val files = ProjectScanner.scan(root)
        val byRelative = files.associateBy { it.relativePath }

        val settingsIndexed: IndexedFile? = byRelative["settings.gradle.kts"] ?: byRelative["settings.gradle"]
        val settingsText = settingsIndexed?.let { runCatching { File(it.path).readText() }.getOrNull() }
        val parsed = settingsText?.let { GradleSettingsParser.parse(it) }

        val rootBuild = (byRelative["build.gradle.kts"] ?: byRelative["build.gradle"])?.path
        val modules = ArrayList<GradleModule>()
        modules += GradleModule(":", root.path, rootBuild)
        parsed?.includes.orEmpty().forEach { gradlePath ->
            if (modules.any { it.gradlePath == gradlePath }) return@forEach
            val dirRelative = parsed!!.projectDirOverrides[gradlePath] ?: GradleSettingsParser.defaultDirectory(gradlePath)
            val dir = File(root, dirRelative).normalize()
            modules += GradleModule(gradlePath, dir.path, findBuildFile(dir))
        }

        val catalogs = LinkedHashSet<String>()
        parsed?.versionCatalogFiles.orEmpty().forEach { rel ->
            val f = File(root, rel).normalize()
            if (f.isFile) catalogs += f.path
        }
        files.filter { it.kind == IndexedFileKind.VERSION_CATALOG && it.relativePath.startsWith("gradle/") }
            .forEach { catalogs += it.path }

        val repositories = ArrayList<RepositoryDeclaration>()
        val repoSources = buildList {
            settingsIndexed?.let { add(it.path) }
            modules.mapNotNull { it.buildFile }.forEach { add(it) }
        }.distinct()
        for (path in repoSources) {
            val text = runCatching { File(path).readText() }.getOrNull() ?: continue
            repositories += GradleRepositoryParser.parse(text, path)
        }

        return ProjectIndex(
            rootPath = root.path,
            rootProjectName = parsed?.rootProjectName ?: root.name,
            files = files,
            settingsFile = settingsIndexed?.path,
            modules = modules,
            versionCatalogs = catalogs.toList(),
            repositories = repositories,
            indexedAtMillis = nowMillis
        )
    }

    private fun findBuildFile(dir: File): String? =
        listOf("build.gradle.kts", "build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }?.path
}
