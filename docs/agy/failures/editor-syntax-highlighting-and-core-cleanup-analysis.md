# AGY Failure & Architecture Report: Editor Syntax Highlighting Root Cause & Core Refactoring Analysis

**Datum:** 2026-10-01  
**Komponenten:** `:feature:editor`, `:feature:settings`, `:feature:filetree`, `:core:*`  
**Autor:** Antigravity AI (AGY)  
**Status:** Behoben & Dokumentiert  

---

## 1. Problemstellung & Fehlersymptome

1. **Fehlendes Code-Coloring im Editor:**  
   Beim Öffnen von Quellcodedateien (`.kt`, `.java`, `.xml`, `.json`, etc.) im `SoraCodeEditor` wurde kein Syntax-Highlighting angewendet. Der Code erschien unbunt als einfacher Text.
2. **Inkonsistente Switch- & Textfarben:**  
   In den Einstellungsbildschirmen (`EditorSettingsScreen`, `FileTreeSettingsScreen`, `ThemeBuilderScreen`) waren einige Schalter und Beschreibungstexte in bestimmten Theme-Modi (Dark/Light) nicht ausreichend kontrastreich oder unlesbar.
3. **Altlasten & Totholz im Codebase:**  
   Im Modul `:feature:editor` sowie in den `core/*`-Modulen existierten ungenutzte Legacy-Klassen und redundante Hilfsstrukturen aus früheren Refactoring-Phasen.

---

## 2. Ursachenanalyse (Root Cause Analysis)

### 2.1 Ursache für das fehlende Syntax-Highlighting
- **`CodeForgeLanguage` Wrapper-Klasse:**  
  Im Modul `:feature:editor` existierte eine Wrapper-Klasse `CodeForgeLanguage(private val delegate: Language, ...): Language by delegate`.
- **Interne Type-Checks des Sora Editors:**  
  Der `sora-editor` verlässt sich intern auf Typprüfungen wie `if (language is TextMateLanguage)` oder `if (language is MonarchLanguage)`, um TextMate- bzw. Monarch-Grammatik-Analyzer zu registrieren und das Farbschema `TextMateColorScheme` an das Tokenizing-Ergebnis zu binden.
- **Fehlgeschlagene Instanzprüfungen:**  
  Durch die Einbettung der Sprachinstanzen in `CodeForgeLanguage` lieferten die `instanceof`-Prüfungen im Sora Editor stets `false`. Infolgedessen wurden keine Syntax-Tokens erzeugt und der Code blieb unbunt.

### 2.2 Ursache für Text- & Switch-Kontrastprobleme
- **Fehlende explizite Farbdefinitionen:**  
  Titeltexte in `SettingSwitchRow` besaßen keine explizite Zuweisung von `color = MaterialTheme.colorScheme.onSurface`. Bei Verschachtelungen in bestimmten Container-Elementen wurde dadurch ein unpassender Standard-Farbwert verwendet.
- **Switch Defaults:**  
  Schalter nutzten teilweise unvollständige `SwitchDefaults.colors()` Parameter, wodurch die Track- und Thumb-Farben im inaktiven/aktiven Zustand in dunklen Themes nicht ausreichend abgehoben waren.

---

## 3. Behebung & Korrekturmaßnahmen

1. **Direkte Sprachinstanz-Rückgabe (`SoraLanguageProvider.kt`):**  
   Die Methode `getLanguage(...)` gibt nun `TextMateLanguage`, `MonarchLanguage` oder `BuiltinJavaLanguage` direkt zurück. Der problematische `CodeForgeLanguage`-Wrapper wurde entfernt.
2. **Explizites Material 3 Farb-Mapping:**  
   In `EditorSettingsScreen.kt`, `FileTreeSettingsScreen.kt` und `ThemeBuilderScreen.kt` wurden alle Titeltexte explizit an `MaterialTheme.colorScheme.onSurface` und Beschreibungstexte an `onSurfaceVariant` gebunden.
3. **Sauberer Theme-Fallback:**  
   `applySchemeByName` wählt bei leerem Theme-Namen automatisch das passende Standard-Farbschema (`codeforge` bzw. `quietlight`) und sorgt für eine zuverlässige Entwertung (`editor.invalidate()`).

---

## 4. Modul-Analyse & Aufräumarbeiten

### 4.1 Modul `:feature:editor`

| Datei | Status | Beschreibung / Begründung |
|---|---|---|
| `SoraCodeEditor.kt` | ✅ Aktiv | Haupt-Compose-Wrapper für Sora CodeEditor |
| `EditorScreen.kt` | ✅ Aktiv | Haupt-UI mit Tabs, Suche & Menü |
| `EditorViewModel.kt` | ✅ Aktiv | ViewModel für Dateien, Tabs, LSP & Settings |
| `EditorContract.kt` | ✅ Aktiv | UiState, OpenFile & Events |
| `SoraLanguageProvider.kt` | ✅ Aktiv | Zentraler Loader für TextMate & Monarch |
| `SoraEditorAppearance.kt` | ✅ Aktiv | Wendet Datastore-Konfiguration auf Editor an |
| `CodeForgeLanguage.kt` | ❌ **Entfernt/Inaktiv** | Veralteter Wrapper, der `instanceof` blockierte |
| `SoraEditorHost.kt` | ❌ **Redundant** | Ersetzt durch `EditorScreen.kt` |
| `EditorSearchPanel.kt` | ❌ **Redundant** | Ersetzt durch integrierte Suche in `EditorScreen.kt` |
| `EditorAssetRegistry.kt` | ❌ **Redundant** | Ersetzt durch `AssetsFileResolver` |

### 4.2 Module `core/*`
- **`:core:datastore`**: Sauberes Proto-DataStore-Mapping. Workspace-Fallback auf `/storage/emulated/0/CodeForgeMobileProjects` vereinheitlicht.
- **`:core:designsystem`**: Saubere Material 3 Theme-Palette (`Theme.kt`, `ThemePresets.kt`).
- **`:core:domain` & `:core:data`**: Strikte Trennung von Repository-Interfaces (`FileSystemRepository`, `GitRepository`, `ComposeSourceAnalyzer`, `TemplateEngineRepository`) und deren Implementierungen.

---

## 5. Fazit

Mit der Eliminierung des `CodeForgeLanguage`-Wrappers funktioniert das Syntax-Highlighting im Sora CodeEditor wieder einwandfrei für alle unterstützten Sprachen. Alle Einstellungen und Toolbar-Elemente entsprechen nun strikten Material 3 Kontrastrichtlinien.
