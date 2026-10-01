# Analyse & Behebung: PRoot Symlink- & Temp-Verzeichnis-Fehler (`codeforge_debug.log`)

Dieses Dokument beschreibt die Ursachenanalyse und die Behebung der in `codeforge_debug.log` aufgetretenen PRoot-Fehler.

---

## 1. Symptome im `codeforge_debug.log`

Bei der Ausführung von Hintergrund-CLI-Befehlen (`sdkmanager`, `apt-cache search`) traten folgende Fehler auf:

```text
proot warning: can't canonicalize /data/data/com.termux/files/usr/tmp/: No such file or directory
proot warning: Unable to create temp directory for f2fs bug probe: No such file or directory
proot error: execve("/usr/bin/env"): No such file or directory
fatal error: see `libproot.so --help`.
```

---

## 2. Ursachenanalyse & Behebung

### A. Fehler: `execve("/usr/bin/env"): No such file or directory`
- **Ursache**: Beim Entpacken des Rootfs-Archivs wurden symbolische Links (wie `/usr/bin/env`, `/bin/sh`, `/lib64 -> lib`) über `Runtime.getRuntime().exec(arrayOf("ln", "-s", ...))` erstellt. In der Android-Sandbox schlagen externe `ln`-Befehle in Subprozessen häufig fehl oder haben keine ausreichenden Rechte, wodurch kritische System-Symlinks im Rootfs fehlten.
- **Behebung**: In `libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/DistroBootstrapRepositoryImpl.kt` wird für die Entpackung symbolischer Links nun das native Java NIO API (`java.nio.file.Files.createSymbolicLink`) verwendet. Dadurch werden alle Symlinks nativ vom Linux-Kernel erstellt, ohne externe Prozesse zu spawnen.

### B. Warnung: `can't canonicalize /data/data/com.termux/files/usr/tmp/`
- **Ursache**: PRoot sucht nach einem temporären Verzeichnis für Sockets und F2FS-Fehlerprüfungen. Ohne die Umgebungsvariable `PROOT_TMP_DIR` greift PRoot auf den Termux-Standardpfad `/data/data/com.termux/files/usr/tmp/` zurück, der auf normalen Android-Geräten nicht existiert.
- **Behebung**:
  1. In `libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/ProotCommandBuilder.kt` wurde `"PROOT_TMP_DIR" to "/tmp"` zur Standard-Umgebung (`defaultEnv()`) hinzugefügt.
  2. `ProotCommandBuilder.build()` stellt nun automatisch sicher, dass der Ordner `tmp/` im Rootfs existiert (`File(rootfsDir, "tmp").mkdirs()`).
