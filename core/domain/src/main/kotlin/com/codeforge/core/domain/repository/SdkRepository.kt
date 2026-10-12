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
 * Skript installiert JDK und CMake (Termux-Pakete), cmdline-tools und platforms (Google) sowie
 * aarch64-build-tools, -platform-tools und NDK (GitHub-Releases HomuHomu833); die App führt es
 * nur aus und zeigt Status/Fortschritt. Die Liste der Versionen stammt allein aus dem Skript.
 * Implementiert in :libs:terminal-engine, konsumiert von :feature:sdkmanager.
 *
 * Paket-IDs (Präfix bestimmt die Kategorie in :feature:sdkmanager): `jdk;21`, `cmdline-tools;latest`,
 * `platform-tools;37.0.0`, `build-tools;37.0.0`, `platforms;android-36`, `ndk;r29d`, `cmake;termux`.
 */
interface SdkRepository {
    suspend fun listAvailablePackages(): Result<List<ToolItem>>
    fun installSdkTool(packagePath: String): Flow<SdkInstallEvent>
    suspend fun uninstallSdkTool(packagePath: String): Result<Unit>

    /** ANDROID_HOME im Termux-Prefix; `null`, wenn der Termux-Bootstrap fehlt. */
    suspend fun sdkRootPath(): String?
}
