/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Systemrechte rund ums eingebaute Terminal.
 *
 * Anders als Editoren, die eine separate Termux-App fernsteuern, braucht CodeForge weder die Termux-Berechtigung
 * `com.termux.permission.RUN_COMMAND` noch `allow-external-apps`: Terminal, Sprachserver und Builds laufen im
 * eigenen Prozess. Nützlich bleiben „Über anderen Apps einblenden“ (Terminal aus dem Hintergrund öffnen) und das
 * Abschalten der Akku-Optimierung (lange Sitzungen).
 */
data class TermuxIntegration(
    val overlayGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val allowExternalApps: Boolean,
    val reloaded: Boolean,
) {
    /** Statusabfragen und Systemdialoge. */
    companion object {
        /** Schlüssel in termux.properties. */
        const val KEY_ALLOW_EXTERNAL_APPS = "allow-external-apps"

        /** „Über anderen Apps einblenden“ erteilt. */
        fun overlayGranted(context: Context): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

        /** Akku-Optimierung für die App ausgeschaltet. */
        fun batteryUnrestricted(context: Context): Boolean = runCatching {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

        /** Systemseite „Über anderen Apps einblenden“ dieser App. */
        fun overlayIntent(context: Context) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

        /** Systemdialog „Akku-Optimierung ignorieren“ (Rückfall: Liste aller Apps). */
        @android.annotation.SuppressLint("BatteryLife") // Terminal mit Builds und Sprachservern: begründeter Fall.
        fun batteryIntent(context: Context) =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

        /** Rückfall, wenn der direkte Dialog nicht verfügbar ist. */
        fun batteryListIntent() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        /** Rückfall für Geräte ohne Seite pro App. */
        fun overlayListIntent() = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    }
}
