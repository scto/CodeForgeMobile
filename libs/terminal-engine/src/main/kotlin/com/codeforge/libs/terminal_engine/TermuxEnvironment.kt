/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 *
 * Pfade und Prozessumgebung des Termux-Bootstraps. Ersetzt die frühere PRoot-Rootfs-
 * Umgebung vollständig: Es gibt genau EINEN Prefix (kein Multi-Distro).
 */
package com.codeforge.libs.terminal_engine

import com.codeforge.shared.termux.TermuxConstants
import com.codeforge.shared.termux.shell.command.environment.TermuxDevEnvironment
import java.io.File

/**
 * [command] an `ProcessBuilder(command)` übergeben, [processEnv] auf dessen `environment()`
 * anwenden, [workingDirectory] als `directory(...)` setzen.
 */
data class ShellLaunchSpec(
    val command: List<String>,
    val processEnv: Map<String, String>,
    val workingDirectory: String,
)

object TermuxEnvironment {
    const val SDK_SCRIPT_NAME = "codeforge-env"

    val prefix: String get() = TermuxConstants.TERMUX_PREFIX_DIR_PATH
    val home: String get() = TermuxConstants.TERMUX_HOME_DIR_PATH

    /** ANDROID_HOME — muss mit `SDK_ROOT` in assets/codeforge-env übereinstimmen. */
    val sdkRoot: String get() = TermuxDevEnvironment.getSdkRoot()
    val sdkScriptPath: String get() = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/$SDK_SCRIPT_NAME"
    val bashPath: String get() = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}/bash"

    fun isBootstrapInstalled(): Boolean = File(bashPath).isFile

    /** Basis-Umgebung der Termux-Shell; [extraPath] wird dem PATH vorangestellt. */
    fun environment(extraPath: List<String> = emptyList(), systemPath: String = System.getenv("PATH").orEmpty()): Map<String, String> {
        val path = (extraPath + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + systemPath + TermuxDevEnvironment.getSdkPathEntries())
            .filter { it.isNotEmpty() }.joinToString(":")
        val env = linkedMapOf<String, String>(
            "PREFIX" to prefix,
            "HOME" to home,
            "TMPDIR" to TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH,
            "PATH" to path,
            "LD_LIBRARY_PATH" to TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH,
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
        )
        // ANDROID_HOME, ANDROID_SDK_ROOT, ANDROID_USER_HOME, GRADLE_USER_HOME, JAVA_HOME, SYSROOT, PROJECTS,
        // TERMUX_PKG_NO_MIRROR_SELECT – dieselbe Quelle wie die Termux-Shell (AndroidIDE Environment.putEnvironment).
        TermuxDevEnvironment.putEnvironment(env, false)
        return env
    }

    /** `bash -lc "<script>"` — Login-Shell, damit `profile.d/codeforge-android.sh` (JAVA_HOME …) gilt. */
    fun launchSpec(
        command: List<String>,
        workingDirectory: String = home,
        extraPath: List<String> = emptyList(),
    ): ShellLaunchSpec = ShellLaunchSpec(command, environment(extraPath), workingDirectory)

    fun shellScriptSpec(script: String, workingDirectory: String = home): ShellLaunchSpec =
        launchSpec(listOf(bashPath, "-lc", script), workingDirectory)

    /** JDK-Home eines Termux-`openjdk-N`-Pakets. */
    fun jdkHome(version: String): String = TermuxDevEnvironment.getJdkHome(version)
}
