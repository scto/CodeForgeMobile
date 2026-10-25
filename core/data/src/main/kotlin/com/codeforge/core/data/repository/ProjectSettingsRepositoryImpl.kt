package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import com.codeforge.core.domain.repository.ProjectSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectSettingsRepositoryImpl @Inject constructor() : ProjectSettingsRepository {

    private val flowMap = mutableMapOf<String, MutableSharedFlow<ProjectBuildSettings>>()

    private fun getFlow(projectPath: String): MutableSharedFlow<ProjectBuildSettings> {
        return synchronized(flowMap) {
            flowMap.getOrPut(projectPath) {
                MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
        }
    }

    override fun getSettings(projectPath: String): Flow<ProjectBuildSettings> {
        val flow = getFlow(projectPath)
        val file = File(projectPath, ".codeforge/project-settings.json")
        if (file.exists()) {
            try {
                flow.tryEmit(parseJson(projectPath, file.readText()))
            } catch (e: Exception) {
                flow.tryEmit(ProjectBuildSettings(projectPath))
            }
        } else {
            flow.tryEmit(ProjectBuildSettings(projectPath))
        }
        return flow.asSharedFlow()
    }

    override suspend fun saveSettings(settings: ProjectBuildSettings): Unit = withContext(Dispatchers.IO) {
        val dir = File(settings.projectPath, ".codeforge")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "project-settings.json")
        val jsonText = toJson(settings)
        file.writeText(jsonText)
        getFlow(settings.projectPath).tryEmit(settings)
    }

    override suspend fun generateInitScript(settings: ProjectBuildSettings): String {
        return buildString {
            appendLine("// CodeForge Generated Project Settings Init Script")
            appendLine("allprojects { project ->")
            appendLine("    project.plugins.withId('com.android.application') {")
            appendLine("        project.android {")
            appendLine("            buildTypes {")
            appendLine("                release {")
            appendLine("                    minifyEnabled ${settings.release.minifyEnabled}")
            if (settings.release.minifyEnabled && settings.release.shrinkResources) {
                appendLine("                    shrinkResources true")
            } else {
                appendLine("                    shrinkResources false")
            }
            if (settings.release.crunchPngs) {
                appendLine("                    crunchPngs true")
            }
            appendLine("                    debuggable ${settings.release.debuggable}")
            if (!settings.release.proguardRulesPath.isNullOrBlank()) {
                appendLine("                    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), '${settings.release.proguardRulesPath}'")
            }
            appendLine("                }")
            appendLine("                debug {")
            appendLine("                    minifyEnabled ${settings.debug.minifyEnabled}")
            if (settings.debug.minifyEnabled && settings.debug.shrinkResources) {
                appendLine("                    shrinkResources true")
            } else {
                appendLine("                    shrinkResources false")
            }
            appendLine("                    debuggable ${settings.debug.debuggable}")
            appendLine("                }")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun toJson(settings: ProjectBuildSettings): String {
        return buildString {
            appendLine("{")
            appendLine("  \"projectPath\": \"${escape(settings.projectPath)}\",")
            appendLine("  \"release\": {")
            appendLine("    \"minifyEnabled\": ${settings.release.minifyEnabled},")
            appendLine("    \"shrinkResources\": ${settings.release.shrinkResources},")
            appendLine("    \"crunchPngs\": ${settings.release.crunchPngs},")
            appendLine("    \"debuggable\": ${settings.release.debuggable},")
            appendLine("    \"proguardRulesPath\": \"${escape(settings.release.proguardRulesPath ?: "")}\",")
            appendLine("    \"signingEnabled\": ${settings.release.signingEnabled}")
            appendLine("  },")
            appendLine("  \"debug\": {")
            appendLine("    \"minifyEnabled\": ${settings.debug.minifyEnabled},")
            appendLine("    \"shrinkResources\": ${settings.debug.shrinkResources},")
            appendLine("    \"crunchPngs\": ${settings.debug.crunchPngs},")
            appendLine("    \"debuggable\": ${settings.debug.debuggable},")
            appendLine("    \"proguardRulesPath\": \"${escape(settings.debug.proguardRulesPath ?: "")}\",")
            appendLine("    \"signingEnabled\": ${settings.debug.signingEnabled}")
            appendLine("  }")
            appendLine("}")
        }
    }

    private fun parseJson(projectPath: String, jsonText: String): ProjectBuildSettings {
        val relBlock = extractBlock(jsonText, "release")
        val debBlock = extractBlock(jsonText, "debug")

        val releaseSettings = if (relBlock != null) {
            BuildTypeSettings(
                minifyEnabled = extractBoolean(relBlock, "minifyEnabled", true),
                shrinkResources = extractBoolean(relBlock, "shrinkResources", false),
                crunchPngs = extractBoolean(relBlock, "crunchPngs", true),
                debuggable = extractBoolean(relBlock, "debuggable", false),
                proguardRulesPath = extractString(relBlock, "proguardRulesPath")?.takeIf { it.isNotBlank() },
                signingEnabled = extractBoolean(relBlock, "signingEnabled", true)
            )
        } else BuildTypeSettings(minifyEnabled = true, crunchPngs = true, signingEnabled = true)

        val debugSettings = if (debBlock != null) {
            BuildTypeSettings(
                minifyEnabled = extractBoolean(debBlock, "minifyEnabled", false),
                shrinkResources = extractBoolean(debBlock, "shrinkResources", false),
                crunchPngs = extractBoolean(debBlock, "crunchPngs", false),
                debuggable = extractBoolean(debBlock, "debuggable", true),
                proguardRulesPath = extractString(debBlock, "proguardRulesPath")?.takeIf { it.isNotBlank() },
                signingEnabled = extractBoolean(debBlock, "signingEnabled", false)
            )
        } else BuildTypeSettings(debuggable = true)

        return ProjectBuildSettings(
            projectPath = projectPath,
            release = releaseSettings,
            debug = debugSettings
        )
    }

    private fun extractBlock(source: String, key: String): String? {
        val idx = source.indexOf("\"$key\"")
        if (idx == -1) return null
        val braceStart = source.indexOf('{', idx)
        if (braceStart == -1) return null
        var depth = 0
        for (i in braceStart until source.length) {
            if (source[i] == '{') depth++
            else if (source[i] == '}') {
                depth--
                if (depth == 0) return source.substring(braceStart + 1, i)
            }
        }
        return null
    }

    private fun extractBoolean(block: String, key: String, default: Boolean): Boolean {
        val regex = Regex(""""$key"\s*:\s*(true|false)""")
        val m = regex.find(block) ?: return default
        return m.groupValues[1] == "true"
    }

    private fun extractString(block: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val m = regex.find(block) ?: return null
        return unescape(m.groupValues[1])
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String =
        s.replace("\\r", "\r").replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
}

