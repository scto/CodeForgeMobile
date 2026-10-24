/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Eigener FileProvider für den Layout-Designer zur Vermeidung von Manifest-Merger-Kollisionen
 * mit anderen Modulen (z. B. :feature:editor).
 */
package com.codeforge.feature.layoutdesigner.files

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class LayoutDesignerFileProvider : FileProvider() {

    companion object {
        fun authority(context: Context): String =
            "${context.packageName}.feature.layoutdesigner.fileprovider"

        fun uriForFile(context: Context, file: File) =
            getUriForFile(context.applicationContext, authority(context), file)

        fun buildSharePngIntent(context: Context, file: File): Intent {
            val uri = uriForFile(context, file)
            return Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
