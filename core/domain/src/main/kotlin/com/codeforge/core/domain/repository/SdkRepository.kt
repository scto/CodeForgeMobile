// Modul: :core:domain
/**
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import kotlinx.coroutines.flow.Flow

interface SdkRepository {
    fun getAvailableTools(): Flow<List<ToolItem>>
    fun getInstalledTools(): Flow<List<ToolItem>>
    fun installSdkTool(packagePath: String): Flow<SdkInstallEvent>
    fun uninstallSdkTool(packagePath: String): Flow<SdkInstallEvent>
}
