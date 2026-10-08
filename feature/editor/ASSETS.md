# TextMate-Assets — Bezugsquellen

`:feature:editor` lädt sein Syntax-Highlighting über `language-textmate` (sora-editor) aus
`src/main/assets/textmate/`. Diese Datei dokumentiert, was dort liegt, was Platzhalter ist,
und woher die vollständigen, produktionsreifen Dateien lizenzkonform zu übernehmen sind —
dieselbe Vendoring-Strategie wie bei `com.termux.terminal.*` in `:libs:terminal-engine`.

## Aktueller Stand

| Datei | Status |
|---|---|
| `languages.json` | Vollständig — Registry-Manifest (scopeName → Grammar-/Config-Pfad) |
| `themes/dark_plus.json`, `themes/light_plus.json` | **Platzhalter** — minimales, kompilierbares Theme mit den wichtigsten Scopes |
| `grammars/kotlin.tmLanguage.json` | **Fehlt** — siehe Bezugsquelle unten |
| `grammars/java.tmLanguage.json` | **Fehlt** |
| `grammars/xml.tmLanguage.json` | **Fehlt** |
| `grammars/json.tmLanguage.json` | **Fehlt** |
| `languages/*-language-configuration.json` | **Fehlt** (Bracket-Pairs, Kommentar-Syntax, Auto-Closing-Pairs pro Sprache) |

`TextMateAssetLoader.ensureInitialized()` fängt ein Fehlen dieser Dateien ab (`Result.failure`
+ Log-Warnung) und fällt auf Plain-Text-Highlighting zurück — die App bleibt lauffähig, auch
ohne die vollständigen Grammar-Dateien.

## Bezugsquellen (MIT/Apache-2.0-kompatibel)

- **Kotlin**: `fwcd/vscode-kotlin` oder die offizielle Kotlin-VSCode-Extension
  (`Kotlin.kotlin` auf dem VS Code Marketplace) → `syntaxes/Kotlin.tmLanguage.json`.
- **Java**: `microsoft/vscode` eingebettete `java.tmLanguage.json`
  (`extensions/java/syntaxes/java.tmLanguage.json`).
- **XML**: `microsoft/vscode` → `extensions/xml/syntaxes/xml.tmLanguage.json`.
- **JSON**: `microsoft/vscode` → `extensions/json/syntaxes/JSON.tmLanguage.json`.
- **Themes**: `microsoft/vscode` → `extensions/theme-defaults/themes/dark_plus.json` /
  `light_plus.json` (vollständige Fassung, aktuell nur in Kurzform vendort).
- **Language-Configuration** (Bracket-Pairs/Kommentare/AutoClosingPairs): jeweils
  `language-configuration.json` aus denselben VS-Code-Extension-Verzeichnissen.

## Vorgehen zum Vervollständigen

1. Datei aus der jeweiligen Quelle 1:1 nach `src/main/assets/textmate/grammars/` bzw.
   `.../languages/` bzw. `.../themes/` kopieren (Dateiname wie in `languages.json` referenziert).
   2. Lizenz-Header/NOTICE in `src/main/assets/textmate/docs/sub/NOTICE.md` ergänzen (Analogon zu
      `README-VENDORED.md` in `:libs:terminal-engine`).
3. `TextMateAssetLoader` erfordert keine Codeänderung — Registrierung erfolgt deklarativ über
   `languages.json`.
