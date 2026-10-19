# Task 38 – KI-Assistent im Editor

**Ziel:** Ein Chat- und Aktionsbereich im Editor, der Fragen zum Code beantwortet, Fehler aus Build/LSP erklärt und Änderungen als Vorschlag mit Diff anbietet, die der Nutzer annimmt oder verwirft. Kein Eingriff ohne Bestätigung.
**Ausgangslage:** Es gibt eine Internetberechtigung (`INTERNET`) und eigene HTTP-Nutzung an einzelnen Stellen (`HttpMavenMetadataClient`, `FileDownloader`, `CompilerRuntimeProviderImpl`), aber keinen KI-Code. Geheimnisse werden bisher über `SecretStorage` (Keystore-gestützt, Schlüssel-Alias „codeforge_git_secret_key“) abgelegt. Das Plugin-System (`:libs:plugin-api`) existiert. Zur Anregung: Cosmic IDE (fremdes Projekt) bietet laut Projektseite KI-Vervollständigung über Google Gemini; das ist keine Vorgabe.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-Daten-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog (`gradle/libs.versions.toml`), Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) muss grün bleiben.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Kein „funktioniert“ ohne Beleg. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse (Passwörter, Tokens, API-Schlüssel) nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/<name>-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` um den Task ergänzen (Commit-Hash eintragen).
* Build muss grün sein: `./gradlew :app:assembleDebug` und die Tests der betroffenen Module (`./gradlew :<modul>:testDebugUnitTest`).

## 38.1 Anbieter-Abstraktion (`:core:domain` + `:libs:ai-client`, neues Modul)
* `AiProvider`-Schnittstelle mit `streamChat(messages, options): Flow<AiEvent>` (`TextDelta`, `Usage`, `Done`, `Error`). Konfigurierbare Anbieter: **Anthropic (Messages-API)**, **OpenAI-kompatibel** (eigene Basis-URL, deckt viele Dienste und lokale Server wie Ollama ab) und **Google Gemini**. Modellname als freies Textfeld mit Vorschlägen; **keine Modell-IDs fest einbauen, die veralten**: Vorschläge aus der Anbieter-Dokumentation bzw. einer Modell-Listen-Anfrage des Anbieters holen und die Quelle im Bericht nennen.
* Streaming per Server-Sent-Events, Abbruch jederzeit, Zeitlimits, Wiederholung mit Backoff bei 429/5xx, verständliche Fehlertexte (Schlüssel ungültig, Kontingent, offline).
* Eigene HTTP-Schicht (z. B. OkHttp, falls bereits im Projekt oder mit Begründung neu) mit TLS-Prüfung an, kein Deaktivieren der Zertifikatsprüfung.

## 38.2 Schlüssel und Datenschutz (verbindlich)
* API-Schlüssel nur im verschlüsselten Speicher (bestehendes `SecretStorage` erweitern oder verallgemeinern, nicht kopieren), Eingabefeld mit Maskierung, „Test“-Schaltfläche, nie in Logs, nie in Crash-Berichten.
* Vor dem ersten Senden Hinweis: Inhalte verlassen das Gerät und gehen an den gewählten Anbieter. Einstellung „Immer fragen / Nur markierter Kontext / Ganze Datei“.
* **Ausschlussliste, nie senden:** `*.jks`, `*.keystore`, `*.p12`, `.env*`, `local.properties`, `signing.properties`, Dateien aus `.gitignore` mit Geheimnismustern, Passwort-/Token-Muster im Text (einfache Erkennung, Treffer schwärzen oder abbrechen). Die Liste ist in den Einstellungen sichtbar.
* Vor dem Senden zeigt ein einklappbarer „Was wird gesendet“-Bereich genau den Kontext (Dateien, Auszüge, Fehlermeldungen).
* Dateiinhalte und Fehlertexte sind **nicht vertrauenswürdig** (Prompt-Injection): Der Assistent führt nie selbstständig Befehle aus; Änderungen nur als Vorschlag.

## 38.3 Kontext und Aktionen
* Kontextquellen wählbar: aktuelle Datei, Auswahl, offene Dateien, Probleme/Build-Ausgabe (Task 28), LSP-Diagnosen, `git diff`. Größenbegrenzung mit sichtbarer Kürzung statt stillem Abschneiden.
* Schnellaktionen am Cursor/an der Auswahl: **Erklären**, **Fehler beheben** (aus Diagnose/Problemliste), **Tests schreiben**, **Umschreiben mit Anweisung**, **Dokumentation (KDoc)** erzeugen.
* Chat-Panel mit Verlauf je Projekt (lokal gespeichert, löschbar), Markdown mit Code-Blöcken (Kopieren, „In Datei einfügen“, „Als Diff anwenden“).

## 38.4 Änderungen anwenden
* Antwort mit Änderung → Diff-Ansicht (bestehende `DiffViewer`-Bausteine aus `:feature:git` wiederverwenden, ggf. in ein gemeinsames Modul heben) → Annehmen/Verwerfen je Datei bzw. Block. Anwenden als ein Undo-Schritt je Datei, vorher Sicherung im Cache.
* Mehrdateien-Änderungen: Liste mit Häkchen je Datei, nichts wird ohne Annehmen geschrieben.
* Warnung, wenn die Datei sich seit der Anfrage geändert hat (Versionsvergleich) statt blind zu überschreiben.

## 38.5 Kosten und Nutzung
Token-Anzeige je Antwort, wenn der Anbieter sie liefert; optionales Tageslimit (Anzahl Anfragen), das nur warnt. Keine geschätzten Euro-Beträge ohne belegte Preisquelle.

## 38.6 Tests
* SSE-Parser je Anbieter mit echten Beispielstrom-Ausschnitten als Fixtures (Mehrzeiler, Teil-JSON, Fehlerereignis, Abbruch).
* Ausschluss- und Schwärzungslogik (viele Fälle, auch Fehlalarme).
* Diff-Anwenden: Konflikt bei geänderter Datei, Undo, Mehrdateien.
* ViewModel mit Fake-Anbieter: Streaming, Abbruch, Fehler.
* Echter Netzaufruf: nur mit einem Schlüssel des Inhabers möglich → im Bericht „nicht ausgeführt“, außer er liefert ihn gesondert.

## Abnahme
Build grün, Tests grün, Sicherheitsabschnitt im Bericht (wo liegt der Schlüssel, was wird gesendet, was nie), ehrlicher Vermerk zu ungetesteten Anbieteraufrufen.
