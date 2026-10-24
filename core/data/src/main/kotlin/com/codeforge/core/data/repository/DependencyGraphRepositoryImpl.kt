// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.DependencyGraph
import com.codeforge.core.domain.model.DependencyInsight
import com.codeforge.core.domain.model.DependencyNode
import com.codeforge.core.domain.model.GradleDependencyInsightParser
import com.codeforge.core.domain.model.GradleDependencyTreeParser
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.DependencyGraphRepository
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DependencyGraphRepositoryImpl @Inject constructor(
    private val gradleBuildRepository: GradleBuildRepository,
    private val dependencyUpdateRepository: DependencyUpdateRepository
) : DependencyGraphRepository {

    private data class CacheEntry(
        val checksum: String,
        val graph: DependencyGraph,
        val timestampMillis: Long
    )

    // Cache-Schlüssel: "$projectRoot:$moduleName:$configuration"
    private val graphCache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun getModules(projectRoot: String): List<String> = withContext(Dispatchers.IO) {
        val rootDir = File(projectRoot)
        val settingsFile = File(rootDir, "settings.gradle.kts").takeIf { it.exists() }
            ?: File(rootDir, "settings.gradle").takeIf { it.exists() }

        if (settingsFile != null) {
            val content = settingsFile.readText()
            val moduleRegex = Regex("""["'](:[a-zA-Z0-9_\-:]+)["']""")
            val matches = moduleRegex.findAll(content).map { it.groupValues[1] }.distinct().toList()
            if (matches.isNotEmpty()) {
                return@withContext matches.sortedWith { a, b ->
                    when {
                        a == ":app" -> -1
                        b == ":app" -> 1
                        else -> a.compareTo(b)
                    }
                }
            }
        }

        listOf(":app")
    }

    override suspend fun getConfigurations(projectRoot: String, moduleName: String): List<String> {
        return listOf(
            "debugRuntimeClasspath",
            "debugCompileClasspath",
            "releaseRuntimeClasspath",
            "debugUnitTestRuntimeClasspath",
            "compileClasspath",
            "runtimeClasspath"
        )
    }

    override suspend fun loadGraph(
        projectRoot: String,
        moduleName: String,
        configuration: String,
        forceReload: Boolean
    ): Result<DependencyGraph> = withContext(Dispatchers.IO) {
        val cacheKey = "$projectRoot:$moduleName:$configuration"
        val currentChecksum = calculateBuildFilesChecksum(projectRoot, moduleName)

        if (!forceReload) {
            val cached = graphCache[cacheKey]
            if (cached != null && cached.checksum == currentChecksum) {
                return@withContext Result.success(cached.graph)
            }
        }

        try {
            gradleBuildRepository.connect(projectRoot)

            val cleanMod = if (moduleName.startsWith(":")) moduleName else ":$moduleName"
            val taskCmd = "$cleanMod:dependencies --configuration $configuration --console=plain"

            val outputBuilder = StringBuilder()
            var failureMessage: String? = null

            gradleBuildRepository.runTask(taskCmd).collect { event ->
                when (event) {
                    is BuildEvent.Output -> outputBuilder.appendLine(event.line)
                    is BuildEvent.BuildFailed -> failureMessage = event.message
                    else -> {}
                }
            }

            val rawOutput = outputBuilder.toString()
            if (rawOutput.isBlank() && failureMessage != null) {
                return@withContext Result.failure(IllegalStateException(failureMessage))
            }

            val parsedGraph = GradleDependencyTreeParser.parse(
                output = rawOutput,
                moduleName = moduleName,
                targetConfiguration = configuration
            )

            // Updates aus DependencyUpdateRepository anreichern
            val updates = try {
                withTimeoutOrNull(500) { dependencyUpdateRepository.observe(projectRoot).first() }?.updates ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val enrichedRoots = parsedGraph.rootNodes.map { enrichNodeWithUpdates(it, updates) }
            val finalGraph = parsedGraph.copy(
                rootNodes = enrichedRoots,
                timestampMillis = System.currentTimeMillis()
            )

            graphCache[cacheKey] = CacheEntry(
                checksum = currentChecksum,
                graph = finalGraph,
                timestampMillis = finalGraph.timestampMillis
            )

            Result.success(finalGraph)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadInsight(
        projectRoot: String,
        moduleName: String,
        configuration: String,
        coordinate: String
    ): Result<DependencyInsight> = withContext(Dispatchers.IO) {
        try {
            gradleBuildRepository.connect(projectRoot)

            val cleanMod = if (moduleName.startsWith(":")) moduleName else ":$moduleName"
            val taskCmd = "$cleanMod:dependencyInsight --dependency $coordinate --configuration $configuration --console=plain"

            val outputBuilder = StringBuilder()
            var failureMessage: String? = null

            gradleBuildRepository.runTask(taskCmd).collect { event ->
                when (event) {
                    is BuildEvent.Output -> outputBuilder.appendLine(event.line)
                    is BuildEvent.BuildFailed -> failureMessage = event.message
                    else -> {}
                }
            }

            val rawOutput = outputBuilder.toString()
            if (rawOutput.isBlank() && failureMessage != null) {
                return@withContext Result.failure(IllegalStateException(failureMessage))
            }

            val insight = GradleDependencyInsightParser.parse(rawOutput, coordinate)
            Result.success(insight)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun invalidateCache(projectRoot: String) {
        val keysToRemove = graphCache.keys().toList().filter { it.startsWith("$projectRoot:") }
        for (key in keysToRemove) {
            graphCache.remove(key)
        }
    }

    fun calculateBuildFilesChecksum(projectRoot: String, moduleName: String): String {
        val rootDir = File(projectRoot)
        val filesToCheck = mutableListOf<File>()

        filesToCheck.add(File(rootDir, "settings.gradle.kts"))
        filesToCheck.add(File(rootDir, "settings.gradle"))
        filesToCheck.add(File(rootDir, "build.gradle.kts"))
        filesToCheck.add(File(rootDir, "build.gradle"))
        filesToCheck.add(File(rootDir, "gradle/libs.versions.toml"))

        val moduleRelPath = moduleName.removePrefix(":").replace(":", "/")
        if (moduleRelPath.isNotBlank()) {
            val modDir = File(rootDir, moduleRelPath)
            filesToCheck.add(File(modDir, "build.gradle.kts"))
            filesToCheck.add(File(modDir, "build.gradle"))
        }

        val digest = MessageDigest.getInstance("SHA-256")
        for (file in filesToCheck) {
            if (file.exists()) {
                digest.update(file.name.toByteArray())
                digest.update(file.length().toString().toByteArray())
                digest.update(file.lastModified().toString().toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun enrichNodeWithUpdates(node: DependencyNode, updates: List<DependencyUpdate>): DependencyNode {
        val update = if (!node.isProject && node.group.isNotBlank() && node.name.isNotBlank()) {
            val coordKey = "${node.group}:${node.name}"
            updates.firstOrNull { u -> u.coordinates.any { it.key == coordKey } }
        } else {
            null
        }

        val enrichedChildren = node.children.map { enrichNodeWithUpdates(it, updates) }
        return node.copy(
            children = enrichedChildren,
            updateAvailable = update?.let { "${it.currentVersion} -> ${it.newVersion}" }
        )
    }
}
