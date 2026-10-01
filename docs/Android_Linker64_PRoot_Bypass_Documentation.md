# Android Linker64 & PRoot Restriktionsumgehung

## Übersicht

In Android-Systemen (insbesondere ab Android 10+ / API 29+) schränken SELinux-Policies und W^X (Write XOR Execute)-Restriktionen das direkte Ausführen von nachgeladenen Binärdateien im App-Datenverzeichnis (`/data/data/com.codeforge.mobile/`) ein.

Um eine vollständige Linux-RootFS-Umgebung (Ubuntu, Debian, Alpine) ohne Root-Rechte auszuführen, nutzt CodeForge Mobile den Android Dynamic Linker (`/system/bin/linker64`) in Kombination mit `PRoot` (`libproot.so`).

---

## Funktionsweise & Architektur

### 1. Aufruf über den Android Dynamic Linker (`linker64`)

Statt `libproot.so` direkt als ausführbare Datei aufzurufen (was von Android blockiert werden kann), wird der System-Linker des Android-Betriebssystems direkt als Prozess gestartet und `libproot.so` als Argument übergeben:

```bash
/system/bin/linker64 /data/app/.../lib/arm64/libproot.so [PROOT_ARGUMENTE]
```

Im Kotlin-Code ([`TerminalSessionRepositoryImpl.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/TerminalSessionRepositoryImpl.kt#L106-L118)) wird dies wie folgt konfiguriert:

```kotlin
val linkerPath = "/system/bin/linker64"
val prootBinaryPath = "${context.applicationInfo.nativeLibraryDir}/libproot.so"

val envList = mutableListOf(
    "PREFIX=$prefix",
    "LD_LIBRARY_PATH=${context.applicationInfo.nativeLibraryDir}",
    "LINKER=$linkerPath",
    "NATIVE_LIB_DIR=${context.applicationInfo.nativeLibraryDir}",
    "PKG=${context.packageName}",
    "PROOT_TMP_DIR=${systemPaths.getLocalTmpDir()}",
    "TMPDIR=${systemPaths.getLocalTmpDir()}",
    "PROOT_BINARY=$prootBinaryPath",
    "PROOT_LOADER=${context.applicationInfo.nativeLibraryDir}/libproot-loader.so",
    "PROOT_LOADER_32=${context.applicationInfo.nativeLibraryDir}/libproot-loader32.so",
    "SHELL=/bin/bash",
    "CODEFORGEMOBILE_SHELL=/bin/bash"
)
```

---

## Wichtige Fehlerbehebungen & Linker-Injektionen

### 1. `LD_LIBRARY_PATH` Injektion (`libtalloc.so` Not Found)
PRoot ist dynamisch gegen `libtalloc.so` gelinkt. Da `ProcessBuilder` in Java standardmäßig nicht die nativeren App-Bibliothekspfade injiziert, muss `LD_LIBRARY_PATH` explizit auf `nativeLibraryDir` gesetzt werden:

```kotlin
processBuilder.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
```

### 2. PRoot `link2symlink` & Temp-Verzeichnis
Da Android-Dateisysteme auf `/sdcard` oder in App-Caches symbolische Links oft einschränken, werden Symlinks über PRoot-interne Mechanismen (`--link2symlink`) auf das `PROOT_TMP_DIR` der App abgebildet.

---

## Referenzierte Dokumente im Projekt

- [`docs/sdkmanager_proot_linker_fix.md`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/docs/sdkmanager_proot_linker_fix.md): Behebung von Linker- und Flag-Fehlern beim SdkManager.
- [`assets/misc/ONBOARDING_LINKER_FIX.md`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/misc/ONBOARDING_LINKER_FIX.md): Behebung des Linker-Fehlers während des Onboarding-Setups.
