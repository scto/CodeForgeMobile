// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import com.codeforge.core.domain.repository.SystemPathsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

@Singleton
class DistroBootstrapRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemPaths: SystemPathsRepository
) : DistroBootstrapRepository {

    override fun rootfsPath(distro: String): String {
        return systemPaths.getDistroDir(distro)
    }

    override fun isBootstrapped(distro: String): Boolean {
        val rootfsDir = File(rootfsPath(distro))
        val binSh = File(rootfsDir, "bin/sh")
        val usrBinEnv = File(rootfsDir, "usr/bin/env")
        return rootfsDir.exists() && (binSh.exists() || usrBinEnv.exists())
    }

    override fun bootstrap(distro: String): Flow<BootstrapProgress> = flow {
        val rootfsDir = File(rootfsPath(distro))
        if (isBootstrapped(distro)) {
            emit(BootstrapProgress.Completed)
            return@flow
        }
        rootfsDir.mkdirs()

        val source = DistroCatalog.sourceFor(distro)
        val archiveFile = File(context.cacheDir, "$distro-rootfs.tar.gz")

        val downloader = RootfsDownloader()
        downloader.download(source.url, archiveFile).collect { percent ->
            emit(BootstrapProgress.Downloading(percent))
        }

        emit(BootstrapProgress.Extracting(0))
        try {
            extractTarGz(archiveFile, rootfsDir) { percent ->
                emit(BootstrapProgress.Extracting(percent))
            }
            archiveFile.delete()

            emit(BootstrapProgress.Finalizing)
            setupRootfsEnvironment(rootfsDir)
            emit(BootstrapProgress.Completed)
        } catch (e: Exception) {
            archiveFile.delete()
            emit(BootstrapProgress.Failed(e.message ?: "Unbekannter Fehler beim Entpacken"))
        }
    }.flowOn(Dispatchers.IO)

    private fun extractTarGz(archiveFile: File, targetDir: File, onProgress: suspend (Int) -> Unit) {
        val totalSize = archiveFile.length()
        var readSize = 0L
        var lastPercent = -1

        archiveFile.inputStream().use { fileIn ->
            GZIPInputStream(fileIn.buffered()).use { gzipIn ->
                TarArchiveInputStream(gzipIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        readSize += entry.size
                        val target = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else if (entry.isSymbolicLink) {
                            target.parentFile?.mkdirs()
                            runCatching {
                                Files.deleteIfExists(target.toPath())
                                Files.createSymbolicLink(target.toPath(), java.nio.file.Paths.get(entry.linkName))
                            }
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                            if ((entry.mode and 0x49) != 0) {
                                target.setExecutable(true, false)
                            }
                        }
                        if (totalSize > 0) {
                            val percent = ((readSize * 100) / totalSize).coerceIn(0, 100).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                kotlinx.coroutines.runBlocking { onProgress(percent) }
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    private fun setupRootfsEnvironment(rootfsDir: File) {
        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
    }
}
