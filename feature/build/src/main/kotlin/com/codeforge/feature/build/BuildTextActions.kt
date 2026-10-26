/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.io.IOException

/**
 * Kopieren, Teilen und Speichern von Reiter-Text (Ausgabe, Probleme, Logcat, Tests).
 * Lebt im Host (nicht im Sheet), damit die Effekte genau einen Empfänger haben und auch bei geschlossenem Sheet ankommen.
 *
 * @param onMessage kurze Rückmeldung für die Snackbar
 */
@Stable
class BuildTextActions internal constructor(
    private val context: Context,
    private val launchSave: (String) -> Unit,
    private val setPendingSave: (String) -> Unit,
    private val onMessage: (String) -> Unit
) {

    fun copy(text: String) {
        val trimmed = text.keepTail()
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("CodeForge", trimmed.text))
            onMessage(
                Res.string(if (trimmed.truncated) R.string.build_tab_kopiert_gekuerzt else R.string.build_tab_kopiert)
            )
        } catch (e: RuntimeException) {
            // z. B. TransactionTooLargeException bei sehr großem Text
            onMessage(Res.string(R.string.build_tab_zu_gross))
        }
    }

    fun share(text: String, title: String) {
        val trimmed = text.keepTail()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, trimmed.text)
        }
        try {
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (trimmed.truncated) onMessage(Res.string(R.string.build_tab_teilen_gekuerzt))
        } catch (e: RuntimeException) {
            onMessage(Res.string(R.string.build_tab_zu_gross))
        }
    }

    fun save(text: String, fileName: String) {
        setPendingSave(text)
        launchSave(fileName)
    }

    internal fun writeTo(uri: Uri?, text: String?) {
        if (uri == null) return // Auswahl abgebrochen
        if (text == null) return
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("Kein Ausgabestrom")
            onMessage(Res.string(R.string.build_tab_gespeichert))
        } catch (e: IOException) {
            onMessage(Res.string(R.string.build_tab_speichern_fehlgeschlagen, e.message.orEmpty()))
        } catch (e: SecurityException) {
            onMessage(Res.string(R.string.build_tab_speichern_fehlgeschlagen, e.message.orEmpty()))
        }
    }
}

@Composable
fun rememberBuildTextActions(onMessage: (String) -> Unit): BuildTextActions {
    val context = LocalContext.current
    val message = rememberUpdatedState(onMessage)
    val pending = remember { arrayOfNulls<String>(1) }
    var actionsRef: BuildTextActions? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        actionsRef?.writeTo(uri, pending[0])
        pending[0] = null
    }
    val actions = remember(context, launcher) {
        BuildTextActions(
            context = context,
            launchSave = { name -> launcher.launch(name) },
            setPendingSave = { pending[0] = it },
            onMessage = { message.value(it) }
        )
    }
    actionsRef = actions
    return actions
}
