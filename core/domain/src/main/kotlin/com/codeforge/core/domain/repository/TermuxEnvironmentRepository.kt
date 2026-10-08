/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res

/**
 * Auswahl für das einmalige Einrichten der Entwicklungsumgebung (`codeforge-env setup`).
 * Die Werte sind fest vorgegebene Auswahllisten der UI, werden aber trotzdem validiert,
 * weil sie als Shell-Argumente ins Terminal geschrieben werden.
 */
data class SdkSetupOptions(
    val jdk: String = "17",
    val ndk: String = "27d",
    val cmake: String = "4.3.0",
    val installNdk: Boolean = true,
    val installCmake: Boolean = true,
) {
    init {
        require(listOf(jdk, ndk, cmake).all { SAFE.matches(it) }) { Res.string(R.string.domain_ungueltige_versionsangabe) }
    }

    /** Kommandozeile für die Termux-Shell. */
    fun toCommand(scriptName: String = "codeforge-env"): String = buildString {
        append(scriptName).append(" setup --jdk ").append(jdk)
        if (installNdk) append(" --ndk ").append(ndk) else append(" --no-ndk")
        if (installCmake) append(" --cmake ").append(cmake) else append(" --no-cmake")
    }

    companion object {
        private val SAFE = Regex("^[0-9A-Za-z.]{1,16}$")
        val JDK_CHOICES = listOf("17", "21")
        val NDK_CHOICES = listOf("27d", "30b")
        const val DEFAULT_CMAKE = "4.3.0"
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
