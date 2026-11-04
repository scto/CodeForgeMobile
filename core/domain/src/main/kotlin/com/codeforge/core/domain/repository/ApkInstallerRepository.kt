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

/** Installieren, Starten und Deinstallieren gebauter APKs über den Android-PackageInstaller. */
interface ApkInstallerRepository {
    /** Sucht die zuletzt gebaute APK eines Projekts (ABI-passend). */
    fun findApk(
        projectRoot: File,
        preferredVariant: String? = null,
        supportedAbis: List<String> = emptyList()
    ): Result<ApkMetadata>

    /**
     * Liest Paketname und Version aus einer APK.
     *
     * @param apkFile die APK-Datei.
     */
    fun getApkPackageInfo(apkFile: File): Result<ApkPackageInfo>
    /** Installiert [apkFile] und meldet den Fortschritt als [ApkInstallStatus]-Strom (inklusive Signaturkonflikt und Selbstersetzung). */
    fun installApk(apkFile: File): Flow<ApkInstallStatus>
    /** Startet die installierte App [packageName]. */
    fun launchApp(packageName: String): Result<Unit>
    /** Deinstalliert die App [packageName]. */
    fun uninstallApp(packageName: String): Result<Unit>
    /** Ob die App APKs installieren darf (Berechtigung „Unbekannte Apps installieren“). */
    fun canRequestPackageInstalls(): Boolean
}
