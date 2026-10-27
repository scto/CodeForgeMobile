// Modul: :feature:settings
package com.codeforge.feature.settings.store

import com.codeforge.core.domain.model.PluginInstallEvent
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.feature.settings.extensions.FileDownloader
import com.codeforge.feature.settings.extensions.HttpFileDownloader
import com.codeforge.feature.settings.extensions.Sha256Utils
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Ergebnis der Installation eines Store-Plugins. */
sealed interface StoreInstallResult {
    data class Installed(val name: String) : StoreInstallResult
    data object HashMismatch : StoreInstallResult
    data class Failed(val message: String) : StoreInstallResult
}

/**
 * Lädt ein Plugin-Archiv, prüft die SHA-256 aus dem Katalog und übergibt es an das Plugin-System.
 * Ohne passende Prüfsumme wird nichts installiert. Die temporäre Datei wird immer gelöscht.
 */
class StoreInstaller internal constructor(
    private val downloader: FileDownloader,
    private val plugins: PluginRepository,
    private val tempDir: File,
) {
    @Inject constructor(
        downloader: HttpFileDownloader,
        plugins: PluginRepository,
        @ApplicationContext context: Context,
    ) : this(downloader as FileDownloader, plugins, File(context.cacheDir, "store"))

    suspend fun install(
        item: StoreItem,
        onDownloadProgress: (Float) -> Unit = {},
        onInstallProgress: (Int) -> Unit = {},
    ): StoreInstallResult {
        val url = item.downloadUrl
        val expected = item.sha256
        if (!item.isInstallablePlugin || url == null || expected == null) {
            return StoreInstallResult.Failed("nicht installierbar")
        }
        val target = File(tempDir, "${item.id.filter { it.isLetterOrDigit() || it in "._-" }}.zip")
        try {
            val download = downloader.download(url, target) { done, total ->
                if (total > 0) onDownloadProgress(done.toFloat() / total)
            }
            download.exceptionOrNull()?.let { return StoreInstallResult.Failed(it.message ?: it.javaClass.simpleName) }
            if (!Sha256Utils.verifySha256(target, expected)) return StoreInstallResult.HashMismatch

            var result: StoreInstallResult = StoreInstallResult.Failed("keine Rückmeldung")
            plugins.installFromFile(target.absolutePath).collect { event ->
                when (event) {
                    is PluginInstallEvent.Progress -> onInstallProgress(event.percent)
                    is PluginInstallEvent.Success -> result = StoreInstallResult.Installed(event.plugin.name)
                    is PluginInstallEvent.Failed -> result = StoreInstallResult.Failed(event.message)
                }
            }
            return result
        } finally {
            runCatching { target.delete() }
        }
    }
}
