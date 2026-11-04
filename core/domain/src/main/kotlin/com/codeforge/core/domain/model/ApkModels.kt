/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

import android.content.Intent
import java.io.File

/** Metadaten einer gebauten APK: Datei, Anwendungs-ID, Variante, Version, optionale ABI und ob sie signiert ist. */
data class ApkMetadata(
    val file: File,
    val applicationId: String,
    val variantName: String,
    val versionCode: Long?,
    val versionName: String?,
    val abiFilter: String? = null,
    val isSigned: Boolean = true,
)

/** Paketinformationen einer APK: Datei, Paketname, Version, App-Name und Signaturstatus. */
data class ApkPackageInfo(
    val file: File,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val appLabel: String?,
    val isSigned: Boolean = true,
)

/** Zustand der APK-Installation. */
sealed interface ApkInstallStatus {
    /** Nichts zu tun. */
    data object Idle : ApkInstallStatus
    /** Die APK wird gebaut. */
    data object Building : ApkInstallStatus
    /** Die APK liegt bereit ([apkInfo]) und kann installiert werden. */
    data class ReadyToInstall(val apkInfo: ApkPackageInfo) : ApkInstallStatus
    /** Die Installation von [packageName] läuft. */
    data class Installing(val packageName: String) : ApkInstallStatus
    /** Der Nutzer muss die Installation im System-Dialog bestätigen ([confirmationIntent]). */
    data class NeedsUserAction(val confirmationIntent: Intent) : ApkInstallStatus
    /** Es fehlt die Berechtigung „Unbekannte Apps installieren“; [settingsIntent] öffnet die Einstellung. */
    data class NeedsPermission(val settingsIntent: Intent) : ApkInstallStatus
    /** Installation abgeschlossen: [packageName], [appLabel] als Anzeigename. */
    data class Installed(val packageName: String, val appLabel: String?) : ApkInstallStatus
    /** Installation fehlgeschlagen ([reason]); die Flags zeigen Signaturkonflikt bzw. Downgrade. */
    data class Failed(
        val reason: String,
        val isSignatureIncompatible: Boolean = false,
        val isDowngrade: Boolean = false,
        val isInsufficientStorage: Boolean = false,
        val isUnsigned: Boolean = false,
        val packageName: String? = null,
    ) : ApkInstallStatus
}
