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
    }

    companion object {
        const val ASSET_NAME = "codeforge-env"

        /** Standard-Repo der eigenen Builds; per `sdk.conf` überschreibbar (siehe Skript). */
        const val DEFAULT_REPO_URL = "@CODEFORGE_SDK_REPO@"

        internal fun render(template: String, repoUrl: String?): String = template
            .replace("@TERMUX_PREFIX@", TermuxConstants.TERMUX_PREFIX_DIR_PATH)
            .replace("@TERMUX_HOME@", TermuxConstants.TERMUX_HOME_DIR_PATH)
            .let { if (repoUrl != null) it.replace("@CODEFORGE_SDK_REPO@", repoUrl) else it }
    }
}
