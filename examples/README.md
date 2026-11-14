# Beispiel-Plugins

> Kleine, buildbare Vorlagen für jede Plugin-Art: `templates/` (Anleitung: `docs/extension-authoring.md`).

| Modul | Art | Inhalt |
|---|---|---|
| `web-search-plugin` | deklarativ (Sideload) | Agent-Werkzeuge `web_search` (DuckDuckGo) und `web_fetch`; Python-Skript, `requires`: pkg `python` |
| `guide/*` | gemischt | Beispiele des Plugin-Leitfadens (Farbschema, Sprachunterstützung, PT-Werkzeug, Code-Plugin) |

**Sprachserver** gibt es hier nicht mehr: Die früheren Code-Plugins (`*-lsp-plugin`, `lsp-plugin-common`) sind durch
deklarative Plugins in `tool-plugins/src/com.codeforge.plugin.<name>.lsp` ersetzt (Store-Kategorie „Sprachserver“,
Bundle `com.codeforge.plugin.all-lsp-servers.bundle`): clangd, Pyright, Bash, Taplo, YAML, Kotlin LS, JDT LS, LemMinX,
kmp-lsp u. a. Programme kommen über `requires` (pkg/npm oder geprüfter Download, siehe `docs/extensions.md`), Start und
Einstellungen übernimmt die App.

## Bauen und Installieren

```bash
export ANDROID_HOME=/pfad/zum/android/sdk   # für das Code-Plugin des Leitfadens (d8)
./gradlew :examples:packageAll               # alles nach examples/out/<name>/ (braucht zusätzlich python3)
python3 templates/build.py --out examples/out examples/web-search-plugin
```

Installation: ZIP aufs Gerät, **Einstellungen → Plugins → „+“**, Plugin aktivieren. Code-Plugins sind **nur per Sideload**
installierbar, nicht über den Store.

## Beispiele des Plugin-Leitfadens

`examples/guide/` enthält vier Plugins anderer Arten, beschrieben in `docs/dev/PluginDevelopment.md`. Alle baut
`./gradlew :examples:packageAll` nach `examples/out/guide-<name>/`; einzeln: `./gradlew :examples:guide-commit-notes:packagePlugin`
bzw. `python3 templates/build.py --out examples/out --prefix guide- examples/guide/<name>`.
