/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import kotlinx.coroutines.flow.Flow

/**
 * Kapselt Befehle an das `sdkmanager`-CLI (Teil der Android-Commandline-Tools) und
 * übersetzt dessen Text-Output in strukturierte Events. Implementiert in :core:data.
 * Konsumiert von :feature:sdkmanager.
 */
interface SdkRepository {
    suspend fun listAvailablePackages(): Result<List<ToolItem>>
    fun installSdkTool(packagePath: String): Flow<SdkInstallEvent>
    suspend fun uninstallSdkTool(packagePath: String): Result<Unit>

    /** SDK-Root-Verzeichnis (ANDROID_HOME/ANDROID_SDK_ROOT), null falls nicht gesetzt. */
    fun sdkRootPath(): String?
}
