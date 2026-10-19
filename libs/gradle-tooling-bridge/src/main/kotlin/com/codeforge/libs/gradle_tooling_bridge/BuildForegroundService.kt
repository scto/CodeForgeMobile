/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

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

class BuildForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "codeforge_build_execution_channel"
        const val NOTIFICATION_ID = 28001

        const val ACTION_START = "com.codeforge.action.START_BUILD"
        const val ACTION_STOP = "com.codeforge.action.STOP_BUILD"
        const val ACTION_CANCEL = "com.codeforge.action.CANCEL_BUILD"

        const val EXTRA_TASK_NAME = "extra_task_name"

        var onCancelRequested: (() -> Unit)? = null

        fun start(context: Context, taskName: String) {
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_NAME, taskName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                onCancelRequested?.invoke()
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "build"
                val notification = buildNotification(taskName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        } else {
                            0
                        }
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(taskName: String): Notification {
        val cancelIntent = Intent(this, BuildForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = Res.string(R.string.build_benachrichtigung_titel, taskName)
        val text = Res.string(R.string.build_benachrichtigung_text, taskName)
        val cancelLabel = Res.string(R.string.build_abbrechen)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Gradle Build Service"
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Gradle build progress"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
