/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.JavaInfo
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.SdkUpdateInterval
import com.codeforge.core.domain.model.ToolItem
import kotlinx.coroutines.flow.Flow

/**
 * Kapselt Befehle an das `sdkmanager`-CLI (Teil der Android-Commandline-Tools, läuft
 * innerhalb der PRoot-Rootfs — nicht im App-Prozess, dort existiert kein sdkmanager-
 * Binary) sowie die JDK-Verwaltung (kein reales sdkmanager-Paket, siehe JdkCatalog-KDoc
 * in :libs:terminal-engine). Implementiert dort, konsumiert von :feature:sdkmanager.
 */
interface SdkRepository {
    suspend fun listAvailablePackages(): Result<List<ToolItem>>
    suspend fun refreshAndCachePackages(): Result<List<ToolItem>>
    suspend fun getUpdateInterval(): SdkUpdateInterval
    suspend fun setUpdateInterval(interval: SdkUpdateInterval)
    suspend fun getCmdlineToolsInstalled(): Boolean
    suspend fun getLastDiagnosticMessage(): String
    suspend fun isJavaInstalled(): Boolean
    suspend fun getJavaInfo(): JavaInfo
    fun installSdkTool(packagePath: String): Flow<SdkInstallEvent>
    suspend fun uninstallSdkTool(packagePath: String): Result<Unit>
    suspend fun syncInstalledToolsToDataStore(): Result<Unit>

    /**
     * SDK-Root-Verzeichnis INNERHALB der Rootfs (ANDROID_HOME/ANDROID_SDK_ROOT, dort per
     * Shell-Probe ermittelt — nicht die (nicht existente) Umgebungsvariable des
     * App-Prozesses). null falls nicht gesetzt oder Rootfs nicht eingerichtet.
     */
    suspend fun sdkRootPath(): String?
}
