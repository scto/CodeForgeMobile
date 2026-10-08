# 📊 Aktueller Status

**Version:** 3.0.0 (`versionCode 30000`) · **Stand:** 2026-10-08 · **Module:** 43 · **Kotlin-Dateien:** ~330 · **JVM-Tests:** 176 `@Test` in 22 Testdateien

> ⚠️ **Wichtig:** Der gesamte Stand wurde in einer Umgebung ohne Android-SDK/Maven-Zugriff geschrieben. **Es gab bisher keinen `./gradlew`-Lauf.**
> Verifiziert ist nur: reine JVM-Logik (Git/JGit, `:libs:code-tools`, Layout-Modell/XML/Scanner; zusammen mit dem Kotlin-Compiler direkt ausgeführt) und die Kotlin-Syntax aller Quelldateien.
> Compose-UI, Hilt-Graph, Manifeste, Ressourcen und angehobene Abhängigkeitsversionen sind **ungeprüft**. Prüfaufträge: `agy-tasks/README.md`.

Legende: ✅ umgesetzt (ungebaut) · 🧪 zusätzlich JVM-getestet · 🟡 teilweise · ⬜ offen

## ✅ Implementierte Features

### Plattform & Build
| Bereich | Status | Details / Doku |
|---|---|---|
| Multi-Modul-Setup (43 Module), Version-Catalog, Gradle 8.14.3, AGP 8.13.2 | ✅ | `docs/build-logic.md` |
| build-logic mit Convention-Plugins (App, Library, Compose, Hilt, Quality, Bootstrap) | ✅ | `build-logic/` |
| Pflichtdateien je Modul (`.gitignore`, `consumer-rules.pro`, `proguard-rules.pro`, Manifest) | ✅ | `docs/module-files-and-app-resources.md` |
| `:app`-Ressourcen (Theme, Farben, Icons, Backup-/Extraktionsregeln, Manifest) | ✅ | dito |
| Zentrale Strings `:core:resources` (~720 Texte, `Res`/`stringRes`, `TestRes`) | ✅ 🧪 | `docs/resources-and-strings.md` |
| Edge-to-Edge, Adaptive Layouts (Compact/Medium/Expanded), Material 3 Expressive | ✅ | `docs/adaptive-edge-to-edge-expressive.md` |
| Repo-Pipeline für Bootstrap + APT-Repository (`build_codeforge_repo.sh`) | ✅ | Passphrase nur noch per Umgebungsvariable |

### Terminal & Umgebung
| Bereich | Status | Details / Doku |
|---|---|---|
| Termux-Vendoring (`termux-emulator/-view/-shared/-app`), GPLv3 | ✅ | `docs/sub/TERMUX-PORTING.md`, `docs/sub/NOTICE.md` |
| Terminal-Feature auf Termux-Shell (`TerminalSessionRepositoryImpl`) | ✅ | `libs/terminal-engine/BOOTSTRAP.md` |
| `codeforge-env` (JDK, SDK, CMake, NDK; `--machine`-Protokoll) | ✅ | `agy-tasks/11-…` |
| Onboarding: Intro → Berechtigungen → Setup → Terminal mit Autostart | ✅ | `feature/onboarding` |
| SDK-Manager (GUI über `codeforge-env`) | ✅ | `feature/sdkmanager` |

### Editor & Werkzeuge
| Bereich | Status | Details / Doku |
|---|---|---|
| Sora-Editor-Wrapper (Magnifier, Sticky Scroll, Zeilenumbruch, Inlay-Hints-Anbindung), TextMate/Tree-Sitter-Gerüst | 🟡 | `docs/bonsai-sora-app-integration.md`; Grammars/Native-Libs fehlen (`agy-tasks/01`, `02`) |
| Dateibaum (Bonsai) im Drawer | ✅ | dito |
| Suche & Ersetzen (Datei/Projekt, Regex, Case, Wort) | ✅ 🧪 | `docs/editor-tools-git-drawer.md` |
| Highlighter/Formatter-Logik, Submodule Maker | ✅ 🧪 | `:libs:code-tools` (53 Tests) |
| Git-Panel (Diff, Merge, Commit, Push/Pull, Graph, Stash, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Blame, Hunks, Konflikt-Editor) | ✅ 🧪 | JGit, 47 Tests laufen gegen echte Repos |
| Indexierung, Dependency-Updater, Editor-Overlays (Versions-Hinweis, Farb-Kästchen) | ✅ | `docs/indexing-and-dependency-updater.md` |
| LSP-Client (`LspClientRepositoryImpl`, JSON-RPC) | 🟡 | implementiert, nicht gegen echten Server geprüft |
| Compose-Preview | 🟡 | Renderer + Brücke vorhanden, ungebaut |
| **Layout-Designer** (Palette, Vorschau, Baum, Eigenschaften, XML, Undo/Redo, Speichern) | ✅ 🧪 (Logik) | `docs/layout-designer.md` |

### Projekt & Einstellungen
| Bereich | Status | Details / Doku |
|---|---|---|
| Projekt-Wizard (2 Schritte) + Template-Engine, 9 Vorlagen | ✅ | `docs/project-wizard-and-template-engine.md` |
| Welcome (Adaptive List-Detail), Settings-Hub, Theme-Builder (5 Presets), Git-/Editor-/Terminal-Einstellungen | ✅ | |
| Plugin-API/-Runtime (Termux-Prefix) | 🟡 | `libs/plugin-api`, `feature/plugins` |

## ⬜ Noch offene Features / Aufgaben

1. **Erster Build** (`./gradlew :app:assembleDebug`) und Behebung der zu erwartenden Compile-Fehler (Compose-/Hilt-/Icon-/API-Details). → `agy-tasks/12`, `13`, `14`, `15`.
2. **Versionen verifizieren** (Kotlin 2.1.21, KSP, Hilt 2.56.2, BOM 2025.09.00, adaptive 1.1.0; evtl. höheres `compileSdk` für Expressive). → `agy-tasks/14`.
3. **Bootstrap-Release** für `com.codeforge.app` bauen/hosten (`build_codeforge_repo.sh`, Fork `terminal-packages-codeforge`), Checksummen eintragen. → `agy-tasks/04`.
4. **Ausführbarkeit** (W^X) von Binaries unter `files/usr` auf dem Gerät testen (targetSdk 35). → `agy-tasks/11`.
5. **TextMate-Grammars/Themes** und **Tree-Sitter-Native-Libs** (C, Bash, CMake, TOML, YAML) beschaffen. → `agy-tasks/01`, `02`.
6. `:libs:gradle-tooling-bridge` (Socket/AIDL) und Build-Ausführung aus der IDE.
7. Layout-Designer: Drag-and-Drop, echte Constraint-Darstellung, Ressourcen-Auflösung (`@string`, `@color`), `include`/`merge`, ViewModel-Tests.
8. Material-3-Expressive-Komponenten (z. B. `LoadingIndicator`) einsetzen; Übersetzungen (`values-en`) nur für den Wizard vorhanden.
9. **Nicht portierte Features aus dem lokalen Experimentzweig** (Minimap, Rainbow Brackets, Pinch-Zoom/PC-Navigation, Extensions-Manager inkl. SHA-256-Prüfung, 15 Theme-Presets mit Colorpicker, Datei-Eigenschaften/Löschdialog im Dateibaum, ImageFilePreview, `TerminalForegroundService`/`RunCommandWorker`). → `agy-tasks/16-port-local-experiment-features.md`.
10. Fluent-2-Designrichtung (`FLUENT2_JETPACK_COMPOSE_GUIDE.md`): bisher nur als Leitfaden, im Code gilt Material 3 (Expressive).
11. Lizenzklärung: Das Projekt ist wegen der Termux-Module **GPLv3** (`LICENSE`); die frühere Angabe „Apache 2.0“ in den READMEs war falsch.

## ⚠️ Risiken

* Alle Punkte unter „Plattform & Build“ sind ungebaut; rechne mit einer Runde Compile-Fehler.
* `build_codeforge_repo.sh` erzeugt einen privaten GPG-Schlüssel unter `.gpg/` – nicht einchecken (steht jetzt in `.gitignore`). Die früher im Klartext eingecheckte Passphrase gilt als kompromittiert, falls das Repository öffentlich war.
* Hinweise zur Termux-Umgebung und W^X: `libs/terminal-engine/BOOTSTRAP.md`.

---

# 🗂️ Historie: Notizen aus dem lokalen Experimentzweig

> Die folgenden Abschnitte stammen aus dem ursprünglichen `STATUS.md` (Termix/Nyamux-Linie mit Rootfs). Sie beschreiben
> Reparaturen an Code, der in diesem Repository-Stand **nicht** vorliegt (z. B. `nyamux-terminal.jar`, `DistroBootstrapRepository`, `RootfsDownloader`),
> und sind nur als Referenz für das Portieren (`agy-tasks/16`) aufgehoben. Der Modulname `:core:resourcess` war ein Tippfehler – gebaut wird `:core:resources`.

# 📊 Aktueller Status

## 📁 1. Dateidokumentation (`docs/`)
* ✅ **`core_resourcess_and_termix_resolution.md`**: Vollständige Dokumentation der Modulzentralisierung `:core:resources (damals `resourcess`)` sowie der Wiederherstellung der Repository-Schnittstellen und des Termix-Moduls.
* ✅ **`sora_editor_ultimate_setup_implementation.md`**: Detaillierte Dokumentation des Sora-Editor Ultimate Setups (Pinch-Zoom, PC-Navigation, Minimap, StickyScroll, Rainbow Brackets, Lupe, CodeFormatter-Fallback & Mipmap/VectorDrawable Renderer).

## 📦 2. Wiederhergestellte Komponenten & Module
* ✅ **`:core:resources (damals `resourcess`)`**: Erstellt, in `settings.gradle.kts` eingebunden, Strings in `strings.xml` zentralisiert, `androidx.core` / `androidx.annotation` / `plurals` eingebunden und erfolgreich kompiliert.
* ✅ **Termix**: Prebuilt `nyamux-terminal.jar` eingebunden, `DistroBootstrapRepository`, `TerminalSessionRepository`, `RootfsDownloader`, `JdkInstaller`, `AndroidRepoCrawler`, `DistroBootstrapRepositoryImpl` wiederhergestellt und erfolgreich kompiliert.
* ✅ **`:feature:composepreview`**: Fehlende Domain-Modelle (`ComposableCandidate`, `PreviewRenderResult`, `ComposePreviewRenderer`, `ComposeViewBitmapRenderer`) wiederhergestellt.


# 📋 Status & Erklärungs-Übersicht

## ❓ Was ist genau passiert? (Warum gab es so viele Fehler?)

> **ℹ️ Architektur-Kontext**
> Das Projekt MobileIDE ist eine sehr große Android-Architektur mit über 20 Gradle-Modulen (`:app`, `:core:resources (damals `resourcess`)`, `:core:datastore`, `:core:domain`, Termix, `:libs:template-engine`, `:feature:editor`, `:feature:onboarding` etc.).

### Die Fehler stammten aus folgenden Hauptgründen:

1. ⚠️ **Vollständige Entkopplung & Zerstückelung von Untermodulen**: Mehrere Untermodule (wie Termix und `:libs:template-engine`) besaßen fehlende Binär-Bibliotheken oder unvollständige Code-Dateien (z.B. fehlende `nyamux-terminal.jar` für die Terminal-Kompilierung).
2. ⚠️ **Aktualisierung des Sora Editors auf v0.24.6**: Sora Editor 0.24.6 nutzt geänderte Methodennamen und Typen.
3. ⚠️ **Erstellung des `:core:resources (damals `resourcess`)`-Moduls**: Das Erstellen des zentralen `:core:resources (damals `resourcess`)`-Moduls zur Auslagerung aller hartkodierten Strings erforderte Anpassungen in fast allen Features.
4. ⚠️ **Fehlende Typen & Gradle-Task-Reihenfolgen (KSP vs. Protobuf)**: Das Proto-Plugin generiert Java-Klassen aus `settings.proto`. Der KSP-Symbol-Processor (für Hilt-Dependency-Injection) lief vor der Protobuf-Generierung und fand die generierten Proto-Klassen (`AppSettings`, `DebugConfig`, etc.) noch nicht.

---

# 🔧 Was wurde bereits repariert?

* ✅ **1. Zentrales `:core:resources (damals `resourcess`)` Modul**: Vollständig eingerichtet (`res/strings.xml`) und in `:core:ui` und `:app` eingebunden.
* ✅ **2. Termix-Modul**: Pre-built JAR (`nyamux-terminal.jar`) integriert, alle JNI-, Session- und Repositorien-Fehler behoben.
* ✅ **3. Sora Editor Ultimate Setup (`:feature:editor`)**: Minimap, StickyScroll, Rainbow Brackets, AutoCompletion, Diagnostic UI und Formatting Fallback sind vollständig angepasst und kompilieren fehlerfrei.
* ✅ **4. Onboarding & Template-Engine (`:feature:onboarding`, `:libs:template-engine`)**: `material-icons-extended` Abhängigkeit ergänzt, Tippfehler bereinigt und Template-Code-Generatoren vervollständigt.
* ✅ **5. Datastore & Protobuf (`:core:datastore`)**: Inkludiert nun die Pfade der generierten Proto-Dateien für KSP.

---

