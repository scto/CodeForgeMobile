package com.codeforge.core.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ProjectsDirectoryProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ProjectsDirectoryProvider {

    override fun isExternalStorageUsable(): Boolean {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun getProjectsRoot(): File {
        val root = if (isExternalStorageUsable()) {
            File(Environment.getExternalStorageDirectory(), ProjectsDirectoryProvider.PROJECTS_DIR_NAME)
        } else {
            // Rückfall ohne Berechtigung: app-spezifischer externer Speicher oder interner Speicher
            context.getExternalFilesDir("projects") ?: File(context.filesDir, "projects")
        }
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    override fun sanitizeProjectName(name: String): String {
        val sanitized = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('.', '_')
        if (sanitized.equals("cfm", ignoreCase = true) || sanitized.isBlank()) {
            return "project"
        }
        return sanitized
    }

    override fun resolveProjectPath(projectName: String): File {
        val safeName = sanitizeProjectName(projectName)
        val root = getProjectsRoot().canonicalFile
        val target = File(root, safeName).canonicalFile

        // Traversal-Schutz: Ziel muss direkt unter dem Stammverzeichnis liegen
        if (!target.path.startsWith(root.path) || target.parentFile?.canonicalPath != root.canonicalPath) {
            throw IllegalArgumentException("Ungültiger Projektpfad außerhalb des Projektstamms: $projectName")
        }
        return target
    }

    override fun isSubpathOfProjectsRoot(file: File): Boolean {
        return try {
            val rootPath = getProjectsRoot().canonicalPath
            val filePath = file.canonicalPath
            filePath == rootPath || filePath.startsWith(rootPath + File.separator)
        } catch (_: Exception) {
            false
        }
    }
}
