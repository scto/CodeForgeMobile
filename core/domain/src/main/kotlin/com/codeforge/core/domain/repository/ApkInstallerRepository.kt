/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.domain.model.ApkMetadata
import com.codeforge.core.domain.model.ApkPackageInfo
import java.io.File
import kotlinx.coroutines.flow.Flow

interface ApkInstallerRepository {
    fun findApk(
        projectRoot: File,
        preferredVariant: String? = null,
        supportedAbis: List<String> = emptyList()
    ): Result<ApkMetadata>

    fun getApkPackageInfo(apkFile: File): Result<ApkPackageInfo>
    fun installApk(apkFile: File): Flow<ApkInstallStatus>
    fun launchApp(packageName: String): Result<Unit>
    fun uninstallApp(packageName: String): Result<Unit>
    fun canRequestPackageInstalls(): Boolean
}
