# CodeForgeMobile ![Stone Badge](https://stone.professorlee.work/api/stone/scto/CodeForgeMobile)
[![CodeForgeMobile CI Pipeline](https://github.com/scto/CodeForgeMobile/actions/workflows/main-build-test.yml/badge.svg)](https://github.com/scto/CodeForgeMobile/actions/workflows/main-build-test.yml)
![Version](https://img.shields.io/badge/version-0.3.2-blue?style=flat-square)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue?style=flat-square)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-green?style=flat-square)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPLv3-orange?style=flat-square)](LICENSE)
#### Dieses Dokument fasst den Entwicklungsstand, die Architektur und die Modulstruktur des Android-Projekts CodeForge Mobile zusammen, basierend auf dem iterativen Entwicklungsprozess.

#### Projektübersicht & Entwicklungslog

##### 🏗️ Architektur & Grundlagen
Das Projekt ist als Gradle-Multi-Module-Projekt (25+ Module) aufgebaut und folgt strengen Architekturvorgaben:
* Architekturmuster: MVI (Model-View-Intent) mit UiState, UiEvent, und UiEffect.
* UI-Framework: Jetpack Compose (inkl. Material 3 Adaptive für Tablets/Foldables).
* Dependency Injection: Dagger/Hilt.
* Dependency-Regel: Feature-Module (:feature:*) dürfen nicht direkt aufeinander zugreifen, sondern kommunizieren über :core:domain (Interfaces) oder :core:navigation (Shared-State-Bridges).

##### 📦 Modulübersicht
**Core-Module (:core:*)
Die Fundamente der Anwendung:
* :core:datastore: Proto-DataStore für typsichere Einstellungen (Settings/Theme) inkl. Serializer und Hilt-Setup.
* :core:designsystem: CodeForgeTheme mit Dynamic-Color-Fallback und einer echten Preset-Registry für Farbschemata.
* :core:domain: Beinhaltet alle Repository-Interfaces und Domain-Models (z. B. OpenFileUseCase, BuildEvent).
* :core:data: Implementierungen der Repositories (z. B. FileSystemRepositoryImpl, RecentProjectsRepository, GitRepositoryImpl via JGit, CommandlineSdkRepository).
* :core:navigation: Beinhaltet Shared-State-Bridges wie ActiveComposablePreviewBridge zur Einhaltung der Dependency-Regeln.
* 
Feature-Module (:feature:*)
Die UI- und Geschäftslogik-Komponenten:
* :feature:onboarding: Drei Schritte (Intro, Runtime-Permissions für Storage/Notifications, Setup inkl. Linux-Distro-Bootstrap).
* :feature:welcome: Icon-Grid für Hauptaktionen und eine adaptive Liste (List-Detail-Pane) für Letzte Projekte.
* :feature:projectwizard: 3-Schritt-Wizard (Template wählen → Parameter validieren → Generieren).
* :feature:editor: Das Herzstück mit vollständigem MVI-Skelett und Sora-Editor-Wrapper.
* :feature:filetree: Lazy-geladener Dateibaum mit CRUD-Operationen, Dropdown-Menüs und Integration in Editor/Git.
* :feature:git: Vollwertiger JGit-Client. Bietet Status (Stage/Commit/Push/Pull/Log) und einen Clone-Flow mit echtem Fortschrittsbalken.
* :feature:terminal: Shell-UI (Monospace, Auto-Scroll) über eine echte PRoot-Session.
* :feature:themebuilder: Live-Vorschau, Modus-Auswahl, Dynamic-Color und Custom-Hex-Editoren, direkt an Proto-DataStore angebunden.
* :feature:settings: Stateless Navigations-Hub mit echten Unterseiten für Editor (Tree-sitter, Tab-Größe) und Terminal (Distro-Wahl).
* :feature:plugins: UI für SAF-Datei-Import (ZIP) und Verwaltung (Installieren, Aktivieren, Deinstallieren).
* :feature:sdkmanager: Sektionierte Liste für SDK-Tools (Build-Tools, Platforms, CMake) mit Install/Uninstall und Live-Fortschritt. Regex-basiertes CLI-Parsing.
* :feature:composepreview: Rendert gefundene Composables im Editor. Setzt eine Pipeline aus K2JVMCompiler, d8 und DexClassLoader voraus.
* 
Bibliotheken & Engines (:libs:*)
Gekapselte Kerntechnologien:
* :libs:template-engine: Echter Freemarker-Renderer (.ftl), liest manifest.json aus App-Assets und rendert Projekte (z. B. Compose Activity, Kotlin CLI).
* :libs:terminal-engine: Echter Rootfs-Download (Alpine/Ubuntu/Debian) via HTTP, Entpackung via Commons-Compress/XZ (inkl. Symlinks & Permissions) und ProotCommandBuilder.
* :libs:gradle-tooling-bridge: Läuft als eigener AIDL-Service im deklarierten :gradletooling-Prozess, um Classloader-Kollisionen zu vermeiden.
* :libs:lsp-client: Echter JSON-RPC 2.0 Client nach LSP-Spec. Startet Subprozesse, handhabt Message-Framing und synchronisiert Text-Edits.
* :libs:plugin-api: ZIP-Extraktion, Manifest-Parsing und echte Laufzeit-Einbindung über DexClassLoader.

##### 🚀 Technische Highlights & Herausforderungen
1. PRoot-Bootstrap (Linux in Android)
Es wurde ein echter Download- und Entpack-Pfad implementiert. Das Ausführen der Prozesse (/bin/sh, Gradle, LSP) erfolgt über den ProotCommandBuilder. Hinweis: Das tatsächliche libproot.so Binary muss als natives jniLibs-Artefakt bereitgestellt werden, da Android-Exec-Restriktionen (ab API 29) dies erfordern.
2. Gradle Tooling API via AIDL
Um Classloader-Konflikte zwischen der Android/ART-Runtime und dem Gradle-Daemon zu verhindern, läuft die Bridge in einem isolierten Android-Prozess (android:process=":gradletooling"). Die Kommunikation zur Haupt-App erfolgt asynchron über AIDL und Kotlin Flows.
3. Plugin-Codeausführung
Vollständig real umgesetzt: Plugins werden als ZIP importiert, entpackt und deren Code über die Android Framework API (DexClassLoader) dynamisch zur Laufzeit geladen. Ein Best-Effort-Reload stellt aktivierte Plugins beim App-Start wieder her.
4. Jetpack Compose On-Device Preview
Die Architektur für eine On-Device-Vorschau wurde implementiert (K2JVMCompiler → d8 → DexClassLoader → Composer-Reflection). Limitierung: In der aktuellen Sandbox-Umgebung ohne Netzwerk (für Dependency-Resolution) und ohne lokale Android NDK/SDK Toolchain konnte diese Pipeline nur als Architektur-Skelett vorbereitet, aber nicht lokal verifiziert werden.

##### 📌 Nächste Schritte / Offene Punkte
1. Externe Artefakte einbinden: Bereitstellung von libproot.so (entweder aus Termux extrahiert oder via NDK cross-kompiliert) anhand der erstellten BOOTSTRAP.md.
2. SDK Manager Fix: Behebung des Bugs im CommandlineSdkRepository, sodass ToolItem.path auf einen echten Dateisystempfad zeigt, anstatt nur die ID zu duplizieren (erforderlich für die d8-Suche in der Compose-Preview).
3. LSP-Server Integration: Den serverCommand des LSP-Clients auf ein echtes Binary (z. B. kotlin-language-server) innerhalb der PRoot-Umgebung lenken.
