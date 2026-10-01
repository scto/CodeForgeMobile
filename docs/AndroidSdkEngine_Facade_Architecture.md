# Android SDK Engine Fassaden-Architektur (`AndroidSdkEngine.md`)

## 1. Übersicht & Motivation
Im Zuge der Erweiterungen für das dynamische SDK-Management wurden vier Hauptkomponenten entwickelt und auf ihre Nützlichkeit geprüft:

1. **`AndroidRepoCrawler`**: HTTP/XML-Netzwerk-Crawler für Googles SDK-Repository (`repository2-3.xml`) als Fallback, falls CLI-Abfragen fehlschlagen.
2. **`AndroidSdkEnvironment`**: Dateisystem-Scanner für Side-by-Side Versionen, Binärpfad-Auflösung (`aapt2`, `ndk-build`, `cmake`) und isolierte Prozessumgebungen (`Map<String, String>`).
3. **`AndroidSdkInstaller`**: Automatisierter CLI-Installer mit Daemon-Thread (`y\n`) zur unbedienten Lizenzannahme und parallelen Batch-Installationen (`AndroidPlatformManager`, `AndroidBuildToolsManager`).
4. **`AndroidSdkEngine`**: **Fassaden-Klasse (Facade Pattern)**, die alle drei Module unter einer sauberen, einfachen Schnittstelle bündelt.

---

## 2. Behebung von Re-Deklarations-Konflikten
Beim Kombinieren mehrerer Quellcodedateien im gleichen Paket (`com.codeforge.libs.terminal_engine`) kam es zu doppelten Klassendeklarationen (`Redeclaration: class AndroidSdkEnvironment`, `class AndroidSdkInstaller` etc.).

### Lösung:
`AndroidSdkEngine` wurde als **Fassadenklasse** refaktoriert. Sie deklariert die Unterklassen nicht doppelt, sondern kapselt Instanzen von `AndroidRepoCrawler`, `AndroidSdkEnvironment`, `AndroidSdkInstaller`, `AndroidPlatformManager` und `AndroidBuildToolsManager` und delegiert Aufrufe direkt.

---

## 3. Quellcode der Fassadenklasse ([AndroidSdkEngine.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/AndroidSdkEngine.kt))

```kotlin
package com.codeforge.libs.terminal_engine

import java.io.File

/**
 * AndroidSdkEngine – Zentrale Fassade für das Android SDK Management.
 * Bündelt Crawler (AndroidRepoCrawler), Environment (AndroidSdkEnvironment)
 * und Installer (AndroidSdkInstaller, AndroidPlatformManager, AndroidBuildToolsManager).
 */
class AndroidSdkEngine(
    customAndroidHome: File? = null,
    prootRunner: ((command: List<String>, onOutputLine: (String) -> Unit) -> Int)? = null
) {
    val environment: AndroidSdkEnvironment = AndroidSdkEnvironment(customAndroidHome)
    val installer: AndroidSdkInstaller = AndroidSdkInstaller(environment, prootRunner)
    val platformManager: AndroidPlatformManager = AndroidPlatformManager(installer)
    val buildToolsManager: AndroidBuildToolsManager = AndroidBuildToolsManager(installer)
    val repoCrawler: AndroidRepoCrawler = AndroidRepoCrawler

    val androidHome: File get() = environment.androidHome

    fun inspect(): SdkInstallation = environment.inspect()

    fun acceptAllLicenses(onOutputLine: ((String) -> Unit)? = null): Boolean {
        return installer.acceptAllLicenses(onOutputLine)
    }

    fun installPackages(packages: List<String>, onOutputLine: ((String) -> Unit)? = null): Boolean {
        return installer.installPackages(packages, onOutputLine)
    }

    fun createProcessEnvironment(
        buildToolsVersion: String? = null,
        ndkVersion: String? = null,
        cmakeVersion: String? = null,
        inheritSystemEnv: Boolean = true
    ): MutableMap<String, String> {
        return environment.createProcessEnvironment(
            buildToolsVersion = buildToolsVersion,
            ndkVersion = ndkVersion,
            cmakeVersion = cmakeVersion,
            inheritSystemEnv = inheritSystemEnv
        )
    }

    fun resolveBinary(binaryName: String, component: String, version: String): File? {
        return environment.resolveBinary(binaryName, component, version)
    }
}
```

---

## 4. Anwendungsbeispiel

```kotlin
val sdkEngine = AndroidSdkEngine()

// 1. Lizenzen per Daemon-Thread akzeptieren
sdkEngine.acceptAllLicenses()

// 2. Parallele Batch-Installation
sdkEngine.installPackages(listOf("build-tools;34.0.0", "ndk;25.2.9519653"))

// 3. Inspektion & Prozessumgebung
val status = sdkEngine.inspect()
val envMap = sdkEngine.createProcessEnvironment(buildToolsVersion = "34.0.0", ndkVersion = "25.2.9519653")
```

---

## 5. Build-Ergebnis
Die Zusammenfassung und die Fassadenstruktur wurden via Gradle `bash gradlew assembleDebug` kompiliert und als APK veröffentlicht.
