# CodeForgeMobile - Settings Structure & Navigation Guide

## Overview
This document details the updated structure, entry ordering, and feature descriptions for the Settings Hub in **CodeForgeMobile**. The settings layout has been organized into a clear, logical hierarchy matching modern mobile IDE standards.

---

## Settings Hub Entry Order & Architecture

The settings menu in [SettingsHubScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/hub/SettingsHubScreen.kt) is structured as follows:

| # | Title | Icon | Description | Destination Route |
|---|---|---|---|---|
| **1** | **Erscheinungsbild & Theme** | `Palette` | Farbschema, Dark/Light Mode & Theme Studio Anpassungen | `onNavigateToTheme` |
| **2** | **Editor** | `Code` | Zeilenumbruch, Schriftart, Einrückung, Formatierung & Verhalten | `onNavigateToEditor` |
| **3** | **Dateibaum & Ansichten** | `Folder` | Sortierung, Versteckte Dateien, Module/Projekt/Datei-Ansichtsmodus | `onNavigateToFileTree` |
| **4** | **Terminal** | `Terminal` | Distro-Auswahl (Alpine/Ubuntu/Debian), Virtuelle Tasten & Farbschemas | `onNavigateToTerminal` |
| **5** | **SDK Manager** | `SdCard` | Android SDK Build-Tools, NDK, CMake & Java 17 verwalten | `onNavigateToSdkManager` |
| **6** | **Plugins & Erweiterungen** | `Extension` | Installierte Erweiterungen, Language Server & Tooling Bridge | `onNavigateToPlugins` |
| **7** | **Debug & Diagnose** | `BugReport` | Logging-Level, Tracing & Systemdiagnose konfigurieren | `onNavigateToDebug` |
| **8** | **Über CodeForge Mobile** | `Info` | App-Version, Lizenzen, Systemübersicht & Entwickler-Informationen | `onNavigateToAbout` |

---

## Detailed Section Breakdown

### 1. 🎨 Erscheinungsbild & Theme (`:feature:themebuilder`)
- **Dark / Light Mode Toggle**: System, Light, and Dark theme mode switching.
- **Theme Selection**: Monokai, Darcula, Solarized, Ayu Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse.
- **Theme Studio**: Custom color palette customization (Primary, Secondary, Tertiary accent colors).

### 2. 💻 Editor (`:feature:settings:editor`)
- **Code Editing Properties**: Auto-closing brackets (`symbolPairAutoCompletion`), auto-indentation, line numbers, word wrap.
- **Typography**: Font family selection (JetBrains Mono, Ubuntu Mono, Roboto Mono) and font size scaling (10 sp – 24 sp).
- **Line Spacing & Whitespace**: Tab size, whitespace painting flags, pin line numbers, sticky scroll.

### 3. 📁 Dateibaum & Ansichten (`:feature:settings:filetree`)
- **View Modes**:
  - 📦 **Modul-Ansicht (Android View)**: Gradle module view hiding build output.
  - 📁 **Projekt-Ansicht (Project View)**: Standard physical file structure.
  - 📄 **Datei-Ansicht (Project Files View)**: Raw filesystem including dotfiles.
- **Indentation Lines**: Continuous, theme-adaptive vertical hierarchy guide lines (`TreeGuidelinePainter`).
- **File Details**: Formatted file size (B, KB, MB, GB) and last modified timestamp (`dd.MM.yy HH:mm`).
- **Sorting & Toggles**: Sort by Name, Type, Size, Date (Ascending/Descending), Show Hidden Files, Compact Mode.

### 4. 🖥️ Terminal (`:feature:settings:terminal`)
- **PRoot Linux Distributions**: Alpine, Ubuntu, Debian guest environments.
- **Virtual Keyboard Bar**: Configurable extra keys row (`Ctrl`, `Alt`, `Tab`, `Esc`, arrows, pipe, brackets).
- **Terminal Themes**: Monokai, Solarized Dark, Cyberpunk 2077, Matrix Green.

### 5. 🪛 SDK Manager (`:feature:sdkmanager`)
- **Build Tools**: Android SDK Commandline Tools, Build-Tools `34.0.0`, Platform-Tools.
- **NDK & CMake**: Android NDK r26, CMake `3.22.1` native C/C++ compilation toolchain.
- **Java Runtime**: Embedded OpenJDK 17 ARM64 environment.

### 6. 🔌 Plugins & Erweiterungen (`:feature:plugins`)
- **Extensions**: Language Server Protocol (LSP) integrations for Kotlin, Java, C/C++, Python.
- **Tooling Bridge**: Gradle tooling bridge API and custom IDE plugin engine.

### 7. 🐞 Debug & Diagnose (`:feature:settings:debug`)
- **Logging Level**: Verbose, Debug, Info, Warn, Error logging configurations.
- **Tracing & System Diagnostics**: Excessive tracing toggle, PRoot environment sanity check, real-time log inspector.

### 8. ℹ️ Über CodeForge Mobile (`:feature:settings:about`)
- **App Metadata**: Build version (`v4.5 Build 2026.09`), PRoot version (`v5.3.1`), architecture (`arm64-v8a`).
- **Licenses & Credits**: Apache 2.0 / MIT licenses, developer credits (Thomas Schmid & Google DeepMind Agentic Coding Team).

---

## Code Reference
- **Source File**: [SettingsHubScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/hub/SettingsHubScreen.kt)
- **Navigation Host**: [CodeForgeNavHost.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/src/main/kotlin/com/codeforge/app/CodeForgeNavHost.kt)
