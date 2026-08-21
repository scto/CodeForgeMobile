// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import java.io.File

/**
 * Baut das proot-Kommando zum Starten eines Prozesses innerhalb einer entpackten Rootfs
 * (chroot-artige Umgebung ohne Root-Rechte). Genutzt von :feature:terminal (interaktive
 * Shell), :libs:gradle-tooling-bridge (JAVA_HOME/Gradle innerhalb der Rootfs) und
 * :libs:lsp-client (Language-Server-Binary innerhalb der Rootfs).
 *
 * WICHTIG: [prootBinaryPath] muss auf ein natives Binary im nativeLibraryDir der App
 * zeigen (z.B. "${context.applicationInfo.nativeLibraryDir}/libproot.so") — seit
 * Android 10 (API 29) dürfen Apps keine beliebigen Dateien aus dem privaten
 * Speicher mehr direkt ausführen, sondern nur Dateien, die als jniLibs-Artefakt
 * (mit "lib"-Präfix und ".so"-Suffix, siehe Termux-Architektur) ausgeliefert wurden.
 * Das proot-Binary selbst (kompiliert für arm64-v8a/x86_64) wird hier NICHT mitgeliefert
 * und muss als natives Artefakt ergänzt werden, sobald eine Kotlin/Native- oder NDK-
 * Build-Pipeline für dieses Projekt aufgesetzt ist.
 */
object ProotCommandBuilder {

    fun build(
        prootBinaryPath: String,
        rootfsDir: File,
        workingDirectory: String = "/",
        env: Map<String, String> = defaultEnv(),
        command: List<String>
    ): List<String> = buildList {
        add(prootBinaryPath)
        add("--link2symlink")
        add("--kill-on-exit")
        add("-r"); add(rootfsDir.path)
        add("-b"); add("/dev")
        add("-b"); add("/proc")
        add("-b"); add("/sys")
        add("-w"); add(workingDirectory)
        env.forEach { (key, value) -> add("-e"); add("$key=$value") }
        add("/usr/bin/env")
        addAll(command)
    }

    private fun defaultEnv(): Map<String, String> = mapOf(
        "HOME" to "/root",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM" to "xterm-256color"
    )
}
