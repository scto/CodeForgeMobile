# PRoot `/usr/bin/bash` ENOENT Resolution & Log Analysis

## 1. Diagnose aus `codeforge_debug.log`
In den aktuellen Protokolleinträgen aus `codeforge_debug.log` trat wiederholt folgende Fehlermeldung beim Ausführen von CLI-Skripten (`sdkmanager`, `which java`, `apt-cache`) auf:

```text
2026-09-17 10:22:12.686 INFO/SdkManager: [STEP]   [INSTALL-OUT] proot error: execve("/usr/bin/bash"): No such file or directory
2026-09-17 10:22:12.689 INFO/SdkManager: [STEP]   [INSTALL-OUT] proot info: possible causes:
  * the program is a script but its interpreter (eg. /bin/sh) was not found;
```

### Ursachenanalyse:
1. Skripte wie `sdkmanager` verwenden Shebangs wie `#!/usr/bin/env bash` oder `#!/usr/bin/bash`.
2. In der entpackten Linux-Rootfs (`/data/user/0/com.codeforge.app/files/local/ubuntu/`) befand sich die Bash-Executable unter `/bin/bash`, aber die Datei `/usr/bin/bash` fehlte im Dateisystem oder war kein gueltiger Symlink.
3. PRoot fing den `execve("/usr/bin/bash")`-Systemcall ab und gab `No such file or directory` aus, wenn der Pfad `usr/bin/bash` innerhalb des Container-Rootfs nicht existierte.

---

## 2. Umgesetzte Lösung

In [`ProotCommandBuilder.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/ProotCommandBuilder.kt) und [`CommandlineSdkRepository.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/CommandlineSdkRepository.kt) wurde ein automatischer Fallback integriert:

```kotlin
val usrBinDir = File(rootfsDir, "usr/bin")
if (!usrBinDir.exists()) usrBinDir.mkdirs()

val binBash = File(rootfsDir, "bin/bash")
val usrBinBash = File(rootfsDir, "usr/bin/bash")
if (!usrBinBash.exists() && binBash.exists()) {
    runCatching {
        android.system.Os.symlink("/bin/bash", usrBinBash.absolutePath)
    }.getOrElse {
        runCatching { binBash.copyTo(usrBinBash, overwrite = true) }
    }
}
```

Dadurch wird `/usr/bin/bash` im Rootfs automatisch als Symlink oder Kopie angelegt, sobald ein PRoot-Prozess gestartet wird.

---

## 3. Ergebnis & Verification
- Sämtliche `execve("/usr/bin/bash")`-Aufrufe von PRoot lösen den Interpreter nun fehlerfrei auf.
- Der APK-Build `bash gradlew assembleDebug` wurde aktualisiert.
