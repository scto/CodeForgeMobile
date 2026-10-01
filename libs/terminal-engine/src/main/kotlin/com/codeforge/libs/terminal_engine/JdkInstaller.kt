// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class JdkInstaller(
    private val context: Context,
    private val distroBootstrapRepository: DistroBootstrapRepository
) {
    fun jdkInstallDir(rootfsDir: File, version: String): File {
        return File(rootfsDir, "opt/jdk-$version")
    }

    fun isInstalled(distro: String, version: String): Boolean {
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        val jdkDir = jdkInstallDir(rootfsDir, version)
        val javaBin = File(jdkDir, "bin/java")
        return javaBin.isFile
    }

    fun install(distro: String, version: String): Flow<SdkInstallEvent> = flow {
        emit(SdkInstallEvent.Progress(0, "Starte Installation von JDK $version..."))
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        val targetDir = jdkInstallDir(rootfsDir, version)
        if (targetDir.exists()) {
            emit(SdkInstallEvent.Success("jdk;$version"))
            return@flow
        }
        targetDir.mkdirs()
        val jdkSource = JdkCatalog.all.firstOrNull { it.version == version }
            ?: JdkCatalog.all.first()
        val archiveFile = File(context.cacheDir, "jdk-$version.tar.gz")
        val downloader = RootfsDownloader()
        downloader.download(jdkSource.url, archiveFile).collect { progress ->
            emit(SdkInstallEvent.Progress(progress / 2, "Lade JDK $version herunter ($progress%)..."))
        }

        emit(SdkInstallEvent.Progress(50, "Entpacke JDK $version..."))
        val processBuilder = ProcessBuilder("tar", "-xzf", archiveFile.absolutePath, "-C", targetDir.absolutePath, "--strip-components=1")
        val process = processBuilder.start()
        val exitCode = process.waitFor()
        archiveFile.delete()

        if (exitCode == 0) {
            emit(SdkInstallEvent.Progress(100, "JDK $version erfolgreich installiert"))
            emit(SdkInstallEvent.Success("jdk;$version"))
        } else {
            emit(SdkInstallEvent.Error(RuntimeException("Entpacken von JDK $version fehlgeschlagen (Code $exitCode)")))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun uninstall(distro: String, version: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
            val targetDir = jdkInstallDir(rootfsDir, version)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
        }
    }
}
