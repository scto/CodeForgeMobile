# 📊 Aktueller Status

**Version:** 3.0.0 (`versionCode 30000`) · **Stand:** 2026-10-16 · **Module:** 43 · **Kotlin-Dateien:** ~330 · **Strings:** 834 (Englisch Standard, Deutsch `values-de`)

> ✅ **Build:** `./gradlew assembleDebug` läuft laut Inhaber (Stand 2026-10-16, on-device in Termux aarch64) ohne Fehler; die Verifikationsberichte zu Task 08–15 liegen in `docs/*-verification.md`.
> ⚠️ **Noch nicht geprüft:** Verhalten auf dem Gerät (Bootstrap-Installation, W^X unter `targetSdk 28`, Layouts, Tastatur, LSP). Checkliste: `docs/device-test-checklist.md` (28 Testfälle, alle „ungeprüft“). Aufträge: `agy-tasks/README.md`.

Legende: ✅ umgesetzt (gebaut) · 🧪 zusätzlich JVM-getestet · 🟡 teilweise · ⬜ offen

## ✅ Implementierte Features

### Plattform & Build
| Bereich | Status | Details / Doku |
|---|---|---|
| Multi-Modul-Setup (43 Module), Version-Catalog, Gradle 8.14.3, AGP 8.13.2 | ✅ | `docs/build-logic.md` |
| build-logic mit Convention-Plugins (App, Library, Compose, Hilt, Quality, Bootstrap) | ✅ | `build-logic/` |
| Pflichtdateien je Modul (`.gitignore`, `consumer-rules.pro`, `proguard-rules.pro`, Manifest) | ✅ | `docs/module-files-and-app-resources.md` |
| `:app`-Ressourcen (Theme, Farben, Icons, Backup-/Extraktionsregeln, Manifest) | ✅ | dito |
| Zentrale Strings `:core:resources` (834 Texte, zweisprachig: `values/` Englisch, `values-de/` Deutsch; `Res`/`stringRes`, `TestRes`, Prüfskript `scripts/check_string_locales.py`) | ✅ 🧪 | `docs/resources-and-strings.md` |
| Edge-to-Edge, Adaptive Layouts (Compact/Medium/Expanded), Material 3 Expressive | ✅ | `docs/adaptive-edge-to-edge-expressive.md` |
| Repo-Pipeline für Bootstrap + APT-Repository (`build_codeforge_repo.sh`, `apt-stages.conf`) | ✅ | Verifiziert (Task 21, `docs/apt-stages-verification.md`) |

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
| Sora-Editor-Wrapper (Magnifier, Sticky Scroll, Minimap, Rainbow Brackets, Pinch-Zoom, Zeilenumbruch, Inlay-Hints-Anbindung), TextMate + Tree-Sitter (alle 10 Sprachen nativ, Task 02/20), Symbolleiste (Task 19), Bildvorschau | ✅ | `docs/bonsai-sora-app-integration.md`, `docs/restore-lost-editor-features.md` |
| Dateibaum (Bonsai) im Drawer | ✅ | dito |
| Suche & Ersetzen (Datei/Projekt, Regex, Case, Wort) | ✅ 🧪 | `docs/editor-tools-git-drawer.md` |
| Highlighter/Formatter-Logik, Submodule Maker | ✅ 🧪 | `:libs:code-tools` (53 Tests) |
| Git-Panel (Diff, Merge, Commit, Push/Pull, Graph, Stash, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Blame, Hunks, Konflikt-Editor) | ✅ 🧪 | JGit, 47 Tests laufen gegen echte Repos |
| Indexierung, Dependency-Updater, Editor-Overlays (Versions-Hinweis, Farb-Kästchen) | ✅ | `docs/indexing-and-dependency-updater.md` |
| LSP-Client (`LspClientRepositoryImpl`, JSON-RPC) | ✅ 🧪 | voll angebunden über `LspServerResolver` und `LspSessionController` im Editor, verifiziert (Task 23) |
| Compose-Preview | 🟡 | Renderer + Brücke vorhanden, ungebaut |
| **Layout-Designer** (Palette, Vorschau, Baum, Eigenschaften, XML, Undo/Redo, Speichern) | ✅ 🧪 (Logik) | `docs/layout-designer.md` |

### Projekt & Einstellungen
| Bereich | Status | Details / Doku |
|---|---|---|
| Projekt-Wizard (2 Schritte) + Template-Engine, 9 Vorlagen, Ordnerauswahl | ✅ | `docs/project-wizard-and-template-engine.md`, Task 22 (`docs/verification-followups-report.md`) |
| Welcome (Adaptive List-Detail), Settings-Hub (inkl. Extensions-Manager für LSP-Server, ✅ repariert, echte SHA-256, tar.gz/gz, Task 23), Theme-Builder (15 Presets, RGB-Colorpicker, Horizontal Preview), Git-/Editor-/Terminal-Einstellungen | ✅ 🧪 | |
| Plugin-API/-Runtime (Termux-Prefix) | 🟡 | `libs/plugin-api`, `feature/plugins` |

## ⬜ Noch offene Features / Aufgaben

1. **Gerätetests** (Bootstrap aarch64/arm, W^X mit `targetSdk 28`, Layouts, Tastatur, Editor-Features, Wizard): `docs/device-test-checklist.md`, 28 Testfälle ungeprüft.
2. **Erster echter Lauf der APT-Etappen-Pipeline** im Fork `scto/terminal-packages-codeforge` (Skripte verifiziert, `docs/apt-stages-verification.md`); Rezepte für Flutter, TypeScript, jdtls, lemminx, kotlin-lsp liegen noch ungepusht unter `agy-output/packages/` im Fork. Pages-Limit (~1 GB) beachten.
3. ~~**Extensions-Manager reparieren und an den LSP-Client anbinden**~~ → ✅ Erledigt (Task 23, `docs/extensions-manager-fix-report.md`).
4. `:libs:gradle-tooling-bridge` (Socket/AIDL) und Build-Ausführung aus der IDE.
5. Layout-Designer: Drag-and-Drop, echte Constraint-Darstellung, Ressourcen-Auflösung (`@string`, `@color`), `include`/`merge`, ViewModel-Tests.
6. Material-3-Expressive-Komponenten erst, wenn eine stabile material3 ≥ 1.4 in der BOM liegt (Entscheidung Task 22).
7. Play-Store: nicht möglich mit `targetSdk = 28` (nötig für Termux-Binaries ab Android 10); Verteilung über F-Droid/GitHub/Sideload.
8. Code-Style: ktlint-Befunde (Paketname `template_engine`, Generator-Funktionsnamen) – Entscheidung über Umbenennung offen (`docs/verification-followups-report.md`).
9. **Portierung aus lokalem Experimentzweig (`agy-tasks/16-port-local-experiment-features.md`)**:
   - ✅ Item 1: Editor & Image Preview (Minimap, Rainbow Brackets, Pinch-Zoom, Desktop-Shortcuts, Diagnose-UI, `ImageFilePreview`)
   - ✅ Item 2: Dateibaum (theme-adaptive Einrückungslinien, 2-zeilige Dateiinfo, Vollzeilen-Tap, Kontextmenü „Eigenschaften“, Löschdialog)
   - ✅ Item 3: Theme Studio (15 Presets, horizontale Live-Vorschau, RGB-Colorpicker mit 10 Swatches)
   - ✅ Item 4: Extensions-Manager (`:feature:settings`): Katalog mit echten SHA-256 Prüfsummen und Archivtypen korrigiert, Termux-Pakete eingebunden, tar.gz/gz-Extraktion implementiert, an LSP-Client im Editor über LspServerResolver und LspSessionController angebunden (Task 23, `docs/extensions-manager-fix-report.md`)
   - ✅ Item 5: Hintergrundbefehle: vollständig durch `TermuxService` abgedeckt (`ForegroundService` mit Wakelock/Wifilock); `RunCommandWorker` entfällt als redundant (siehe `docs/fluent2-and-background-services-decision.md`)
   - ✅ Item 6: Fluent 2: leichtgewichtiges Token-Set in `:core:designsystem` (`FluentTokens.kt`) eingeführt; 4 Unit-Tests in `FluentTokensTest`
10. Fluent-2-Designrichtung (`FLUENT2_JETPACK_COMPOSE_GUIDE.md`): `FluentTokens` in `:core:designsystem` bereitgestellt, primäres App-Theme bleibt Material 3 (Expressive).
11. Lizenzklärung: Das Projekt ist wegen der Termux-Module **GPLv3** (`LICENSE`); die frühere Angabe „Apache 2.0“ in den READMEs war falsch.

## ⚠️ Risiken

* `targetSdk = 28` ist Voraussetzung für die Ausführbarkeit der Termux-Binaries (Task 11), schließt aber den Play Store aus.
* Der LSP-Pfad ist im Code angebunden und per Unit-Tests verifiziert (Task 23); On-Device-Verhalten mit echten Servern bleibt im Rahmen der Checkliste (`docs/device-test-checklist.md`) zu testen.
* `build_codeforge_repo.sh` erzeugt einen privaten GPG-Schlüssel unter `.gpg/` – nicht einchecken (steht in `.gitignore`). Die früher im Klartext eingecheckte Passphrase gilt als kompromittiert, falls das Repository öffentlich war; Passphrase nur per `CODEFORGE_GPG_PASSPHRASE`.
* Umgebung und W^X: `libs/terminal-engine/BOOTSTRAP.md`.

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

