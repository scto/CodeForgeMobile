/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res

/**
 * Feste Zielumgebung der Ersteinrichtung: JDK 21, Android-SDK (cmdline-tools, platform-tools,
 * build-tools 37, Plattform 36), NDK r29 und CMake 4.1.2. Es gibt keine Auswahl mehr; die Werte werden
 * trotzdem validiert, weil sie als Shell-Argumente ins Terminal geschrieben werden.
 */
data class SdkSetupOptions(
    val jdk: String = "21",
    val sdk: String = "37.0.0",
    val platform: String = "36",
    val ndk: String = "r29",
    val cmake: String = DEFAULT_CMAKE,
) {
    init {
        require(listOf(jdk, sdk, platform, ndk, cmake).all { SAFE.matches(it) }) { Res.string(R.string.domain_ungueltige_versionsangabe) }
    }

    /** Kommandozeile für die Termux-Shell. */
    fun toCommand(scriptName: String = "codeforge-env"): String =
        "$scriptName setup --jdk $jdk --sdk $sdk --platform $platform --ndk $ndk --cmake $cmake"

    companion object {
        private val SAFE = Regex("^[0-9A-Za-z.]{1,16}$")

        /** Standardversion von CMake. */
        const val DEFAULT_CMAKE = "4.1.2"
    }
}

/**
 * Die Entwicklungsumgebung läuft vollständig im Termux-Bootstrap (kein PRoot, keine
 * Distro-Auswahl). Implementiert in :libs:terminal-engine.
 */
interface TermuxEnvironmentRepository {
    /** `true`, wenn der Termux-Bootstrap entpackt ist (bash vorhanden). */
    fun isBootstrapInstalled(): Boolean

    /**
     * Stellt sicher, dass das Skript `codeforge-env` im Prefix liegt (nötig, falls es nicht
     * als Termux-Paket im Bootstrap enthalten ist). Idempotent.
     */
    suspend fun installSdkScript(): Result<Unit>

    /** Pfad des Android-SDK-Roots (ANDROID_HOME) im Termux-Prefix. */
    fun sdkRootPath(): String
}
