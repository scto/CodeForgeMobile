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
 * Skript installiert das JDK (Termux-Paket), cmdline-tools und platforms (Google) sowie
 * aarch64-build-tools, -platform-tools, NDK und CMake (Mirror `scto/codeforge-tools`, Rückfall HomuHomu833); die App führt es
 * nur aus und zeigt Status/Fortschritt. Die Liste der Versionen stammt allein aus dem Skript.
 * Implementiert in :libs:terminal-engine, konsumiert von :feature:sdkmanager.
 *
 * Paket-IDs (Präfix bestimmt die Kategorie in :feature:sdkmanager): `jdk;21`, `cmdline-tools;latest`,
 * `platform-tools;37.0.0`, `build-tools;37.0.0` (auch 34.0.4, 33.0.3 aus androidide-tools), `platforms;android-36`,
 * `ndk;r29`, `cmake;3.22.1`. Die Versionen entstehen im Skript aus den GitHub-Releases des Mirrors (Beta/RC ausgenommen),
 * die App kennt keine feste Liste. `platform-tools` gibt es nur einmal: als installiert gilt die zuletzt installierte Version.
 */
interface SdkRepository {
    suspend fun listAvailablePackages(): Result<List<ToolItem>>
    fun installSdkTool(packagePath: String): Flow<SdkInstallEvent>
    suspend fun uninstallSdkTool(packagePath: String): Result<Unit>

    /** ANDROID_HOME im Termux-Prefix; `null`, wenn der Termux-Bootstrap fehlt. */
    suspend fun sdkRootPath(): String?
}
