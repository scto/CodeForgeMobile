// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import java.io.File

/**
 * Baut das proot-Kommando zum Starten eines Prozesses innerhalb einer entpackten Rootfs für Ubuntu.
 */
object ProotCommandBuilder {

    fun build(
        prootBinaryPath: String,
        rootfsDir: File,
        workingDirectory: String = "/root",
        env: Map<String, String> = defaultEnv(rootfsDir.path),
        command: List<String>
    ): List<String> = buildList {
        val tmpDir = File(rootfsDir, "tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()

        // Clean up broken symlinks at usr/bin/bash, usr/bin/sh, bin/sh
        listOf("usr/bin/bash", "usr/bin/sh", "bin/sh", "bin/bash").forEach { relPath ->
            val file = File(rootfsDir, relPath)
            runCatching {
                val path = file.toPath()
                if (java.nio.file.Files.isSymbolicLink(path)) {
                    val target = java.nio.file.Files.readSymbolicLink(path).toString()
                    if (!java.nio.file.Files.exists(path)) {
                        java.nio.file.Files.deleteIfExists(path)
                    }
                }
            }
        }

        add(prootBinaryPath)
        add("--link2symlink")
        add("--kill-on-exit")
        add("-0")
        add("-r"); add(rootfsDir.path)
        add("-b"); add("/dev")
        add("-b"); add("/proc")
        add("-b"); add("/sys")
        add("-b"); add("/storage")
        add("-w"); add(workingDirectory)

        val binBash = File(rootfsDir, "bin/bash")
        val usrBinBash = File(rootfsDir, "usr/bin/bash")
        val binSh = File(rootfsDir, "bin/sh")
        val usrBinSh = File(rootfsDir, "usr/bin/sh")

        val shellExecutable = when {
            binBash.exists() -> "/bin/bash"
            usrBinBash.exists() -> "/usr/bin/bash"
            binSh.exists() -> "/bin/sh"
            usrBinSh.exists() -> "/usr/bin/sh"
            else -> "/bin/sh"
        }

        add(shellExecutable)
        add("-c")

        val exports = env.map { (k, v) -> "export $k=\"$v\"" }.joinToString("; ")
        val rawCmdStr = if (command.isNotEmpty()) command.joinToString(" ") else "true"
        val cmdStr = if (rawCmdStr.startsWith("/bin/sh -c ")) rawCmdStr.removePrefix("/bin/sh -c ").trim('"', '\'') else rawCmdStr
        add("$exports; $cmdStr")
    }

    fun defaultEnv(rootfsPath: String = "/data/data/com.codeforgemobile/rootfs"): Map<String, String> = mapOf(
        "COLORTERM" to "truecolor",
        "ROOTFS" to rootfsPath,
        "TERM_PROGRAM_VERSION" to "1.0.0",
        "HOSTNAME" to "codeforgemobile",
        "PWD" to "/root",
        "PROOT_TMP_DIR" to "/tmp",
        "SYSTEMSERVERCLASSPATH" to "/system/framework/com.android.location.provider.jar:/system/framework/services.jar:/apex/com.android.adservices/javalib/service-adservices.jar:/apex/com.android.adservices/javalib/service-sdksandbox.jar:/apex/com.android.appsearch/javalib/service-appsearch.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.compos/javalib/service-compos.jar:/apex/com.android.configinfrastructure/javalib/service-configinfrastructure.jar:/apex/com.android.crashrecovery/javalib/service-crashrecovery.jar:/apex/com.android.healthfitness/javalib/service-healthfitness.jar:/apex/com.android.media/javalib/service-media-s.jar:/apex/com.android.npumanager/javalib/service-npumanager.jar:/apex/com.android.ondevicepersonalization/javalib/service-ondevicepersonalization.jar:/apex/com.android.permission/javalib/service-permission.jar:/apex/com.android.profiling/javalib/service-anomaly-detector.jar:/apex/com.android.rkpd/javalib/service-rkp.jar:/apex/com.android.telephonycore/javalib/service-telecom.jar:/apex/com.android.virt/javalib/service-virtualization.jar",
        "EXTERNAL_STORAGE" to "/sdcard",
        "CODEFORGEMOBILE" to "1",
        "HOME" to "/root",
        "LANG" to "C.UTF-8",
        "DEX2OATBOOTCLASSPATH" to "/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/system/framework/framework.jar:/system/framework/framework-graphics.jar:/system/framework/framework-location.jar:/system/framework/ext.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/ims-common.jar:/system/framework/framework-ondeviceintelligence-platform.jar:/apex/com.android.i18n/javalib/core-icu4j.jar",
        "TMPDIR" to "/tmp",
        "ANDROID_DATA" to "/data",
        "ANDROID_STORAGE" to "/storage",
        "TERM" to "xterm-256color",
        "ASEC_MOUNTPOINT" to "/mnt/asec",
        "ANDROID_I18N_ROOT" to "/apex/com.android.i18n",
        "SHLVL" to "1",
        "BASH_ENV" to "/root/.bashrc",
        "ANDROID_ROOT" to "/system",
        "BOOTCLASSPATH" to "/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/system/framework/framework.jar:/system/framework/framework-graphics.jar:/system/framework/framework-location.jar:/system/framework/ext.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/ims-common.jar:/system/framework/framework-ondeviceintelligence-platform.jar:/apex/com.android.i18n/javalib/core-icu4j.jar:/apex/com.android.adservices/javalib/framework-adservices.jar:/apex/com.android.adservices/javalib/framework-sdksandbox.jar:/apex/com.android.appsearch/javalib/framework-appsearch.jar:/apex/com.android.bt/javalib/framework-bluetooth.jar:/apex/com.android.configinfrastructure/javalib/framework-configinfrastructure.jar:/apex/com.android.conscrypt/javalib/conscrypt.jar:/apex/com.android.conscrypt/javalib/framework-conscrypt-nsc.jar:/apex/com.android.crashrecovery/javalib/framework-crashrecovery.jar:/apex/com.android.devicelock/javalib/framework-devicelock.jar:/apex/com.android.healthfitness/javalib/framework-healthfitness.jar:/apex/com.android.ipsec/javalib/android.net.ipsec.ike.jar:/apex/com.android.media/javalib/updatable-media.jar:/apex/com.android.mediaprovider/javalib/framework-mediaprovider.jar:/apex/com.android.mediaprovider/javalib/framework-pdf.jar:/apex/com.android.mediaprovider/javalib/framework-pdf-v.jar:/apex/com.android.mediaprovider/javalib/framework-photopicker.jar:/apex/com.android.nfcservices/javalib/framework-nfc.jar:/apex/com.android.npumanager/javalib/framework-npumanager.jar:/apex/com.android.ondevicepersonalization/javalib/framework-ondevicepersonalization.jar:/apex/com.android.os.statsd/javalib/framework-statsd.jar:/apex/com.android.permission/javalib/framework-permission.jar:/apex/com.android.permission/javalib/framework-permission-s.jar:/apex/com.android.profiling/javalib/framework-anomaly-detector.jar:/apex/com.android.profiling/javalib/framework-profiling.jar:/apex/com.android.scheduling/javalib/framework-scheduling.jar:/apex/com.android.sdkext/javalib/framework-sdkextensions.jar:/apex/com.android.telephonycore/javalib/framework-telecom.jar:/apex/com.android.telephonycore/javalib/framework-telephony.jar:/apex/com.android.tethering/javalib/framework-connectivity.jar:/apex/com.android.tethering/javalib/framework-connectivity-b.jar:/apex/com.android.tethering/javalib/framework-connectivity-t.jar:/apex/com.android.tethering/javalib/framework-tethering.jar:/apex/com.android.uprobestats/javalib/framework-uprobestats.jar:/apex/com.android.uwb/javalib/framework-ranging.jar:/apex/com.android.uwb/javalib/framework-uwb.jar:/apex/com.android.virt/javalib/framework-virtualization.jar:/apex/com.android.webapp/javalib/framework-webapp.jar:/apex/com.android.wifi/javalib/framework-wifi.jar",
        "PS1" to "\\[\\e[38;5;141m\\][codeforgemobile]\\[\\e[0m\\] \\[\\e[38;5;245m\\]\\w\\[\\e[0m\\] \\$ ",
        "ANDROID_TZDATA_ROOT" to "/apex/com.android.tzdata",
        "LC_ALL" to "C.UTF-8",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "ANDROID_ART_ROOT" to "/apex/com.android.art",
        "DEBUG" to "false",
        "ANDROID_ASSETS" to "/system/app",
        "DEBIAN_FRONTEND" to "noninteractive",
        "TERM_PROGRAM" to "codeforgemobile",
        "_" to "/usr/bin/env"
    )
}
