# 🛠️ CodeForge Mobile

<div align="center">

# 🚀 CodeForge Mobile IDE
### *Native development environment for Android – with an on-device Termux shell*

A native Android IDE featuring **Sora Editor**, a **Termux-based shell** (JDK, Android SDK, NDK via `codeforge-env`), **Git (JGit)**, **Language Server Protocol**, **Compose live preview**, a **visual layout designer**, and **Material 3 Expressive**.

[![Version](https://img.shields.io/badge/Version-3.0.0-blueviolet.svg?style=for-the-badge)](CHANGELOG.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-AGP_8.13.2-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2025.09.00-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Design](https://img.shields.io/badge/Design-Material_3_Expressive-0078D4.svg?style=for-the-badge)](docs/adaptive-edge-to-edge-expressive.md)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](LICENSE)
[![Build](https://img.shields.io/badge/Build-unverified-orange.svg?style=for-the-badge)](STATUS.md)

[About](#-about-the-project) • [Features](#-key-features) • [Architecture](#-system-architecture) • [Tech Stack](#-tech-stack--libraries) • [Getting Started](#-getting-started) • [Documentation](#-documentation--references) • [🇩🇪 Deutsche Version](README_DE.md)

</div>

---

> ⚠️ **Development status:** The code was written without an Android SDK/Gradle run. There is **no verified build yet**; only pure JVM logic (Git, code tools, layout model) is tested. See [STATUS.md](STATUS.md) for details.

## 📌 About the Project

**CodeForge Mobile** is a highly modular integrated development environment (IDE) for Android phones, tablets, and foldables. It follows **Clean Architecture** (UI → Domain → Data), MVI/MVVM, Kotlin coroutines/Flow, and Jetpack Compose with **Material 3 Expressive**. The UI is adaptive (compact/medium/expanded) and edge-to-edge.

The development environment (shell, JDK, Android SDK, NDK) runs inside a **Termux prefix** at `/data/data/com.codeforge.app/files/usr`; PRoot, rootfs, and multi-distro support have been removed. Because of the bundled Termux modules the project is **GPLv3**.

---

## ✨ Key Features

Legend: ✅ implemented (unbuilt) · 🧪 logic JVM-tested · 🟡 partial

### ⚡ 1. Sora Code Editor (`:feature:editor`) 🟡
* 🔍 Sora editor wrapper for Compose with magnifier, sticky scroll, word wrap, and LSP integration (decorator `LspAwareLanguage`).
* 🌈 TextMate and Tree-Sitter scaffolding; **grammars and native libs still need to be sourced** (`agy-tasks/01`, `02`).
* 🧩 Overlays: version hints (`4.0.1 → 4.0.3`) in TOML catalogs with Update/Update All, color swatches next to color values.
* 🔎 Find & replace (file and project, regex/case/word) 🧪, formatter/highlighter logic 🧪 (`:libs:code-tools`).

### 💻 2. Terminal & Environment (`:feature:terminal`, `:libs:terminal-engine`, `:libs:termux-*`) ✅
* 🐚 A real Termux shell (vendored from `scto/AndroidIDE`, package `com.codeforge`).
* 🧰 **`codeforge-env`**: installs JDK 17/21, the Android SDK (`$PREFIX/opt/android-sdk`), cmdline-tools, platform-tools, CMake, and the NDK; the SDK manager is a GUI on top of it.
* 🚀 Onboarding: intro → permissions → bootstrap → setup script in the terminal.

### 🎨 3. Compose Preview & Layout Designer (`:feature:composepreview`, `:feature:layoutdesigner`)
* 👁️ Compose live preview for `@Composable` functions 🟡.
* ✏️ **Layout designer** ✅🧪: visual editor for Android layout XML with palette, preview (4 device sizes), structure tree, properties, XML tab, undo/redo, and a “Layouts” drawer section. Approximate preview, no drag-and-drop – see [docs/layout-designer.md](docs/layout-designer.md).

### 🚀 4. Project Wizard & Template Engine (`:feature:projectwizard`, `:libs:template-engine`) ✅
* 🧙 2-step wizard with 9 templates (Compose app, empty activity, multi-module, …), typed validation, SAF import; `git init` on project creation.

### 🌿 5. Git (`:feature:git`, `:feature:settings`) ✅🧪
* 🐙 JGit panel: diff, merge, commit, push/pull, graph, stash, tags, rebase, cherry-pick, revert, reset, amend, blame, hunk-wise staging, conflict editor; settings for name/email/token.

### 🔌 6. Language Server & Plugins (`:libs:lsp-client`, `:libs:plugin-api`) 🟡
* 🔌 LSP client (JSON-RPC) and plugin API; example plugins in `examples/` (Kotlin/Java). An extensions manager with download and SHA-256 verification exists only in the local experiment branch (`agy-tasks/16`).

### 📐 7. Indexing & Dependency Updater (`:libs:indexing-*`, `:libs:dependency-updater-*`) ✅
* 🔄 Project indexing and version check (TOML catalog or `build.gradle(.kts)` of all modules) with a Dismiss / Ask later / Update dialog.

### 🎭 8. Theme & Design (`:core:designsystem`, `:feature:themebuilder`) ✅
* 🎨 Material 3 Expressive, dynamic color, 5 presets, custom palettes; adaptive layouts and edge-to-edge. The Fluent 2 guide ([FLUENT2_JETPACK_COMPOSE_GUIDE.md](FLUENT2_JETPACK_COMPOSE_GUIDE.md)) is a design direction only and not yet implemented.

### 🌍 9. Centralized Strings (`:core:resources`) ✅🧪
* 📝 All visible texts in one `strings.xml` (~720 entries), accessed via `Res`/`stringRes`; `TestRes` for JVM tests.

---

## 🏗️ System Architecture

43 Gradle modules with a strict dependency direction (`:feature:*` → only `:core:*`/`:libs:*-api`; feature modules do not know each other, communication goes through bridges in `:core:navigation`):

```text
CodeForgeMobile/
├── app/                          # Entry point, Hilt, navigation, workspace/drawer
├── build-logic/                  # Convention plugins
├── core/
│   ├── common/ data/ datastore/  # Utilities · repositories · Proto DataStore
│   ├── designsystem/ ui/         # Theme (M3 Expressive) · WidthClass/Compose helpers
│   ├── domain/ navigation/       # UseCases/models · bridges
│   ├── resources/ testing/       # Central strings · test helpers
├── feature/
│   ├── composepreview/ dependencyupdates/ editor/ filetree/ git/
│   ├── layoutdesigner/ modulemaker/ onboarding/ plugins/ projectwizard/
│   └── sdkmanager/ search/ settings/ terminal/ themebuilder/ welcome/
├── libs/
│   ├── code-tools/               # Format, search, module maker (pure JVM)
│   ├── indexing-api|impl/ dependency-updater-api|impl/
│   ├── lsp-client/ plugin-api/ gradle-tooling-bridge/ template-engine/
│   ├── terminal-engine/          # Termux integration, `codeforge-env`
│   └── termux-emulator|view|shared|app/   # Vendored, GPLv3
├── agy-tasks/                    # Tasks for the AI coding agent agy
├── docs/                         # Architecture, features, `sub/` with Termux/license documents
└── examples/                     # Example plugins (Kotlin/Java LSP)
```

---

## 🛠️ Tech Stack & Libraries

| Category | Technology | Version |
| :--- | :--- | :--- |
| Language | Kotlin | `2.1.21` |
| Build | Gradle / AGP / KSP | `8.14.3` / `8.13.2` / `2.1.21-2.0.1` |
| UI | Jetpack Compose BOM / Material 3 Adaptive | `2025.09.00` / `1.1.0` |
| DI | Dagger Hilt | `2.56.2` |
| Persistence | Proto DataStore / Protobuf | `1.1.1` / `3.25.3` |
| Editor | Rosemoe Sora-Editor (`editor`, `language-textmate`, `language-treesitter`) | `0.23.4` |
| Git | Eclipse JGit | `6.10.0` |
| LSP | Eclipse LSP4J | `0.24.0` |
| File tree | Bonsai | `1.2.0` |
| SDK | minSdk / targetSdk / compileSdk | `26` / `35` / `36` |
| Quality | ktlint plugin / detekt | `14.2.0` / `2.0.0-alpha.5` |

> Versions come from `gradle/libs.versions.toml`; some are **not yet verified against Maven/Google** (see [STATUS.md](STATUS.md)).

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio** (current) with Android SDK platform 36
* **JDK 17** or 21
* Device/emulator running Android 8.0 (API 26) or newer

### Build
```bash
git clone https://github.com/scto/CodeForgeMobile.git
cd CodeForgeMobile
./gradlew :app:assembleDebug     # never run so far – expect errors
./gradlew ktlintCheck
```

### Termux Bootstrap
The app needs a bootstrap built for `com.codeforge.app`. Build and publish it with `build_codeforge_repo.sh` ([guide](build_codeforge_repo.md)); the GPG passphrase is read from the `CODEFORGE_GPG_PASSPHRASE` environment variable.

---

## 📚 Documentation & References

* 📊 [STATUS.md](STATUS.md) – implemented and open features
* 📜 [CHANGELOG.md](CHANGELOG.md) – release history
* 🤖 [agy-tasks/README.md](agy-tasks/README.md) – verification and implementation tasks (01–16)
* 🏛️ [docs/architecture-decisions.md](docs/architecture-decisions.md), [docs/overview.md](docs/overview.md)
* 🧱 [docs/build-logic.md](docs/build-logic.md), [docs/resources-and-strings.md](docs/resources-and-strings.md), [docs/module-files-and-app-resources.md](docs/module-files-and-app-resources.md)
* 📱 [docs/adaptive-edge-to-edge-expressive.md](docs/adaptive-edge-to-edge-expressive.md), [docs/layout-designer.md](docs/layout-designer.md)
* ✍️ [docs/editor-tools-git-drawer.md](docs/editor-tools-git-drawer.md), [docs/bonsai-sora-app-integration.md](docs/bonsai-sora-app-integration.md), [docs/indexing-and-dependency-updater.md](docs/indexing-and-dependency-updater.md), [docs/project-wizard-and-template-engine.md](docs/project-wizard-and-template-engine.md)
* 🐧 [docs/sub/TERMUX-PORTING.md](docs/sub/TERMUX-PORTING.md), [libs/terminal-engine/BOOTSTRAP.md](libs/terminal-engine/BOOTSTRAP.md), [docs/sub/NOTICE.md](docs/sub/NOTICE.md)
* 🎨 [FLUENT2_JETPACK_COMPOSE_GUIDE.md](FLUENT2_JETPACK_COMPOSE_GUIDE.md), [Flutter.md](Flutter.md) (Flutter setup under Termux, notes)

---

## 📄 License

**GNU GPL v3** – see [LICENSE](LICENSE); Termux provenance and consequences in [docs/sub/NOTICE.md](docs/sub/NOTICE.md) and [docs/sub/LICENSE.termux](docs/sub/LICENSE.termux).

---

<div align="center">
  <sub>Built with ❤️ for the mobile developer community.</sub>
</div>
