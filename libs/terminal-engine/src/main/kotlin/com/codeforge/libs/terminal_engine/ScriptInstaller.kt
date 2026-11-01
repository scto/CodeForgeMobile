/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 */
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.shared.termux.TermuxConstants
import java.io.File

/**
 * Legt das mitgelieferte Skript `codeforge-env` nach `$PREFIX/bin`, falls es nicht bereits als
 * Termux-Paket im Bootstrap enthalten ist. Die Platzhalter ersetzt diese Klasse; das Repo für
 * die eigenen SDK-Builds kommt aus `$PREFIX/etc/codeforge/sdk.conf`, falls dort gesetzt.
 */
internal class ScriptInstaller(private val context: Context) {

    fun install(): Result<Unit> = runCatching {
        check(TermuxEnvironment.isBootstrapInstalled()) { Res.string(R.string.terminal_engine_termux_bootstrap_ist_nicht_installiert) }
        val target = File(TermuxEnvironment.sdkScriptPath)
        val template = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val content = render(template, repoUrl = null)
        if (target.isFile && target.readText() == content) return@runCatching
        target.parentFile?.mkdirs()
        target.writeText(content)
        target.setExecutable(true, false)
        target.setReadable(true, false)
        installMotd()
    }

    /**
     * Ersetzt die Termux-Begrüßung durch die von CodeForgeMobile ([MOTD_ASSET]). Schlägt das Schreiben fehl,
     * bleibt das Skript trotzdem installiert. Dynamische MOTD-Skripte von termux-tools (falls vorhanden)
     * geben danach nur noch diese Datei aus.
     */
    private fun installMotd() {
        runCatching {
            val motd = context.assets.open(MOTD_ASSET).bufferedReader().use { it.readText() }
            writeMotd(File(TermuxEnvironment.prefix), motd)
        }
    }

    companion object {
        const val ASSET_NAME = "codeforge-env"
        const val MOTD_ASSET = "codeforge-motd"
        private val MOTD_SCRIPTS = listOf("etc/motd.sh", "etc/termux/motd.sh")

        /** Schreibt `etc/motd` und ersetzt vorhandene Termux-MOTD-Skripte durch `cat` dieser Datei (idempotent). */
        internal fun writeMotd(prefix: File, motd: String) {
            val motdFile = File(prefix, "etc/motd")
            if (!motdFile.isFile || motdFile.readText() != motd) {
                motdFile.parentFile?.mkdirs()
                motdFile.writeText(motd)
            }
            val shim = "#!${prefix.path}/bin/sh\ncat \"${prefix.path}/etc/motd\"\n"
            for (rel in MOTD_SCRIPTS) {
                val script = File(prefix, rel)
                if (script.isFile && script.readText() != shim) {
                    script.writeText(shim)
                    script.setExecutable(true, false)
                }
            }
        }

        /** Standard-Repo der eigenen Builds; per `sdk.conf` überschreibbar (siehe Skript). */
        const val DEFAULT_REPO_URL = "@CODEFORGE_SDK_REPO@"

        internal fun render(template: String, repoUrl: String?): String = template
            .replace("@TERMUX_PREFIX@", TermuxConstants.TERMUX_PREFIX_DIR_PATH)
            .replace("@TERMUX_HOME@", TermuxConstants.TERMUX_HOME_DIR_PATH)
            .let { if (repoUrl != null) it.replace("@CODEFORGE_SDK_REPO@", repoUrl) else it }
    }
}
