package com.codeforge.core.common.fileprovider

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Eigener FileProvider für CFM-Dateien (Crash-Logs, IDE-Logs, Export).
 * Eigene Klasse verhindert Manifest-Merger-Kollisionen.
 */
class CfmFileProvider : FileProvider() {

    companion object {
        fun authority(context: Context): String =
            "${context.packageName}.cfm.fileprovider"

        fun getUriForFile(context: Context, file: File): Uri =
            FileProvider.getUriForFile(context, authority(context), file)

        fun buildShareIntent(context: Context, file: File, title: String = "Crash-Log teilen"): Intent {
            val uri = getUriForFile(context, file)
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, title) }
        }
    }
}
