# proot-Binary beschaffen (libproot.so)

`ProotCommandBuilder` (in `:libs:terminal-engine`) erwartet ein natives Binary unter:

```
${context.applicationInfo.nativeLibraryDir}/libproot.so
```

Dieses Binary wird in dieser Entwicklungsumgebung **nicht mitgeliefert** — hier standen
weder Netzwerkzugriff (zum Herunterladen von Quellcode/vorkompilierten Binaries) noch
eine Android-NDK-Cross-Compile-Toolchain zur Verfügung. Ein hier "erzeugtes" Binary wäre
zwangsläufig unecht. Stattdessen zwei reale, von Termux und vergleichbaren Projekten
etablierte Wege:

## Option A — vorkompiliertes Binary übernehmen (schneller)

1. proot-Binaries für die relevanten ABIs (mindestens `arm64-v8a`, optional `x86_64`
   für Emulatoren) besorgen — z. B. aus dem Termux-Paket `proot-static` oder dem
   `termux-packages`-Build-Repository (dortige CI baut proot bereits für alle
   Android-ABIs).
2. Jede Binary-Datei nach dem Schema umbenennen, das Android für ausführbare native
   Bibliotheken vorschreibt (Präfix `lib`, Suffix `.so` — Android führt seit API 29
   nur noch Dateien aus, die als jniLibs-Artefakt erkannt werden):
   ```
   app/src/main/jniLibs/arm64-v8a/libproot.so
   app/src/main/jniLibs/x86_64/libproot.so
   ```
3. Kein zusätzlicher Gradle-Code nötig — AGP verpackt `src/main/jniLibs/<abi>/*.so`
   automatisch mit korrekter ABI-Zuordnung in die APK, und
   `context.applicationInfo.nativeLibraryDir` zeigt zur Laufzeit automatisch auf die
   für das jeweilige Gerät passende Datei.

## Option B — proot selbst cross-kompilieren

1. Android NDK installieren (r26 oder neuer empfohlen).
2. proot-Quellcode besorgen (inkl. der von proot benötigten `talloc`-Bibliothek als
   statische Abhängigkeit).
3. Mit dem NDK-Toolchain-Cross-Compiler bauen, z. B. für arm64:
   ```
   export TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64
   export CC=$TOOLCHAIN/bin/aarch64-linux-android29-clang
   # talloc statisch einbinden, dann proot mit diesem Cross-Compiler bauen
   # (siehe proot-Makefile: ARCH=arm64, CC=$CC, gegen die talloc-Headers/-Lib linken)
   ```
4. Resultierendes Binary wie in Option A nach `jniLibs/arm64-v8a/libproot.so` legen.

## Nach dem Einbinden

Sobald `libproot.so` vorhanden ist, funktioniert der Rest der Kette bereits — sowohl
`:feature:terminal` (interaktive Shell) als auch die für `:libs:gradle-tooling-bridge`
und `:libs:lsp-client` dokumentierten TODOs (Prozesse innerhalb der Rootfs statt direkt
im App-Prozess starten) bauen auf genau diesem `ProotCommandBuilder.build(...)`-Aufruf
auf und benötigen keine weiteren Codeänderungen.
