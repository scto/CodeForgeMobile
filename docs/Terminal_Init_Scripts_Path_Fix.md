# Terminal Init Skripte Pfad-Korrektur (`files/files/proot`)

## Übersicht

Dieses Dokument beschreibt die Korrektur der Pfadauflösung in den Terminal-Init-Skripten, um den Fehler `cp: bad '/data/user/0/com.codeforge.app/files/files/proot': No such file or directory` zu beheben.

---

## 1. Problemursache

In Android-Anwendungen entspricht die Pfadvariable `$PREFIX` der Anwendungsdatenstruktur `context.filesDir.absolutePath`:
```
/data/user/0/com.codeforge.app/files
```

In den Shell-Skripten (`init-ubuntu-host.sh`, `init-debian-host.sh`, `init-host.sh` etc.) wurden Pfade hartkodiert wie folgt zusammengesetzt:
```sh
[ ! -e "$PROOT_BIN" ] && cp "$PREFIX/files/proot" "$PROOT_BIN"
for sofile in "$PREFIX/files/"*.so.2; do
tar -xf "$PREFIX/files/ubuntu.tar.gz" -C "$UBUNTU_DIR"
```

Durch Ersetzung von `$PREFIX` entstand ein doppeltes Segment:
```
/data/user/0/com.codeforge.app/files/files/proot
```
Da dieser Ordner auf dem Host-Dateisystem nicht existiert, brach das Kopieren ab.

---

## 2. Lösung & Implementierung

### 2.1 Dynamische Quellprüfungen für `PROOT_BIN`
Das Kopieren von `proot` prüft nun nacheinander alle möglichen Pfadursprünge (einschließlich der nativen Android-Bibliotheken):

```sh
if [ ! -e "$PROOT_BIN" ]; then
    if [ -n "$PROOT_BINARY" ] && [ -e "$PROOT_BINARY" ]; then
        cp "$PROOT_BINARY" "$PROOT_BIN"
    elif [ -e "$PREFIX/proot" ]; then
        cp "$PREFIX/proot" "$PROOT_BIN"
    elif [ -e "$PREFIX/files/proot" ]; then
        cp "$PREFIX/files/proot" "$PROOT_BIN"
    elif [ -n "$NATIVE_LIB_DIR" ] && [ -e "$NATIVE_LIB_DIR/libproot.so" ]; then
        cp "$NATIVE_LIB_DIR/libproot.so" "$PROOT_BIN"
    fi
fi
[ -e "$PROOT_BIN" ] && chmod +x "$PROOT_BIN" 2>/dev/null || true
```

### 2.2 Korrektur der Shared Libraries (`*.so.2`)
```sh
for sofile in "$PREFIX/"*.so.2 "$PREFIX/files/"*.so.2; do
    if [ -f "$sofile" ]; then
        dest="$LIB_DIR/$(basename "$sofile")"
        [ ! -e "$dest" ] && cp "$sofile" "$dest"
    fi
done
```

### 2.3 Korrektur der Rootfs-Archive (`ubuntu.tar.gz`, `debian.tar.gz`, `alpine.tar.gz`)
```sh
if [ -f "$PREFIX/ubuntu.tar.gz" ]; then
    tar -xf "$PREFIX/ubuntu.tar.gz" -C "$UBUNTU_DIR"
elif [ -f "$PREFIX/files/ubuntu.tar.gz" ]; then
    tar -xf "$PREFIX/files/ubuntu.tar.gz" -C "$UBUNTU_DIR"
fi
```

---

## 3. Aktualisierte Skripte

Die Änderungen wurden in folgenden Dateien durchgeführt:
- [init-ubuntu-host.sh](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/assets/init-ubuntu-host.sh)
- [init-ubuntu-root.sh](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/assets/init-ubuntu-root.sh)
- [init-debian-host.sh](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/assets/init-debian-host.sh)
- [init-debian-root.sh](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/assets/init-debian-root.sh)
- [init-host.sh](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/assets/init-host.sh)
- [init-root.sh](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/assets/init-root.sh)

---

## 4. Build-Verifizierung

Der Build wurde mit `bash gradlew assembleDebug` verifiziert:
- `BUILD SUCCESSFUL in 1m 32s`
- Erstellte APK: `app/build/outputs/apk/debug/app-debug.apk` (50 MB)
