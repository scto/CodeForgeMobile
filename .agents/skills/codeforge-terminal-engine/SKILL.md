---
name: codeforge-terminal-engine
description: CodeForgeMobile Linux-Terminal, PRoot Sandbox-Bootstrapping und Gradle IPC Bridge.
---
# CodeForgeMobile Terminal, PRoot & Gradle

## Wichtige Dateien
- `:libs:terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/ProotCommandBuilder.kt`: Baut Shell-Kommandos zur Ausführung in der PRoot-Umgebung.
- `:libs:terminal-engine/.../DistroBootstrapRepositoryImpl.kt`: Lädt und entpackt Alpine/Ubuntu/Debian.
- `:libs:gradle-tooling-bridge/`: AIDL-basierter Service im isolierten Prozess (`:gradletooling`).

## Konzepte
- **PRoot**: Erlaubt eine chroot-ähnliche Linux-Umgebung ohne Root-Rechte. Native Binaries (wie `proot`) müssen im `nativeLibraryDir` (`.so` Endung) der Android-App liegen.
- **Gradle IPC**: Die Gradle Tooling API kollidiert oft mit Androids ART-Classloadern. Daher läuft sie in CodeForgeMobile im isolierten `:gradletooling`-Prozess und kommuniziert via AIDL mit der App.
- **Terminal UI**: Das UI (`:feature:terminal`) zeigt lediglich den `sessionState` und den `output` Flow an. PTY-Features sind für die Zukunft angedacht.