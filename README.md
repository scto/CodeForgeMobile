# 🛠️ CodeForge Mobile

<div align="center">

# 🚀 CodeForge Mobile IDE
### *High-Performance Native Development Environment for Android*

A modern, native Android IDE featuring **Sora Editor**, **Terminal Engine (Linux Bootstrap)**, **Language Server Protocol (LSP)**, **Real-Time Jetpack Compose Preview**, & **Microsoft Fluent 2 Design System**.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-AGP_8.13.1-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.10.01-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/)
[![Design](https://img.shields.io/badge/Design-Microsoft_Fluent_2_%26_M3-0078D4.svg?style=for-the-badge&logo=microsoft)](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/FLUENT2_JETPACK_COMPOSE_GUIDE.md)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge)](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/LICENSE)

[About](#-about-the-project) • [Features](#-key-features) • [Architecture](#-system-architecture) • [Tech Stack](#-tech-stack) • [Getting Started](#-getting-started) • [Documentation](#-documentation) • [🇩🇪 German Version](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/README_DE.md)

</div>

---

## 📌 About the Project

**CodeForge Mobile** is a full-featured, highly modularized integrated development environment (IDE) designed for Android smartphones, tablets, and foldable devices. It brings the power and flexibility of desktop development tools directly to mobile hardware.

The project strictly follows **Clean Architecture** and **Modern Android Development (MAD)** principles. Its user interface combines the **Microsoft Fluent 2** design system with **Material 3**, delivering an elegant, adaptive, and customizable workspace in both light and dark themes.

---

## ✨ Key Features

### ⚡ 1. Sora Code Editor Ultimate Setup (`:feature:editor`)
* 🗺️ **Minimap Overview**: Visual code overview for fast navigation across large source files.
* 📌 **Sticky Scroll**: Keeps active function and class declarations pinned at the top of the editor.
* 🌈 **Rainbow Brackets & Inlay Hints**: Color-coded bracket pairing and dynamic type hints.
* 🔤 **Syntax Highlighting Engine**: Support for Kotlin, Java, Python, C/C++, XML, JSON, AIDL, Logcat via Tree-Sitter & Monarch.
* 🔍 **Gestures & Keyboard Support**: Pinch-to-zoom font scaling, magnifier, diagnostic UI, and desktop keyboard shortcuts.
* 🛠️ **Code Formatting & Auto-Completion**: Built-in code formatting and intelligent code completion.

### 💻 2. Embedded Terminal Engine (`:feature:terminal` / `:libs:terminal-engine`)
* 🐚 **Nyamux Terminal Core**: Native terminal emulation based on `nyamux-terminal.jar`.
* 🐧 **Linux Environment & Termix Engine**: Linux distro bootstrap, rootfs downloader, and automated JDK installer.
* ⚙️ **Background Execution**: Robust background command execution via `WorkManager` & `TerminalForegroundService`.

### 🎨 3. UI Preview & Visual Layout Designer (`:feature:composepreview` & `:feature:layoutdesigner`)
* 👁️ **Jetpack Compose Live Preview**: Render `@Composable` UI components in real time on mobile devices.
* ✏️ **Visual Drag & Drop Designer**: Visually design layouts with automatic code generation.

### 🚀 4. Android Studio Style Project Wizard (`:feature:projectwizard` & `:libs:template-engine`)
* 🧙 **2-Step Wizard**: Quickly create new Android and Kotlin projects with custom package names, SDK versions, and module structures.
* 📋 **Project Templates**: Pre-built templates (Compose App, Clean Architecture, Empty Activity, Multi-Module Engine, etc.).
* 📁 **SAF Storage Picker**: Seamless folder selection using the Android Storage Access Framework.

### 🔍 5. Language Server Protocol (LSP) & Extensions (`:libs:lsp-client` & `:feature:settings`)
* 🔌 **LSP Client Engine**: Integrated Eclipse `lsp4j` client enabling auto-completion, go-to-definition, symbol lookup, and diagnostics.
* 🧩 **Extension Manager**: Download, SHA-256 checksum verification, and usage validation of language server packages (Kotlin, Java, Python, C/C++).

### 🌿 6. Native Git Version Control (`:feature:git`)
* 🐙 **Eclipse JGit Integration**: Branch management, commits, remote management, pull/push operations, and file status tracking.

### 🎨 7. Theme Studio & Microsoft Fluent 2 Integration (`:feature:themebuilder`)
* 🎭 **15 Built-in Presets**: Monokai, Darcula, GitHub Light, VS Code Dark+, Cyberpunk Neon, Ocean, and more.
* 🎨 **RGB Color Picker**: Customizable brand accent colors with live preview cards.
* 🌓 **Adaptive Dark/Light Themes**: Full support for system dark mode and accessibility standards.

---

## 🏗️ System Architecture

CodeForge Mobile is organized into over 20 specialized Gradle modules to ensure high maintainability, fast build times, and clear interface separation:

```text
CodeForgeMobile/
├── app/                        # Main entry point, Hilt setup, & App Navigation
├── core/                       # Core modules & shared logic
│   ├── common/                 # Utilities, Coroutine Dispatchers, & Extensions
│   ├── data/                   # Repository implementations & data sources
│   ├── datastore/              # Protobuf DataStore (AppSettings, EditorConfig)
│   ├── designsystem/           # Fluent 2 & Material 3 Theme System
│   ├── domain/                 # Business logic, UseCases, & Domain models
│   ├── navigation/             # Navigation Routes & Destinations
│   ├── resources/              # Centralized String Resources & ResGetter Utility
│   ├── testing/                # Testing utilities & mocks
│   └── ui/                     # Reusable Compose UI components
├── feature/                    # Feature modules (UI, State, & ViewModel)
│   ├── composepreview/         # Jetpack Compose Live Renderer
│   ├── editor/                 # Sora Editor Ultimate Setup
│   ├── filetree/               # Bonsai Tree-View File Explorer
│   ├── git/                    # Git Integration & Status UI
│   ├── layoutdesigner/         # Drag & Drop UI Layout Builder
│   ├── onboarding/             # Quick Setup & Onboarding
│   ├── plugins/                # Plugin Management
│   ├── projectwizard/          # Android Studio-Style Project Creator
│   ├── sdkmanager/             # Toolchain & SDK Manager
│   ├── settings/               # App Settings & LSP Extension Manager
│   ├── terminal/               # Linux Terminal & Console UI
│   ├── themebuilder/           # Theme Studio & Color Picker
│   └── welcome/                # IDE Dashboard & Welcome Screen
├── libs/                       # Standalone libraries & engine bridges
│   ├── gradle-tooling-bridge/  # Integration with Gradle Tooling API
│   ├── lsp-client/             # Language Server Protocol Client (LSP4J)
│   ├── plugin-api/             # Extension API for developer plugins
│   ├── template-engine/        # Code & Project Generation (Freemarker)
│   └── terminal-engine/        # Terminal Emulation & Native JNI Layer
└── examples/                   # Example LSP plugins (Kotlin & Java)
```

---

## 🛠️ Tech Stack & Libraries

| Category | Technology / Library | Version | Description |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | `2.2.21` | Coroutines, Flow, & Serialization |
| **Build System** | Gradle / AGP | `9.5.1` / `8.13.1` | KSP (`2.2.21-2.0.5`), KtLint (`14.2.0`) |
| **UI Framework** | Jetpack Compose | `2024.10.01` (BOM) | Material 3 & Adaptive Navigation |
| **Design System** | Microsoft Fluent 2 | Custom Tokens | Fluent Controls & Design Tokens |
| **Code Editor** | Rosemoe Sora-Editor | `0.24.6` | Tree-Sitter (`4.3.2`) & Monarch (`1.0.3`) |
| **Dependency Injection** | Dagger Hilt | `2.57.1` | Multi-module Dependency Injection |
| **Persistence** | Proto DataStore | `1.1.1` | Protobuf (`4.35.1`) Type-safe settings |
| **Terminal Engine** | Nyamux / JNI | Prebuilt JAR | Terminal Session, Termix Rootfs, & JDK Installer |
| **Git Client** | Eclipse JGit | `6.10.0` | Native Git commands in pure Java |
| **LSP** | Eclipse LSP4J | `1.0.0` | Language Server Protocol Client |
| **File Tree** | Bonsai | `1.2.0` | High-performance Jetpack Compose Tree View |

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio**: Ladybug (2024.2.1) or newer recommended.
* **JDK**: OpenJDK 17 or JDK 21.
* **Min SDK**: Android 6.0 (API 23+), recommended Android 10+ (API 29+).

### Building the Project

```bash
# Clone the repository
git clone https://github.com/your-org/CodeForgeMobile.git
cd CodeForgeMobile

# Build Debug APK
./gradlew assembleDebug

# Run linters & code quality checks
./gradlew ktlintCheck
```

---

## 📚 Documentation & References

* 📊 [STATUS.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/STATUS.md) – Detailed development & component status.
* 🎨 [FLUENT2_JETPACK_COMPOSE_GUIDE.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/FLUENT2_JETPACK_COMPOSE_GUIDE.md) – Fluent 2 UI design guide.
* 📜 [CHANGELOG.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/CHANGELOG.md) – Chronological release history.
* ⚙️ [settings.gradle.kts](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/settings.gradle.kts) – Declaration of all 20+ Gradle modules.

---

## 📄 License

This project is licensed under the **Apache 2.0 License**. See [LICENSE](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/LICENSE) for details.

---

<div align="center">
  <sub>Built with ❤️ for the mobile developer community.</sub>
</div>