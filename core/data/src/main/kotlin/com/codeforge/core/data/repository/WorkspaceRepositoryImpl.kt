package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.CfmFileInfo
import com.codeforge.core.domain.model.CfmOpenFile
import com.codeforge.core.domain.model.CfmWorkspace
import com.codeforge.core.domain.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor() : WorkspaceRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mutexMap = ConcurrentHashMap<String, Mutex>()

    private fun getMutex(projectDir: File): Mutex {
        val key = try { projectDir.canonicalPath } catch (_: Exception) { projectDir.absolutePath }
        return mutexMap.computeIfAbsent(key) { Mutex() }
    }

    private fun isoNow(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return df.format(Date())
    }

    override suspend fun ensureGitExclude(projectDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val gitDir = File(projectDir, ".git")
            if (!gitDir.isDirectory) return@runCatching

            val infoDir = File(gitDir, "info").apply { mkdirs() }
            val excludeFile = File(infoDir, "exclude")

            val currentText = if (excludeFile.exists()) excludeFile.readText() else ""
            val lines = currentText.lines().map { it.trim() }

            val alreadyExcluded = lines.any { it == ".cfm" || it == ".cfm/" }
            if (!alreadyExcluded) {
                val prefix = if (currentText.isNotEmpty() && !currentText.endsWith("\n")) "\n" else ""
                excludeFile.appendText("${prefix}.cfm/\n")
            }
        }
    }

    override suspend fun loadWorkspace(projectDir: File): CfmWorkspace? = withContext(Dispatchers.IO) {
        val mutex = getMutex(projectDir)
        mutex.withLock {
            val cfmFile = File(File(projectDir, WorkspaceRepository.CFM_DIR_NAME), WorkspaceRepository.WORKSPACE_FILE_NAME)
            if (!cfmFile.exists() || !cfmFile.isFile || cfmFile.length() == 0L) {
                return@withLock null
            }

            try {
                val text = cfmFile.readText()
                val parsed = json.decodeFromString<CfmWorkspace>(text)
                if (parsed.version != WorkspaceRepository.SCHEMA_VERSION) {
                    throw IllegalStateException("Unbekannte Schema-Version: ${parsed.version}")
                }
                parsed
            } catch (_: Throwable) {
                // Bei Beschädigung oder falscher Version: umbenennen in cfm.json.broken-<timestamp>
                try {
                    val brokenTarget = File(
                        cfmFile.parentFile,
                        "${WorkspaceRepository.WORKSPACE_FILE_NAME}.broken-${System.currentTimeMillis()}"
                    )
                    cfmFile.renameTo(brokenTarget)
                } catch (_: Throwable) {}
                null
            }
        }
    }

    override suspend fun saveWorkspace(projectDir: File, workspace: CfmWorkspace): Result<Unit> = withContext(Dispatchers.IO) {
        val mutex = getMutex(projectDir)
        mutex.withLock {
            runCatching {
                ensureGitExclude(projectDir)

                val cfmDir = File(projectDir, WorkspaceRepository.CFM_DIR_NAME)
                if (!cfmDir.exists()) {
                    cfmDir.mkdirs()
                }

                val targetFile = File(cfmDir, WorkspaceRepository.WORKSPACE_FILE_NAME)
                val tmpFile = File(cfmDir, "${WorkspaceRepository.WORKSPACE_FILE_NAME}.tmp")

                val serialized = json.encodeToString(CfmWorkspace.serializer(), workspace)
                tmpFile.writeText(serialized)

                val success = tmpFile.renameTo(targetFile)
                if (!success) {
                    // Fallback falls rename fehlschlägt (z.B. plattformspezifische Dateisperren)
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
            }
        }
    }

    override suspend fun scanProjectFiles(projectDir: File, maxFiles: Int): List<CfmFileInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CfmFileInfo>()
        if (!projectDir.exists() || !projectDir.isDirectory) return@withContext emptyList()

        val rootCanonical = try { projectDir.canonicalFile } catch (_: Exception) { projectDir.absoluteFile }

        fun walk(dir: File) {
            if (result.size >= maxFiles) return
            val children = dir.listFiles() ?: return

            for (file in children) {
                if (result.size >= maxFiles) break
                val name = file.name
                if (file.isDirectory) {
                    if (name in WorkspaceRepository.DEFAULT_EXCLUDED_DIRS) {
                        continue
                    }
                    walk(file)
                } else if (file.isFile) {
                    val relPath = file.canonicalPath
                        .removePrefix(rootCanonical.path)
                        .trimStart(File.separatorChar)
                        .replace(File.separatorChar, '/')

                    result.add(
                        CfmFileInfo(
                            path = relPath,
                            size = file.length(),
                            modified = file.lastModified()
                        )
                    )
                }
            }
        }

        walk(rootCanonical)
        result
    }

    override suspend fun updateModifiedFiles(projectDir: File, modifiedRelativePaths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = loadWorkspace(projectDir)
        val files = existing?.files ?: scanProjectFiles(projectDir)
        val updated = (existing ?: CfmWorkspace(
            version = WorkspaceRepository.SCHEMA_VERSION,
            project = projectDir.name,
            updatedAt = isoNow(),
            files = files,
            openFiles = emptyList(),
            modifiedFiles = emptyList()
        )).copy(
            updatedAt = isoNow(),
            files = files,
            modifiedFiles = modifiedRelativePaths.distinct()
        )
        saveWorkspace(projectDir, updated)
    }

    override suspend fun updateOpenFiles(projectDir: File, openFiles: List<CfmOpenFile>): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = loadWorkspace(projectDir)
        val files = existing?.files ?: scanProjectFiles(projectDir)
        val updated = (existing ?: CfmWorkspace(
            version = WorkspaceRepository.SCHEMA_VERSION,
            project = projectDir.name,
            updatedAt = isoNow(),
            files = files,
            openFiles = openFiles,
            modifiedFiles = emptyList()
        )).copy(
            updatedAt = isoNow(),
            openFiles = openFiles
        )
        saveWorkspace(projectDir, updated)
    }
}
