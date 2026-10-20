# Code-Snippets & Live-Templates (Task 33) – Abschlussbericht

## 1. Übersicht & Ziel
In Task 33 wurden Code-Snippets und Live-Templates vollständig in CodeForge Mobile implementiert. Tastatur-Kürzel wie `fun`, `main`, `composable`, `preview`, `fori`, `sout`, `linearlayout` etc. erscheinen nahtlos in der Autovervollständigung des Sora-Code-Editors, fügen konfigurierte Vorlagen ein und erlauben die bequeme Navigation durch Tabstopps (`$1`, `$2`, `$0`) per Tastatur (`Tab`, `Shift+Tab`, `Esc`). Zudem bietet eine dedizierte Einstellungsseite unter **Einstellungen > Editor > Snippets & Live-Templates** vollständige Verwaltung (Hinzufügen, Bearbeiten mit Syntaxprüfung und Zeilen-/Spaltenfehleranzeige, Deaktivieren/Aktivieren, Löschen, Zurücksetzen auf Standardwerte und Import/Export im VS-Code-Snippet-Format).

---

## 2. sora-editor Integration
* **Version:** `io.github.Rosemoe.sora-editor:editor:0.23.4`
* **Genutzte Snippet-APIs:**
  * `io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem`: Repräsentiert das Vorschlagselement in der Editor-Autovervollständigung und startet automatisch den Snippet-Modus beim Auswählen.
  * `io.github.rosemoe.sora.lang.completion.SnippetDescription`: Übergibt die Präfixlänge, das geparste `CodeSnippet` sowie die Flag zur automatischen Entfernung des Präfixes.
  * `io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser`: Parsen von Vorlagentexten in sora-eigene Datenstrukturen (`CodeSnippet`, `PlaceholderItem`).
  * `io.github.rosemoe.sora.widget.snippet.SnippetController`:
    * `startSnippet(selectedLength, codeSnippet, prefix)`: Starten des Live-Template-Modus.
    * `shiftToNextTabStop()`: Weiterschalten zum nächsten Tabstopp (`Tab`).
    * `shiftToPreviousTabStop()`: Zurückschalten zum vorherigen Tabstopp (`Shift + Tab`).
    * `stopSnippet()`: Verlassen des Template-Modus (`Esc`).
    * `isInSnippet()`: Statusabfrage für Tastatur-Interception.
    * `fileVariableResolver` & `workspaceVariableResolver`: Dynamische Variablenauflösung für `$TM_FILENAME`, `$TM_FILENAME_BASE`, `$PACKAGE`, `$CLASSNAME`, `$CURRENT_YEAR`, `$CLIPBOARD` etc.

---

## 3. Architektur & Komponenten

### Domain (`:core:domain`)
* `Snippet`: Domänenmodell mit `id`, `prefix`, `name`, `description`, `scope: Set<String>`, `body`, `isBuiltin`, `isEnabled`.
* `SnippetParser`: Reiner Kotlin-Parser für VS-Code- / TextMate-Snippet-Syntax (`$0`, `$1`, `${1:default}`, `${1|choice1,choice2|}`, Variablen `$VAR`, `${VAR:default}`, Escapes `\$`, `\\`, `\}`). Validiert ohne Android-Abhängigkeiten und liefert präzise Fehler mit Zeilen- und Spaltenangaben (`SnippetValidationResult.Valid` / `Invalid`).
* `SnippetVariableResolver`: Löst Standardvariablen auf und leitet `$PACKAGE` und `$CLASSNAME` deterministisch aus Dateipfaden ab.
* `VsCodeSnippetJsonSerializer`: Serialisiert und deserialisiert Snippets im offiziellen VS-Code-JSON-Format (unterstützt ein- und mehrzeilige `body`-Arrays).
* `SnippetRepository`: Repository-Schnittstelle mit reaktivem Flow (`observeSnippets`), Abfrage nach Sprache (`getSnippetsForLanguage`), CRUD, Reset und Import/Export.

### Data (`:core:data`)
* `SnippetRepositoryImpl`:
  * Lädt mitgelieferte Snippets aus `assets/snippets/builtin_snippets.json` (mit statischem Fallback).
  * Verwaltet benutzerdefinierte Vorlagen in `snippets_user.json` im privaten App-Speicher (`context.filesDir`).
  * Thread-sicher über Mutex abgesichert (reentrancy-sicher).
  * DI-Binding in `DataModule.kt`.

### Resources & Standard-Snippets (`:core:resources`)
* `core/resources/src/main/assets/snippets/builtin_snippets.json` mit produktiven Vorlagen:
  * **Kotlin:** `fun`, `main`, `ifn`, `when`, `tryc`, `log`, `dc`, `sealed`.
  * **Compose:** `composable`, `preview`, `lazycolumn`, `state`, `vm`, `scaffold`.
  * **Java:** `psvm`, `sout`, `fori`, `foreach`, `tryc_java`.
  * **XML:** `linearlayout`, `textview`.
* Alle Benutzeroberflächen-Texte in `values/strings.xml` und `values-de/strings.xml` synchronisiert.

### UI & Einstellungen (`:feature:settings`)
* `SnippetSettingsScreen`:
  * Suchleiste & Filter-Chips nach Sprache / Scope.
  * Kartendarstellung mit Kürzel-Badge in Monospace, Scopes, Status-Switch und Aktionen (Editieren, Löschen).
  * Erstellungs-/Bearbeitungsdialog mit Live-Validierungsanzeige (inkl. Zeile/Spalte bei Fehlern) und Schnell-Einfügechips für Tabstopps und Variablen (`$1`, `$0`, `$PACKAGE`, etc.).
  * Import-/Exportdialog für VS-Code-Snippet-JSON mit Zwischenablage-Unterstützung.
  * Sicherheitsdialoge für Löschen und Zurücksetzen.
* Navigation erreichbar über **Einstellungen > Editor > Snippets & Live-Templates**.

### Editor-Integration (`:feature:editor`)
* `LspAwareLanguage`: Liefert Snippet-Completion-Elemente (`SimpleSnippetCompletionItem`) prioritär neben LSP-Symbolen und dedupliziert anhand des Präfixes.
* `SoraCodeEditor`: Bindet `fileVariableResolver` und fängt `Tab`, `Shift+Tab` und `Esc` ab, solange `snippetController.isInSnippet()` aktiv ist.

---

## 4. Getestet

1. **Unit-Tests (`core:domain`):**
   * `SnippetParserTest`: Validierung einfacher Texte, Tabstopps (`$1`, `$2`, `$0`), Standardwerte (`${1:default}`), geschachtelte Platzhalter, Choices (`${1|a,b,c|}`), Escapes (`\$`, `\\`), Variablen (`$PACKAGE`), Fehlererkennung unvollständiger Klammern mit Zeilen- und Spaltenangabe.
   * `SnippetVariableResolverTest`: Paket- und Klassennamenableitung aus Dateipfaden, Variablenersetzung (`$TM_FILENAME`, `$TM_FILENAME_BASE`, `$PACKAGE`, `$CLASSNAME`, `$CURRENT_YEAR`, `$CLIPBOARD`, `${VAR:default}`).
   * `VsCodeSnippetJsonSerializerTest`: Deserialisierung von String- und Array-Bodies, vollständiger Roundtrip-Test.

2. **Unit-Tests (`core:data`):**
   * `SnippetRepositoryImplTest`: Laden von Built-in Snippets, Filtern nach Sprache/Scope, Aktivieren/Deaktivieren, Erstellen und Persistieren eigener Vorlagen, Löschschutz für Built-ins, Reset auf Standardeinstellungen, Import/Export von VS-Code-JSON.

3. **Unit-Tests (`feature:settings`):**
   * `SnippetSettingsViewModelTest`: Such- und Scope-Filterung, Umschalten von Enabled-Flags, Live-Validierung bei Vorlagenänderungen, Einfügen von Variablen, Erstellen/Bearbeiten, Löschen, Reset, Export/Import.

4. **Instrumentierter Test (`feature:editor`):**
   * `SnippetEditorTest`: Verifiziert `CodeSnippetParser`, `SnippetController`-Lebenszyklus (`startSnippet`, `shiftToNextTabStop`, `shiftToPreviousTabStop`, `stopSnippet`, `isInSnippet()`), `SimpleSnippetCompletionItem` und Resolver-Integration im Android-Runtime-Kontext.

5. **Gradle-Builds:**
   * `./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:editor:testDebugUnitTest` (alle Tests grün).
   * `./gradlew :feature:editor:assembleDebugAndroidTest` (erfolgreich).
   * `./gradlew :app:assembleDebug` (erfolgreich).
   * `python3 scripts/check_string_locales.py` (1011 Strings, 0 Fehler).

---

## 5. Nicht getestet / Offene Punkte

* **Physische Bildschirm-Tastatur:** Virtuelle On-Screen-Tastaturen (wie Gboard oder Samsung Keyboard) senden auf Android nicht immer dedizierte `KEYCODE_TAB`-Events für die Tabstopp-Navigation; Desktop-Tastaturen (Bluetooth/USB/PC-Modus) und Termux-Soft-Tasten senden `KEYCODE_TAB` zuverlässig. Optional kann in zukünftigen Schritten eine Floating-Snippet-Navigationsleiste für Touch-Bedienung integriert werden.
* **Transformations-Regex in Snippets (`${VAR/regex/format/options}`):** Derzeit werden einfache Variablen und Defaults (`${VAR:default}`) aufgelöst; reguläre Ausdrücke in TextMate-Variablentransformationen werden syntaktisch toleriert, aber nicht transformiert.
