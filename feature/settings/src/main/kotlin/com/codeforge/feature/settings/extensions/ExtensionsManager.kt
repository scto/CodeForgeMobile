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
    private val downloader: FileDownloader,
    private val customTermuxBinDir: File? = null
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        downloader: HttpFileDownloader
    ) : this(
        baseDir = resolveBaseDirectory(context),
        downloader = downloader,
        customTermuxBinDir = null
    )

    private val termuxBinDir: File
        get() = customTermuxBinDir ?: run {
            val prefix = System.getenv("PREFIX") ?: "/data/data/com.codeforge.app/files/usr"
            File(prefix, "bin")
        }

    fun getCatalog(): List<LspExtensionPackage> = LspExtensionCatalog.ALL

    fun getExtensionDirectory(pkg: LspExtensionPackage): File = File(baseDir, pkg.id)

    fun getExecutableFile(pkg: LspExtensionPackage): File? {
        return when (val source = pkg.source) {
            is ExtensionSource.TermuxPackage -> {
                val direct = File(termuxBinDir, source.binaryName)
                if (direct.exists() && direct.canExecute()) {
                    direct
                } else {
                    findBinaryOnPath(source.binaryName)
                }
            }
            is ExtensionSource.Download -> {
                val extensionDir = getExtensionDirectory(pkg)
                if (!extensionDir.exists()) return null
                val executable = File(extensionDir, pkg.executableRelativePath)
                if (executable.exists()) executable else null
            }
        }
    }

    fun isInstalled(pkg: LspExtensionPackage): Boolean {
        return getExecutableFile(pkg) != null
    }

    var customNpmRunner: (suspend (packageDir: File) -> Result<Unit>)? = null

    /**
     * Prüft die Laufzeitvoraussetzungen (`requires`) für ein Paket.
     * Liefert eine Liste der fehlenden Binaries/Paketnamen zurück (z. B. "openjdk-17", "nodejs").
     */
    fun checkMissingRequirements(pkg: LspExtensionPackage): List<String> {
        val missing = mutableListOf<String>()
        for (req in pkg.requires) {
            val satisfied = when (req) {
                "openjdk-17", "java" -> hasBinary("java")
                "nodejs", "node" -> hasBinary("node")
                "npm" -> hasBinary("npm")
                "python", "python3" -> hasBinary("python3") || hasBinary("python")
                "clang" -> hasBinary("clangd") || hasBinary("clang")
                "rust-analyzer" -> hasBinary("rust-analyzer")
                "taplo" -> hasBinary("taplo")
                "shellcheck" -> hasBinary("shellcheck")
                else -> hasBinary(req)
            }
            if (!satisfied) {
                missing.add(req)
            }
        }
        return missing
    }

    /**
     * Prüft optionale Laufzeitvoraussetzungen (`optionalRequires`) für ein Paket.
     * Fehlende optionale Abhängigkeiten verhindern nicht die Installation oder den Start.
     */
    fun checkMissingOptionalRequirements(pkg: LspExtensionPackage): List<String> {
        val missing = mutableListOf<String>()
        for (req in pkg.optionalRequires) {
            val satisfied = when (req) {
                "shellcheck" -> hasBinary("shellcheck")
                else -> hasBinary(req)
            }
            if (!satisfied) {
                missing.add(req)
            }
        }
        return missing
    }

    suspend fun install(
        pkg: LspExtensionPackage,
        onProgress: (progress: Float, status: ExtensionStatus) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadSource = when (val source = pkg.source) {
                is ExtensionSource.Download -> source
                is ExtensionSource.TermuxPackage -> {
                    throw UnsupportedOperationException(
                        "Termux package '${source.packageName}' must be installed via 'codeforge-env pkg ${source.packageName}' in the terminal."
                    )
                }
            }

            val extensionDir = getExtensionDirectory(pkg)
            val downloadsDir = File(baseDir, "downloads").apply { mkdirs() }
            val archiveFile = File(downloadsDir, "${pkg.id}.${downloadSource.archiveType.extension}")

            try {
                // 1. Download
                onProgress(0f, ExtensionStatus.DOWNLOADING)
                downloader.download(downloadSource.url, archiveFile) { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0.5f
                    }
                    onProgress(progress, ExtensionStatus.DOWNLOADING)
                }.getOrThrow()

                // 2. Verify SHA-256
                onProgress(1f, ExtensionStatus.VERIFYING)
                val isValid = Sha256Utils.verifySha256(archiveFile, downloadSource.sha256)
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

                when (downloadSource.archiveType) {
                    ExtensionArchiveType.ZIP -> {
                        ArchiveUtils.extractZip(archiveFile, extensionDir).getOrThrow()
                    }
                    ExtensionArchiveType.TAR_GZ -> {
                        ArchiveUtils.extractTarGz(archiveFile, extensionDir).getOrThrow()
                    }
                    ExtensionArchiveType.GZ -> {
                        val targetBin = File(extensionDir, pkg.executableRelativePath)
                        ArchiveUtils.extractGz(archiveFile, targetBin).getOrThrow()
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

                // Delete temporary archive after successful extraction
                archiveFile.delete()

                // 4. Install npm dependencies if required
                if (pkg.needsNpmInstall) {
                    onProgress(1f, ExtensionStatus.INSTALLING_DEPENDENCIES)
                    val packageDir = File(extensionDir, "package").takeIf { it.exists() } ?: extensionDir
                    val runner = customNpmRunner
                    if (runner != null) {
                        runner(packageDir).getOrThrow()
                    } else {
                        runNpmInstall(packageDir).getOrThrow()
                    }
                }

                val executable = getExecutableFile(pkg)
                    ?: throw IllegalStateException("Executable not found after extraction: ${pkg.executableRelativePath}")

                executable.setExecutable(true, false)
                onProgress(1f, ExtensionStatus.INSTALLED)
                executable
            } catch (e: Exception) {
                // Cleanup on failure
                archiveFile.delete()
                if (extensionDir.exists()) {
                    extensionDir.deleteRecursively()
                }
                throw e
            }
        }
    }

    private fun runNpmInstall(packageDir: File): Result<Unit> = runCatching {
        val npmBin = File(termuxBinDir, "npm").takeIf { it.canExecute() }
            ?: findBinaryOnPath("npm")
            ?: throw IllegalStateException("npm binary not found in Termux. Please install nodejs via 'pkg install nodejs'.")

        val process = ProcessBuilder(npmBin.absolutePath, "install", "--omit=dev")
            .directory(packageDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("npm install failed with exit code $exitCode: $output")
        }
    }

    suspend fun uninstall(pkg: LspExtensionPackage): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (pkg.source is ExtensionSource.Download) {
                val extensionDir = getExtensionDirectory(pkg)
                if (extensionDir.exists()) {
                    val deleted = extensionDir.deleteRecursively()
                    if (!deleted) {
                        throw IllegalStateException("Could not delete directory: ${extensionDir.absolutePath}")
                    }
                }
                // Cleanup any leftovers in downloads
                val downloadsDir = File(baseDir, "downloads")
                val archiveType = pkg.archiveType ?: ExtensionArchiveType.ZIP
                val cachedFile = File(downloadsDir, "${pkg.id}.${archiveType.extension}")
                if (cachedFile.exists()) {
                    cachedFile.delete()
                }
            }
        }
    }

    private fun hasBinary(name: String): Boolean {
        if (File(termuxBinDir, name).canExecute()) return true
        return findBinaryOnPath(name) != null
    }

    private fun findBinaryOnPath(name: String): File? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(':')) {
            if (dir.isBlank()) continue
            val candidate = File(dir, name)
            if (candidate.exists() && candidate.canExecute()) {
                return candidate
            }
        }
        return null
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
