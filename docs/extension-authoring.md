# Plugins schreiben (Autorenhandbuch)

Referenz aller Felder: `docs/extensions.md`. Lauffähige Vorlagen: `templates/` (Übersicht in `templates/README.md`).

## 1. Welche Art brauche ich?

| Ziel | Vorlage | Art | Store |
|---|---|---|---|
| App-Farben, Editor-Theme | `color-theme` | deklarativ | Kategorie `theme` |
| Datei-/Ordner-Icons | `icon-theme` | deklarativ | `icons` |
| UI-Übersetzung | `language-pack` | deklarativ | `language` |
| Syntaxfarben für eigene Dateitypen | `grammar` | deklarativ | `grammar` |
| Code-Bausteine | `snippets` | deklarativ | `snippets` |
| Language Server anbinden | `lsp-server` | deklarativ | `lsp` |
| Externen Formatierer anbinden | `formatter` | deklarativ | `formatter` |
| Werkzeug im PT-Menü des Dateibaums (Skript im Projekt ausführen) | `tool-plugins/scripts` (Beispiele) | deklarativ + Skript, Berechtigung `shell` | nur Sideload |
| Mehrere Plugins bündeln | `extension-pack` | deklarativ | `bundle` |
| Befehle, Menüs, Ereignisse, eigene Logik | `code-plugin` | Code | nur Sideload (`plugin`) |

Deklarativ = nur Dateien, kein ausführbarer Code. Der Store installiert ausschließlich solche Pakete; Archive mit
`entryPointClass`, `.dex/.jar/.so/.apk/.sh` oder ohne Beiträge werden abgelehnt.

Fertige Formatierer-, Language-Server- und Werkzeug-Plugins: `tool-plugins/` (Generator + Quellen). Benötigte Programme deklarierst du mit `requires`; die App installiert sie nach Bestätigung.

## 2. Schnellstart (deklarativ)

1. Vorlage kopieren: `cp -r templates/color-theme mein-theme`
2. In `plugin.json` `id` (eindeutig, `[A-Za-z0-9][A-Za-z0-9._-]*`), `name`, `version` ändern; Dateien anpassen.
3. Bauen: `python3 templates/build.py mein-theme` (bzw. ZIP von Hand: `plugin.json` muss im Wurzelverzeichnis des ZIPs liegen, nicht in einem Unterordner).
4. Aufs Gerät kopieren, Einstellungen → Plugins → „+“, Plugin aktivieren, dann in Einstellungen → Design auswählen.

## 3. Schnellstart (Code-Plugin)

1. `templates/code-plugin` kopieren, `id`, `entryPointClass`, Paketnamen anpassen.
2. `export ANDROID_HOME=…` (Android SDK mit build-tools/`d8`), `./gradlew :templates:code-plugin:packagePlugin`.
3. ZIP (`plugin.json` + `classes.dex`) per Sideload installieren. Nur Berechtigungen deklarieren, die gebraucht werden
   (`commands`, `menus`, `file-events`, `git-events`, `shell`, `lsp`); fehlende werfen `PluginPermissionException`.
4. Die Plugin-API (`:libs:plugin-api`) ist `compileOnly`; Coroutines/SDK liefert die App zur Laufzeit.

## 4. Typische Fehler

| Symptom | Ursache |
|---|---|
| „plugin.json fehlt“ | ZIP enthält einen Unterordner statt `plugin.json` im Wurzelverzeichnis |
| Installation abgelehnt (apiVersion) | `apiVersion` neuer als die App; für deklarative Plugins `2` verwenden |
| Beitrag erscheint nicht | Plugin nicht aktiviert, oder `path` falsch (relativ, ohne `..` und führenden `/`) |
| Icons zeigen Standardbild | SVG statt PNG/WebP/JPEG, oder Dateiname in `theme.json` falsch |
| Sprachpaket wirkt nicht überall | Plurals, `Context.getString` und Systembenachrichtigungen sind nicht abgedeckt |
| Grammatik wirkt nicht | Dateityp hat eine eingebaute Grammatik (Kotlin, Java, XML, JSON) oder Endung fehlt in `extensions` |
| Store lehnt Paket ab | enthält Code/Skripte oder keine `contributes` |

## 5. Im Store veröffentlichen (nur deklarativ)

1. ZIP bauen, `build.py` gibt die SHA-256 aus (sonst `sha256sum mein-theme.zip`).
2. ZIP irgendwo per **HTTPS** hosten (z. B. GitHub Release).
3. Eintrag in `store.json` (Repository `scto/codeforge-store`, Vorlage `store-site/store.json`):

```json
{ "id": "com.example.nord", "category": "theme",
  "name": { "de": "Nord", "en": "Nord" }, "summary": { "de": "Nord-Farbschema", "en": "Nord colors" },
  "version": "1.0.0", "author": "example",
  "downloadUrl": "https://github.com/example/nord/releases/download/v1.0.0/nord.zip",
  "sha256": "<64 Hex-Zeichen>", "homepage": "https://github.com/example/nord" }
```

`id` muss der Plugin-`id` aus `plugin.json` entsprechen. Kategorien: `theme`, `icons`, `language`, `grammar`, `snippets`, `lsp`, `formatter`, `bundle`.
Bundle-Mitglieder müssen ebenfalls im Katalog stehen, damit sie automatisch mitinstalliert werden.

## 6. Prüfen

* `python3 templates/build.py` validiert JSON und referenzierte Dateien.
* `./gradlew :core:domain:testDebugUnitTest` (`TemplatesValidTest`) prüft die Vorlagen mit den echten Parsern.
* Nicht auf einem Gerät geprüft: siehe `agy-tasks/61-erweiterungssystem-geraetepruefung.md`.
