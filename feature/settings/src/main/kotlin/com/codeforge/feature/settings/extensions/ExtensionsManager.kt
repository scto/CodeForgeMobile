package com.codeforge.feature.settings.extensions

import android.content.Context
import com.codeforge.core.common.logging.AppLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for downloading, installing, and removing CodeForge extensions (LSP language servers, tools, etc.).
 */
class ExtensionsManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "extensions_sha_prefs"
        private const val KEY_SHA_PREFIX = "sha_"
        private const val TAG = "ExtensionsManager"
        private val TERMUX_PREFIX_DIR_PATH = "/data/data/com.termux/files/usr"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getServerDir(id: String): File = File(context.filesDir, id)

    fun getExtensionDir(id: String): File = getServerDir(id)

    fun isExtensionInstalled(id: String): Boolean = getServerDir(id).exists()

    fun isExtensionInstalled(extension: ExtensionEntry): Boolean {
        if (isExtensionInstalled(extension.id)) return true

        extension.checkPaths?.forEach { path ->
            val file = if (path.startsWith("/")) {
                File(TERMUX_PREFIX_DIR_PATH, path.substring(1))
            } else {
                File(TERMUX_PREFIX_DIR_PATH, path)
            }
            if (file.exists()) return true
        }

        return false
    }

    fun getInstalledSha256(id: String): String = prefs.getString(KEY_SHA_PREFIX + id, "") ?: ""

    private fun setInstalledSha256(id: String, sha256: String) {
        prefs.edit().putString(KEY_SHA_PREFIX + id, sha256).apply()
    }

    fun isUpdateAvailable(extension: ExtensionEntry): Boolean {
        val installedSha = getInstalledSha256(extension.id)
        return isExtensionInstalled(extension) &&
            installedSha.isNotEmpty() &&
            !installedSha.equals(extension.sha256, ignoreCase = true)
    }

    fun getExtensionSize(id: String): Long {
        val dir = getServerDir(id)
        if (!dir.exists()) {
            return 0L
        }
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        return files.sumOf { it.length() }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
        else -> "${String.format("%.1f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    suspend fun installExtension(extension: ExtensionEntry): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val downloadFile = File(context.cacheDir, "${extension.id}.download")
                val extensionDir = if (extension.type == "usr") {
                    File(TERMUX_PREFIX_DIR_PATH)
                } else {
                    getExtensionDir(extension.id)
                }

                URL(extension.url).openConnection().let { connection ->
                    val httpConnection = connection as HttpURLConnection
                    httpConnection.connectTimeout = 10_000
                    httpConnection.readTimeout = 10_000
                    httpConnection.setRequestProperty("User-Agent", "CodeForge-Extensions/1.0")
                    httpConnection.connect()
                    httpConnection.inputStream.use { input ->
                        downloadFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    httpConnection.disconnect()
                }

                val actualSha256 = calculateSha256(downloadFile)
                if (extension.sha256.isNotBlank() && !actualSha256.equals(extension.sha256, ignoreCase = true)) {
                    downloadFile.delete()
                    return@withContext Result.failure(Exception("SHA-256 Mismatch"))
                }

                val extensionName = extension.url.substringAfterLast('/')
                when {
                    extensionName.endsWith(".zip") -> {
                        unzipFile(downloadFile, extensionDir)
                    }

                    extensionName.endsWith(".jar") -> {
                        if (!extensionDir.exists()) extensionDir.mkdirs()
                        val targetJar = File(extensionDir, extensionName)
                        downloadFile.copyTo(targetJar, overwrite = true)
                    }

                    else -> {
                        if (!extensionDir.exists()) extensionDir.mkdirs()
                        val targetFile = File(extensionDir, extensionName)
                        downloadFile.copyTo(targetFile, overwrite = true)
                    }
                }

                downloadFile.delete()
                setInstalledSha256(extension.id, actualSha256)

                Result.success(extensionDir.absolutePath)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Extension installation failed", e)
                Result.failure(e)
            }
        }

    suspend fun uninstallExtension(extension: ExtensionEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (extension.type == "usr") {
                    extension.checkPaths?.forEach { path ->
                        val file = if (path.startsWith("/")) {
                            File(TERMUX_PREFIX_DIR_PATH, path.substring(1))
                        } else {
                            File(TERMUX_PREFIX_DIR_PATH, path)
                        }
                        if (file.exists()) {
                            file.deleteRecursively()
                        }
                    }
                } else {
                    val extensionDir = getExtensionDir(extension.id)
                    if (extensionDir.exists()) {
                        extensionDir.deleteRecursively()
                    }
                }
                prefs.edit().remove(KEY_SHA_PREFIX + extension.id).apply()
                Result.success(Unit)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Extension uninstallation failed", e)
                Result.failure(e)
            }
        }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzipFile(zipFile: File, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()
        val canonicalTargetDir = targetDir.canonicalPath
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalTargetDir)) {
                    throw SecurityException("Invalid zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                entry = zip.nextEntry
            }
        }
    }
}
