// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistroBootstrapRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DistroBootstrapRepository {

    private val downloader = RootfsDownloader()
    private val extractor = RootfsExtractor()

    override fun bootstrap(distro: String): Flow<BootstrapProgress> = flow {
        val source = DistroCatalog.sourceFor(distro)
        if (source == null) {
            emit(BootstrapProgress.Failed("Unbekannte Distribution: $distro"))
            return@flow
        }

        val cacheFile = File(context.cacheDir, "$distro-rootfs.${extensionFor(source.archiveFormat)}")
        val targetDir = rootfsDir(distro)

        downloader.download(source.url, cacheFile).collect { percent ->
            emit(BootstrapProgress.Downloading(percent))
        }

        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        extractor.extract(cacheFile, targetDir, source.archiveFormat).collect { percent ->
            emit(BootstrapProgress.Extracting(percent))
        }

        emit(BootstrapProgress.Finalizing)
        cacheFile.delete()

        emit(BootstrapProgress.Completed)
    }
        .catch { throwable ->
            emit(BootstrapProgress.Failed(throwable.message ?: "Unbekannter Fehler beim Bootstrap von $distro"))
        }
        .flowOn(Dispatchers.IO)

    override fun rootfsPath(distro: String): String = rootfsDir(distro).path

    private fun rootfsDir(distro: String): File = File(context.filesDir, "distro/$distro")

    private fun extensionFor(format: ArchiveFormat): String = when (format) {
        ArchiveFormat.TAR_GZ -> "tar.gz"
        ArchiveFormat.TAR_XZ -> "tar.xz"
    }
}
