# CodeForge Mobile — ursprünglicher Scaffold-Überblick (historisch)

> **Hinweis:** Dies ist der frühere Projekt-README aus der Scaffold-Phase (25 Module). Der aktuelle Stand steht in `/README.md`, `/README_DE.md`, `/STATUS.md` und `/CHANGELOG.md`.

# CodeForge Mobile — generiert aus dem Skill `android-ide-architect`

Dieses Grundgerüst wurde nach der Spezifikation im Attachment (`android-ide-architect` SKILL.md) erzeugt.

## Enthalten (voll ausimplementiert)
- **Root-Setup**: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` mit allen 43 Modulen.
- **`:core:datastore`**: vollständiges Proto-Schema (`settings.proto`), `AppSettingsSerializer`, Hilt-`DataStoreModule`, `SettingsRepository` (Abschnitt 3).
- **`:core:designsystem`**: `CodeForgeTheme` inkl. Dynamic-Color-Fallback und Custom-Palette-Resolver (Abschnitt 4).
- **`:core:domain`**: `OpenFileUseCase`, `FileSystemRepository`- und `LspClientRepository`-Interfaces.
- **`:feature:editor`**: komplettes MVI-Set (`EditorUiState`/`UiEvent`/`UiEffect` → `EditorViewModel` → `EditorScreen` → `SoraCodeEditor`), exakt nach dem im Skill vorgegebenen Muster (Abschnitt 2 + 7).
- **`:app`**: `CodeForgeApplication`, `MainActivity`, `CodeForgeNavHost` mit Onboarding/Welcome/Editor-Routen.
- Alle übrigen Module (`:feature:*`, `:libs:*`, restliche `:core:*`) besitzen bereits ein korrektes, kompilierendes `build.gradle.kts` inkl. Dependency-Regel (`:feature:*` → nur `:core:*`, keine Feature-Feature-Abhängigkeiten) und leeres `AndroidManifest.xml` — bereit, um gemäß Abschnitt 5, 6, 8, 9 des Skills befüllt zu werden.

## Nächste sinnvolle Schritte (Stand 2026-10-07)
1. **Erster echter Build:** `./gradlew :app:assembleDebug` – bisher wurde nichts mit Gradle/Android gebaut. Reihenfolge der Prüfaufträge: `agy-tasks/12`, `13`, `14`, `11`, `08`–`10`.
2. Versionen in `gradle/libs.versions.toml` (Kotlin 2.1.21, KSP, Hilt 2.56.2, Compose BOM 2025.09.00) gegen Maven/Google verifizieren (`docs/adaptive-edge-to-edge-expressive.md`).
3. Bootstrap-Pakete für `com.codeforge.app` bauen/hosten (`libs/terminal-engine/BOOTSTRAP.md`); W^X-Ausführbarkeit auf targetSdk 35 auf dem Gerät testen.
4. `:libs:lsp-client` → `LspClientRepository`-Implementierung (JSON-RPC); `:libs:gradle-tooling-bridge` (Socket/AIDL); `:feature:layoutdesigner` ist umgesetzt (`docs/layout-designer.md`), aber ungebaut.

## Design-Entscheidungen (aus dem Skill übernommen)
- MVI statt MVVM: klare Event-Sealed-Interfaces für testbares Terminal↔UI-Callback-Handling.
- Proto-DataStore statt Preferences-DataStore: Typsicherheit + Migrationspfad für verschachtelten State.
- Gradle-Tooling-Bridge in separatem Prozess geplant: verhindert Classloader-Kollisionen mit dem Gradle-Daemon.
