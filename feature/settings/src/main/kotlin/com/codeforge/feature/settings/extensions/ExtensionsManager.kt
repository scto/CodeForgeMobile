// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import android.content.Context
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.extension.RequirementManager
import com.codeforge.core.domain.extension.ToolRunEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installiert, prüft und entfernt Erweiterungen (Language-Server): Download mit SHA-256-Prüfung, Entpacken, Termux-Pakete und Voraussetzungen.
 */
@Singleton
class ExtensionsManager internal constructor(
    private val baseDir: File,
    private val downloader: FileDownloader,
    private val customTermuxBinDir: File? = null
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        downloader: HttpFileDownloader,
        toolRunner: PluginToolRunner
    ) : this(
        baseDir = resolveBaseDirectory(context),
        downloader = downloader,
        customTermuxBinDir = null
    ) {
        dependencyInstaller = { requirements ->
            runCatching {
                var exitCode = 0
                val tail = ArrayDeque<String>()
                toolRunner.install(requirements).collect { event ->
                    when (event) {
                        is ToolRunEvent.Line -> {
                            tail.addLast(event.text)
                            if (tail.size > 8) tail.removeFirst()
                        }
                        is ToolRunEvent.Finished -> exitCode = event.exitCode
                    }
                }
                check(exitCode == 0) {
                    "Installation fehlgeschlagen (Exit-Code $exitCode): ${requirements.joinToString { it.name }}" +
                        tail.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\n") .orEmpty()
                }
            }
        }
    }

    /**
     * Installiert fehlende Laufzeit-Pakete (JDK, Node.js, Python, clang …) per APT. In Tests ersetzbar;
     * ohne Installer (`null`) werden fehlende Voraussetzungen nur gemeldet.
     */
    var dependencyInstaller: (suspend (List<RequirementContribution>) -> Result<Unit>)? = null

    private val termuxBinDir: File
        get() = customTermuxBinDir ?: run {
            val prefix = System.getenv("PREFIX") ?: "/data/data/com.codeforge.app/files/usr"
            File(prefix, "bin")
        }

    /** Alle bekannten Erweiterungen. */
    fun getCatalog(): List<LspExtensionPackage> = LspExtensionCatalog.ALL

    /** Installationsordner einer Erweiterung. */
    fun getExtensionDirectory(pkg: LspExtensionPackage): File = File(baseDir, pkg.id)

    /** Ausführbare Datei der Erweiterung oder `null`, wenn nicht installiert. */
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

    /** Ob die Erweiterung installiert ist. */
    fun isInstalled(pkg: LspExtensionPackage): Boolean {
        return getExecutableFile(pkg) != null
    }

    /** Ersetzt den npm-Aufruf (Tests). */
    var customNpmRunner: (suspend (packageDir: File) -> Result<Unit>)? = null

    /**
     * Prüft die Laufzeitvoraussetzungen (`requires`) für ein Paket.
     * Liefert eine Liste der fehlenden Binaries/Paketnamen zurück (z. B. "openjdk-17", "nodejs").
     */
    fun checkMissingRequirements(pkg: LspExtensionPackage): List<String> {
        val missing = mutableListOf<String>()
        for (req in pkg.requires) {
            if (LspDependencies.checkBinaries(req).none { hasBinary(it) }) missing.add(req)
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
            if (LspDependencies.checkBinaries(req).none { hasBinary(it) }) missing.add(req)
        }
        return missing
    }

    /**
     * Installiert die Erweiterung (Voraussetzungen, Download, Prüfung, Entpacken).
     *
     * @param pkg Zu installierende Erweiterung.
     * @param onProgress Meldet Anteil (0–1) und Status.
     * @return Ausführbare Datei oder Fehler.
     */
    suspend fun install(
        pkg: LspExtensionPackage,
        onProgress: (progress: Float, status: ExtensionStatus) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // 0. Laufzeit-Voraussetzungen (JDK, Node.js, Python, clang …) automatisch nachinstallieren.
            onProgress(0f, ExtensionStatus.INSTALLING_DEPENDENCIES)
            ensureDependencies(pkg)

            val downloadSource = when (val source = pkg.source) {
                is ExtensionSource.Download -> source
                is ExtensionSource.TermuxPackage -> {
                    // Das Paket selbst (z. B. clang → clangd) kommt aus dem APT-Repository.
                    if (getExecutableFile(pkg) == null) {
                        val installer = dependencyInstaller ?: throw UnsupportedOperationException(
                            "Termux package '${source.packageName}' must be installed via 'codeforge-env pkg ${source.packageName}' in the terminal."
                        )
                        installer(listOf(RequirementContribution(RequirementManager.PKG, source.packageName, source.binaryName))).getOrThrow()
                    }
                    val bin = getExecutableFile(pkg)
                        ?: throw IllegalStateException("${source.binaryName} nach der Installation von ${source.packageName} nicht gefunden")
                    onProgress(1f, ExtensionStatus.INSTALLED)
                    return@runCatching bin
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

    /**
     * Installiert fehlende Pflicht-Voraussetzungen (Fehler bricht ab) und optionale (best effort).
     * Danach wird erneut geprüft, damit ein stilles Scheitern nicht zu einem kaputten Server führt.
     */
    private suspend fun ensureDependencies(pkg: LspExtensionPackage) {
        val installer = dependencyInstaller ?: return
        val missing = checkMissingRequirements(pkg)
        if (missing.isNotEmpty()) {
            installer(LspDependencies.toInstallList(missing)).getOrThrow()
            val still = checkMissingRequirements(pkg)
            check(still.isEmpty()) { "Voraussetzungen fehlen weiterhin: ${still.joinToString()}" }
        }
        val optional = checkMissingOptionalRequirements(pkg)
        if (optional.isNotEmpty()) {
            installer(LspDependencies.toInstallList(optional)) // optional: Fehler ignorieren
        }
    }

    private fun runNpmInstall(packageDir: File): Result<Unit> = runCatching {
        val npmBin = File(termuxBinDir, "npm").takeIf { it.canExecute() }
            ?: findBinaryOnPath("npm")
            ?: throw IllegalStateException("npm binary not found in Termux. Please install nodejs via 'pkg install nodejs'.")

        val builder = ProcessBuilder(npmBin.absolutePath, "install", "--omit=dev")
            .directory(packageDir)
            .redirectErrorStream(true)
        applyTermuxEnvironment(builder.environment())
        val process = builder.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException("npm install failed with exit code $exitCode: $output")
        }
    }

    /** Deinstalliert die Erweiterung. */
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

    /**
     * npm ist ein Skript mit Shebang `#!/usr/bin/env node`; `env` sucht `node` im PATH des Prozesses.
     * Der App-Prozess hat den Termux-bin-Ordner nicht im PATH, daher wird er hier vorangestellt.
     */
    internal fun applyTermuxEnvironment(env: MutableMap<String, String>) {
        val prefix = termuxBinDir.parentFile
        val currentPath = env["PATH"].orEmpty()
        env["PATH"] = if (currentPath.isBlank()) termuxBinDir.absolutePath else "${termuxBinDir.absolutePath}:$currentPath"
        if (prefix != null) {
            env["PREFIX"] = prefix.absolutePath
            val tmp = File(prefix, "tmp").apply { mkdirs() }
            env["TMPDIR"] = tmp.absolutePath
            if (env["HOME"].isNullOrBlank()) env["HOME"] = File(prefix.parentFile, "home").absolutePath
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
