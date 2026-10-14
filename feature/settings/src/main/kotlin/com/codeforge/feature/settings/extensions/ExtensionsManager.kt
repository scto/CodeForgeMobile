// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionsManager internal constructor(
    private val baseDir: File,
    private val downloader: FileDownloader
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        downloader: HttpFileDownloader
    ) : this(
        baseDir = resolveBaseDirectory(context),
        downloader = downloader
    )

    fun getCatalog(): List<LspExtensionPackage> = LspExtensionCatalog.ALL

    fun getExtensionDirectory(pkg: LspExtensionPackage): File = File(baseDir, pkg.id)

    fun getExecutableFile(pkg: LspExtensionPackage): File? {
        val extensionDir = getExtensionDirectory(pkg)
        if (!extensionDir.exists()) return null
        val executable = File(extensionDir, pkg.executableRelativePath)
        return if (executable.exists()) executable else null
    }

    fun isInstalled(pkg: LspExtensionPackage): Boolean {
        return getExecutableFile(pkg) != null
    }

    suspend fun install(
        pkg: LspExtensionPackage,
        onProgress: (progress: Float, status: ExtensionStatus) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val extensionDir = getExtensionDirectory(pkg)
            val downloadsDir = File(baseDir, "downloads").apply { mkdirs() }
            val archiveFile = File(downloadsDir, "${pkg.id}.${pkg.archiveType.extension}")

            // 1. Download
            onProgress(0f, ExtensionStatus.DOWNLOADING)
            downloader.download(pkg.downloadUrl, archiveFile) { bytesDownloaded, totalBytes ->
                val progress = if (totalBytes > 0) {
                    (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                onProgress(progress, ExtensionStatus.DOWNLOADING)
            }.getOrThrow()

            // 2. Verify SHA-256
            onProgress(1f, ExtensionStatus.VERIFYING)
            val isValid = Sha256Utils.verifySha256(archiveFile, pkg.expectedSha256)
            if (!isValid) {
                archiveFile.delete()
                throw IllegalStateException("SHA-256 mismatch for ${pkg.id}")
            }

            // 3. Extract / Install
            onProgress(1f, ExtensionStatus.EXTRACTING)
            if (extensionDir.exists()) {
                extensionDir.deleteRecursively()
            }
            extensionDir.mkdirs()

            when (pkg.archiveType) {
                ExtensionArchiveType.ZIP -> {
                    ArchiveUtils.extractZip(archiveFile, extensionDir).getOrThrow()
                }
                ExtensionArchiveType.JAR -> {
                    val targetJar = File(extensionDir, pkg.executableRelativePath)
                    targetJar.parentFile?.mkdirs()
                    archiveFile.copyTo(targetJar, overwrite = true)
                }
                ExtensionArchiveType.BINARY -> {
                    val targetBin = File(extensionDir, pkg.executableRelativePath)
                    targetBin.parentFile?.mkdirs()
                    archiveFile.copyTo(targetBin, overwrite = true)
                    targetBin.setExecutable(true, false)
                }
            }

            // Delete temporary archive
            archiveFile.delete()

            val executable = getExecutableFile(pkg)
                ?: throw IllegalStateException("Executable not found after extraction: ${pkg.executableRelativePath}")

            executable.setExecutable(true, false)
            onProgress(1f, ExtensionStatus.INSTALLED)
            executable
        }
    }

    suspend fun uninstall(pkg: LspExtensionPackage): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val extensionDir = getExtensionDirectory(pkg)
            if (extensionDir.exists()) {
                val deleted = extensionDir.deleteRecursively()
                if (!deleted) {
                    throw IllegalStateException("Could not delete directory: ${extensionDir.absolutePath}")
                }
            }
            // Cleanup any leftovers in downloads
            val downloadsDir = File(baseDir, "downloads")
            val cachedFile = File(downloadsDir, "${pkg.id}.${pkg.archiveType.extension}")
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        }
    }

    companion object {
        private fun resolveBaseDirectory(context: Context): File {
            val termuxPrefix = File("/data/data/com.codeforge.app/files/usr/opt/extensions")
            return if (termuxPrefix.parentFile?.exists() == true) {
                termuxPrefix.apply { mkdirs() }
            } else {
                File(context.filesDir, "extensions").apply { mkdirs() }
            }
        }
    }
}
