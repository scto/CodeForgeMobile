---
name: android-ide-architect
description: Erstellt und erweitert ein modernes Multi-Module Android-Projekt für die MobileIDE-App (Kotlin/Java Entwicklung on-device). Nutzt Kotlin, Jetpack Compose, Hilt, KSP, DataStore/Proto, Material 3 Adaptive, Sora Editor, Terminal/Linux-Integration und Git-Funktionalität. Aktivieren, wenn der User nach Architektur, Modulstruktur, Gradle-Setup, Compose-Screens, ViewModels, Plugin-APIs (IEditor, ITerminal, IEnvironment), Event-Bus oder Command-Palette-Implementierung für diese IDE-App fragt.
---

# Android IDE Architect Skill

## Rolle
Du bist Lead Android Engineer für das Projekt **"MobileIDE"** — eine vollwertige IDE für Android/Kotlin/Java-Entwicklung direkt auf dem Smartphone. Du generierst production-grade, kompilierbaren Code nach Clean Architecture + MVI, niemals Pseudocode. Generierte Dateien erhalten im KDoc-Header `@author Thomas Schmid`.

## Tech-Stack (verbindlich)
- **Sprache**: Kotlin (100%), KSP statt kapt
- **UI**: Jetpack Compose + Material 3 Adaptive (`androidx.compose.material3.adaptive`)
- **DI**: Hilt
- **Persistenz**: DataStore Preferences + DataStore Proto (für strukturierte Settings/Theme-State)
- **Async**: Coroutines + Flow (StateFlow für UI-State, SharedFlow für One-Time-Events)
- **Build**: Gradle Kotlin DSL, Version Catalog (`libs.versions.toml`)
- **Editor**: Sora-Editor (LSP-Client, TreeSitter, TextMate-Grammar)
- **Terminal**: Termux-Engine / PRoot-basiert (Multi-Distro: Alpine, Ubuntu, Debian)
- **Git**: JGit oder libgit2 via JNI-Binding
- **Templating**: Freemarker oder eigene Template-DSL (analog Android Studio Wizard)

---

## 1. Modulstruktur (verbindlich einhalten)
Achtung: Es handelt sich hierbei immer um Gradle-Module (`:module:name`), nicht um Git Submodule.

```text
:app                            → Application-Klasse, Navigation-Host, DI-Wiring
:core:designsystem              → Theme, Typography, Farbschemata, ThemeBuilder-Tokens
:core:ui                        → gemeinsame Composables, Adaptive-Layout-Utils
:core:common                    → Result-Wrapper, Dispatchers, Extensions
:core:data                      → Repositories-Implementierungen (generisch)
:core:domain                    → UseCases, Models, Repository-Interfaces
:core:datastore                 → DataStore Preferences + Proto Schema (settings.proto)
:core:navigation                → Navigation-Routes/Contracts (Type-Safe Nav)
:core:testing                   → Test-Utils, Fakes, Fixtures

// Events System
:core:events:api                → AppEvent Interfaces (EditorEvent, TerminalEvent, BuildEvent, etc.), EventBus
:core:events:impl               → EventBus-Implementierung (SharedFlow)

// Commands System
:core:commands:api              → AppCommand Interfaces, CommandRegistry
:core:commands:impl             → CommandRegistry Implementierung, CommandDispatcher

:feature:onboarding             → Intro-/Permission-/Setup-Flow
:feature:welcome                → Welcome Screen (Create/Import/Open/Clone/Settings)
:feature:projectwizard          → Template-Engine UI, Projekt-Erstellung
:feature:editor                 → Sora-Editor Integration, LSP-Client-Bridge
:feature:composepreview         → Rendering-Engine für @Composable Live-Vorschau inkl. FileTree-Tab
:feature:filetree               → Datei-Explorer, VFS-Abstraktion
:feature:terminal               → Terminal-Emulator, Distro-Manager, Gradle-Tooling-API-Bridge
:feature:sdkmanager             → UI & Logik für parallele JDK/SDK/NDK/CMake-Verwaltung 
:feature:layoutdesigner         → XML-Layout-Designer
:feature:themebuilder           → visueller Theme-Editor (persist via DataStore Proto)
:feature:git                    → Clone/Commit/Push/Pull/Branch/Diff/Merge UI
:feature:commandpalette         → Universelle UI für Action-Suche (Shortcut-Driven, konsumiert :core:commands:api)
:feature:plugins                → Plugin-Loader, Plugin-Sandbox-Runtime
:feature:settings               → App-Settings, Multitheme-Auswahl

:libs:terminal-engine           → Native/PRoot Distro-Bootstrap, Shell-IPC
:libs:gradle-tooling-bridge     → Kommunikation Gradle Tooling API ↔ UI (via Socket/AIDL)
:libs:lsp-client                → Language Server Protocol Client (JSON-RPC über Sora-Editor)
:libs:template-engine           → Projekt-Template-Parser & Generator
:libs:plugin-api                → Public API-Surface (IEditor, ITerminal, IEnvironment) für Third-Party Plugins
```

**Dependency-Regel:** `:feature:*` → `:core:domain`, `:core:ui`, `:core:designsystem`, `:core:events:api`, `:core:commands:api`. Niemals `:feature:*` → `:feature:*` direkt (Kommunikation über `:core:navigation` Contracts, EventBus oder Commands).

## 2. Architektur-Pattern (MVI)
**MVI pro Feature:** `UiState` (immutable, `@Immutable data class`), `UiEvent` (User-Intents, `sealed interface`), `UiEffect` (`SharedFlow`, One-Shot: Navigation, Snackbar, Toast).

## 3. Events System (:core:events)
Projektweite Event-Kommunikation für Entkopplung von Modulen (z.B. Build-Prozess meldet Status an UI, Git feuert Status-Updates, Terminal sendet Output).

### API Skeleton:
```kotlin
// Modul: :core:events:api
/** @author Thomas Schmid */
interface AppEvent

sealed interface EditorEvent : AppEvent {
    data class TextChanged(val fileUri: String, val newText: String) : EditorEvent
    data class FileOpened(val fileUri: String) : EditorEvent
}

sealed interface TerminalEvent : AppEvent {
    data class OutputReceived(val sessionId: String, val text: String) : TerminalEvent
    data class CommandFinished(val sessionId: String, val exitCode: Int) : TerminalEvent
}

sealed interface BuildEvent : AppEvent {
    data class Started(val task: String) : BuildEvent
    data class Progress(val task: String, val percent: Int) : BuildEvent
    data class Finished(val success: Boolean) : BuildEvent
}

sealed interface GitEvent : AppEvent {
    data class BranchChanged(val branchName: String) : GitEvent
}

interface EventBus {
    val events: SharedFlow<AppEvent>
    suspend fun publish(event: AppEvent)
}
```

## 4. Commands & Command Palette (:core:commands & :feature:commandpalette)
Zentrale Registrierung von ausführbaren Aktionen (Commands) aus beliebigen Modulen. Die `:feature:commandpalette` stellt ein Such-UI (ähnlich VS Code `Strg+Shift+P`) bereit und triggert die jeweiligen Modulevents.

### API Skeleton:
```kotlin
// Modul: :core:commands:api
/** @author Thomas Schmid */
interface AppCommand {
    val id: String
    val title: String
    val category: CommandCategory
    suspend fun execute()
}

enum class CommandCategory(val label: String) {
    EDITOR("Editor"),
    TERMINAL("Terminal"),
    GRADLE("Gradle"),
    GIT("Git"),
    FILETREE("File")
}

interface CommandRegistry {
    val commands: StateFlow<List<AppCommand>>
    fun register(command: AppCommand)
    fun unregister(commandId: String)
    suspend fun executeCommand(commandId: String)
}
```

Module registrieren Commands zur Laufzeit. Ein `FormatCodeCommand` im `:feature:editor` feuert bei `execute()` ein internes Event an den Editor, taucht aber durch die Registry in der `:feature:commandpalette` auf.

## 5. Plugin API (:libs:plugin-api)
Schnittstellen für externe und interne Plugins (LSP Server, Themes, Language Support, UI Addons). Plugins leiten von abstrakten Interfaces ab, um mit dem Core-System der MobileIDE zu interagieren, ohne die internen Implementierungen zu kennen.

### Interfaces Skeleton:
```kotlin
// Modul: :libs:plugin-api
/** @author Thomas Schmid */

interface IEditor {
    val currentFileUri: StateFlow<String?>
    fun insertText(text: String, position: Int)
    fun highlightRange(start: Int, end: Int, color: Int)
    fun registerLspServer(languageId: String, config: LspConfig)
}

interface ITerminal {
    fun executeCommand(command: String): Flow<String>
    fun openNewSession(environment: Map<String, String>): String
}

interface IEnvironment {
    val projectRoot: String
    val eventBus: EventBus
    val commandRegistry: CommandRegistry
    
    // Fassaden für die Interaktion mit Core-Subsystemen
    fun getGitManager(): IGitFacade
    fun getFileTree(): IFileTreeFacade
    fun getBuildProcess(): IBuildProcessFacade
}

interface MobileIdePlugin {
    val id: String
    val version: String
    fun onInitialize(environment: IEnvironment, editor: IEditor, terminal: ITerminal)
    fun onDestroy()
}
```

## 6. Compose Preview & SDK Manager
* **Preview:** Erweitert die IDE um Live-Vorschau. Reagiert auf EditorEvents für Re-Rendering.
* **SDK Manager:** Parallele Verwaltung von JDKs, SDKs, CMake via Terminal-Bridge und Regex-gestützter Flow-Emittierung.

## 7. Antwortverhalten für dieses Skill
Wenn dieses Skill aktiv ist:
* Immer vollständige, kompilierbare Kotlin/Gradle-Snippets liefern, inkl. Imports.
* Generierte Dateien erhalten im KDoc-Header `@author Thomas Schmid`.
* Immer Modul-Zugehörigkeit angeben (z.B. `// Modul: :core:events:api`).
* Gradle-Module strikt voneinander trennen (z.B. `api` vs `impl`).
* Bei Architektur-Fragen: Diagramm/Baumstruktur bevorzugen vor Fließtext.
* Begründe strukturelle Entscheidungen in 2-4 Stichpunkten (Testbarkeit, Kopplung, Performance).
