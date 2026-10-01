# 🛠️ CodeForge Mobile

<div align="center">

# 🚀 CodeForge Mobile IDE
### *High-Performance Native Entwicklungsumgebung für Android*

Eine moderne, native Android-IDE mit **Sora Editor**, **Terminal-Engine (Linux Bootstrap)**, **Language Server Protocol (LSP)**, **Real-Time Jetpack Compose Preview** & **Microsoft Fluent 2 Design System**.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-AGP_8.13.1-3DDC84.svg?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.10.01-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/)
[![Design](https://img.shields.io/badge/Design-Microsoft_Fluent_2_%26_M3-0078D4.svg?style=for-the-badge&logo=microsoft)](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/FLUENT2_JETPACK_COMPOSE_GUIDE.md)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge)](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/LICENSE)

[Über das Projekt](#-über-das-projekt) • [Features](#-hauptmerkmale) • [Architektur](#-systemarchitektur) • [Tech Stack](#-tech-stack) • [Erste Schritte](#-erste-schritte) • [Dokumentation](#-dokumentation) • [🇬🇧 English Version](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/README.md)

</div>

---

## 📌 Über das Projekt

**CodeForge Mobile** ist eine vollwertige, hochgradig modularisierte integrierte Entwicklungsumgebung (IDE) für Android-Smartphones, Tablets und Falt-Geräte. Sie bringt die Leistungsfähigkeit und Flexibilität von Desktop-Entwicklungswerkzeugen direkt auf mobile Endgeräte.

Das Projekt folgt strikt den Prinzipien der **Clean Architecture** und des **Modern Android Development (MAD)**. Die Benutzeroberfläche nutzt das **Microsoft Fluent 2** Designsystem gemeinsam mit **Material 3**, um eine elegante, adaptive und hochgradig anpassbare Arbeitsumgebung im Light- und Dark-Mode zu bieten.

---

## ✨ Hauptmerkmale

### ⚡ 1. Sora Code Editor Ultimate Setup (`:feature:editor`)
* 🗺️ **Minimap Overview**: Visuelle Code-Übersicht für schnelle Navigation in großen Quelldateien.
* 📌 **Sticky Scroll**: Behält Funktions- und Klassendeklarationen am oberen Editorrand fixiert.
* 🌈 **Rainbow Brackets & Inlay Hints**: Farbliche Hervorhebung von Klammerpaaren und dynamische Typ-Hinweise.
* 🔤 **Syntax Highlighting Engine**: Unterstützung für Kotlin, Java, Python, C/C++, XML, JSON, AIDL, Logcat via Tree-Sitter & Monarch.
* 🔍 **Gesten & Tastatur-Support**: Pinch-to-Zoom zur Schriftgrößenanpassung, Lupe (Magnifier), Diagnose-UI und Desktop-Tastaturkürzel.
* 🛠️ **Code Formatting & Autovervollständigung**: Integrierte Formatierung und intellektuelle Codevervollständigung.

### 💻 2. Integrierte Terminal Engine (`:feature:terminal` / `:libs:terminal-engine`)
* 🐚 **Nyamux Terminal Core**: Native Emulation basierend auf `nyamux-terminal.jar`.
* 🐧 **Linux-Umgebung & Termix Engine**: Linux Distro Bootstrap, Rootfs Downloader und automatisierter JDK Installer.
* ⚙️ **Hintergrund-Ausführung**: Robuste Befehlsausführung über `WorkManager` & `TerminalForegroundService`.

### 🎨 3. UI Preview & Visual Layout Designer (`:feature:composepreview` & `:feature:layoutdesigner`)
* 👁️ **Jetpack Compose Live Preview**: Rendern von `@Composable` Komponenten in Echtzeit auf dem mobilen Gerät.
* ✏️ **Visual Drag & Drop Designer**: Visuelles Erstellen von Layouts mit Code-Generierung.

### 🚀 4. Android Studio Style Project Wizard (`:feature:projectwizard` & `:libs:template-engine`)
* 🧙 **2-Schritt Assistent**: Schnelle Erstellung neuer Android- und Kotlin-Projekte mit konfigurierbaren Paketnamen, SDK-Versionen und Modulstrukturen.
* 📋 **Projekt-Templates**: Vorgefertigte Vorlagen (Compose App, Clean Architecture, Empty Activity, Multi-Module Engine etc.).
* 📁 **SAF Storage Picker**: Nahtlose Ordnerauswahl über das Android Storage Access Framework.

### 🔍 5. Language Server Protocol (LSP) & Extensions (`:libs:lsp-client` & `:feature:settings`)
* 🔌 **LSP Client Engine**: Integrierter Eclipse `lsp4j` Client für Auto-Vervollständigung, Go-to-Definition, Symbol-Suche und Diagnostics.
* 🧩 **Erweiterungs-Manager**: Download, SHA-256 Verifizierung und Verwendungsprüfung von Sprachserver-Paketen (Kotlin, Java, Python, C/C++).

### 🌿 6. Native Git Versionskontrolle (`:feature:git`)
* 🐙 **Eclipse JGit Integration**: Branches verwalten, Commits erstellen, Remotes anbinden, Pull/Push-Operationen und Dateistatus-Anzeige.

### 🎨 7. Theme Studio & Microsoft Fluent 2 Integration (`:feature:themebuilder`)
* 🎭 **15 Integrierte Presets**: Monokai, Darcula, GitHub Light, VS Code Dark+, Cyberpunk Neon, Ocean u.v.m.
* 🎨 **RGB Color Picker**: Vollständig anpassbare Markenfarben und Live-Farbkarten-Vorschau.
* 🌓 **Adaptive Dark/Light Themes**: Nahtlose Unterstützung für System-Dunkelmodus und Barrierefreiheit.

---

## 🏗️ Systemarchitektur

CodeForge Mobile ist in über 20 spezialisierte Gradle-Module strukturiert, was für hohe Wartbarkeit, schnelle Build-Zeiten und klare Schnittstellen sorgt:

```text
CodeForgeMobile/
├── app/                        # App-Einstiegspunkt, Hilt Setup & App Navigation
├── core/                       # Zentrale Kern-Module & Shared Logic
│   ├── common/                 # Utilities, Coroutine Dispatchers & Extensions
│   ├── data/                   # Repository Implementierungen & Datenquellen
│   ├── datastore/              # Protobuf DataStore (AppSettings, EditorConfig)
│   ├── designsystem/           # Fluent 2 & Material 3 Theme System
│   ├── domain/                 # Business-Logik, UseCases & Domain-Modelle
│   ├── navigation/             # Navigation Routes & Destinations
│   ├── resources/              # Centralized String Resources & ResGetter Utility
│   ├── testing/                # Test-Utilities & Mocks
│   └── ui/                     # Wiederverwendbare Compose UI Komponenten
├── feature/                    # Feature-Module (UI, State & ViewModel)
│   ├── composepreview/         # Jetpack Compose Live Renderer
│   ├── editor/                 # Sora Editor Ultimate Setup
│   ├── filetree/               # Bonsai Tree-View File Explorer
│   ├── git/                    # Git Integration & Status UI
│   ├── layoutdesigner/         # Drag & Drop UI Layout Builder
│   ├── onboarding/             # Quick Setup & Einführung
│   ├── plugins/                # Plugin-Verwaltung
│   ├── projectwizard/          # Android Studio-Style Project Creator
│   ├── sdkmanager/             # Toolchain & SDK Manager
│   ├── settings/               # Einstellungen & LSP Extension Manager
│   ├── terminal/               # Linux Terminal & Console UI
│   ├── themebuilder/           # Theme Studio & Color Picker
│   └── welcome/                # IDE Dashboard & Welcome Screen
├── libs/                       # Standalone Bibliotheken & Engine Bridges
│   ├── gradle-tooling-bridge/  # Integration der Gradle Tooling API
│   ├── lsp-client/             # Language Server Protocol Client (LSP4J)
│   ├── plugin-api/             # Schnittstellen für Entwickler-Plugins
│   ├── template-engine/        # Code- & Projekt-Generierung (Freemarker)
│   └── terminal-engine/        # Terminal Emulation & Native JNI Layer
└── examples/                   # Beispiel-Plugins für LSP (Kotlin & Java)
```

---

## 🛠️ Tech Stack & Bibliotheken

| Kategorie | Technologie / Bibliothek | Version | Beschreibung |
| :--- | :--- | :--- | :--- |
| **Sprache** | Kotlin | `2.2.21` | Coroutines, Flow & Serialization |
| **Build System** | Gradle / AGP | `9.5.1` / `8.13.1` | KSP (`2.2.21-2.0.5`), KtLint (`14.2.0`) |
| **UI Framework** | Jetpack Compose | `2024.10.01` (BOM) | Material 3 & Adaptive Navigation |
| **Design System** | Microsoft Fluent 2 | Custom Tokens | Fluent Controls & Design Tokens |
| **Code Editor** | Rosemoe Sora-Editor | `0.24.6` | Tree-Sitter (`4.3.2`) & Monarch (`1.0.3`) |
| **Dependency Injection** | Dagger Hilt | `2.57.1` | Multi-Modul Dependency Injection |
| **Persistence** | Proto DataStore | `1.1.1` | Protobuf (`4.35.1`) Typsichere Einstellungen |
| **Terminal Engine** | Nyamux / JNI | Prebuilt JAR | Terminal Session, Termix Rootfs & JDK Installer |
| **Git Client** | Eclipse JGit | `6.10.0` | Native Git-Befehle in Pure Java |
| **LSP** | Eclipse LSP4J | `1.0.0` | Language Server Protocol Client |
| **File Tree** | Bonsai | `1.2.0` | Jetpack Compose High-Performance Tree View |

---

## 🚀 Erste Schritte

### Voraussetzungen
* **Android Studio**: Ladybug (2024.2.1) oder neuer.
* **JDK**: OpenJDK 17 oder JDK 21.
* **Min. SDK**: Android 6.0 (API 23+), empfohlen Android 10+ (API 29+).

### Projekt bauen

```bash
# Repository klonen
git clone https://github.com/your-org/CodeForgeMobile.git
cd CodeForgeMobile

# Debug APK kompilieren
./gradlew assembleDebug

# Code Quality & Linter ausführen
./gradlew ktlintCheck
```

---

## 📚 Dokumentation & Referenzen

* 📊 [STATUS.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/STATUS.md) – Detaillierter Entwicklungs- & Komponenten-Status.
* 🎨 [FLUENT2_JETPACK_COMPOSE_GUIDE.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/FLUENT2_JETPACK_COMPOSE_GUIDE.md) – Fluent 2 UI Design-Leitfaden.
* 📜 [CHANGELOG.md](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/CHANGELOG.md) – Chronologisches Änderungsprotokoll.
* ⚙️ [settings.gradle.kts](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/settings.gradle.kts) – Deklaration aller 20+ Gradle-Module.

---

## 📄 Lizenz

Dieses Projekt steht unter der **Apache 2.0 Lizenz**. Details siehe [LICENSE](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/LICENSE).

---

<div align="center">
  <sub>Erstellt mit ❤️ für die mobile Entwickler-Community.</sub>
</div>