package com.codeforge.feature.x2c.run

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
import com.codeforge.feature.x2c.X2cPhase
import com.codeforge.feature.x2c.ui.phaseLabel

/**
 * Task 52.11: Vordergrunddienst für lange Läufe der Layout-Migration (Umwandlung, Build, Rückgängig), damit Android den
 * Prozess nicht beendet, wenn die App in den Hintergrund geht. Zeigt die aktuelle Phase und bietet „Abbrechen“.
 *
 * Der Dienst enthält keine Logik; die Arbeit läuft im ViewModel. Der Abbruch geht über [onCancelRequested].
 */
class X2cForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                onCancelRequested?.invoke()
                // Der Dienst läuft weiter, bis das ViewModel `finished()` meldet: die Rücknahme der Änderungen läuft noch.
            }
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_START -> {
                val notification = buildNotification(this, phase = null, percent = null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val CHANNEL_ID = "codeforge_x2c_channel"
        const val NOTIFICATION_ID = 52011

        const val ACTION_START = "com.codeforge.action.X2C_START"
        const val ACTION_STOP = "com.codeforge.action.X2C_STOP"
        const val ACTION_CANCEL = "com.codeforge.action.X2C_CANCEL"

        /** Wird vom Notifier gesetzt; ruft den Abbruch im ViewModel auf. */
        @Volatile
        var onCancelRequested: (() -> Unit)? = null

        fun start(context: Context) {
            val intent = Intent(context, X2cForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, X2cForegroundService::class.java).setAction(ACTION_STOP))
        }

        fun update(context: Context, phase: X2cPhase?, percent: Int?) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIFICATION_ID, buildNotification(context, phase, percent))
        }

        private fun buildNotification(context: Context, phase: X2cPhase?, percent: Int?): Notification {
            val cancelIntent = PendingIntent.getService(
                context,
                0,
                Intent(context, X2cForegroundService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = phase?.let { Res.string(R.string.x2c_notification_text, Res.string(phaseLabel(it))) }
                ?: Res.string(R.string.x2c_phase_analyse)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(Res.string(R.string.x2c_notification_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, percent ?: 0, percent == null)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    Res.string(R.string.x2c_notification_cancel),
                    cancelIntent
                )
                .build()
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                Res.string(R.string.x2c_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = Res.string(R.string.x2c_notification_channel_desc) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
