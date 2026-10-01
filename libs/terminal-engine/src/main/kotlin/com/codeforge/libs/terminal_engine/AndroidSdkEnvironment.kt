/**
 * Modul: :libs:terminal-engine
 * Dynamic Android SDK Component & Version Environment Manager.
 * Handles side-by-side versions of NDK, CMake, Build-Tools, and isolated Process environments.
 */
package com.codeforge.libs.terminal_engine

import java.io.File

data class SdkInstallation(
    val androidHome: File,
    val platformToolsDir: File?,
    val cmdlineToolsBin: File?,
    val installedBuildTools: List<String>,
    val installedNdks: List<String>,
    val installedCmakes: List<String>
)

class AndroidSdkEnvironment(
    customAndroidHome: File? = null
) {
    val androidHome: File = customAndroidHome
        ?: System.getenv("ANDROID_HOME")?.let { File(it) }
        ?: System.getenv("ANDROID_SDK_ROOT")?.let { File(it) }
        ?: File(System.getProperty("user.home") ?: "/root", "android-sdk")

    fun isValid(): Boolean = androidHome.exists() && androidHome.isDirectory

    /**
     * Scannt das SDK-Verzeichnis und listet alle verfuegbaren Versionen auf.
     */
    fun inspect(): SdkInstallation {
        return SdkInstallation(
            androidHome = androidHome,
            platformToolsDir = File(androidHome, "platform-tools").takeIf { it.isDirectory },
            cmdlineToolsBin = File(androidHome, "cmdline-tools/latest/bin").takeIf { it.isDirectory },
            installedBuildTools = listVersions("build-tools"),
            installedNdks = listVersions("ndk"),
            installedCmakes = listVersions("cmake")
        )
    }

    /**
     * Erstellt eine isolierte Environment-Map mit angepasstem PATH.
     * Ermoeglicht das gezielte Ansprechen spezifischer NDK-/CMake-Versionen pro Prozess.
     */
    fun createProcessEnvironment(
        buildToolsVersion: String? = null,
        ndkVersion: String? = null,
        cmakeVersion: String? = null,
        inheritSystemEnv: Boolean = true
    ): MutableMap<String, String> {
        val env = if (inheritSystemEnv) HashMap(System.getenv()) else HashMap()

        // 1. Basis-SDK-Variablen
        env["ANDROID_HOME"] = androidHome.absolutePath
        env["ANDROID_SDK_ROOT"] = androidHome.absolutePath

        // 2. NDK-Variablen (falls NDK-Version spezifiziert oder vorhanden)
        val selectedNdk = ndkVersion ?: listVersions("ndk").firstOrNull()
        if (selectedNdk != null) {
            val ndkDir = File(androidHome, "ndk/$selectedNdk")
            if (ndkDir.isDirectory) {
                env["ANDROID_NDK_HOME"] = ndkDir.absolutePath
                env["ANDROID_NDK_ROOT"] = ndkDir.absolutePath
            }
        }

        // 3. Pfade fuer den PATH zusammenstellen
        val pathEntries = mutableListOf<String>()

        // Immer global: platform-tools & cmdline-tools
        File(androidHome, "platform-tools").takeIf { it.isDirectory }?.let { pathEntries.add(it.absolutePath) }
        File(androidHome, "cmdline-tools/latest/bin").takeIf { it.isDirectory }?.let { pathEntries.add(it.absolutePath) }

        // Spezifische Build-Tools
        val selectedBuildTools = buildToolsVersion ?: listVersions("build-tools").firstOrNull()
        if (selectedBuildTools != null) {
            File(androidHome, "build-tools/$selectedBuildTools").takeIf { it.isDirectory }?.let {
                pathEntries.add(it.absolutePath)
            }
        }

        // Spezifisches CMake
        val selectedCmake = cmakeVersion ?: listVersions("cmake").firstOrNull()
        if (selectedCmake != null) {
            File(androidHome, "cmake/$selectedCmake/bin").takeIf { it.isDirectory }?.let {
                pathEntries.add(it.absolutePath)
            }
        }

        // NDK-Tools
        if (selectedNdk != null) {
            File(androidHome, "ndk/$selectedNdk").takeIf { it.isDirectory }?.let {
                pathEntries.add(it.absolutePath)
            }
        }

        // Bestehenden PATH anhaengen
        val existingPath = env["PATH"] ?: ""
        val newPath = (pathEntries + listOf(existingPath).filter { it.isNotBlank() })
            .joinToString(File.pathSeparator)

        env["PATH"] = newPath
        return env
    }

    /**
     * Direkter Dateizugriff auf spezifische Binaries ohne PATH-Lookup.
     */
    fun resolveBinary(binaryName: String, component: String, version: String): File? {
        val binary = when (component) {
            "build-tools" -> File(androidHome, "build-tools/$version/$binaryName")
            "cmake" -> File(androidHome, "cmake/$version/bin/$binaryName")
            "ndk" -> File(androidHome, "ndk/$version/$binaryName")
            "platform-tools" -> File(androidHome, "platform-tools/$binaryName")
            "cmdline-tools" -> File(androidHome, "cmdline-tools/latest/bin/$binaryName")
            else -> return null
        }
        return binary.takeIf { it.exists() && it.canExecute() }
    }

    fun listVersions(subDir: String): List<String> {
        val dir = File(androidHome, subDir)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sortedWith { a, b -> compareVersions(b, a) } // Neueste zuerst
            ?: emptyList()
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return v1.compareTo(v2)
    }
}
