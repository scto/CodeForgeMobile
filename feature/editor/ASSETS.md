# TextMate-Assets — Bezugsquellen

`:feature:editor` lädt sein Syntax-Highlighting über `language-textmate` (sora-editor) aus
`src/main/assets/textmate/`. Diese Datei dokumentiert, was dort liegt, was Platzhalter ist,
und woher die vollständigen, produktionsreifen Dateien lizenzkonform zu übernehmen sind —
dieselbe Vendoring-Strategie wie bei `com.termux.terminal.*` in `:libs:terminal-engine`.

## Aktueller Stand

| Datei | Status |
|---|---|
| `languages.json` | Vollständig — Registry-Manifest (scopeName → Grammar-/Config-Pfad) |
| `themes/dark_plus.json`, `themes/light_plus.json` | **Vollständig** — Autarke, vollständige Dark+/Light+ Themes |
| `grammars/kotlin.tmLanguage.json` | **Vollständig** — aus `fwcd/vscode-kotlin` |
| `grammars/java.tmLanguage.json` | **Vollständig** — aus `microsoft/vscode` |
| `grammars/xml.tmLanguage.json` | **Vollständig** — aus `microsoft/vscode` |
| `grammars/json.tmLanguage.json` | **Vollständig** — aus `microsoft/vscode` |
| `languages/*-language-configuration.json` | **Vollständig** — Bracket-Pairs, Kommentar-Syntax, Auto-Closing-Pairs pro Sprache |

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

---

# Tree-sitter Assets & native Bibliotheken — Bezugsquellen & Lizenzen

Tree-sitter-Highlighting nutzt `.scm`-Query-Dateien in `src/main/assets/treesitter/<sprache>/` sowie native Shared Libraries (`libtree-sitter-<sprache>.so`) in `src/main/jniLibs/<abi>/` bzw. transitive Maven-AARs (`com.itsaky.androidide.treesitter:*`).

## Tree-sitter Grammatiken & Lizenzen (alle MIT)

| Komponente / Sprache | Upstream-Repository | Version / Tag | Lizenz |
|---|---|---|---|
| **Tree-sitter Core & Maven AARs** (Kotlin, Java, JSON, XML, C++, C) | `AndroidIDEOfficial/android-tree-sitter` | `4.3.1` | MIT License |
| **tree-sitter-bash** (`.so` + `highlights.scm`) | [`tree-sitter/tree-sitter-bash`](https://github.com/tree-sitter/tree-sitter-bash) | `v0.23.3` (ABI 14) | MIT License |
| **tree-sitter-cmake** (`.so` + `highlights.scm`) | [`uyha/tree-sitter-cmake`](https://github.com/uyha/tree-sitter-cmake) | `v0.7.5` (ABI 14) | MIT License |
| **tree-sitter-toml** (`.so` + `highlights.scm`) | [`tree-sitter-grammars/tree-sitter-toml`](https://github.com/tree-sitter-grammars/tree-sitter-toml) | `v0.7.0` (ABI 14) | MIT License |
| **tree-sitter-yaml** (`.so` + `highlights.scm`) | [`tree-sitter-grammars/tree-sitter-yaml`](https://github.com/tree-sitter-grammars/tree-sitter-yaml) | `v0.7.2` (ABI 14) | MIT License |

Alle Upstream-Grammatiken und Query-Dateien unterliegen der MIT-Lizenz und sind uneingeschränkt kompatibel mit CodeForgeMobile. Details zum Build-Vorgang und SHA-256 Prüfsummen siehe [`TREESITTER.md`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/editor/TREESITTER.md).

