# SdkManager & PRoot Linker & Flag Fix (`libtalloc.so` & `-e`)

## Problembeschreibung

Beim Ausführen des **SdkManagers** in CodeForge Mobile wurden im Hintergrund 0 Pakete (Build-Tools, Platform-Tools, NDK, CMake) gefunden.

Die Diagnose über [`codeforge_debug.log`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/codeforge_debug.log) ergab zwei Ursachen bei der Ausführung der CLI-Befehle (`sdkmanager --list`, `apt update`):

### 1. Linker-Fehler (`libtalloc.so` not found)
```text
INFO/SdkManager: [STEP] RESP CLI (exitCode=1, totalLines=1):
INFO/SdkManager: [STEP]   [CLI-OUT] CANNOT LINK EXECUTABLE "/data/app/.../lib/arm64/libproot.so": library "libtalloc.so" not found: needed by main executable
```

### 2. PRoot Flag-Fehler (`unknown option '-e'`)
```text
INFO/SdkManager: [STEP] RESP CLI (exitCode=1, totalLines=2):
INFO/SdkManager: [STEP]   [CLI-OUT] proot error: unknown option '-e'.
INFO/SdkManager: [STEP]   [CLI-OUT] fatal error: see `libproot.so --help`.
```

---

## Ursachenanalyse

1. **Host-Linker (`LD_LIBRARY_PATH`)**: Wenn der SdkManager im Hintergrund CLI-Prozesse innerhalb des PRoot-Containers über den Java `ProcessBuilder` gestartet hat, fehlte im Host-Prozess die Umgebungsvariable `LD_LIBRARY_PATH`. Dadurch konnte der Android Dynamic Linker (`linker64`) beim Laden von `libproot.so` die Abhängigkeit `libtalloc.so` in `nativeLibraryDir` nicht auflösen.
2. **PRoot CLI-Argumente (`/usr/bin/env`)**: `ProotCommandBuilder` übergab Umgebungsvariablen als `-e KEY=VALUE` vor `/usr/bin/env`. Standard-PRoot unterstützt jedoch kein `-e`-Flag, weshalb PRoot mit `proot error: unknown option '-e'` abbrach. Umgebungsvariablen müssen nach `/usr/bin/env` als `KEY=VALUE` übergeben werden.

---

## Lösung & Behebung

### 1. `CommandlineSdkRepository.kt` (`:libs:terminal-engine`)
Zentrale Hilfsmethode `createProcessBuilder(distro, script)`, welche bei allen CLI-Prozessstarts den Pfad `context.applicationInfo.nativeLibraryDir` an `LD_LIBRARY_PATH` übergibt:

```kotlin
private fun createProcessBuilder(distro: String, script: String): ProcessBuilder {
    return ProcessBuilder(rootfsShellCommand(distro, script)).apply {
        environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
    }
}
```

### 2. `ProotCommandBuilder.kt` (`:libs:terminal-engine`)
Anpassung der Befehlszeilen-Generierung, sodass `/usr/bin/env` vor den Umgebungsvariablen steht:

```kotlin
add("/usr/bin/env")
env.forEach { (key, value) -> add("$key=$value") }
addAll(command)
```

### 3. `ProotExecutorImpl.kt` (`:libs:terminal-engine`)
Ergänzung von `LD_LIBRARY_PATH` im `ProcessBuilder.environment()` für allgemeine PRoot-Prozessausführungen:

```kotlin
val pb = ProcessBuilder(prootCommand)
pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
```

---

## Ergebnis
`libproot.so` linkt `libtalloc.so` nun fehlerfrei und führt Befehle über `/usr/bin/env` ohne Syntaxfehler aus. CLI-Abfragen an `sdkmanager` und den Paketmanager laufen erfolgreich durch.
