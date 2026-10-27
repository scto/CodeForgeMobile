# Erweiterungssystem (Plugins / Extensions)

Ein Plugin ist ein ZIP mit `plugin.json` im Wurzelverzeichnis. Es kann **deklarative Beiträge** (`contributes`)
und/oder **Code** (`entryPointClass`, `classes.dex`) enthalten. Der Store listet nur rein deklarative Plugins;
Code-Plugins gibt es nur per manuellem Sideload (Einstellungen → Plugins).

## Module

| Modul | Inhalt |
|---|---|
| `:libs:plugin-api` | SDK für Code-Plugins: `CodeForgePlugin`, `PluginContext`, `PluginHostServices` |
| `:core:domain` (`…domain.extension`) | Manifest-Modelle, `PluginManifestParser`, `ActiveContributions`, `ContributionRepository`, Farbschema-Parser |
| `:libs:plugin-impl` | Laufzeit: `PluginRuntime` (DexClassLoader), `PluginRegistry`, `PluginRepositoryImpl`, `ExtensionThemeRepositoryImpl`, Hilt-Modul |

## plugin.json (Manifest v2)

```json
{
  "id": "com.example.nord",
  "name": "Nord",
  "version": "1.0.0",
  "apiVersion": 2,
  "publisher": "example",
  "description": "Nord-Farbschema",
  "permissions": [],
  "entryPointClass": null,
  "contributes": {
    "colorThemes":   [{ "id": "nord", "label": "Nord", "uiTheme": "dark", "path": "themes/nord.json" }],
    "iconThemes":    [{ "id": "…", "label": "…", "path": "icons/theme.json" }],
    "languagePacks": [{ "locale": "fr", "label": "Français", "path": "l10n/fr.json" }],
    "grammars":      [{ "languageId": "zig", "scopeName": "source.zig", "path": "g/zig.tmLanguage.json", "extensions": ["zig"] }],
    "snippets":      [{ "languageId": "kotlin", "path": "snippets/kotlin.json" }],
    "lspServers":    [{ "id": "zls", "label": "ZLS", "languages": ["zig"], "command": ["${pluginDir}/zls"] }],
    "extensionPack": ["com.example.icons"]
  }
}
```

* v1-Manifeste (nur `id`, `name`, `version`, `description`, `entryPointClass`) bleiben gültig (`apiVersion` = 1).
* `id`: `[A-Za-z0-9][A-Za-z0-9._-]*`, kein `..`. Alle `path`-Werte sind relativ zum Plugin-Verzeichnis, ohne `..` und ohne führenden `/`.
* `permissions` (nur Code-Plugins): `commands`, `menus`, `file-events`, `git-events`, `shell`, `lsp`. Unbekannte Werte lehnen die Installation ab.
* Eine neuere `apiVersion` als die App kennt wird abgelehnt.
* Rein deklarative Plugins (ohne `entryPointClass`) laden keinen Code; ihre Beiträge werden direkt aus dem Manifest gelesen.
* Aktivieren/Deaktivieren schaltet die Beiträge sofort (`ContributionRepository.contributions`).

## Farbschemata (`colorThemes`)

Datei (`path`):

```json
{ "colors": { "primary": "#88C0D0", "onPrimary": "#2E3440", "background": "#2E3440", "surface": "#3B4252" },
  "textmateTheme": "syntax/nord.json" }
```

* Schlüssel sind Material-3-Rollen (`primary`, `onPrimary`, `primaryContainer`, `secondary`, `tertiary`, `error`, `background`, `surface`, `surfaceVariant`, `outline`, `surfaceContainer*`, …; Liste in `ColorThemeFileParser.ROLES`). Nicht angegebene Rollen kommen aus dem Standardschema der Helligkeit (`uiTheme`: `dark`/`light`). Unbekannte Rollen werden ignoriert.
* Farben als `#RGB`, `#RRGGBB` oder `#AARRGGBB`.
* `textmateTheme` (optional): VS-Code/TextMate-Theme-JSON für den Editor. Gilt, wenn in den Editor-Einstellungen „auto“ gewählt ist.
* Auswahl: Einstellungen → Design → „Aus Erweiterungen“. Gespeichert wird `ThemeConfig.color_scheme_id = "ext:<pluginId>/<themeId>"`; ist das Plugin weg oder deaktiviert, fällt die App auf das Standardschema zurück. Dynamic Color hat Vorrang, wenn aktiviert.

## Icon-Pakete (`iconThemes`)

Datei (`path`); alle Werte sind Bilddateien (PNG, WebP oder JPEG — **kein SVG**) relativ zur Theme-Datei:

```json
{ "file": "file.png", "folder": "folder.png", "folderOpen": "folder-open.png",
  "fileExtensions": { "kt": "kotlin.png", "tar.gz": "archive.png" },
  "fileNames": { "build.gradle.kts": "gradle.png" },
  "folderNames": { "src": "folder-src.png" }, "folderNamesOpen": { "src": "folder-src-open.png" } }
```

* Auflösung Datei: exakter Name → längste passende Endung (`tar.gz` vor `gz`) → `file` → App-Standardicon. Ordner: Name (offen/zu) → `folderOpen`/`folder`. Groß-/Kleinschreibung egal.
* Bilder werden auf ~96 px verkleinert geladen und gecacht; fehlende/defekte Bilder fallen auf das Standardicon zurück. Plugin-Icons werden nicht eingefärbt.
* Gilt in: Dateibaum, Editor-Tabs, Suchergebnissen, Git-Änderungen.
* Auswahl: Einstellungen → Design → „Icon-Pakete“ (`ThemeConfig.icon_theme_id`, leer = Standard).

## Sprachpakete (`languagePacks`)

Datei (`path`): `{ "strings": { "<Name der String-Ressource>": "Übersetzter Text" } }`

* Schlüssel = **Ressourcenname** aus `core/resources/src/main/res/values/strings.xml` (z. B. `common_abbrechen`). Platzhalter (`%1$s`, `%2$d`) müssen wie im Original stehen; passen sie nicht, gilt der Originaltext.
* Fehlende Schlüssel fallen auf die App-Sprache zurück — ein Paket darf unvollständig sein.
* Wirkt auf `Res.string(...)` und `stringRes(...)` (alle UI-Texte). **Nicht abgedeckt:** Plurals, direkte `Context.getString(...)`-Aufrufe, System-Benachrichtigungen, native Terminal-Texte.
* Auswahl: Einstellungen → Design → „Sprachpakete“ (`ThemeConfig.language_pack_id`, leer = App-Sprache). Max. 20 000 Einträge je Paket.

## Grammatiken (`grammars`)

`{ "languageId": "zig", "scopeName": "source.zig", "path": "g/zig.tmLanguage.json", "extensions": ["zig"] }`

* TextMate-Grammatik (JSON oder plist). Zuordnung per Dateiendung (ohne Punkt, case-insensitiv) oder exaktem Dateinamen (z. B. `justfile`).
* Gilt nur für Dateien **ohne eingebaute Grammatik** (eingebaut: Kotlin, Java, XML, JSON). Die Grammatik wird beim ersten Öffnen einer passenden Datei registriert; lässt sie sich nicht laden, bleibt der Editor bei Klartext.
* Ohne eigene Sprachkonfiguration (Klammerpaare/Kommentare) — nur Syntaxfarben.

## Snippets (`snippets`)

`{ "languageId": "kotlin", "path": "snippets/kotlin.json" }` — Datei im **VS-Code-Snippet-Format** (`prefix`, `body`, `description`).

* Scope ist immer die `languageId` des Beitrags. IDs sind `ext:<pluginId>/<languageId>/<Name>`.
* Erscheinen in der Auto-Vervollständigung und in den Snippet-Einstellungen, sind dort aber **schreibgeschützt** (nicht löschbar/bearbeitbar/abschaltbar — Plugin deaktivieren, um sie loszuwerden). Der Export enthält nur eigene Snippets.

## Extension-Packs / Bundles (`extensionPack`)

`"extensionPack": ["com.example.icons", "com.example.nord"]` — ein Plugin, das weitere Plugins als Mitglieder benennt (kann selbst nur ein Bundle sein, ohne eigene Beiträge).

* `ExtensionPacks.missingMembers` liefert fehlende Mitglieder; `ExtensionPacks.resolveClosure` löst verschachtelte Bundles aus einem Katalog auf (ohne Zyklen, Tiefe ≤ 4).
* Beim Installieren aus dem Store werden fehlende Mitglieder aus dem Katalog mit installiert (siehe Store-Integration, Phase 7).

## Language Server (`lspServers`)

`command` darf `${pluginDir}` und `${home}` enthalten. Ein eingebauter, installierter Server hat Vorrang; sonst wird der Plugin-Server für die angegebenen `languages` verwendet. Existiert die ausführbare Datei bei absolutem Pfad nicht, gibt es keinen Server.

## Code-Plugins: Befehle, Menüs, Ereignisse (API v2)

```kotlin
class MyPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        val host = context.hostServices
        host.registerCommand("count-lines", "Zeilen zählen") { args ->          // permissions: ["commands"]
            host.showMessage("Datei: ${args.activeFilePath}")
        }
        host.registerMenuItem("count", "Zeilen zählen", PluginMenuTarget.FILE_CONTEXT_MENU, "count-lines") // ["menus"]
        host.addEventListener { event ->                                          // ["file-events"] / ["git-events"]
            if (event is PluginEvent.FileSaved) host.showMessage("Gespeichert: ${event.path}")
        }
    }
    override fun onUnload() = Unit
}
```

* **Berechtigungen** stehen in `plugin.json → permissions`. Ohne sie wirft der Zugriff `PluginPermissionException` (das Plugin lädt dann nicht).
  `commands`, `menus`, `file-events`, `git-events` sind immer deklarationspflichtig. `shell` (`buildShellCommand`) und `lsp` (`lspClientRepository`)
  bleiben für **v1-Plugins** (ohne `apiVersion`) wie bisher erlaubt; ab `apiVersion: 2` müssen auch sie deklariert sein.
* **Menüs:** `EDITOR_MENU` → Plugin-Schaltfläche in der Editor-Leiste (erscheint nur, wenn ein Plugin Befehle registriert hat; alle Befehle bleiben dort erreichbar);
  `FILE_CONTEXT_MENU` → Kontextmenü im Dateibaum (Befehl bekommt den gewählten Pfad).
* **Ereignisse:** `FileOpened/FileSaved/FileClosed` (Editor), `GitCommitted/GitBranchChanged` (Git-Panel). Zustellung asynchron; ein werfender Listener stört weder andere Plugins noch die App.
* **Grenzen:** max. 100 Befehle/Menüeinträge je Plugin, Titel ≤ 120 Zeichen, Nachrichten ≤ 500 Zeichen, Befehl wird nach 60 s abgebrochen.
* Beim Deaktivieren/Deinstallieren/Fehlschlagen von `onLoad` werden alle Registrierungen des Plugins entfernt.

## Sicherheit

* ZIP-Einträge werden gegen Zip-Slip geprüft; Contribution-Dateien dürfen das Plugin-Verzeichnis nicht verlassen (kanonischer Pfad).
* Plugin-Code wird vor dem Laden schreibgeschützt gesetzt (Android 14+).
* Der Store bietet nur deklarative Plugins mit SHA-256 an.

## Status je Beitragsart

| Beitrag | Stand |
|---|---|
| `colorThemes` | umgesetzt (Phase 2) |
| `iconThemes` | umgesetzt (Phase 3): Dateibaum, Tabs, Suche, Git |
| `languagePacks` | umgesetzt (Phase 4), nicht abgedeckt: Plurals/`Context.getString` |
| `grammars` | umgesetzt (Phase 5), nur Dateien ohne eingebaute Grammatik |
| `snippets` | umgesetzt (Phase 5), schreibgeschützt |
| `extensionPack` | umgesetzt (Phase 5/7): Store installiert fehlende Mitglieder aus dem Katalog (Tiefe ≤ 4) |
| Store-Policy | umgesetzt (Phase 7): nur deklarative Kategorien (`theme`, `icons`, `language`, `grammar`, `snippets`, `lsp`, `bundle`); `installFromFile(declarativeOnly = true)` lehnt Archive mit `entryPointClass`, `.dex/.jar/.so/.apk/.sh` oder ohne Beiträge ab; Code-Plugins nur per Sideload |
| Code-Plugin-Hooks | umgesetzt (Phase 6): Befehle, Menüs, Datei-/Git-Ereignisse, Berechtigungen |
| `lspServers` | umgesetzt (Resolver) |
| Geräteprüfung | alles bisher nur per Unit-Test/Kompilierung geprüft, siehe `agy-tasks/61-erweiterungssystem-geraetepruefung.md` |
