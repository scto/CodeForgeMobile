# 🛠️ CodeForge Mobile

<div align="center">

# 🚀 CodeForge Mobile IDE
### *Native Entwicklungsumgebung für Android – mit Termux-Shell im Gerät*

Eine native Android-IDE mit **Sora Editor**, **Termux-basierter Shell** (JDK, Android SDK, NDK per `codeforge-env`), **Git (JGit)**, **Language Server Protocol**, **Compose-Live-Preview**, **visuellem Layout-Designer** und **Material 3 Expressive**.

[![Version](https://img.shields.io/badge/Version-3.0.0-blueviolet.svg?style=for-the-badge)](CHANGELOG.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-AGP_8.13.2-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2025.09.00-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Design](https://img.shields.io/badge/Design-Material_3_Expressive-0078D4.svg?style=for-the-badge)](docs/adaptive-edge-to-edge-expressive.md)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/badge/Build-ungepr%C3%BCft-orange.svg?style=for-the-badge)](STATUS.md)

[Über das Projekt](#-über-das-projekt) • [Features](#-hauptmerkmale) • [Architektur](#-systemarchitektur) • [Tech Stack](#-tech-stack--bibliotheken) • [Erste Schritte](#-erste-schritte) • [Dokumentation](#-dokumentation--referenzen) • [🇬🇧 English Version](README.md)

</div>

---

> ⚠️ **Entwicklungsstand:** Der Code wurde ohne Android-SDK/Gradle-Lauf geschrieben. Es gibt noch **keinen verifizierten Build**; nur reine JVM-Logik (Git, Code-Tools, Layout-Modell) ist getestet. Details in [STATUS.md](STATUS.md).

## 📌 Über das Projekt

**CodeForge Mobile** ist eine hochgradig modularisierte integrierte Entwicklungsumgebung (IDE) für Android-Smartphones, Tablets und Falt-Geräte. Das Projekt folgt **Clean Architecture** (UI → Domain → Data), MVI/MVVM, Kotlin Coroutines/Flow und Jetpack Compose mit **Material 3 Expressive**. Die Oberfläche ist adaptiv (Compact/Medium/Expanded) und Edge-to-Edge.

Die Entwicklungsumgebung (Shell, JDK, Android SDK, NDK) läuft in einem **Termux-Prefix** unter `/data/data/com.codeforge.app/files/usr`; PRoot, Rootfs und Multi-Distro gibt es nicht mehr. Wegen der eingebundenen Termux-Module ist das Projekt **GPLv3**.

---

## ✨ Hauptmerkmale

Legende: ✅ umgesetzt (ungebaut) · 🧪 Logik JVM-getestet · 🟡 teilweise

### ⚡ 1. Sora Code Editor (`:feature:editor`) 🟡
* 🔍 Sora-Editor-Wrapper für Compose mit Magnifier, Sticky Scroll, Zeilenumbruch und LSP-Anbindung (Decorator `LspAwareLanguage`).
* 🌈 TextMate- und Tree-Sitter-Gerüst; **Grammars und Native-Libs müssen noch beschafft werden** (`agy-tasks/01`, `02`).
* 🧩 Overlays: Versions-Hinweis (`4.0.1 → 4.0.3`) in TOML-Katalogen mit Update/Update-All, Farb-Kästchen neben Farbwerten.
* 🔎 Suchen & Ersetzen (Datei und Projekt, Regex/Case/Wort) 🧪, Formatter/Highlighter-Logik 🧪 (`:libs:code-tools`).

### 💻 2. Terminal & Umgebung (`:feature:terminal`, `:libs:terminal-engine`, `:libs:termux-*`) ✅
* 🐚 Echte Termux-Shell (vendort aus `scto/AndroidIDE`, Paket `com.codeforge`).
* 🧰 **`codeforge-env`**: installiert JDK 17/21, Android SDK (`$PREFIX/opt/android-sdk`), cmdline-tools, platform-tools, CMake und NDK; der SDK-Manager ist eine GUI darüber.
* 🚀 Onboarding: Intro → Berechtigungen → Bootstrap → Setup-Skript im Terminal.

### 🎨 3. Compose-Preview & Layout-Designer (`:feature:composepreview`, `:feature:layoutdesigner`)
* 👁️ Compose-Live-Preview für `@Composable`-Funktionen 🟡.
* ✏️ **Layout-Designer** ✅🧪: visueller Editor für Android-Layout-XML mit Palette, Vorschau (4 Gerätegrößen), Struktur-Baum, Eigenschaften, XML-Tab, Undo/Redo und Drawer-Bereich „Layouts“. Näherungsvorschau, kein Drag-and-Drop – siehe [docs/layout-designer.md](docs/layout-designer.md).

### 🚀 4. Projekt-Wizard & Template-Engine (`:feature:projectwizard`, `:libs:template-engine`) ✅
* 🧙 2-Schritt-Assistent mit 9 Vorlagen (u. a. Compose-App, Empty Activity, Multi-Modul), typisierte Validierung, SAF-Import; `git init` bei Projekterstellung.

### 🌿 5. Git (`:feature:git`, `:feature:settings`) ✅🧪
* 🐙 JGit-Panel: Diff, Merge, Commit, Push/Pull, Graph, Stash, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Blame, Hunk-weises Stage, Konflikt-Editor; Settings für Name/E-Mail/Token.

### 🔌 6. Language Server & Plugins (`:libs:lsp-client`, `:libs:plugin-api`) 🟡
* 🔌 LSP-Client (JSON-RPC) und Plugin-API; Beispiel-Plugins in `examples/` (Kotlin/Java). Ein Extensions-Manager mit Download und SHA-256-Prüfung existiert nur im lokalen Experimentzweig (`agy-tasks/16`).

### 📐 7. Indexierung & Dependency-Updater (`:libs:indexing-*`, `:libs:dependency-updater-*`) ✅
* 🔄 Projektindexierung und Versions-Check (TOML-Katalog bzw. `build.gradle(.kts)` aller Module) mit Dialog Dismiss / Ask later / Update.

### 🎭 8. Theme & Design (`:core:designsystem`, `:feature:themebuilder`) ✅
* 🎨 Material 3 Expressive, Dynamic Color, 5 Presets, eigene Paletten; adaptive Layouts und Edge-to-Edge. Der Fluent-2-Leitfaden ([FLUENT2_JETPACK_COMPOSE_GUIDE.md](FLUENT2_JETPACK_COMPOSE_GUIDE.md)) ist bisher nur Designrichtung, nicht umgesetzt.

### 🌍 9. Zentrale Strings (`:core:resources`) ✅🧪
* 📝 Alle sichtbaren Texte in einer `strings.xml` (~720 Einträge), Zugriff über `Res`/`stringRes`; `TestRes` für JVM-Tests.

---

## 🏗️ Systemarchitektur

43 Gradle-Module mit klarer Abhängigkeitsrichtung (`:feature:*` → nur `:core:*`/`:libs:*-api`; Feature-Module kennen einander nicht, Kommunikation über Bridges in `:core:navigation`):

```text
CodeForgeMobile/
├── app/                          # Einstieg, Hilt, Navigation, Workspace/Drawer
├── build-logic/                  # Convention-Plugins
├── core/
│   ├── common/ data/ datastore/  # Utilities · Repositories · Proto-DataStore
│   ├── designsystem/ ui/         # Theme (M3 Expressive) · WidthClass/Compose-Helfer
│   ├── domain/ navigation/       # UseCases/Modelle · Bridges
│   ├── resources/ testing/       # Zentrale Strings · Test-Helfer
├── feature/
│   ├── composepreview/ dependencyupdates/ editor/ filetree/ git/
│   ├── layoutdesigner/ modulemaker/ onboarding/ plugins/ projectwizard/
│   └── sdkmanager/ search/ settings/ terminal/ themebuilder/ welcome/
├── libs/
│   ├── code-tools/               # Format, Suche, Modul-Maker (reine JVM)
│   ├── indexing-api|impl/ dependency-updater-api|impl/
│   ├── lsp-client/ plugin-api/ gradle-tooling-bridge/ template-engine/
│   ├── terminal-engine/          # Termux-Anbindung, `codeforge-env`
│   └── termux-emulator|view|shared|app/   # Vendort, GPLv3
├── agy-tasks/                    # Aufträge für den KI-Coding-Agenten agy
├── docs/                         # Architektur, Features, `sub/` mit Termux-/Lizenzdokumenten
└── examples/                     # Beispiel-Plugins (Kotlin/Java LSP)
```

---

## 🛠️ Tech Stack & Bibliotheken

| Kategorie | Technologie | Version |
| :--- | :--- | :--- |
| Sprache | Kotlin | `2.1.21` |
| Build | Gradle / AGP / KSP | `8.14.3` / `8.13.2` / `2.1.21-2.0.1` |
| UI | Jetpack Compose BOM / Material 3 Adaptive | `2025.09.00` / `1.1.0` |
| DI | Dagger Hilt | `2.56.2` |
| Persistenz | Proto DataStore / Protobuf | `1.1.1` / `3.25.3` |
| Editor | Rosemoe Sora-Editor (`editor`, `language-textmate`, `language-treesitter`) | `0.23.4` |
| Git | Eclipse JGit | `6.10.0` |
| LSP | Eclipse LSP4J | `0.24.0` |
| Dateibaum | Bonsai | `1.2.0` |
| SDK | minSdk / targetSdk / compileSdk | `26` / `35` / `36` |
| Quality | ktlint plugin / detekt | `14.2.0` / `2.0.0-alpha.5` |

> Die Versionen stammen aus `gradle/libs.versions.toml` und sind teilweise **nicht gegen Maven/Google verifiziert** (siehe [STATUS.md](STATUS.md)).

---

## 🚀 Erste Schritte

### Voraussetzungen
* **Android Studio** (aktuell) mit Android-SDK-Plattform 36
* **JDK 17** oder 21
* Gerät/Emulator ab Android 8.0 (API 26)

### Bauen
```bash
git clone https://github.com/scto/CodeForgeMobile.git
cd CodeForgeMobile
./gradlew :app:assembleDebug     # noch nie ausgeführt – Fehler sind zu erwarten
./gradlew ktlintCheck
```

### Termux-Bootstrap
Die App braucht einen Bootstrap für `com.codeforge.app`. Bauen und veröffentlichen mit `build_codeforge_repo.sh` ([Anleitung](build_codeforge_repo.md)); die GPG-Passphrase kommt aus der Umgebungsvariable `CODEFORGE_GPG_PASSPHRASE`.

---

## 📚 Dokumentation & Referenzen

* 📊 [STATUS.md](STATUS.md) – implementierte und offene Features
* 📜 [CHANGELOG.md](CHANGELOG.md) – Änderungsprotokoll
* 🤖 [agy-tasks/README.md](agy-tasks/README.md) – Prüf- und Umsetzungsaufträge (01–16)
* 🏛️ [docs/architecture-decisions.md](docs/architecture-decisions.md), [docs/overview.md](docs/overview.md)
* 🧱 [docs/build-logic.md](docs/build-logic.md), [docs/resources-and-strings.md](docs/resources-and-strings.md), [docs/module-files-and-app-resources.md](docs/module-files-and-app-resources.md)
* 📱 [docs/adaptive-edge-to-edge-expressive.md](docs/adaptive-edge-to-edge-expressive.md), [docs/layout-designer.md](docs/layout-designer.md)
* ✍️ [docs/editor-tools-git-drawer.md](docs/editor-tools-git-drawer.md), [docs/bonsai-sora-app-integration.md](docs/bonsai-sora-app-integration.md), [docs/indexing-and-dependency-updater.md](docs/indexing-and-dependency-updater.md), [docs/project-wizard-and-template-engine.md](docs/project-wizard-and-template-engine.md)
* 🐧 [docs/sub/TERMUX-PORTING.md](docs/sub/TERMUX-PORTING.md), [libs/terminal-engine/BOOTSTRAP.md](libs/terminal-engine/BOOTSTRAP.md), [docs/sub/NOTICE.md](docs/sub/NOTICE.md)
* 🎨 [FLUENT2_JETPACK_COMPOSE_GUIDE.md](FLUENT2_JETPACK_COMPOSE_GUIDE.md), [Flutter.md](Flutter.md) (Flutter-Setup unter Termux, Notizen)

---

## 📄 Lizenz

**GNU GPL v3** – siehe [LICENSE](LICENSE); Termux-Herkunft und Folgen in [docs/sub/NOTICE.md](docs/sub/NOTICE.md) und [docs/sub/LICENSE.termux](docs/sub/LICENSE.termux).

---

<div align="center">
  <sub>Erstellt mit ❤️ für die mobile Entwickler-Community.</sub>
</div>
