/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

import android.content.Intent
import java.io.File

data class ApkMetadata(
    val file: File,
    val applicationId: String,
    val variantName: String,
    val versionCode: Long?,
    val versionName: String?,
    val abiFilter: String? = null,
    val isSigned: Boolean = true,
)

data class ApkPackageInfo(
    val file: File,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val appLabel: String?,
    val isSigned: Boolean = true,
)

sealed interface ApkInstallStatus {
    data object Idle : ApkInstallStatus
    data object Building : ApkInstallStatus
    data class ReadyToInstall(val apkInfo: ApkPackageInfo) : ApkInstallStatus
    data class Installing(val packageName: String) : ApkInstallStatus
    data class NeedsUserAction(val confirmationIntent: Intent) : ApkInstallStatus
    data class NeedsPermission(val settingsIntent: Intent) : ApkInstallStatus
    data class Installed(val packageName: String, val appLabel: String?) : ApkInstallStatus
    data class Failed(
        val reason: String,
        val isSignatureIncompatible: Boolean = false,
        val isDowngrade: Boolean = false,
        val isInsufficientStorage: Boolean = false,
        val isUnsigned: Boolean = false,
        val packageName: String? = null,
    ) : ApkInstallStatus
}
