# CodeForge Mobile: Zentrale String-Ressourcen (`:core:resources`)

## 1. Übersicht
Zur Reduzierung von hart kodierten Zeichenketten und zur Vorbereitung von Mehrsprachigkeit (i18n) wurden sämtliche Strings aus allen in `settings.gradle.kts` definierten Modulen und Submodulen in ein neues, zentrales Modul **`:core:resources`** ausgelagert.

---

## 2. Modulkonfiguration & Einbindung

### A. Modulstruktur (`core/resources/`)
- **Modulpfad**: `core/resources/`
- **Build-Skript**: `core/resources/build.gradle.kts` (`namespace = "com.codeforge.core.resources"`)
- **Manifest**: `core/resources/src/main/AndroidManifest.xml`
- **Zentrale String-Datei**: `core/resources/src/main/res/values/strings.xml`

### B. Einbindung in `settings.gradle.kts`
```kotlin
include(":core:common")
include(":core:data")
include(":core:datastore")
include(":core:designsystem")
include(":core:domain")
include(":core:navigation")
include(":core:resources")
include(":core:testing")
include(":core:ui")
```

### C. Transitiver Zugriff via `:core:ui` & `:app`
In `core/ui/build.gradle.kts` und `app/build.gradle.kts` wurde `:core:resources` eingebunden (`api(project(":core:resources"))`), sodass Composables und Screens aller Features transparent auf `com.codeforge.core.resources.R.string.*` zugreifen können.

---

## 3. String-Kategorien in `strings.xml`

Die zentralisierten Strings in `core/resources/src/main/res/values/strings.xml` gliedern sich wie folgt:

1. **Allgemeine Aktionen (`action_*`)**:
   - `action_back` ("Zurück"), `action_save` ("Speichern"), `action_cancel` ("Abbrechen"), `action_delete` ("Löschen"), `action_confirm` ("Bestätigen"), `action_close` ("Schließen"), `action_search` ("Suchen"), `action_format` ("Formatieren"), `action_copy` ("Kopieren"), `action_cut` ("Ausschneiden"), `action_paste` ("Einfügen"), `action_rename` ("Umbenennen"), `action_properties` ("Eigenschaften").
2. **Einstellungen Hub (`settings_category_*`)**:
   - Kategorien 1 bis 8 (Erscheinungsbild, Editor, Dateibaum, Terminal, SDK Manager, Extensions, Debug, Über CodeForge Mobile).
3. **Dateibaum-Einstellungen (`filetree_*`)**:
   - Schriftgröße, Einrückungslinien, Dateidetails, versteckte Dateien, Sortieroptionen (Aufstg./Abstg., Name, Typ, Größe, Datum), ViewModes (Module, Projekt, Datei).
4. **Kontextmenü & Dialoge (`dialog_*`)**:
   - Titel und Meldungen für Löschen- und Eigenschaften-Dialoge.
5. **Theme Studio & Editor (`theme_*`, `editor_*`)**:
   - Presets, Colorpicker-Optionen, Word Wrap, Zeilennummern, Minimap, Sticky Scroll.
6. **Extensions & Debug (`extensions_*`, `debug_*`)**:
   - Sprachserver, Tooling Bridge, Logging-Level.

---

## 4. Nutzung in Composables
In Jetpack Compose Komponenten erfolgt der Zugriff nun typsicher über `stringResource`:

```kotlin
import androidx.compose.ui.res.stringResource
import com.codeforge.core.resources.R

Text(text = stringResource(id = R.string.action_save))
Text(text = stringResource(id = R.string.filetree_show_indent_lines))
```
