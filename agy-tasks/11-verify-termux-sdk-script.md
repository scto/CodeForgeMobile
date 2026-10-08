# Task 11 – Verifikation: Termux-Umgebung, `codeforge-env`, SDK-Manager, Onboarding

Kontext: `libs/terminal-engine/BOOTSTRAP.md`, `docs/architecture-decisions.md` (ADR-3). PRoot/Multi-Distro wurden
entfernt; nichts davon wurde bisher mit Gradle/Android gebaut.

1. `./gradlew assembleDebug` — Compile-Fehler in `:libs:terminal-engine`, `:libs:plugin-api`, `:feature:onboarding`
   (neue Dependency `:libs:termux-app`), `:feature:sdkmanager`, `:feature:terminal`, `:feature:settings`, `:app` beheben.
   Achtung: `:libs:termux-app` hat denselben `namespace` wie `:app` (`com.codeforge.app`) — bei Konflikt anpassen.
   `embedTerminalBootstrap` braucht einen echten Release (`libs/termux-app/build.gradle.kts`).
2. `./gradlew :libs:terminal-engine:test` (SdkScriptProtocolTest).
3. **Exec-Frage:** Startet auf Gerät/Emulator (API 29+) die Termux-Shell aus `files/usr` bei `targetSdk 35`?
   Wenn nein: `targetSdk 28` oder `termux-exec`/Linker-Wrapper entscheiden und umsetzen.
4. Skript: `codeforge-env` als Termux-Paket in `terminal-packages-codeforge` aufnehmen (Quelle
   `libs/terminal-engine/src/main/assets/codeforge-env`), Manifest + Archive der eigenen Builds
   (cmdline-tools, platform-tools, build-tools inkl. aapt2, NDK 27d/30b, CMake 4.3.0) veröffentlichen, Repo-URL
   in `$PREFIX/etc/codeforge/sdk.conf` bzw. Platzhalter `@CODEFORGE_SDK_REPO@` setzen.
5. Gerät: Onboarding → Terminal öffnet und `codeforge-env setup` läuft → SDK-Manager zeigt Status, Install/Uninstall
   einzelner Komponenten, Fortschritt; neues Terminal hat ANDROID_HOME/JAVA_HOME; Gradle-Build eines Wizard-Projekts.
6. Plugins (Kotlin/Java-LSP-Beispiele): `buildShellCommand` im Termux-Prefix, `$HOME`-Pfade.
