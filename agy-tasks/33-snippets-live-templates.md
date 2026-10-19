# Task 33 – Snippets und Live-Templates

**Ziel:** Kürzel wie `fori`, `log`, `composable`, `viewmodel` erscheinen in der Autovervollständigung und fügen Vorlagen mit Tabstopps ein. Der Nutzer kann eigene Snippets verwalten und im VS-Code-Format importieren/exportieren.
**Ausgangslage:** Keine eigene Snippet-Verwaltung im Repo. Das Einfügen von Server-Snippets existiert bereits (Treffer für „snippet“ in `SoraCodeEditor.kt`, `LspCompletionProvider.kt`, `LspAwareLanguage.kt`, `EditorLanguageFactory.kt`); **lies das zuerst und nutze es weiter**, baue keinen zweiten Einfüge-Mechanismus. `LspCompletionProvider` liefert „Snippet-Insert-Text“ von Servern; sora-editor bringt eine eigene Snippet-Unterstützung mit (Paket `io.github.rosemoe.sora.lang.completion.snippet`; **Version und Verfügbarkeit in der eingebundenen sora-Version prüfen**, nicht aus dem Gedächtnis annehmen).

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg.
* Geheimnisse (Passwörter, Tokens) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 33.1 Modell und Parser (reines Kotlin)
* `Snippet(prefix, name, description, scope: Set<LanguageId>, body)`.
* Syntax wie bei VS Code/LSP: `$1`, `${2:Standard}`, `${3|a,b,c|}`, `$0`, Variablen `$TM_FILENAME`, `$TM_FILENAME_BASE`, `$CURRENT_YEAR`, `$CLIPBOARD`, plus projektspezifisch `$PACKAGE` (aus der Ordnerstruktur/Manifest) und `$CLASSNAME`. Gleichnamige Tabstopps spiegeln.
* Fehlerhafte Vorlagen ablehnen mit Zeilenangabe, nie Absturz.

## 33.2 Eingebaute Snippets (als Assets, versioniert)
Kotlin: `fun`, `main`, `ifn`, `when`, `tryc`, `log`, `dc` (data class), `sealed`. Jetpack Compose: `composable`, `preview`, `lazycolumn`, `state` (`remember { mutableStateOf }`), `vm` (ViewModel + UiState), `scaffold`. Java: `psvm`, `sout`, `fori`, `foreach`, `tryc`. XML: `linearlayout`, `textview`. Nur Muster, die dem Repo-Stil entsprechen (Kommentare/Texte zweisprachig nicht nötig, Code englisch).

## 33.3 Verwaltung
* Speicherung: eigene Snippets als JSON-Datei im App-Speicher (DataStore oder Datei), Reihenfolge und Aktivierung je Sprache.
* Einstellungsseite in `:feature:settings`: Liste, Hinzufügen, Bearbeiten (Editor mit Syntaxprüfung), Löschen, Zurücksetzen auf Standard; Import/Export im VS-Code-Snippet-JSON.

## 33.4 Editor-Integration
* Snippet-Treffer in der Vervollständigung, markiert mit Symbol und Beschreibung, sortiert nach Präfix-Übereinstimmung; zusammen mit LSP-Vorschlägen, ohne Duplikate.
* Einfügen mit Tabstopp-Navigation (Tab/Shift+Tab, Esc beendet); Einrückung an die aktuelle Zeile anpassen; ein Undo-Schritt.

## 33.5 Tests
* Parser: alle Syntaxformen, Verschachtelung, Fehlerfälle, Escape-Zeichen.
* Variablenauflösung.
* Import/Export-Roundtrip mit einer echten VS-Code-Snippet-Datei.
* Einfügen mit Tabstopps im Editor: instrumentierter Test, der kompiliert (`assembleDebugAndroidTest`); Lauf auf Gerät ausdrücklich als ausgeführt/nicht ausgeführt melden.

## Abnahme
Build grün, Tests grün, Bericht mit sora-Version und tatsächlich genutzter Snippet-API.
