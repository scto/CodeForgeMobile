/**
 * Modul: :libs:debug-client
 * @author Thomas Schmid
 *
 * Installiert und startet den mitgelieferten DAP-Adapter ([CodeForgeDapAdapter.java], JDI-basiert) in der
 * Termux-Umgebung. Der Adapter spricht DAP über stdin/stdout und verbindet sich per JDWP-Socket mit dem
 * Ziel – lokal (`127.0.0.1`) oder über eine `adb forward`-Weiterleitung. Ohne Adapter wäre der JDWP-Port
 * nicht ansprechbar (er versteht kein DAP).
 */
package com.codeforge.libs.debug_client

import android.content.Context
import android.util.Log
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Installiert bei Bedarf den Debug-Adapter und startet ihn mit dem Termux-JDK; die Verbindung läuft über dessen stdio. */
@Singleton
class DapAdapterLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val installDir: File get() = File(context.filesDir, "dap")

    /** Installiert (bei Bedarf kompiliert) den Adapter und startet ihn; die Verbindung läuft über dessen stdio. */
    fun start(): DapConnection {
        val env = TermuxEnvironment.environment().toMutableMap().apply { put("JAVA_TOOL_OPTIONS", "") }
        val java = findExecutable("java", env)
            ?: throw IllegalStateException(NO_JDK_MESSAGE)
        ensureInstalled(env)

        val process = ProcessBuilder(java, "-Xshare:auto", "-cp", installDir.absolutePath, ADAPTER_CLASS)
            .directory(installDir)
            .also { it.environment().clear(); it.environment().putAll(env) }
            .start()
        Thread({
            runCatching {
                process.errorStream.bufferedReader().forEachLine { Log.d(TAG, it) }
            }
        }, "dap-adapter-stderr").apply { isDaemon = true }.start()
        return DapConnection(process.inputStream, process.outputStream)
    }

    @Synchronized
    private fun ensureInstalled(env: Map<String, String>) {
        val source = context.assets.open(ASSET_PATH).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
        val dir = installDir.apply { mkdirs() }
        val hashFile = File(dir, "adapter.sha256")
        if (File(dir, "$ADAPTER_CLASS.class").isFile && hashFile.takeIf { it.isFile }?.readText() == hash) return

        val src = File(dir, "$ADAPTER_CLASS.java").apply { writeBytes(source) }
        val javac = findExecutable("javac", env) ?: throw IllegalStateException(NO_JDK_MESSAGE)
        val proc = ProcessBuilder(javac, "-encoding", "UTF-8", "-d", dir.absolutePath, src.absolutePath)
            .redirectErrorStream(true)
            .also { it.environment().clear(); it.environment().putAll(env) }
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(90, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            throw IllegalStateException("Kompilieren des Debug-Adapters: Zeitüberschreitung")
        }
        check(proc.exitValue() == 0) { "Kompilieren des Debug-Adapters fehlgeschlagen:\n$output" }
        hashFile.writeText(hash)
    }

    private fun findExecutable(name: String, env: Map<String, String>): String? {
        val candidates = buildList {
            env["JAVA_HOME"]?.takeIf { it.isNotBlank() }?.let { add(File(it, "bin/$name")) }
            env["PATH"].orEmpty().split(':').filter { it.isNotBlank() }.forEach { add(File(it, name)) }
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }

    private companion object {
        const val TAG = "DapAdapter"
        const val ASSET_PATH = "dap/CodeForgeDapAdapter.java"
        const val ADAPTER_CLASS = "CodeForgeDapAdapter"
        const val NO_JDK_MESSAGE =
            "Kein JDK gefunden. Bitte im Terminal installieren: pkg install openjdk-17 (enthält java, javac und jdk.jdi)."
    }
}
