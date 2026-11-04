/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.resources.R
import javax.inject.Inject
import javax.inject.Singleton

/** Löst String-Ressourcen auf, ohne dass die Domain von Android abhängt. */
fun interface StringResolver {
    /** Liefert den Text zur Ressourcen-ID [resId] mit Formatargumenten. */
    fun resolve(resId: Int, vararg args: Any): String
}

/** Statuscodes der Paketinstallation (entsprechen `PackageInstaller`). */
object ApkInstallStatusCodes {
    /** Installation erfolgreich. */
    const val STATUS_SUCCESS = 0
    /** Allgemeiner Fehler. */
    const val STATUS_FAILURE = 1
    /** Blockiert (z. B. durch Richtlinie). */
    const val STATUS_FAILURE_BLOCKED = 2
    /** Abgebrochen. */
    const val STATUS_FAILURE_ABORTED = 3
    /** Ungültige APK. */
    const val STATUS_FAILURE_INVALID = 4
    /** Konflikt mit einer installierten App. */
    const val STATUS_FAILURE_CONFLICT = 5
    /** Zu wenig Speicher. */
    const val STATUS_FAILURE_STORAGE = 6
    /** Mit dem Gerät nicht kompatibel. */
    const val STATUS_FAILURE_INCOMPATIBLE = 7
    /** Wartet auf eine Bestätigung des Nutzers. */
    const val STATUS_PENDING_USER_ACTION = -1
}

/** Übersetzt Installationsstatuscodes in verständliche Meldungen. */
@Singleton
class ApkInstallStatusMapper @Inject constructor() {

    /** Ordnet einen Statuscode samt Systemmeldung einem lesbaren Ergebnis zu. */
    fun mapStatusCode(
        statusCode: Int,
        rawMessage: String?,
        packageName: String?,
        appLabel: String?,
        stringResolver: StringResolver
    ): ApkInstallStatus {
        val msg = rawMessage.orEmpty()
        val isDowngrade = msg.contains("downgrade", ignoreCase = true) ||
            msg.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true)

        if (isDowngrade) {
            return ApkInstallStatus.Failed(
                reason = stringResolver.resolve(R.string.install_fehler_downgrade),
                isDowngrade = true,
                packageName = packageName
            )
        }

        return when (statusCode) {
            ApkInstallStatusCodes.STATUS_SUCCESS -> {
                ApkInstallStatus.Installed(
                    packageName = packageName.orEmpty(),
                    appLabel = appLabel
                )
            }
            ApkInstallStatusCodes.STATUS_FAILURE_STORAGE -> {
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_fehler_speicherplatz),
                    isInsufficientStorage = true,
                    packageName = packageName
                )
            }
            ApkInstallStatusCodes.STATUS_FAILURE_INCOMPATIBLE -> {
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_inkompatible_signatur_nachricht),
                    isSignatureIncompatible = true,
                    packageName = packageName
                )
            }
            ApkInstallStatusCodes.STATUS_FAILURE_BLOCKED -> {
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_fehler_blockiert),
                    packageName = packageName
                )
            }
            ApkInstallStatusCodes.STATUS_FAILURE_ABORTED -> {
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_fehler_abgebrochen),
                    packageName = packageName
                )
            }
            ApkInstallStatusCodes.STATUS_FAILURE_INVALID -> {
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_fehler_beschaedigt),
                    packageName = packageName
                )
            }
            ApkInstallStatusCodes.STATUS_FAILURE_CONFLICT -> {
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_fehler_konflikt),
                    packageName = packageName
                )
            }
            else -> {
                val reasonText = if (msg.isNotBlank()) msg else "Unknown error"
                ApkInstallStatus.Failed(
                    reason = stringResolver.resolve(R.string.install_fehler_unbekannt, statusCode, reasonText),
                    packageName = packageName
                )
            }
        }
    }
}
