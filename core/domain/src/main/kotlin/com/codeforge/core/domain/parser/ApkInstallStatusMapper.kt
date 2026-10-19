/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.resources.R
import javax.inject.Inject
import javax.inject.Singleton

fun interface StringResolver {
    fun resolve(resId: Int, vararg args: Any): String
}

object ApkInstallStatusCodes {
    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = 1
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_ABORTED = 3
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7
    const val STATUS_PENDING_USER_ACTION = -1
}

@Singleton
class ApkInstallStatusMapper @Inject constructor() {

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
