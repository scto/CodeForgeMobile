/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Kapselt den androidx.core.content.FileProvider-Zugriff für den Editor: Teilen ("Senden an")
 * und externes Öffnen ("Öffnen mit...") einer im Editor aktiven Datei, ohne eine
 * FileUriExposedException (file://-URIs über Prozessgrenzen, verboten seit Android 7).
 * Authority und Pfad-Konfiguration: siehe AndroidManifest.xml / xml/editor_file_provider_paths.xml.
 */
package com.codeforge.feature.editor.fileprovider

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object EditorFileProvider {

    private fun authority(context: Context): String =
        "${context.packageName}.feature.editor.fileprovider"

    fun uriForFile(context: Context, absolutePath: String) =
        FileProvider.getUriForFile(context.applicationContext, authority(context), File(absolutePath))

    /** Baut einen Share-Intent (Intent.ACTION_SEND) für die gegebene Datei. */
    fun buildShareIntent(context: Context, absolutePath: String, mimeType: String = "text/plain"): Intent {
        val uri = uriForFile(context, absolutePath)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Baut einen "Öffnen mit..."-Intent (Intent.ACTION_VIEW) für die gegebene Datei. */
    fun buildOpenWithIntent(context: Context, absolutePath: String, mimeType: String = "*/*"): Intent {
        val uri = uriForFile(context, absolutePath)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
