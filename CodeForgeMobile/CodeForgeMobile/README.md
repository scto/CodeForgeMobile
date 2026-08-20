# CodeForge Mobile — generiert aus dem Skill `android-ide-architect`

Dieses Grundgerüst wurde nach der Spezifikation im Attachment (`android-ide-architect` SKILL.md) erzeugt.

## Enthalten (voll ausimplementiert)
- **Root-Setup**: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` mit allen 25 Modulen aus Abschnitt 1.
- **`:core:datastore`**: vollständiges Proto-Schema (`settings.proto`), `AppSettingsSerializer`, Hilt-`DataStoreModule`, `SettingsRepository` (Abschnitt 3).
- **`:core:designsystem`**: `CodeForgeTheme` inkl. Dynamic-Color-Fallback und Custom-Palette-Resolver (Abschnitt 4).
- **`:core:domain`**: `OpenFileUseCase`, `FileSystemRepository`- und `LspClientRepository`-Interfaces.
- **`:feature:editor`**: komplettes MVI-Set (`EditorUiState`/`UiEvent`/`UiEffect` → `EditorViewModel` → `EditorScreen` → `SoraCodeEditor`), exakt nach dem im Skill vorgegebenen Muster (Abschnitt 2 + 7).
- **`:app`**: `CodeForgeApplication`, `MainActivity`, `CodeForgeNavHost` mit Onboarding/Welcome/Editor-Routen.
- Alle übrigen 20 Module (`:feature:*`, `:libs:*`, restliche `:core:*`) besitzen bereits ein korrektes, kompilierendes `build.gradle.kts` inkl. Dependency-Regel (`:feature:*` → nur `:core:*`, keine Feature-Feature-Abhängigkeiten) und leeres `AndroidManifest.xml` — bereit, um gemäß Abschnitt 5, 6, 8, 9 des Skills befüllt zu werden.

## Nächste sinnvolle Schritte
1. `:feature:welcome` (Icon-Button-Grid, `NavigableListDetailPaneScaffold`) und `:feature:onboarding` (IntroPager/Permission/Setup) implementieren — dann in `CodeForgeNavHost` einhängen (Kommentare markieren bereits die Stelle).
2. `:libs:lsp-client` → `LspClientRepository`-Implementierung (JSON-RPC).
3. `:libs:terminal-engine` (PRoot-Bootstrap) + `:libs:gradle-tooling-bridge` (Socket/AIDL).
4. `:libs:template-engine` mit den vier Basis-Templates aus Abschnitt 8.

## Design-Entscheidungen (aus dem Skill übernommen)
- MVI statt MVVM: klare Event-Sealed-Interfaces für testbares Terminal↔UI-Callback-Handling.
- Proto-DataStore statt Preferences-DataStore: Typsicherheit + Migrationspfad für verschachtelten State.
- Gradle-Tooling-Bridge in separatem Prozess geplant: verhindert Classloader-Kollisionen mit dem Gradle-Daemon.
