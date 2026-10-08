/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import kotlinx.coroutines.flow.Flow

/**
 * Grafischer Aufsatz auf das Termux-Skript `codeforge-env` (`--machine`-Ausgabe). Das
 * Skript installiert JDK (Termux-Pakete) sowie cmdline-tools, platform-tools, build-tools,
 * NDK und CMake aus eigenen Builds; die App führt es nur aus und zeigt Status/Fortschritt.
 * Implementiert in :libs:terminal-engine, konsumiert von :feature:sdkmanager.
 *
 * Paket-IDs: `jdk;21`, `cmdline-tools;latest`, `platform-tools;latest`, `build-tools;35.0.0`,
 * `platforms;android-35`, `ndk;27d`, `cmake;4.3.0`.
 */
interface SdkRepository {
    suspend fun listAvailablePackages(): Result<List<ToolItem>>
    fun installSdkTool(packagePath: String): Flow<SdkInstallEvent>
    suspend fun uninstallSdkTool(packagePath: String): Result<Unit>

    /** ANDROID_HOME im Termux-Prefix; `null`, wenn der Termux-Bootstrap fehlt. */
    suspend fun sdkRootPath(): String?
}
