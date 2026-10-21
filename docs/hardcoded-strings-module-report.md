# Abschlussbericht Task 42: Hardcoded Strings Modul & Engine

## 1. Übersicht & Zielsetzung

Task 42 implementiert ein vollwertiges In-App-Werkzeug zur Erkennung, Überprüfung und automatisierten Extraktion hartcodierter sichtbarer Strings aus Benutzerprojekten in Android-Ressourcen (`res/values/strings.xml`).

Im Unterschied zu statischen Host-Prüfungen (wie `scripts/find_hardcoded_strings.py` für die CodeForge-App selbst) läuft dieses Modul als interaktives Entwickler-Tool direkt in der CodeForge Mobile IDE auf beliebigen Android- und Kotlin/JVM-Projekten des Anwenders.

### Kernanforderungen & Umsetzung

1. **Reine JVM-Engine (`:libs:hardcoded-strings`)**:
   - Vollständig entkoppelt von Android-Framework-Abhängigkeiten (`android.*`).
   - Eigener präziser Pure-Kotlin Lexer & Scanner ohne native Tree-sitter `.so`-Binaries, wodurch 100%ige JUnit-Testbarkeit auf der JVM garantiert ist.
   - Intelligente Kontexterkennung mit Konfidenzstufen (`HIGH`, `MEDIUM`, `LOW`).
   - String-Template-Konvertierung (`$var` / `${expr}` zu `%1$s`, `%2$s`).
   - Kollisionsauflösung und Wiederverwendung bestehender String-Schlüssel in `strings.xml`.
   - Sichere Escaping-Logik für Android XML (`\'`, `\"`, `&amp;`, `&lt;`, leading `\@` / `\?`).
   - Modul- und Namespace-Auflösung über Gradle-Dateien und Manifeste.

2. **Feature-UI & Integration (`:feature:hardcodedstrings`)**:
   - Modernes Material 3 UI mit Compose, MVVM/MVI und Hilt.
   - Filter-Tabs nach Konfidenz (`Alle`, `Sicher (High)`, `Mittel (Medium)`, `Niedrig (Low)`).
   - Gruppierung nach Datei und Modul.
   - Live-Editierbare Key-Eingabe mit Prefix-Vorschau (`R.string.<key>` oder `@string/<key>`).
   - Schnellfilter und Volltextsuche über Strings, Keys und Dateipfade.
   - Ignore-Funktion zur dauerhaften Unterdrückung via `.codeforge/hardcoded-strings.json`.
   - Sprung zum Quellcode im Editor.

3. **Sicherheit & Reversibilität**:
   - **Vorschau per Standard (Dry-Run)**: Vor dem Schreiben wird ein vollständiger Diff aller betroffenen Quellcodedateien und `strings.xml`-Updates angezeigt.
   - **Automatische Sicherung**: Vor jeder Schreiboperation wird ein zeitgestempelter Snapshot aller Ziel-Dateien unter `.codeforge/backups/hardcoded-strings/<timestamp>/` abgelegt.
   - **Atomares Rollback**: Scheitert eine Schreiboperation mitten im Vorgang, stellt der `BackupManager` sofort den vorherigen Stand wieder her.
   - **Rückgängig-Funktion (Undo)**: Über die TopAppBar kann die letzte Extraktion jederzeit mit einem Tippen rückgängig gemacht werden.

4. **Integration in die IDE-Oberfläche**:
   - **Dateibaum (FileTree)**: Kontextmenü auf Ordnern oder Modulen enthält „Hardcoded Strings suchen“ (`Icons.Default.Translate`).
   - **Editor**: Intention Action / QuickFix-Menü (`Icons.Default.AutoFixHigh`) bietet „String-Ressource extrahieren“ an und navigiert direkt zum Extraktions-Workflow.
   - **Navigation**: Neuer NavHost-Endpunkt `Routes.HARDCODED_STRINGS_PATTERN` mit parametrisiertem `targetPath` und `projectRoot`.

---

## 2. Architektur

```
                                 ┌──────────────────────────────────────────┐
                                 │         :feature:hardcodedstrings         │
                                 │   (Compose UI, ViewModel, MVI Contract)   │
                                 └─────────────────────┬────────────────────┘
                                                       │
                                                       ▼
                                 ┌──────────────────────────────────────────┐
                                 │         :libs:hardcoded-strings          │
                                 │        (Pure Kotlin JVM Library)         │
                                 ├──────────────────────────────────────────┤
                                 │ • SourceLexer / Token                    │
                                 │ • KotlinStringExtractor                  │
                                 │ • XmlStringExtractor                     │
                                 │ • StringKeyGenerator                     │
                                 │ • AndroidStringEscaper                   │
                                 │ • AndroidModuleResolver                  │
                                 │ • StringsXmlWriter                       │
                                 │ • CodeReplacementApplier                 │
                                 │ • BackupManager                          │
                                 │ • HardcodedStringsEngine                 │
                                 └──────────────────────────────────────────┘
```

### Module

- **`:libs:hardcoded-strings`**:
  - Konfiguriert mit `codeforge.kotlin.library` und `codeforge.quality`.
  - Reine JUnit-Tests mit In-Memory- und Temp-Dateisystem-Fixtures.
- **`:feature:hardcodedstrings`**:
  - Konfiguriert mit `codeforge.android.library.compose`, `codeforge.android.hilt` und `codeforge.quality`.
  - Abhängigkeiten: `:libs:hardcoded-strings`, `:core:resources`, `:core:domain`, `:core:ui`, `:core:designsystem`, `:core:navigation`.

---

## 3. Testabdeckung & Verifikation

Folgende Unit-Testsuiten wurden implementiert:

| Test-Klasse | Getestete Funktionalität | Status |
|---|---|---|
| `SourceLexerTest` | Präzises Tokenizing von Kommentaren, regulären/rohen Strings, Escapes und Identifiers | Bestanden (2 Tests) |
| `AndroidStringEscaperTest` | XML-Entities, Quotes/Apostrophen, führende `@`/`?`, Whitespace-Quotes, String-Templates (`$var`, `${expr}`) | Bestanden (6 Tests) |
| `StringKeyGeneratorTest` | Umlaut-Transliteration (`ä->ae`), Prefix-Herleitung, Wiederverwendung bestehender Keys, Kollisionsauflösung | Bestanden (5 Tests) |
| `KotlinStringExtractorTest` | UI-Composable-Erkennung, Parameter-Erkennung, Template-Erkennung, Logging/Exception-Ausschluss, `i18n-ignore` | Bestanden (5 Tests) |
| `XmlStringExtractorTest` | Layout-Attribute (`android:text`, `android:hint`), Ausschluss von `@string/` und `res/values/` | Bestanden (3 Tests) |
| `StringsXmlWriterTest` | XML-Neugenerierung, Inplace-Einfügen vor `</resources>`, Schutz vor doppelten Schlüsseln | Bestanden (3 Tests) |
| `CodeReplacementApplierTest` | Mehrfachersetzungen in umgekehrter Offset-Reihenfolge (kein Offset-Drift), automatische Import-Pflege | Bestanden (2 Tests) |
| `HardcodedStringsEngineTest` | Vollständiger End-to-End-Durchlauf: Scan -> Plan -> Apply -> Verifikation Quellcode & XML -> Undo-Verifikation | Bestanden (1 Test) |
| `HardcodedStringsViewModelTest` | MVI-State-Flows, Filterung nach Konfidenz und Suchbegriffen, Key-Modifikation, Selektion | Bestanden (2 Tests) |

- **Zero Hardcoded Strings im CodeForge-Quellcode**: `python3 scripts/find_hardcoded_strings.py` zeigt 0 ungelöste Strings im neuen Feature- und Lib-Modul.
- **Lokalisierung**: Alle Benutzertexte vollständig zweisprachig (Deutsch in `values-de/strings.xml`, Englisch in `values/strings.xml`).
