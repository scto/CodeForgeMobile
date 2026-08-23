Project: CodeForgeMobile Skills
Project Structure
📦 codeforge-skills
📂 codeforge-architecture
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-build-ci
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-compose-theme
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-data-datastore
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-editor-lsp
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-terminal-engine
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-plugins
  📂 agents
    📄 openai.yaml
  📄 SKILL.md
📂 codeforge-git-templates
  📂 agents
    📄 openai.yaml
  📄 SKILL.md

File Contents
codeforge-architecture/agents/openai.yaml
interface: 
 display_name: "CodeForge Architektur & Navigation" 
 short_description: "Navigation durch die Multi-Modul-Architektur von CodeForgeMobile" 
 default_prompt: "Nutze CodeForge Architektur, um zu prüfen, in welches Modul diese Änderung gehört und welche Abhängigkeiten beachtet werden müssen." 

codeforge-architecture/SKILL.md
---
name: codeforge-architecture
description: CodeForgeMobile Projektarchitektur. Hilft bei Modulgrenzen, MVI-Pattern, Dependency-Regeln und Navigation.
---
# CodeForgeMobile Architektur & Navigation

## Wichtige Dateien
- `settings.gradle.kts`: Modulübersicht und Build-Struktur.
- `app/src/main/kotlin/com/codeforge/app/CodeForgeNavHost.kt`: Zentrale Jetpack Compose Navigation.
- `app/src/main/kotlin/com/codeforge/app/CodeForgeApplication.kt`: Hilt Application Entry Point.

## Modul-Struktur & Regeln
Das Projekt folgt einer strengen Multi-Modul Clean Architecture:
- `:app`: Application, NavHost, Hilt Dependency Root.
- `:core:*`: Fundament. `:core:domain` (UseCases, Models), `:core:data` (Repository-Impl), `:core:designsystem` (Theme), `:core:datastore` (Proto/Prefs).
- `:feature:*`: Isolierte UI-Module (MVI-Architektur). **Regel: Ein Feature darf niemals ein anderes Feature importieren!** Kommunikation erfolgt über Navigation-Routen oder Shared ViewModels.
- `:libs:*`: Gekapselte Kern-Engines (Terminal, LSP, Gradle Tooling, Templates).

## MVI Architektur in Features
Jedes Feature (z. B. `:feature:editor`) besteht aus:
1. `Contract.kt`: Definiert `@Immutable data class UiState`, `sealed interface UiEvent` und `UiEffect`.
2. `ViewModel.kt`: `@HiltViewModel` mit `StateFlow` für State und `SharedFlow` für Effects.
3. `Screen.kt`: Compose UI, die ausschließlich auf den State reagiert und Events an das ViewModel feuert.




codeforge-build-ci/agents/openai.yaml
interface: 
 display_name: "CodeForge Build & CI" 
 short_description: "Verwaltung von Gradle, Version Catalogs, ktfmt und GitHub Actions" 
 default_prompt: "Prüfe mit CodeForge Build & CI mögliche Gradle-Probleme oder CI/CD-Pipelines." 

codeforge-build-ci/SKILL.md
---
name: codeforge-build-ci
description: CodeForgeMobile Build-Prozess, Version Catalog, CI/CD Pipelines und Kotlin-Formatierung (ktfmt).
---
# CodeForgeMobile Build & CI

## Wichtige Dateien
- `gradle/libs.versions.toml`: Zentrale Abhängigkeits- und Versionsverwaltung.
- `.github/workflows/`: GitHub Actions Pipelines (z. B. `main-build-test.yml`, `ktfmt-check.yml`).
- `build.gradle.kts`: Root-Build-Script.

## Best Practices & Fakten
- **Abhängigkeiten**: Neue Libraries MÜSSEN in die `libs.versions.toml` eingetragen und via `alias(libs.x.y)` referenziert werden.
- **Code-Formatierung**: Das Projekt nutzt `ktfmt`. Vor Commits sollte `./gradlew ktfmtFormat` ausgeführt werden. Die CI prüft dies (`ktfmt-check.yml`).
- **Build-Befehle**:
 - Debug APK: `./gradlew assembleDebug`
 - Release APK: `./gradlew assembleRelease`
 - Tests ausführen: `./gradlew testDebugUnitTest`

codeforge-compose-theme/agents/openai.yaml
interface: 
 display_name: "CodeForge Compose UI & Theme" 
 short_description: "Entwicklung von Jetpack Compose UI, Material 3 Adaptive und Theming" 
 default_prompt: "Nutze CodeForge Compose UI, um das Theming, adaptive Layouts oder UI-Komponenten anzupassen." 

codeforge-compose-theme/SKILL.md
---
name: codeforge-compose-theme
description: Richtlinien für UI-Entwicklung mit Jetpack Compose, Material 3 (inkl. Adaptive) und Theme-Persistenz.
---
# CodeForgeMobile Compose UI & Theme

## Wichtige Dateien
- `:core:designsystem/src/main/kotlin/com/codeforge/core/designsystem/CodeForgeTheme.kt`: Haupt-Theme mit Support für Dynamic Colors.
- `:core:datastore/src/main/proto/settings.proto`: Definition des Themes im Proto DataStore (`ThemeConfig`).
- `:feature:themebuilder`: UI zur Anpassung von Themes und Farben.

## UI-Prinzipien
- **Material 3**: Verwendung von M3-Komponenten (`androidx.compose.material3`).
- **Adaptive Layouts**: Nutzung von `androidx.compose.material3.adaptive` (z. B. `NavigableListDetailPaneScaffold` im `:feature:welcome` Modul) zur Unterstützung von Tablets und Foldables.
- **Theme-Persistenz**: Theme-Änderungen im `ThemeBuilderViewModel` werden als Protobuf geschrieben und global via `SettingsRepository` abonniert.

## Warnung
Keine harten Farben in Compose verwenden (kein `Color.Red`). Immer `MaterialTheme.colorScheme.*` nutzen.

codeforge-data-datastore/agents/openai.yaml
interface: 
 display_name: "CodeForge Data & DataStore" 
 short_description: "Umgang mit Proto DataStore, Repositories und Kotlin Flow" 
 default_prompt: "Prüfe mit CodeForge Data, wie Daten gespeichert oder aus dem DataStore gelesen werden sollen." 

codeforge-data-datastore/SKILL.md
---
name: codeforge-data-datastore
description: CodeForgeMobile Datenhaltung. Proto DataStore für Settings, Preferences DataStore für Recents, sowie Coroutines/Flow.
---
# CodeForgeMobile Data & DataStore

## Wichtige Dateien
- `:core:datastore/src/main/proto/settings.proto`: Die Single-Source-of-Truth für App-Settings (Theme, Editor, Terminal).
- `:core:datastore/src/main/kotlin/com/codeforge/core/datastore/SettingsRepository.kt`: Kotlin-Wrapper um den Proto-DataStore.
- `:core:data/`: Implementierungen der Domain-Interfaces (z. B. FileSystem, Git, SdkManager).

## Speicher-Strategien
- **Proto DataStore**: Wird für strukturierte Daten verwendet (Editor-Konfigurationen, Theme). Änderungen erfolgen typ-sicher via `.toBuilder().set...build()`.
- **Preferences DataStore**: Wird für flache Listen verwendet, z. B. `RecentProjectsRepositoryImpl` (Serialisierung über Trennzeichen).
- **Dateisystem**: VFS-Abstraktion in `FileSystemRepositoryImpl`. Physische App-Dateien liegen in `context.filesDir` (Plugins, Terminal Rootfs).

Alle Repositories werden als `@Singleton` über Dagger Hilt in `:core:data` bereitgestellt.

codeforge-editor-lsp/agents/openai.yaml
interface: 
 display_name: "CodeForge Editor & LSP" 
 short_description: "Modifikation von Sora-Editor, TreeSitter und JSON-RPC LSP Client" 
 default_prompt: "Analysiere mit CodeForge Editor & LSP diese Änderungen an Textdarstellung oder Language Servern." 

codeforge-editor-lsp/SKILL.md
---
name: codeforge-editor-lsp
description: CodeForgeMobile Editor-Integration (Sora-Editor) und Language Server Protocol (LSP).
---
# CodeForgeMobile Editor & LSP

## Wichtige Dateien
- `:feature:editor/src/main/kotlin/com/codeforge/feature/editor/SoraCodeEditor.kt`: Der `AndroidView`-Wrapper für den Sora-Editor.
- `:feature:editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt`: Verwaltet offene Tabs und Dateizustände.
- `:libs:lsp-client/`: JSON-RPC Implementierung über Stdin/Stdout Streams.

## Architektur
- **Sora-Editor**: Verwendet TextMate-Grammatiken oder TreeSitter für Syntax-Highlighting. Anpassbar über `EditorSettings`.
- **LSP Bridge**: Der `LspClientRepositoryImpl` startet Language Server als Subprozess (meistens verpackt via PRoot). Kommunikation erfolgt via `LspRpcConnection` (Content-Length Header + JSON).
- **Compose Preview**: Der Editor meldet Composable-Funktionen über die `ActiveComposablePreviewBridge` an das `:feature:composepreview` Modul.

codeforge-terminal-engine/agents/openai.yaml
interface: 
 display_name: "CodeForge Terminal & PRoot" 
 short_description: "Entwicklung von Shell, PRoot-Umgebungen und Gradle Tooling" 
 default_prompt: "Prüfe mit CodeForge Terminal & PRoot Änderungen am Distro-Bootstrap oder IPC." 

codeforge-terminal-engine/SKILL.md
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

codeforge-plugins/agents/openai.yaml
interface: 
 display_name: "CodeForge Plugins" 
 short_description: "Modifikation der Plugin-API, JSON-Registries und Archiv-Extraktion" 
 default_prompt: "Prüfe Plugin-Änderungen mit CodeForge Plugins." 

codeforge-plugins/SKILL.md
---
name: codeforge-plugins
description: CodeForgeMobile Plugin System. Verwaltung von ZIP-Erweiterungen und der PluginRegistry.
---
# CodeForgeMobile Plugins

## Wichtige Dateien
- `:libs:plugin-api/src/main/kotlin/com/codeforge/libs/plugin_api/PluginRepositoryImpl.kt`: Entpackt ZIP-Dateien und parst `plugin.json`.
- `:feature:plugins/src/main/kotlin/com/codeforge/feature/plugins/PluginsScreen.kt`: UI zum Importieren via SAF (Storage Access Framework).

## Architektur
- **Aktueller Stand**: Plugins sind ZIP-Archive mit einer `plugin.json` (ID, Name, Version, entryPointClass).
- **Dateisystem**: Plugins werden in `context.filesDir/plugins` gespeichert. Die Registrierung erfolgt in einer lokalen `registry.json` (Klasse `PluginRegistry`).
- **Einschränkung**: Echtes dynamisches Klassenladen (`DexClassLoader`) ist in der Basisstruktur vorbereitet, aber aktuell deaktiviert, bis das Sandbox-Sicherheitsmodell finalisiert ist.

codeforge-git-templates/agents/openai.yaml
interface: 
 display_name: "CodeForge Git & Templates" 
 short_description: "Entwicklung mit JGit und dem Freemarker Template Engine Repository" 
 default_prompt: "Analysiere mit CodeForge Git & Templates diese Versionierungs- oder Generierungs-Logik." 

codeforge-git-templates/SKILL.md
---
name: codeforge-git-templates
description: CodeForgeMobile Projekt-Erstellung (Templates) und Versionsverwaltung (JGit).
---
# CodeForgeMobile Git & Templates

## Git Integration (`:feature:git`, `:core:data`)
- Nutzt reine Java-Implementierung **JGit**. Kein natives `libgit2` (es sei denn, JNI wird explizit gefordert).
- `GitRepositoryImpl` im `:core:data` Modul steuert clone, status, commit, push, pull in Coroutines (`Dispatchers.IO`).
- Clone-Fortschritt (ProgressMonitor) feuert via `Flow`.

## Template Engine (`:libs:template-engine`, `:feature:projectwizard`)
- Basiert auf **Freemarker** (`.ftl` Dateien) + einem JSON-Manifest (`manifest.json`).
- Templates werden direkt aus den App-Assets geladen (`AssetTemplateLoader`).
- Beinhaltet Platzhalter wie `${packageName}` oder `${packagePath}`. Parameter-Bedingungen (`conditionParam`) ermöglichen z. B. optionale Test-Module.
- Der Wizard iteriert über die im Descriptor festgelegten Parameter und leitet diese an die Engine weiter.
