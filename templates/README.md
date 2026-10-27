# Plugin-Vorlagen

Je Ordner eine lauffähige Minimalvorlage. Ausführliche Anleitung: `docs/extension-authoring.md`, Referenz: `docs/extensions.md`.

| Ordner | Art | Kurzbeschreibung |
|---|---|---|
| `color-theme` | deklarativ | App-Farbschema + Editor-Theme |
| `icon-theme` | deklarativ | Datei-/Ordner-Icons |
| `language-pack` | deklarativ | UI-Übersetzung |
| `grammar` | deklarativ | Syntaxfarben für eigene Dateitypen |
| `snippets` | deklarativ | Code-Bausteine |
| `lsp-server` | deklarativ | Language-Server-Anbindung |
| `formatter` | deklarativ | Externer Formatierer (stdin → stdout) |
| `extension-pack` | deklarativ | Bundle aus mehreren Plugins |
| `code-plugin` | Code (Gradle) | Befehle, Menüs, Ereignisse |

## Bauen

```bash
python3 templates/build.py              # alle deklarativen → templates/build/*.zip (mit SHA-256)
python3 templates/build.py color-theme  # nur eine
./gradlew :templates:code-plugin:packagePlugin   # Code-Plugin (ANDROID_HOME nötig)
```

`TemplatesValidTest` (`:core:domain`) prüft die Vorlagen mit den echten Parsern und der Store-Regel.
