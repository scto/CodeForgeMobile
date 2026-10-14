# Task 18 – Strings: Standard auf Englisch umstellen, Deutsch nach `values-de`

**Ziel:** `:core:resources` wird zweisprachig mit **Englisch als Standard** (`values/`) und **Deutsch** als Übersetzung (`values-de/`).
Heute ist `values/strings.xml` deutsch (745 Einträge), `values-en/strings.xml` enthält nur die ~40 Projekt-Assistent-Texte (`pw_*`).
Arbeitsverzeichnis = Projekt-Root `CodeForgeMobile/`. Einmaliger Task (danach gilt Task 17 mit der Anpassung unten).

Pflichtlektüre: `core/resources/README.md`, `docs/resources-and-strings.md`, `agy-tasks/17-externalize-hardcoded-strings.md`.

## Vorbedingungen

* Sauberer Git-Stand, eigener Branch (`git switch -c i18n-en-default`). Task 17 nicht parallel laufen lassen.
* Referenz-Stand merken: `BASE=$(git rev-parse HEAD)` – das deutsche Original ist `git show $BASE:core/resources/src/main/res/values/strings.xml`.
* Prüfskript `scripts/check_string_locales.py` liegt im Repo (falls nicht: aus dieser Task-Lieferung übernehmen).

## Ablauf

1. **Deutsch sichern (zuerst, bevor irgendetwas übersetzt wird):**
   `mkdir -p core/resources/src/main/res/values-de && cp core/resources/src/main/res/values/strings.xml core/resources/src/main/res/values-de/strings.xml`
   Kommentarzeile 2 in `values-de` auf „Deutsche Texte (Übersetzung); Standard ist Englisch in values/" ändern, sonst **keine** Änderung am Inhalt.
2. **Englisch einsetzen in `values/strings.xml`** (Datei bleibt, Schlüssel/Reihenfolge/Gruppenkommentare bleiben unverändert):
   * `pw_*`-Schlüssel: den vorhandenen Text aus `values-en/strings.xml` **wörtlich** übernehmen (nicht neu übersetzen).
   * Alle übrigen Einträge aus dem deutschen Text ins Englische übersetzen – **abschnittsweise** (je `<!-- gruppe -->`-Block, ~50–100 Einträge pro Schreibvorgang), nicht die ganze Datei in einem Zug.
   * Schlüsselnamen sind teils deutsch (`common_abbrechen`) – **nicht umbenennen** (Code in allen Modulen nutzt sie; siehe Task 17).
3. **Übersetzungsregeln:**
   * Platzhalter exakt erhalten: `%1$s`, `%2$d`, `%%`, Reihenfolge der Argumente nicht ändern (Satzstellung darf sich ändern, der Index nicht).
   * Escapes erhalten: `\n`, `\'`, `\"`, `\@`, `&amp;`, `&lt;`; Anführungszeichen um Werte mit führenden/endenden Leerzeichen bleiben. Kein neues XML-Markup.
   * Ton: knappes UI-Englisch nach Material-Richtlinien, **Sentence case** (wie in `pw_*`): „Create project", nicht „Create Project". Buttons Imperativ („Cancel", „Retry"), Fehlermeldungen als Satz mit Punkt, wenn das deutsche Original einen hat.
   * Fachbegriffe, die in der IDE englisch üblich sind, bleiben: Git, Commit, Branch, Stash, Merge, Rebase, Remote, Tag, Diff, Blame, Terminal, Shell, Gradle, SDK, NDK, Bootstrap, Plugin, Theme, Linter, LSP. „Datei/Ordner/Projekt" → file/folder/project.
   * Eigennamen, Dateinamen, Befehle, Pfade, Einheiten und reine Symbole/Abkürzungen (z. B. `Aa`, `Git`, `OK`, `UTF-8`) unverändert.
   * Plural-Hinweise im Deutschen („Datei(en)") → neutral formulieren („file(s)") – **keine** `<plurals>` einführen (eigener Task).
   * Wortlaut nicht „verbessern", nichts weglassen oder ergänzen; Bedeutung 1:1.
   * Bei Mehrdeutigkeit (z. B. „Übernehmen" = Apply/Accept/Cherry-pick) aus der Verwendung entscheiden: `grep -rn "R.string.<schlüssel>" --include=*.kt app feature core libs`.
4. **`values-en` auflösen:** Nach Schritt 2 steht der gesamte englische Text in `values/`. `core/resources/src/main/res/values-en/strings.xml` **löschen** (`git rm`), da redundant. Vorher prüfen, dass jeder `pw_*`-Schlüssel in `values/` den identischen Text hat.
5. **Prüfen:**
   `python3 scripts/check_string_locales.py --german-baseline $BASE` → Exit 0. Das Skript prüft: Wohlgeformtheit, keine Duplikate, gleiche Schlüsselmenge in `values` und `values-de`,
   gleiche Platzhalter/Escapes je Schlüssel, und dass `values-de` dem deutschen Original aus `$BASE` entspricht. Die beiden „Hinweis"-Listen sind Heuristik:
   * „identisch in en/de": nur ok bei Eigennamen/Symbolen – alles andere nachübersetzen.
   * „deutsche Reste in values/": jeden Treffer ansehen (Heuristik kennt auch englische Wörter wie „die"/„Neu"-Teile nicht perfekt) und echte deutsche Reste übersetzen.
   Erwartung: nach der Arbeit sind beide Listen leer oder enthalten nur begründete Ausnahmen (im Bericht nennen).
6. **Tests und Hilfsklassen anpassen:**
   * `core/testing/.../TestRes.kt` liest `values/strings.xml` und formatiert mit `Locale.GERMANY` → auf `Locale.ENGLISH` umstellen und KDoc („liest die englische Standarddatei") anpassen.
   * `./gradlew testDebugUnitTest` ausführen; Tests, die deutsche Texte aus Ressourcen erwarten (`grep -rn "assert" --include=*.kt */src/test | grep -E '"[^"]*[äöüß]|Fehler|Abbrechen|Datei'`), auf die englischen Texte (oder besser auf `Res.string(R.string.x)`) umstellen. Nur Erwartungswerte ändern, keine Logik.
7. **Build prüfen:** `./gradlew :core:resources:assembleDebug` und `./gradlew assembleDebug`; zusätzlich `./gradlew :app:lintDebug` auf `MissingTranslation`/`ExtraTranslation`/`StringFormatMatches`/`StringFormatInvalid` (keine neuen Fehler).
8. **Sprachverhalten:** Prüfen, dass die App ohne eigene Sprachlogik der Systemsprache folgt (Deutsch → `values-de`, sonst Englisch). Falls `app/build.gradle.kts` `resourceConfigurations`/`androidResources.localeFilters` setzt, `en` und `de` darin zulassen. Optional (nur melden, nicht ohne Rückfrage bauen): `res/xml/locales_config.xml` mit `en`, `de` für die per-App-Sprachauswahl ab Android 13.
9. **Task 17 anpassen** (`agy-tasks/17-externalize-hardcoded-strings.md`):
   * Schritt 3: neue Schlüssel mit **englischem** Text in `values/strings.xml` **und** deutschem Text in `values-de/strings.xml` anlegen (gleicher Schlüssel, gleiche Platzhalter).
   * Schritt 5 („Englisch optional") ersetzen durch „Deutsch in `values-de` pflegen – Pflicht".
   * Schritt 6: zusätzlich `python3 scripts/check_string_locales.py` → Exit 0.
   * Bei vorhandenem deutschem Literal: Der Wortlaut wandert nach `values-de`, die englische Fassung entsteht neu (Regel „nicht umformulieren" gilt für die Ausgangssprache).
10. **Doku:**
    * `core/resources/README.md` Zeile „Sprachen: …" → „Standard ist Englisch (`values/`), Deutsch in `values-de/`; neue Texte immer in beiden Dateien".
    * `docs/resources-and-strings.md`: Zeile 8 (Dateien/Anzahl) und den „Offen/ungeprüft"-Hinweis zu `values-en` aktualisieren.
    * `CHANGELOG.md` unter `[Unreleased]`: „Strings: Standardsprache Englisch, Deutsch in `values-de`".
    * `agy-tasks/README.md`: Tabellenzeile 18 ergänzen (liegt dieser Lieferung bei).

## Grenzen / nicht tun

* Keine Schlüssel umbenennen, löschen oder sortieren; keine Strings aus `libs/termux-app`, `libs/termux-shared`, `libs/termux-view` anfassen (eigene Ressourcen, `libs/termux-*` haben schon eigenes `values-de`).
* Keine harten Kotlin-Literale in diesem Task migrieren (das ist Task 17).
* Keine `<plurals>`/`<string-array>` einführen; kein weiteres Locale (fr, es …) anlegen.
* Kein Auto-Übersetzer-Dienst und keine externen Downloads; Übersetzung erfolgt durch den Agenten selbst.
* Nicht committen, bevor Schritt 5 und 7 grün sind. Commit-Vorschlag: `i18n: Standardsprache Englisch, Deutsch nach values-de`.

## Akzeptanz

* `values/strings.xml` ist durchgehend englisch, `values-de/strings.xml` entspricht dem alten deutschen `values/strings.xml` (Prüfskript mit `--german-baseline`).
* `values-en/` existiert nicht mehr; Schlüsselmenge, Platzhalter und Escapes in beiden Dateien identisch.
* `python3 scripts/check_string_locales.py` Exit 0; `./gradlew assembleDebug` und `testDebugUnitTest` grün.
* Auf dem Gerät/Emulator: Systemsprache Deutsch → deutsche UI, Englisch (oder andere) → englische UI; keine `@string/…`-Platzhalter.
* Diff enthält nur: die beiden `strings.xml`, gelöschtes `values-en`, `TestRes.kt`, angepasste Test-Erwartungen, Doku/CHANGELOG, Task 17, ggf. Locale-Konfiguration.
