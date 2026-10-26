// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vordergrunddienst für Installationen von Erweiterungen: hält den Prozess am Leben, solange der
 * [ExtensionInstallCoordinator] arbeitet, zeigt Fortschritt mit „Abbrechen“ und meldet das Ergebnis.
 * Die Arbeit selbst liegt im Coordinator; der Dienst beendet sich, sobald nichts mehr läuft.
 */
@AndroidEntryPoint
class ExtensionInstallService : Service() {

    @Inject lateinit var coordinator: ExtensionInstallCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            coordinator.cancelAll()
        }
        enterForeground()
        if (!observing) {
            observing = true
            scope.launch {
                coordinator.active.collect { active ->
                    if (active.isEmpty()) {
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        notify(NOTIFICATION_ID, buildProgress(this@ExtensionInstallService, active.values.toList()))
                    }
                }
            }
            scope.launch {
                coordinator.outcomes.collect { outcome -> notify(resultId(outcome.pkg), buildResult(outcome)) }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15: dataSync-Dienste haben ein Zeitlimit; dann sauber beenden statt abgeschossen zu werden. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        coordinator.cancelAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        val notification = buildProgress(this, coordinator.active.value.values.toList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun notify(id: Int, notification: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    private fun buildResult(outcome: ExtensionInstallOutcome): Notification {
        val name = Res.string(outcome.pkg.nameRes)
        val text = outcome.result.fold(
            onSuccess = { Res.string(R.string.extensions_notification_done, name) },
            onFailure = { Res.string(R.string.extensions_notification_failed, name) }
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Res.string(R.string.extensions_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "codeforge_extensions_channel"
        const val NOTIFICATION_ID = 61001
        const val ACTION_CANCEL = "com.codeforge.action.EXTENSIONS_CANCEL"

        private fun resultId(pkg: LspExtensionPackage): Int = NOTIFICATION_ID + 1 + (pkg.id.hashCode() and 0xFFFF)

        fun start(context: Context) {
            val intent = Intent(context, ExtensionInstallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        internal fun percentOf(active: List<ExtensionInstallProgress>): Int? {
            val downloading = active.filter { it.status == ExtensionStatus.DOWNLOADING }
            if (downloading.isEmpty()) return null
            return (downloading.map { it.progress }.average() * 100).toInt().coerceIn(0, 100)
        }

        private fun buildProgress(context: Context, active: List<ExtensionInstallProgress>): Notification {
            val names = active.joinToString(", ") { Res.string(it.pkg.nameRes) }
            val percent = percentOf(active)
            val cancelIntent = PendingIntent.getService(
                context,
                0,
                Intent(context, ExtensionInstallService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(Res.string(R.string.extensions_notification_title))
                .setContentText(names.ifBlank { Res.string(R.string.extensions_notification_working) })
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, percent ?: 0, percent == null)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    Res.string(R.string.extensions_notification_cancel),
                    cancelIntent
                )
                .build()
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                Res.string(R.string.extensions_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
