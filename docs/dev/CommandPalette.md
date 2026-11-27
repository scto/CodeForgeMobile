# Befehlspalette

Die Befehlspalette (Editor, Strg+Umschalt+P bzw. doppelt Umschalt) ist in zwei Module aufgeteilt:

| Modul | Inhalt |
|---|---|
| `:libs:command-palette-api` | Verträge: `PaletteItem`, `PaletteAction`, `PaletteCategory`, `PaletteContext`, `CommandPaletteState`, `PaletteProvider`, `CommandPalette` |
| `:libs:command-palette-impl` | ein Anbieter je Bereich, Sammeln (`CommandPaletteImpl`), unscharfer Filter, Hilt-Modul |

`:feature:editor` hängt nur von der API ab (Oberfläche `CommandPaletteSheet`, Ausführen der Einträge), `:app` bindet
die Implementierung ein.

## Bereiche und Anbieter

| Bereich (`PaletteCategory`) | Anbieter | Quelle |
|---|---|---|
| Umbau | `RefactorPaletteProvider` | eingebaut (Schnellkorrektur, Refactoring, Umbenennen, String auslagern) |
| Navigation | `NavigationPaletteProvider` | eingebaut (Definition, Typ, Implementierung, Verwendungen, Gliederung, Zeile) |
| Code | `CodePaletteProvider` | eingebaut (Imports, alles korrigieren, Hover, Signatur, Formatieren) |
| Editor | `EditorPaletteProvider`, `IdeCommandPaletteProvider(EDITOR)` | eingebaut (Suchen, Speichern, Falten), `cfm.editor.*` |
| Sprachserver | `LspPaletteProvider` | Sitzungen, `workspace/executeCommand` |
| Git | `IdeCommandPaletteProvider(GIT)` | `cfm.git.*`, `cfm.navigate.git` |
| Dateibaum | `IdeCommandPaletteProvider(FILE_TREE)` | `cfm.file.*`, `cfm.fileTree.*` |
| Build & Ausführen | `IdeCommandPaletteProvider(BUILD_RUN)` | `cfm.build.*`, `cfm.run.*`, `cfm.test.*`, `cfm.terminal.*`, Ausführen-/Testeinträge der Plugins |
| Agent & KI | `AgentAiPaletteProvider`, `IdeCommandPaletteProvider(AGENT_AI)` | KI-Assistent, `cfm.agent.*`, `cfm.ai.*` |
| IDE | `IdeCommandPaletteProvider(IDE)` | übrige `cfm.*` (Einstellungen, Navigation …) |
| Plugins | `IdeCommandPaletteProvider(PLUGIN)`, `PluginPaletteProvider`, `ContributedPaletteProvider` | Befehle der API 5 und 2, Plugin-Editor-Aktionen und -Bereiche |

Die Zuordnung der `cfm.*`-Befehle steht an einer Stelle: `IdeCommandAreas` (Präfix → Bereich). Neue IDE-Befehle
erscheinen damit automatisch im richtigen Bereich.

## Erweitern

- **Neuer eingebauter Eintrag:** Aktion in `BuiltinPaletteAction`, Regel im passenden `BuiltinActionProvider`, Titel
  in `BuiltinTitles`, Ausführung in `EditorScreen.runBuiltin`.
- **Neuer Bereich / eigenes Modul:** `PaletteProvider` implementieren und per Hilt anmelden:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyPaletteModule {
    @Provides
    @IntoSet
    fun myProvider(): PaletteProvider = object : PaletteProvider {
        override val id = "my-area"
        override val order = 120
        override fun items(context: PaletteContext) = listOf(/* … */)
    }
}
```

Anbieter bekommen alles über `PaletteContext` (Datei, Sprachserver, Projekt/Git, Befehlsverzeichnis, ausgeblendete
Befehle, fertige Einträge) und dürfen nicht blockieren. Ein Anbieter, der wirft, wird übersprungen; doppelte Aktionen
zeigt die Palette einmal (der Anbieter mit kleinerem `order` gewinnt).

Tests: `libs/command-palette-impl/src/test/…/CommandPaletteImplTest.kt`.
