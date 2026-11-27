# Plugin-API 5 – CodeForge Mobile vollständig ansteuern

Stand: 2026-11-27 · Status: Etappen 1–5 umgesetzt (siehe Abschnitt 9)

API 5 erweitert die bestehende Plugin-API (1–4) um Zugriff auf die ganze IDE:
- Befehle, Ereignisbus und App-Einstellungen
- Editor, Projektdateien, Git, Navigation und Statusleiste
- eigene Oberflächen in Compose: Werkzeugfenster, Bildschirme, Einstellungsseiten

Vorbild sind die Plugin-Schnittstellen von IntelliJ, CodeAssist (`plugins/`, GPL-3.0) und der VS-Code-API. Alles
Bisherige bleibt gültig, API 5 kommt nur dazu.

## 1. Vertrauensstufen

| Stufe | Wie installiert | Läuft | Darf |
|---|---|---|---|
| Deklarativ | Store oder Sideload | – (nur Dateien) | Themes, Grammatiken, Snippets, LSP, Formatierer, Werkzeuge |
| Sandbox-Code | Store (`"sandbox": true`) | isolierter Prozess | Editor-Beiträge (Aktionen, Vervollständigung, Hover …) |
| Code im App-Prozess | **nur Sideload**, Bestätigung beim Installieren | App-Prozess | alles aus API 1–5 je nach Berechtigung, **inkl. Compose-Oberflächen** |

Die volle API 5 gibt es nur in der dritten Stufe. Dort laufen Plugins mit den Rechten der App, deshalb nur
aus vertrauenswürdiger Quelle. Die Plugin-Ansicht zeigt dazu die Signatur (`signer`) und alle Berechtigungen an. Die
Sandbox erhält einen Teil von API 5 (Befehle, Ereignisse, Statusleiste), siehe Abschnitt 6.1.

## 2. Einstieg

```kotlin
class MyPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        val ide = context.ide()                 // API 5
        ide.commands.register("hello", "Hallo sagen", IdeCommandHandler { args ->
            ide.statusBar.set("greeting", "Hallo ${args["name"] ?: "Welt"}")
            null
        })
        ide.events.subscribe("file.saved") { e -> context.logger("main").info("gespeichert: ${e.payload["path"]}") }
    }
    override fun onUnload() = Unit              // Befehle, Abos, Statuseinträge räumt der Host selbst ab
}
```

`plugin.json`:

```json
{
  "id": "com.example.hello", "name": "Hello", "version": "1.0.0", "apiVersion": 5,
  "entryPointClass": "com.example.hello.MyPlugin",
  "permissions": ["commands", "commands.execute", "events", "ui"]
}
```

## 3. Berechtigungen (neu in API 5)

| Berechtigung | Erlaubt |
|---|---|
| `commands.execute` | Befehle der IDE und anderer Plugins auflisten und ausführen |
| `events` | Ereignisbus abonnieren, eigene Ereignisse `plugin.<id>.*` veröffentlichen |
| `settings.read` / `settings.write` | freigegebene App-Einstellungen lesen / ändern |
| `files.read` / `files.write` | Dateien des geöffneten Projekts lesen / ändern (nie außerhalb des Projekts) |
| `git.read` / `git.write` | Status, Diff, Branches / stagen, committen, Branch wechseln |

Weiterhin gelten:
- `commands`: eigene Befehle anmelden
- `editor`: Editor lesen und ändern
- `ui`: Navigation, Statusleiste, Compose-Oberflächen
- `menus`, `shell`, `lsp`, `build`, `project`, `notifications` aus API 2–4

## 4. Bereiche von `context.ide()`

### 4.1 Befehle (`ide.commands`)
- `list()`: alle Befehle mit ID, Titel, Kategorie und Argumentbeschreibung
- `execute(id, args)`: führt einen Befehl aus, liefert das Ergebnis als Text
- `register(id, title, handler, category)`: meldet einen Befehl als `<pluginId>.<id>` an

IDE-Befehle beginnen mit `cfm.`. Sie stehen auch in der Befehlspalette des Editors unter „Befehle“; dort läuft ein
Befehl mit `path` = aktive Datei.

| ID | Argumente | Wirkung |
|---|---|---|
| `cfm.editor.save` / `cfm.editor.saveAll` | `path` | speichern |
| `cfm.editor.open` | `path`, `line`, `column` | Datei öffnen |
| `cfm.editor.close` | `path` | Datei schließen |
| `cfm.editor.format` | – | aktive Datei formatieren |
| `cfm.editor.insert` | `text` | Text an Cursor/Auswahl einfügen |
| `cfm.file.new` | `path`, `text` | Datei anlegen und öffnen |
| `cfm.git.stageAll` | – | alle Änderungen vormerken |
| `cfm.git.commit` | `message` | vorgemerkte Änderungen committen |
| `cfm.git.pull` / `cfm.git.push` | – | Pull / Push |
| `cfm.build.run` | `task` | Gradle-Aufgabe im Build-Panel |
| `cfm.terminal.run` | `command` | Terminal öffnen und Befehl ausführen |
| `cfm.settings.open` | `page` | Einstellungen (Seite) öffnen |
| `cfm.navigate.git` / `cfm.navigate.build` | – | Git- bzw. Build-Ansicht öffnen |
| `cfm.commands.list` | – | alle Befehle als Text |
| `<pluginId>.toolWindow.<id>` / `<pluginId>.screen.<id>` | – | Werkzeugfenster bzw. Bildschirm eines Plugins öffnen (automatisch) |

### 4.2 Ereignisbus (`ide.events`)

`subscribe(prefix, listener)` und `publish(name, payload)`. Themen in Punktschreibweise, Nutzdaten als Text:

| Thema | Nutzdaten |
|---|---|
| `file.opened`, `file.saved`, `file.closed`, `editor.activated` | `path` |
| `editor.diagnosticsChanged` | `path`, `errors`, `warnings` |
| `git.committed` | `repo`, `message` |
| `git.branchChanged` | `repo`, `branch` |
| `build.started` / `build.finished` | `task` (+ `success`) |
| `project.opened` / `project.closed` | `root` |
| `settings.changed` | `key` |
| `plugin.<id>.<name>` | frei (Plugin-zu-Plugin) |

### 4.3 App-Einstellungen (`ide.settings`)
- `keys()`: freigegebene Schlüssel mit Typ und Beschreibung
- `get(key)` / `set(key, value)`
- `onChange { key -> }`: meldet jede Änderung, auch durch den Nutzer

Freigegeben ist eine feste, geprüfte Tabelle, nicht das ganze Einstellungsobjekt:
- **Theme:** `theme.mode` (`system|light|dark`), `theme.colorScheme`, `theme.dynamicColor`, `theme.iconTheme`
- **Editor, Zahlen:** `editor.tabSize`, `editor.fontSize`, `editor.lineSpacingPercent`, `editor.rulerColumn`
- **Editor, Text:** `editor.fontFamily`
- **Editor, Schalter:** `editor.wordWrap`, `editor.showNonPrintable`, `editor.stickyScroll`, `editor.minimap`, `editor.rainbowBrackets`, `editor.hardTabs`, `editor.formatOnPaste`, `editor.formatOnSave.{kotlin,java,xml,json}`, `editor.lineNumbers`, `editor.codeFolding`, `editor.inlayHints`, `editor.indentGuides`, `editor.currentLineHighlight`, `editor.cursorBlink`, `editor.readOnlyDefault`
- **Sonstiges:** `terminal.fontSize`, `git.gutterMarkers`, `fileTree.showHidden`

Eigene Einstellungen eines Plugins gibt es seit API 3 (`contributes.settings` und `context.settings()`). Neu in API 5:
Jedes Plugin mit Einstellungen steht in der Einstellungsübersicht im Abschnitt **Plugin-Einstellungen**:
- Hat es eine eigene Seite (`PluginUiPoints.SETTINGS_PAGE`, Abschnitt 5), öffnet sich diese.
- Sonst führt der Eintrag zur Plugin-Ansicht mit den Einstellungen aus `contributes.settings`.
- Eine eigene Seite speichert die Werte aus `contributes.settings` mit `context.settings().set(key, value)`
  (`suspend`, `null` = Vorgabe; in der Sandbox nicht verfügbar).

### 4.4 Editor (`ide.editor`)

`snapshot()` liefert Pfad, Text, Cursor, Auswahl, ungespeichert und offene Dateien. Dazu kommen:
- `open(path, line, column)`
- `applyEdits(path, edits)` (rückgängig machbar)
- `select(start, end)`, `save(path)`, `close(path)`

Aufträge liefern `false`, wenn gerade kein Editor offen ist.

### 4.5 Dateien (`ide.files`)

`projectRoot()`, `list(dir)`, `exists`, `read`, `write`, `mkdirs`, `rename`, `delete`:
- Pfade sind absolut oder relativ zum Projekt; außerhalb des Projekts wird abgelehnt.
- Änderungen erscheinen sofort im Dateibaum und in offenen Editoren.
- Ungespeicherte Puffer werden vor dem Umbenennen gesichert.

### 4.6 Git (`ide.git`)

`status()` (Branch, ahead/behind, Änderungen, vorgemerkt), `diff(path, staged)`, `branches()`, `stage(paths)`,
`commit(message)` und `checkout(branch)`. Commit und Checkout melden die passenden Ereignisse.

### 4.7 Navigation (`ide.navigation`)

`navigate(target)` mit diesen Zielen:
- `Settings(page)`
- `Terminal(command)`
- `Git`
- `BuildPanel`
- `PluginScreen(pluginId, screenId)`
- `PluginToolWindow(pluginId, id)`

### 4.8 Statusleiste (`ide.statusBar`)

`set(id, text, tooltip, commandId, priority)` und `remove(id)`. Höchstens 5 Einträge je Plugin; Tippen führt den Befehl
aus.

## 5. Oberflächen (`:libs:plugin-ui-api`, Compose)

Eigenes SDK-Modul, damit Plugins ohne Oberfläche kein Compose brauchen. Angemeldet wird mit
`context.register(PluginUiPoints.X, …)`; dafür ist die Berechtigung `ui` nötig.

| Andockstelle | Schnittstelle | Ort in der App |
|---|---|---|
| `PluginUiPoints.TOOL_WINDOW` | `PluginToolWindow { id; title; @Composable Content(scope, modifier) }` | Blatt von unten im Projekt; über die Befehlspalette oder `navigate(PluginToolWindow(...))` |
| `PluginUiPoints.SCREEN` | `PluginScreen { id; title; Content }` | Vollbild mit Kopfzeile; über die Palette oder `navigate(PluginScreen(...))` |
| `PluginUiPoints.SETTINGS_PAGE` | `PluginSettingsPage { title; summary; Content }` | Einstellungen → Plugin-Einstellungen |
| `PluginUiExtensionPoints.PANEL` (API 3) | `PluginPanel` | Bereich im Editor |
| `PluginUiExtensionPoints.PREVIEW` (API 4) | `PluginPreview` | Vorschau für Dateitypen |

`PluginUiScope` liefert die Plugin-ID und `close()`; alles Weitere holt sich die Oberfläche über das in `onLoad`
gemerkte `context.ide()`. Die Oberflächen verwenden das Material-3-Theme der App mit. Compose-Laufzeit und
-Compiler müssen zur App passen.

### 5.x Seitenleiste (`PluginUiPoints.SIDEBAR`, ab App 3.6.1)

Ein Plugin kann ein Symbol in die Leiste der Projekt-Seitenleiste setzen – neben Dateien, Git, Layouts, Themes.
Antippen zeigt die Ansicht des Plugins darunter (wie die Layout-Liste); für größere Arbeit öffnet sie einen eigenen
Bildschirm.

```kotlin
context.register(PluginUiPoints.SIDEBAR, object : PluginSidebarView {
    override val id = "strings"
    override val title = "Strings"            // kurz, steht unter dem Symbol
    override val icon = Icons.Filled.Translate // gefüllte Material-Symbole

    @Composable
    override fun Content(scope: PluginSidebarScope, modifier: Modifier) {
        // scope.projectRoot, scope.close() (Seitenleiste schließen), scope.openScreen("editor")
    }
})
```

Beispiel: `code-plugins/string-editor`. Plugins, die das nutzen, setzen `"minHostVersion": "3.6.1"`.

## 6. Menüs, Tastenkürzel, Bedingungen (`plugin.json` → `contributes`)

Neben `hostServices.registerMenuItem` (Code) gibt es die deklarative Form. Sie gilt für alle Stufen, auch für
deklarative und Sandbox-Plugins:

```json
"contributes": {
  "commands": [{ "id": "hello", "title": "Hallo sagen", "category": "Beispiel" }],
  "menus": {
    "editor/context": [{ "command": "hello", "when": "editorLangId == kotlin" }],
    "fileTree/context": [{ "command": "hello", "when": "resourceExtname == .md" }],
    "commandPalette": [{ "command": "hello", "when": "projectOpen" }]
  },
  "keybindings": [{ "command": "hello", "key": "ctrl+alt+h", "when": "editorHasSelection" }]
}
```

- Befehls-IDs ohne Punkt werden zu `<pluginId>.<id>` (der Befehl, den das Plugin mit `ide.commands.register`
  anmeldet). IDs mit Punkt (z. B. `cfm.git.commit`) bleiben unverändert, so lassen sich auch IDE-Befehle einhängen.
- `editor/context` erscheint im Editor-Menü (Werkzeugleiste), `fileTree/context` im Kontextmenü des Dateibaums (der
  Befehl bekommt `path` als Argument). `commandPalette` mit `when` blendet den Befehl in der Palette aus, solange die
  Bedingung nicht gilt.
- Tastenkürzel: `ctrl`, `alt`, `shift`, `meta` plus Taste, z. B. `ctrl+shift+k`. Ungültige Kürzel lehnt die
  Installation ab. Bei Konflikten gewinnt das zuerst geladene Plugin.
- `when` ist eine Teilmenge der VS-Code-Syntax: `!`, `==`, `!=`, `=~` (regulärer Ausdruck), `&&`, `||`, Klammern.
  Schlüssel: `editorLangId`, `resourceExtname`, `resourceFilename`, `resourcePath`, `projectOpen`, `gitRepo`,
  `editorHasSelection`. Ungültige Ausdrücke lehnt die Installation ab.

### 6.1 API 5 in der Sandbox

Sandbox-Plugins mit `apiVersion` 5 bekommen über `context.ide()` einen eingeschränkten Teil der API:

| Bereich | in der Sandbox |
|---|---|
| `commands.register` | ✅ (Berechtigung `commands`) |
| `events.subscribe` / `publish` | ✅ (Berechtigung `events`) |
| `statusBar.set` / `remove` | ✅ (Berechtigung `ui`) |
| alles andere (`commands.execute`, Einstellungen, Editor, Dateien, Git, Navigation) | `PluginPermissionException` |

Technik: Der Sandbox-Prozess kann die App nicht selbst aufrufen. Was das Plugin anmeldet, landet in einer
Ausgangspost, die jede Antwort an die App mitnimmt; zusätzlich holt die App sie alle 3 Sekunden ab. Befehle und
Ereignisse ruft die App per Anfrage in der Sandbox auf. Die App prüft jede Meldung mit denselben Regeln wie bei
In-Process-Plugins, Berechtigungen über die Sandbox-Liste hinaus werden ignoriert.

### 6.2 Beispiele

- `code-plugins/session-stats` – Sandbox: Befehle, Menüs, Tastenkürzel, `when`, Ereignisse, Statusleiste,
  Einstellungen, Editor-Aktion. Im Store direkt installierbar.
- `code-plugins/ide-showcase` – App-Prozess: alle Bereiche von `context.ide()`, Werkzeugfenster (Projektbaum mit
  Git-Status), Bildschirm, eigene Einstellungsseite (`settings().set`, neu in API 5). Im Store als Sideload-Download.
- Projektvorlage „Plugin · Code (Sandbox)“ (ab Vorlagen-Version 1.2.0) mit Befehl, Menüs und Tastenkürzel.

## 7. Lebenszyklus und Grenzen
- Alles über `ide()` Angemeldete (Befehle, Abos, Einstellungs-Beobachter, Statuseinträge) wird beim Deaktivieren oder
  Entfernen automatisch entfernt.
- Befehle laufen mit Zeitlimit (2 Minuten); Ausnahmen kommen beim Aufrufer als Fehler an und stürzen die App nicht ab.
- Dateien bis 8 MB lesen, höchstens 1000 Textänderungen je Auftrag, Ereignis-Nutzdaten höchstens 50 Einträge.

## 8. Kompatibilität
- Plugins mit `apiVersion` 2–4 laufen unverändert.
- `context.ide()` wirft dort `PluginPermissionException`; in der Sandbox gilt Abschnitt 6.1.
- `PluginHostServices.registerCommand` und `addEventListener` (API 2) bleiben bestehen. Ihre Befehle und Ereignisse
  erscheinen zusätzlich im Befehlsverzeichnis bzw. auf dem Bus.

## 9. Umsetzungsstand

| Etappe | Inhalt | Stand |
|---|---|---|
| 1 | Befehlsverzeichnis, Ereignisbus, IDE-Befehle `cfm.*`, Befehle in der Palette | ✅ |
| 2 | App-Einstellungen (feste Tabelle), Abschnitt „Plugin-Einstellungen“ | ✅ |
| 3 | Editor, Dateien, Git, Navigation, Statusleiste | ✅ |
| 4 | Werkzeugfenster, Bildschirme, Einstellungsseiten (`PluginUiPoints`) | ✅ |
| 5 | `contributes.menus`/`keybindings`/`when`, Sandbox-Anbindung per IPC | ✅ |

Code: `core/domain/.../extension/ide/IdeHost.kt` (Verträge), `libs/plugin-api/.../IdeApi.kt` (SDK),
`libs/plugin-impl/.../ide/` (Host), `libs/plugin-ui-api/.../PluginWindows.kt` (Oberflächen),
`core/domain/.../extension/ide/IdeMenus.kt` (Menüs, `when`),
`libs/plugin-sandbox/.../SandboxIde.kt` + `libs/plugin-impl/.../ide/HostSandboxIdeSink.kt` (Sandbox),
`feature/editor/.../plugin/EditorIdeConnector.kt`, `app/.../PluginUiHost.kt`, `app/.../IdeNavigationViewModel.kt`.
