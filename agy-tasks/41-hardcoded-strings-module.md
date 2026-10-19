# Task 41 – Modul „hardcodedstrings“: harte Texte in Nutzerprojekten finden und nach strings.xml auslagern

**Ziel:** Ein neues Modul, das in einem **beliebigen Android-Projekt des Nutzers** (dem in CodeForge geöffneten Projekt, nicht in CodeForge selbst) hart kodierte, sichtbare Texte in Quelldateien findet, sie in `res/values/strings.xml` des zuständigen Moduls schreibt und im Code durch Ressourcenzugriffe ersetzt. Der Nutzer prüft jeden Fund und bestätigt; ohne Bestätigung wird nichts geschrieben.
**Abgrenzung:** Task 17 macht dasselbe für das **CodeForge-Repo** über das Skript `scripts/find_hardcoded_strings.py` (Allowlist, Konventionen in `docs/resources-and-strings.md`). Das neue Modul ist die generische, in der App laufende Variante. **Lies das Skript und `docs/resources-and-strings.md` zuerst** und übernimm die dort bewährten Regeln (Kontexte, Ausnahmen), kopiere sie nicht blind. Die beiden Wege dürfen sich nicht widersprechen.
**Umfang der Auslegung:** „Submodule“ ist hier ein Gradle-Modul: Engine als `:libs:hardcoded-strings` (reine JVM-Library, Convention-Plugin `codeforge.kotlin.library` wie `:libs:code-tools`), Oberfläche als `:feature:hardcodedstrings` (Compose, Hilt). In `settings.gradle.kts` eintragen.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*`, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog, Convention-Plugins aus `build-logic`.
* Alle eigenen UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); String-Check (Task 17/18) bleibt grün.
* Nichts erfinden: Was du nicht ausgeführt hast, steht im Bericht unter **nicht getestet**. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst.
* Bericht in `docs/hardcoded-strings-module-report.md` (**getestet / nicht getestet / offene Punkte**); `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen.
* Build grün: `./gradlew :app:assembleDebug` und `./gradlew :libs:hardcoded-strings:test :feature:hardcodedstrings:testDebugUnitTest`.

## 41.1 Was als „hart kodierter Text“ gilt

**Fundorte**
* **Kotlin:** Literale als Argument bekannter UI-Parameter und -Aufrufe: `Text(...)`, `text =`, `title =`, `label =`, `placeholder =`, `supportingText =`, `contentDescription =`, `confirmText =`, `dismissText =`, `message =`, `hint =`, `Toast.makeText(..., "…", ...)`, `Snackbar`/`showSnackbar`, `AlertDialog`-Titel/-Texte, `setText`, `setTitle`, `setMessage`, `setHint`, `setContentDescription`.
* **Java:** dieselben Aufrufe (`setText("…")`, `Toast.makeText(…, "…", …)`, `new AlertDialog.Builder(…).setTitle("…")` usw.).
* **XML-Layouts und Menüs:** `android:text`, `android:hint`, `android:contentDescription`, `android:title`, `app:title`, `android:label`, `android:summary`, `android:entries`-Arrays nur als Hinweis; Werte, die nicht mit `@string/` oder `?attr/` beginnen und nicht reine Platzhalter sind (`tools:`-Attribute ignorieren).
* **AndroidManifest:** `android:label` mit Literal.

**Ausnahmen (nie vorschlagen oder nur mit niedriger Sicherheit):** Log-Tags und -Meldungen (`Log.*`, `Timber.*`, `println`), Ausnahmetexte (`error(...)`, `require(...)`, `throw …(...)`, `check(...)`; per Einstellung zuschaltbar), `const val`, Annotationsargumente, `@Preview`-Funktionen und Beispieldaten darin, Test-Quellen (`src/test`, `src/androidTest`, `src/*Test`), Build-Dateien (`*.gradle(.kts)`), generierter Code (`build/`, `generated/`, `.gradle/`), Regex, Pfade, URLs, MIME-Typen, Intent-Actions, JSON-/SQL-/Schlüsselnamen, reine Bezeichner (camelCase/snake_case ohne Leerzeichen), Strings ohne Buchstaben, bereits suppressierte Stellen (`tools:ignore="HardcodedText"`, `@SuppressLint("HardcodedText")`, `@Suppress("HardCodedStringLiteral")`, Kommentar `// i18n-ignore`).

**Sicherheit je Fund:** `HIGH` (bekannter UI-Parameter, enthält Buchstaben und Leerzeichen/Satzzeichen oder Großschreibung), `MEDIUM`, `LOW`. Standard in der Oberfläche: `HIGH` und `MEDIUM` sichtbar, `LOW` einklappbar. Die Regeln sind als Daten konfigurierbar (Liste der Aufrufe/Parameter), nicht fest im Code verstreut.

## 41.2 Engine (`:libs:hardcoded-strings`, JVM-testbar)

**Lesen der Quelle:** Eigener Lexer für Kotlin und Java, der Kommentare, Zeichen-Literale, normale und rohe Strings (`"""`), Escapes und String-Templates (`$name`, `${…}`) korrekt überspringt bzw. zerlegt, und je Literal die Position (Datei, Zeile, Spalte, Start-/Endoffset) liefert. Kontext je Literal aus einfacher Klammer- und Block-Analyse: umgebender Aufruf, benanntes Argument, umgebende Funktion samt Annotationen (`@Composable`, `@Preview`), umgebende Klasse. **Tree-sitter nur verwenden, wenn der Bericht belegt, dass der Lexer-Kontext für typische Fälle nicht reicht** (die nativen Bibliotheken sind Android-gebunden und nicht als JVM-Test lauffähig). Entscheidung mit Begründung im Bericht.

**XML:** Ein Parser, der die **Zeilen-/Spaltenposition der Attributwerte** erhält (z. B. SAX mit Locator oder eigener Scanner), damit gezielt ersetzt werden kann, ohne die Datei umzuformatieren.

**Modulzuordnung:** Für jede Quelldatei das zuständige Android-Modul ermitteln (nächstes übergeordnetes Verzeichnis mit `build.gradle(.kts)` und `src/main/AndroidManifest.xml`), daraus `src/main/res/values/strings.xml`, den `R`-Paketnamen (`namespace` aus der Build-Datei, sonst `package` im Manifest) und ob das Modul Compose verwendet (Abhängigkeit/Plugin). Kein Android-Modul → Funde melden, aber nicht auslagern (z. B. reines JVM-Modul). Kotlin-Multiplatform-Compose-Ressourcen (`composeResources`, `Res.string`) erkennen und **ausdrücklich nicht** unterstützen (Hinweis statt falscher Änderung).

## 41.3 Schlüssel und strings.xml

* **Schlüsselname:** aus dem Text abgeleitet, `snake_case`, Umlaute transliteriert (`ä→ae`, `ß→ss`), höchstens 40 Zeichen (an Wortgrenze kürzen), Präfix aus Datei/Bildschirm (`settings_title`), mit Ziffer beginnend → Präfix voranstellen. Reservierte Wörter und unzulässige Zeichen vermeiden. Bestehende Schlüsselmuster des Moduls erkennen und fortsetzen (z. B. Präfix `login_`).
* **Wiederverwendung:** Gibt es denselben **Wert** schon in `strings.xml` desselben Moduls, den vorhandenen Schlüssel verwenden (kein Duplikat). Kollision mit anderem Wert → numerisches Suffix, nie überschreiben.
* **Schreiben, ohne die Datei umzuformatieren:** Neue `<string>`-Einträge vor `</resources>` einfügen, Einrückung der Datei übernehmen, bestehende Kommentare und Reihenfolge unangetastet lassen. Existiert die Datei nicht, mit Standardkopf anlegen (`<?xml version="1.0" encoding="utf-8"?><resources>…</resources>`).
* **Escaping nach Android-Regeln:** `'` → `\'`, `"` → `\"`, `&` → `&amp;`, `<` → `&lt;`, führendes `@`/`?` → `\@`/`\?`, Zeilenumbruch → `\n`, führende/endende Leerzeichen → Wert in Anführungszeichen, literales `%` bei vorhandenen Formatargumenten → `%%`. Unicode unverändert lassen.
* **Templates:** `"Hallo $name"` / `"Hallo ${user.name}"` → Wert `Hallo %1$s`, Argumente in Reihenfolge übergeben; mehrere Argumente `%1$s %2$s`; Zahlen-Ausdrücke heuristisch als `%1$d` nur bei eindeutigem Typ, sonst `%s` mit Hinweis. Verschachtelte oder komplexe Ausdrücke (Funktionsaufrufe mit Klammern, Lambdas) → Fund als „manuell“ markieren.
* **Nicht übersetzbar:** Option „als `translatable="false"` anlegen“ je Fund.
* **Weitere Sprachen:** Nur `values/strings.xml` wird geschrieben. Optional (Schalter, Standard aus): fehlende Schlüssel als Kommentar-Hinweis in vorhandenen `values-xx/strings.xml` auflisten, **nicht** automatisch übersetzen.

## 41.4 Ersetzung im Code

| Kontext | Ersetzung | Import |
|---|---|---|
| Kotlin, innerhalb `@Composable` | `stringResource(R.string.key, args)` | `androidx.compose.ui.res.stringResource`, `<namespace>.R` |
| Kotlin, mit `Context` im Gültigkeitsbereich (`context`, `this` ist `Context`/`Activity`/`Fragment`) | `context.getString(R.string.key, args)` bzw. `getString(R.string.key, args)` | `<namespace>.R` |
| Kotlin, kein Context erkennbar | **nicht automatisch ersetzen**; Fund als „manuell“ mit Vorschlag (`@StringRes`-Parameter / Context übergeben) |
| Java | `getString(R.string.key)` / `context.getString(…)` | `<namespace>.R` |
| XML | `@string/key` | – |

Regeln: Import nur ergänzen, wenn nicht vorhanden, an der lexikographisch richtigen Stelle des vorhandenen Import-Blocks; nie doppelte Imports. Existiert ein `R`-Konflikt (anderes `R` bereits importiert), Alias vorschlagen statt zu überschreiben. Den Text nur an der genauen Literal-Position ersetzen (kein Suchen-und-Ersetzen über die Datei); gleichlautende Literale an anderen Stellen sind eigene Funde. Zeilenenden (CRLF/LF) und Einrückung beibehalten.

## 41.5 Anwenden: sicher und umkehrbar

1. **Vorschau (Trockenlauf) ist der Standard:** Liste der Änderungen mit Diff je Datei (bestehende `DiffViewer`-Bausteine aus `:feature:git` wiederverwenden) und die geplanten Einträge für `strings.xml`.
2. **Sicherung:** Vor dem Schreiben alle betroffenen Dateien in einen Cache-Ordner kopieren; Wiederherstellung per Aktion „Rückgängig“. Zusätzlich Hinweis, wenn das Projekt ein Git-Repo mit ungesicherten Änderungen ist (Abfrage über `GitRepository.status`), und Angebot, vorher zu committen.
3. **Atomar:** Schlägt eine Datei fehl, alles zurückrollen und Fehler anzeigen.
4. **Offene Editor-Puffer:** Änderungen an geöffneten Dateien über den Editor-Puffer (ein Undo-Schritt je Datei), nicht am Puffer vorbei auf die Platte schreiben. Wurde die Datei seit dem Scan geändert (Zeitstempel/Prüfsumme), Fund verwerfen und neu scannen.
5. **Prüfung nach dem Anwenden:** (a) jede neu verwendete `R.string.x` existiert in `strings.xml`; (b) keine doppelten Schlüssel; (c) geänderte Dateien lassen sich erneut lexen (Klammern/Strings ausgeglichen); (d) optional, wenn Task 28 vorhanden ist: `compile<Variant>Kotlin` des Moduls über den Build-Läufer, Ergebnis in den Probleme-Reiter. Ergebnis der Prüfung dem Nutzer zeigen.
6. **Idempotent:** Ein zweiter Lauf auf dem Ergebnis findet nichts Neues zum Auslagern.

## 41.6 Oberfläche (`:feature:hardcodedstrings`)

* Einstieg: Kontextmenü im Dateibaum („Hardcodierte Strings suchen“) für Datei, Ordner, Modul oder Projekt; Befehl im Editor für die aktuelle Datei.
* Ergebnisliste gruppiert nach Modul → Datei, je Fund: Zeile, Code-Auszug, Text, vorgeschlagener Schlüssel (bearbeitbar), Sicherheit, Häkchen. Filter (Sicherheit, Modul, Dateityp), Suche, „Alle sichtbaren auswählen“. Aktionen je Fund: **Auslagern**, **Ignorieren** (dauerhaft), **Als nicht übersetzbar**, **Springe zur Stelle**.
* Ignorierliste pro Projekt in `.codeforge/hardcoded-strings.json` im Projektordner (Nutzer entscheidet, ob er sie einchecken will); Format: Datei + Text, kein Zeilenbezug, damit sie Änderungen übersteht.
* Fortschritt und Abbruch beim Scan (Coroutine, abbrechbar); große Projekte: Dateien parallel mit begrenzter Parallelität, Ergebnisse gestreamt, Speicher begrenzt; `build/`, `.git/`, `.gradle/`, `node_modules/` überspringen. Dateigröße-Limit (z. B. 2 MB) mit Hinweis.
* Leerzustand („nichts gefunden“), Fehlerzustand (nicht lesbare Datei) und Hinweis bei nicht unterstützten Projekttypen (kein Android-Modul, Multiplatform-Ressourcen).
* Alle Texte zweisprachig in `:core:resources`.

## 41.7 Tests (Pflicht, JVM)

Testdaten als Dateien unter `src/test/resources`, mit **kleinen, erfundenen Beispielprojekten** (Kotlin, Java, XML) und je erwartetem Ergebnis.
* **Lexer:** Kommentare, Raw-Strings, Escapes (`\"`, `\\`, `\n`, `ä`), Templates mit verschachtelten Klammern, Zeichen-Literale, Strings in Kommentaren/Strings.
* **Fundregeln:** Treffer und Nicht-Treffer für alle Ausnahmen aus 41.1 (jede Ausnahme mindestens ein Fall), Unterdrückungen.
* **Schlüsselbildung:** Umlaute, Länge, Kollision, Wiederverwendung, Ziffernanfang.
* **Escaping:** `'`, `"`, `&`, `<`, `%`, `@`, führendes/endendes Leerzeichen, Mehrzeiler.
* **Templates:** ein, mehrere, verschachtelte Ausdrücke, `$`-Literal ohne Platzhalter.
* **Ersetzung:** Compose, Context, Java, XML, kein Context → „manuell“; Importe (vorhanden, fehlend, Konflikt, Reihenfolge); CRLF.
* **Schreiben von `strings.xml`:** vorhandene Datei bleibt außer den neuen Zeilen byte-genau erhalten (Test mit Kommentaren und ungewöhnlicher Einrückung); neue Datei; Modulzuordnung bei mehreren Modulen.
* **Atomarität/Rückgängig:** Fehler in der zweiten Datei → erste wiederhergestellt; Rückgängig stellt alles wieder her; zweiter Lauf findet nichts.
* **Ende-zu-Ende auf einem Beispielprojekt:** Scan → Auswahl → Anwenden → Prüfung (a)–(c) → erneuter Scan ohne Funde.
* UI: ViewModel-Tests mit Fake-Engine (Auswahl, Filter, Ignorieren, Fortschritt, Abbruch); `assembleDebugAndroidTest` kompiliert.
* **Nicht in JVM-Tests prüfbar** (im Bericht ausdrücklich getestet oder nicht getestet ausweisen): echte Kompilierung des Ergebnisses in einem Android-Projekt, Verhalten auf dem Gerät.

## Abnahme

Build grün, alle Tests grün, Bericht mit (1) Entscheidung Lexer vs. Tree-sitter samt Begründung, (2) Liste der unterstützten und bewusst nicht unterstützten Fälle, (3) ehrlicher Aussage, ob das Ergebnis in einem echten Android-Projekt kompiliert wurde (sonst „nicht getestet“), (4) Beleg, dass das Verhalten zu Task 17 (`scripts/find_hardcoded_strings.py`) passt oder wo es bewusst abweicht.
