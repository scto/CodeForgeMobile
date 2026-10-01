# Dynamic Android SDK Version Environment Manager (`AndroidSdkEnvironment.kt`)

## 1. Übersicht & Zielsetzung
Zur Verwaltung dynamisch installierter Android-SDK-Komponenten (Build-Tools, NDK, CMake, Platform-Tools, Cmdline-Tools) wurde die Klasse `AndroidSdkEnvironment` im Modul `:libs:terminal-engine` implementiert.

Sie erlaubt:
- Dynamische Inspektion der im SDK-Verzeichnis installierten Komponenten (`inspect()`).
- Unterstützung paralleler Side-by-Side Versionen von NDK, CMake und Build-Tools.
- Erzeugung isolierter Prozess-Umgebungen (`createProcessEnvironment()`) für `ProcessBuilder` oder Gradle-Builds, ohne globale Umgebungsvariablen auf dem Host zu verunreinigen.
- Direkter auflösbarer Dateizugriff auf spezifische Binärdateien (`resolveBinary()`) wie `aapt2`, `ndk-build`, `cmake`, `sdkmanager` oder `adb`.
- Abwärtskompatible Versionsvergleiche (`compareVersions()`).

---

## 2. Struktur der Implementierung

### Datentyp `SdkInstallation`
```kotlin
data class SdkInstallation(
    val androidHome: File,
    val platformToolsDir: File?,
    val cmdlineToolsBin: File?,
    val installedBuildTools: List<String>,
    val installedNdks: List<String>,
    val installedCmakes: List<String>
)
```

### Klasse `AndroidSdkEnvironment`
- **Konstruktor**: `AndroidSdkEnvironment(customAndroidHome: File? = null)`
- **Hauptmethoden**:
  - `inspect()`: Scannt das SDK-Verzeichnis und fasst alle vorhandenen Versionen in ein `SdkInstallation`-Objekt zusammen.
  - `createProcessEnvironment(buildToolsVersion, ndkVersion, cmakeVersion, inheritSystemEnv)`: Erstellt ein `MutableMap<String, String>` mit angepasstem `ANDROID_HOME`, `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT` und bereinigtem `PATH`.
  - `resolveBinary(binaryName, component, version)`: Prüft das Vorhandensein und die Ausführbarkeit einer spezifischen Binärdatei.
  - `listVersions(subDir)`: Listet Unterordner (Versionen) auf und sortiert sie absteigend (neueste Version zuerst).

---

## 3. Quellcode-Auszug ([AndroidSdkEnvironment.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/AndroidSdkEnvironment.kt))

```kotlin
package com.codeforge.libs.terminal_engine

import java.io.File

class AndroidSdkEnvironment(
    customAndroidHome: File? = null
) {
    val androidHome: File = customAndroidHome
        ?: System.getenv("ANDROID_HOME")?.let { File(it) }
        ?: System.getenv("ANDROID_SDK_ROOT")?.let { File(it) }
        ?: File(System.getProperty("user.home") ?: "/root", "android-sdk")

    fun isValid(): Boolean = androidHome.exists() && androidHome.isDirectory

    fun inspect(): SdkInstallation = SdkInstallation(
        androidHome = androidHome,
        platformToolsDir = File(androidHome, "platform-tools").takeIf { it.isDirectory },
        cmdlineToolsBin = File(androidHome, "cmdline-tools/latest/bin").takeIf { it.isDirectory },
        installedBuildTools = listVersions("build-tools"),
        installedNdks = listVersions("ndk"),
        installedCmakes = listVersions("cmake")
    )

    fun createProcessEnvironment(
        buildToolsVersion: String? = null,
        ndkVersion: String? = null,
        cmakeVersion: String? = null,
        inheritSystemEnv: Boolean = true
    ): MutableMap<String, String> {
        val env = if (inheritSystemEnv) HashMap(System.getenv()) else HashMap()
        env["ANDROID_HOME"] = androidHome.absolutePath
        env["ANDROID_SDK_ROOT"] = androidHome.absolutePath

        val selectedNdk = ndkVersion ?: listVersions("ndk").firstOrNull()
        if (selectedNdk != null) {
            val ndkDir = File(androidHome, "ndk/$selectedNdk")
            if (ndkDir.isDirectory) {
                env["ANDROID_NDK_HOME"] = ndkDir.absolutePath
                env["ANDROID_NDK_ROOT"] = ndkDir.absolutePath
            }
        }

        val pathEntries = mutableListOf<String>()
        File(androidHome, "platform-tools").takeIf { it.isDirectory }?.let { pathEntries.add(it.absolutePath) }
        File(androidHome, "cmdline-tools/latest/bin").takeIf { it.isDirectory }?.let { pathEntries.add(it.absolutePath) }

        val selectedBuildTools = buildToolsVersion ?: listVersions("build-tools").firstOrNull()
        if (selectedBuildTools != null) {
            File(androidHome, "build-tools/$selectedBuildTools").takeIf { it.isDirectory }?.let {
                pathEntries.add(it.absolutePath)
            }
        }

        val selectedCmake = cmakeVersion ?: listVersions("cmake").firstOrNull()
        if (selectedCmake != null) {
            File(androidHome, "cmake/$selectedCmake/bin").takeIf { it.isDirectory }?.let {
                pathEntries.add(it.absolutePath)
            }
        }

        if (selectedNdk != null) {
            File(androidHome, "ndk/$selectedNdk").takeIf { it.isDirectory }?.let {
                pathEntries.add(it.absolutePath)
            }
        }

        val existingPath = env["PATH"] ?: ""
        val newPath = (pathEntries + listOf(existingPath).filter { it.isNotBlank() })
            .joinToString(File.pathSeparator)

        env["PATH"] = newPath
        return env
    }
}
```

---

## 4. Anwendungsbeispiele

### 1. SDK-Status abfragen
```kotlin
val sdk = AndroidSdkEnvironment(File(rootfsDir, "root/android-sdk"))
val status = sdk.inspect()

println("Installierte Build-Tools: ${status.installedBuildTools.joinToString()}")
println("Installierte NDKs: ${status.installedNdks.joinToString()}")
```

### 2. Spezifische NDK-/Build-Tools Prozessumgebung bauen
```kotlin
val envForNdk25 = sdk.createProcessEnvironment(
    buildToolsVersion = "34.0.0",
    ndkVersion = "25.2.9519653"
)

val processBuilder = ProcessBuilder("ndk-build", "-C", "/path/to/project")
processBuilder.environment().putAll(envForNdk25)
processBuilder.start()
```

---

## 5. Build Verification
Die Implementierung wurde mit `bash gradlew assembleDebug` erfolgreich getestet und in die APK integriert.
