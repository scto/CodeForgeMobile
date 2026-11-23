# Code-Plugins (API 4)

Acht Code-Plugins nach dem Vorbild beliebter VS-Code-Extensions – zugleich Vorlagen für eigene. Sieben laufen im
App-Prozess (nur Sideload, im Store als Download); `text-tools` läuft in der Prozess-Sandbox und ist direkt aus dem
Store installierbar.

| Modul | Plugin-ID | Inhalt | Berechtigungen | Aktivierung |
|---|---|---|---|---|
| `markdown-preview` | `com.codeforge.plugin.markdown-preview.code` | Markdown-Vorschau (Tabellen, Aufgaben, Code, Links) | `ui` | `onLanguage:markdown` |
| `data-viewer` | `com.codeforge.plugin.data-viewer.code` | CSV/TSV als sortier-/filterbare Tabelle, JSON als Baum | `ui` | `onFileOpen:*.{csv,tsv,json,geojson}` |
| `sqlite-viewer` | `com.codeforge.plugin.sqlite-viewer.code` | SQLite-Datenbanken im Projekt, Tabellen, lesende Abfragen | `ui` | Start |
| `script-tasks` | `com.codeforge.plugin.script-tasks.code` | npm/pnpm/yarn/bun-Skripte, Make, Cargo, Go, just als „Ausführen: …“ | `run`, `shell`, `project`, `project-events` | `onProject:package.json` u. a. |
| `rest-client` | `com.codeforge.plugin.rest-client.code` | `.http`/`.rest`: Anfragen senden, Antwort ansehen | `ui`, `network` | `onFileOpen:*.{http,rest}` |
| `gradle-tasks` | `com.codeforge.plugin.gradle-tasks.code` | Module und Gradle-Aufgaben, Ausgabe live, Stand des letzten Builds | `ui`, `project`, `build` | `onProject:settings.gradle(.kts)` |
| `python-tests` | `com.codeforge.plugin.python-tests.code` | pytest-Tests mit Start-Knöpfen und Ergebnissen | `run`, `shell` | `onLanguage:python` |
| `text-tools` | `com.codeforge.plugin.text-tools.code` | 17 Textaktionen (Groß/klein, Sortieren, Base64, URL, Hash, Statistik …) – **Sandbox** | `editor` | Start |

## Bauen

```bash
export ANDROID_HOME=/pfad/zum/sdk
./gradlew :code-plugins:packageAll                      # alle → code-plugins/out/<modul>/<id>.zip
./gradlew :code-plugins:markdown-preview:packagePlugin  # eines
./gradlew :code-plugins:markdown-preview:testDebugUnitTest
CF_PLUGIN_SIGNING_KEY=~/key.pem ./gradlew :code-plugins:packageAll   # signiert (docs/dev/PluginSigning.md)
```

## Eigenes Plugin anlegen

1. Ordner kopieren (UI: `markdown-preview`, ohne UI: `python-tests`), `namespace`, Paket und `plugin.json` anpassen.
2. In `settings.gradle.kts` unter „Code-Plugins“ eintragen.
3. SDK, Compose und Coroutines nur `compileOnly` bzw. über die Compose-Konvention einbinden – sie kommen zur Laufzeit
   aus der App. `gradle/code-plugin.gradle.kts` (gemeinsam für alle Code-Plugins) dext nur die eigenen Klassen.
4. Logik in reine Kotlin-Dateien legen und testen; Compose-Code klein halten (Ausnahmen in der Komposition beenden die App).

Sandbox-Plugin: Vorlage `text-tools`, `"sandbox": true`, nur `editor`, keine UI (Regeln in Kapitel 10a.1).

Leitfaden: `docs/dev/PluginDevelopment.md` (Kapitel 9.13, 10a, 10a.1, 10b).

## Geprüft / offen

- Gebaut, gedext und mit Unit-Tests der Plugin-Logik geprüft; Manifeste mit dem App-Parser (`CodePluginsValidTest`).
- Sandbox: Protokoll per Loopback-Test, Messenger-Transport per Robolectric geprüft.
- **Nicht auf einem Gerät** installiert und ausprobiert (auch der isolierte Prozess nicht).
