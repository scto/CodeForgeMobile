// Modul: :app
package com.codeforge.app.keepalive

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.codeforge.app.MainActivity
import com.codeforge.core.domain.repository.TerminalSessionRepository
import com.codeforge.core.domain.runtime.IdeActivityRegistry
import com.codeforge.core.domain.runtime.IdeActivityState
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.gradle_tooling_bridge.BuildForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Vordergrunddienst mit einer Benachrichtigung, solange etwas Heikles läuft (Build, Klonen, SDK-Installation),
 * ein Terminal offen ist oder der Wakelock gehalten wird. Schalter: Wakelock aktivieren/freigeben, Beenden.
 * Der Dienst endet von selbst, wenn nichts mehr davon gilt ([IdeActivityState.needsForeground]).
 */
@AndroidEntryPoint
class IdeKeepAliveService : Service() {

    @Inject lateinit var terminalSession: TerminalSessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            IdeActivityRegistry.state.collect { state ->
                if (!state.needsForeground) {
                    releaseWakeLock()
                    stopForegroundAndSelf()
                    return@collect
                }
                applyWakeLock(state.wakeLock)
                publish(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_WAKELOCK -> IdeActivityRegistry.setWakeLock(!IdeActivityRegistry.state.value.wakeLock)
            ACTION_CANCEL_BUILD -> BuildForegroundService.onCancelRequested?.invoke()
            ACTION_EXIT -> exitIde()
            else -> publish(IdeActivityRegistry.state.value) // ACTION_START: sofort startForeground (5-s-Frist)
        }
        return START_NOT_STICKY
    }

    private fun exitIde() {
        IdeActivityRegistry.setWakeLock(false)
        BuildForegroundService.onCancelRequested?.invoke()
        scope.launch { runCatching { terminalSession.stop() } }
        IdeActivityRegistry.requestExit()
    }

    private fun publish(state: IdeActivityState) {
        val notification = buildNotification(state)
        if (!foregroundStarted) {
            foregroundStarted = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: IdeActivityState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val wakeLabel = Res.string(
            if (state.wakeLock) R.string.ide_keepalive_wakelock_freigeben else R.string.ide_keepalive_wakelock_aktivieren
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Res.string(R.string.ide_keepalive_titel))
            .setContentText(summary(state))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary(state)))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_lock_idle_lock, wakeLabel, service(ACTION_TOGGLE_WAKELOCK))
            .addAction(android.R.drawable.ic_delete, Res.string(R.string.ide_keepalive_beenden), service(ACTION_EXIT))
        if (state.tasks.any { it.startsWith("Build") }) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                Res.string(R.string.ide_keepalive_build_abbrechen),
                service(ACTION_CANCEL_BUILD)
            )
        }
        return builder.build()
    }

    private fun service(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, IdeKeepAliveService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun applyWakeLock(wanted: Boolean) {
        if (wanted) {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Sicherheitsgrenze: nach 6 h gibt das System den Wakelock selbst frei.
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "codeforge:ide-keepalive").apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopForegroundAndSelf() {
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, Res.string(R.string.ide_keepalive_kanal), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "codeforge_ide_keepalive"
        const val NOTIFICATION_ID = 28010
        const val ACTION_START = "com.codeforge.action.IDE_KEEPALIVE_START"
        const val ACTION_TOGGLE_WAKELOCK = "com.codeforge.action.IDE_TOGGLE_WAKELOCK"
        const val ACTION_EXIT = "com.codeforge.action.IDE_EXIT"
        const val ACTION_CANCEL_BUILD = "com.codeforge.action.IDE_CANCEL_BUILD"
        private const val WAKELOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000

        /** Inhalt der Benachrichtigung: Anzahl Terminals, laufende Vorgänge, Wakelock-Status. */
        internal fun summary(state: IdeActivityState): String = buildList {
            add(Res.string(R.string.ide_keepalive_terminals, state.terminals))
            if (state.tasks.isNotEmpty()) add(Res.string(R.string.ide_keepalive_aktiv, state.tasks.joinToString(", ")))
            if (state.wakeLock) add(Res.string(R.string.ide_keepalive_wakelock_an))
        }.joinToString("\n")
    }
}
