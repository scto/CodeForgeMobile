/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.codeforge.core.domain.extension.PluginPermissionPolicy
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.PluginPromptRegistry
import com.codeforge.core.domain.runtime.IdeProgressKind
import com.codeforge.core.domain.runtime.IdeProgressRegistry
import com.codeforge.libs.plugin_api.PluginPermissionException
import com.codeforge.libs.plugin_api.PluginUi
import com.codeforge.libs.plugin_api.ProgressReporter
import java.util.concurrent.ConcurrentHashMap

/**
 * [PluginUi] eines Plugins: Fortschritt über [IdeProgressRegistry], Rückfragen über [PluginPromptRegistry],
 * Benachrichtigungen über einen eigenen Kanal. [close] beendet alles Offene des Plugins.
 */
internal class HostPluginUi(
    private val pluginId: String,
    private val pluginName: String,
    private val apiVersion: Int,
    private val granted: Set<String>,
    private val notifier: PluginNotifier?
) : PluginUi {
    private val progressIds = ConcurrentHashMap.newKeySet<Long>()

    override suspend fun <T> withProgress(title: String, block: suspend (ProgressReporter) -> T): T {
        val id = IdeProgressRegistry.begin(IdeProgressKind.PLUGIN, title.take(MAX_TITLE).ifBlank { pluginName })
        progressIds += id
        return try {
            block(object : ProgressReporter {
                override fun report(detail: String, percent: Int?) {
                    if (id in progressIds) IdeProgressRegistry.update(id, detail.take(MAX_DETAIL), percent)
                }
            })
        } finally {
            progressIds -= id
            IdeProgressRegistry.end(id)
        }
    }

    override suspend fun confirm(title: String, message: String, confirmLabel: String): Boolean =
        PluginPromptRegistry.ask(pluginId, title, message, listOf(confirmLabel.ifBlank { "OK" })) == 0

    override suspend fun choose(title: String, options: List<String>): Int? =
        if (options.isEmpty()) null else PluginPromptRegistry.ask(pluginId, title, "", options)

    override fun notify(title: String, text: String, tag: String) {
        if (!PluginPermissionPolicy.isAllowed(apiVersion, granted, PluginPermissions.NOTIFICATIONS)) {
            throw PluginPermissionException(pluginId, PluginPermissions.NOTIFICATIONS)
        }
        notifier?.show(pluginId, pluginName, tag.take(MAX_TITLE), title.take(MAX_TITLE), text.take(MAX_TEXT))
    }

    /** Beim Entladen: Rückfragen abbrechen, Fortschrittsanzeigen entfernen. */
    fun close() {
        PluginPromptRegistry.cancelAll(pluginId)
        progressIds.toList().forEach { IdeProgressRegistry.end(it) }
        progressIds.clear()
    }

    private companion object {
        const val MAX_TITLE = 120
        const val MAX_DETAIL = 200
        const val MAX_TEXT = 1_000
    }
}

/** Zeigt Benachrichtigungen von Plugins im Kanal „Plugins“; Tippen öffnet die App. */
internal class PluginNotifier(private val context: Context) {
    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    /** Zeigt bzw. ersetzt die Benachrichtigung ([pluginId], [tag]). */
    fun show(pluginId: String, pluginName: String, tag: String, title: String, text: String) {
        val nm = manager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSubText(pluginName)
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        nm.notify("plugin/$pluginId/$tag", NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "codeforge.plugins"
        const val CHANNEL_NAME = "Plugins"
        const val NOTIFICATION_ID = 7_301
    }
}
