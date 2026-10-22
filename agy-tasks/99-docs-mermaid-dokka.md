# Task 99 – Dokumentation: Anwendungsdoku mit Mermaid, API-Doku mit Dokka, wiederholbar aktualisierbar

**Ziel:** Es gibt (1) eine **Anwendungsdokumentation** (Architektur, Module, Navigation, Datenfluss, Build- und Paket-Pipeline) mit **Mermaid**-Diagrammen und (2) eine **API-Dokumentation** mit **Dokka** für die öffentlichen Schnittstellen. Beides lässt sich mit **einem Befehl** neu erzeugen und prüfen und wird in der CI überwacht, damit es nicht wieder veraltet. Der Task ist **wiederholbar**: Er wird nach größeren Änderungen erneut ausgeführt (Abschnitt 99.7 „Wiederholter Lauf“).
**Nummer:** 99 ist absichtlich eine Dauer-Task-Nummer und bleibt von der laufenden Zählung (35–47) getrennt.
**Warum:** Im Repo liegen über 60 Dokumente unter `docs/` (Berichte, Anleitungen, Verifikationen) ohne Index und ohne Prüfung auf Aktualität. Beispiele aus dem Bestand: `docs/BOOTSTRAP-WORKFLOW.md` und `libs/terminal-engine/BOOTSTRAP.md` nennen den Bootstrap `2026.10.11`, `gradle.properties` steht auf `2026.10.19`; `README.md`-Tabellen und `TASKS-STATUS.md` pflegt man von Hand. Dokka ist im Repo **nicht** eingerichtet (Stichwortsuche in `*.kts`, `*.toml`, `*.yml` ohne Treffer); Mermaid kommt nur vereinzelt in Berichten vor (`docs/logcat-viewer-report.md` und drei weitere).

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos. Hier betrifft es Build-Skripte, Gradle-Plugins in `build-logic` und Dokumentationsdateien; **kein** Produktivcode der App wird geändert, außer KDoc-Kommentaren und `@Suppress`-/`@sample`-Hilfen, die das Verhalten nicht beeinflussen.
* Gradle Kotlin DSL, Versionen im Version Catalog, Convention-Plugins aus `build-logic`. Kein neues Plugin ohne Eintrag im Katalog.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet**. Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen in einem Repo-Snapshot; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* **Keine Geheimnisse** in generierter Dokumentation: keine Schlüssel, Passphrasen, Token, keine Passwörter aus Beispielen, keine privaten Pfade des Inhabers, keine E-Mail-Adressen außer solchen, die bereits im Repo als Autorenangabe stehen. Generatoren lesen nur Quelltext und Konfiguration, **nie** `.gpg/`, `local.properties`, `*.keystore`, `*.jks`, `.env`.
* Dokumentationstexte deutsch (wie der Bestand), Code-Bezeichner und KDoc-Tags in der Sprache des umgebenden Codes; die Wahl für KDoc (deutsch oder englisch) prüfst du am Bestand und hältst sie ein.
* Bericht in `docs/docs-generation-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash eintragen; Task 99 als Dauer-Task kennzeichnen).
* Build grün: `./gradlew :app:assembleDebug` und der neue Befehl `./gradlew docsCheck` (siehe 99.5).

**Reihenfolge:** 99.1 → 99.2 und 99.3 (parallel möglich) → 99.4 → 99.5 → 99.6; **99.8** (Betriebshandbuch) kann nach 99.1 parallel zu 99.2–99.4 begonnen werden und ist vor Abschluss von 99.5 fertig. Jeder Teil einzeln committen.

---

## 99.1 Bestandsaufnahme und Struktur

1. **Inventur:** Tabelle `docs/index.md` über **jede** Datei in `docs/` (und `README.md`, `README_DE.md`, `STATUS.md`, `CHANGELOG.md`, `docs/sub/` falls vorhanden): Titel, Art (**Anleitung** · **Referenz** · **Bericht/Verifikation** (Momentaufnahme eines Tasks) · **Entscheidung** · **Archiv**), Zielgruppe, „zuletzt geprüft am“, Status (**aktuell** · **veraltet** · **unbekannt**). Berichte sind Momentaufnahmen und werden nicht „aktualisiert“, sondern als solche gekennzeichnet (Kopfzeile „Stand des Tasks, nicht der Gegenwart“) und im Index zu den Referenzdokumenten verlinkt, die den aktuellen Stand tragen.
2. **Zielstruktur** (bestehende Dateien nicht verschieben, nur ergänzen; Verschiebungen nur, wenn Links mitgeführt werden und in einem eigenen Commit):
   * `docs/index.md` – Einstieg und Status je Dokument.
   * `docs/app/` – Anwendungsdokumentation (99.2), von Hand geschrieben **mit eingebetteten generierten Blöcken**.
   * `docs/generated/` – **nur** maschinell erzeugt, nie von Hand ändern (Kopfzeile „GENERIERT – nicht von Hand bearbeiten, Erzeugung: `./gradlew docs`“).
   * `docs/api/` – Verweis/Anleitung zur Dokka-Ausgabe (die HTML-Ausgabe selbst wird **nicht** eingecheckt, siehe 99.4).
3. **Fakten-Drift-Prüfung** (Skript `scripts/docs_facts_check.py`, nur Standardbibliothek, mit `python3 -I` lauffähig): eine kleine Liste von Behauptungen, die sich aus dem Code ableiten lassen, wird gegen die Docs geprüft. Startumfang:
   * Bootstrap-Version in `gradle.properties` (`codeforgeBootstrapVersion`) gegen jede Nennung `bootstrap-JJJJ.MM.TT`/`20JJ.MM.TT` in den aktuellen Referenzdokumenten (nicht in Berichten);
   * Moduliste aus `settings.gradle.kts` gegen die Modultabellen in `README.md`/`README_DE.md`/`docs/app/modules.md`;
   * Task-Dateien in `agy-tasks/` gegen die Zeilen in `TASKS-STATUS.md` und `README.md` (fehlende/überzählige Nummern);
   * Gruppen in `apt-groups.json` gegen die Gruppentabelle in `docs/BOOTSTRAP-WORKFLOW.md`;
   * `libs.versions.toml`-Werte (AGP, Kotlin), die in Docs genannt sind.
   Ausgabe: Liste „Doku sagt X, Code sagt Y“ mit Datei und Zeile. Das Skript **ändert nichts**, es meldet nur; Korrekturen macht der Mensch oder der Agent bewusst. Bekannte Ausgangsfunde (aus dem Bestand, prüfen) als erster Testfall: Bootstrap `2026.10.11` vs. `2026.10.19`.
4. Die gefundenen Widersprüche **korrigierst du in diesem Task** in den Referenzdokumenten; Berichte bleiben unverändert (Kopfzeile genügt).

## 99.2 Anwendungsdokumentation mit Mermaid

Dokumente in `docs/app/`, jeweils mit kurzem Text und Diagrammen. Diagramme als ```` ```mermaid ````-Blöcke (GitHub rendert sie direkt). Jedes Diagramm hat Titel, Legende bei Bedarf und eine Zeile „Quelle“ (Datei oder Generator). Vorschlag der Dokumente (Umfang an den Bestand anpassen, nichts beschreiben, was du nicht im Code gesehen hast):

| Dokument | Inhalt | Mermaid-Diagramme |
|---|---|---|
| `architecture.md` | Schichten UI / Domain / Data, Regeln (MVVM/MVI, `:core:domain` ohne Android), Verweis auf `docs/architecture-decisions.md` | Schichtendiagramm (`flowchart`), Abhängigkeitsregel |
| `modules.md` | Alle Module aus `settings.gradle.kts` mit Zweck, Gruppen `:app`, `:core:*`, `:feature:*`, `:libs:*`, `:examples:*` | **generiert**: Modulgraph (99.3), je Gruppe ein Teilgraph |
| `navigation.md` | Alle Routen aus `CodeForgeNavHost.kt` (`Routes.*`) und die Übergänge | **generiert**: Navigationsgraph (99.3) |
| `data-and-settings.md` | DataStore/Proto (`core/datastore/src/main/proto/settings.proto`), Repositories | **generiert**: Klassendiagramm der Proto-Nachrichten |
| `terminal-and-bootstrap.md` | Bootstrap-Einbettung, `TermuxInstaller`, `codeforge-env`, Erststart | Sequenzdiagramm Erststart; Zustandsdiagramm Setup |
| `package-pipeline.md` | Workflows `build-bootstrap-repo.yml`, `build-all-stages.yml`, `build-compiler-runtime.yml`, Release `large-packages`, Mirror `codeforge-tools`, `apt-groups.json`/`apt-stages.conf` | Flussdiagramm der Pipeline; Quellen-Übersicht (Bootstrap, APT, Mirror, Compiler-Runtime). Inhalt und Befehle verweisen auf `docs/BOOTSTRAP-WORKFLOW.md` und die Anleitung vom 2026-10-21, **nicht** kopieren |
| `lsp.md` | `LspClientRepository`, `LspSessionController`, `LspServerResolverImpl`, `ExtensionsManager`, Task 43/44 (Stand kennzeichnen) | Sequenzdiagramm „Datei öffnen → Server starten“ |
| `editor.md` | `EditorViewModel`, `SoraCodeEditor`, Einstellungen, Overlay | Komponentendiagramm |
| `build-and-signing.md` | Build-Läufer, Signierung, Schlüsselverwaltung | Sequenzdiagramm „Build mit Signierung“ (Stand vor/nach Task 47 kennzeichnen) |
| `plugins.md` | Plugin-System, `:libs:plugin-api`, Beispiele | Klassendiagramm der API-Schnittstellen, Lebenszyklus |

Regeln für Mermaid:
* Nur Diagrammtypen, die GitHub und `mermaid-cli` rendern (`flowchart`, `sequenceDiagram`, `classDiagram`, `stateDiagram-v2`, `erDiagram`). Keine experimentellen Typen.
* Knoten-IDs ohne Sonderzeichen; Beschriftungen in Anführungszeichen. Große Graphen in **Teilgraphen** (`subgraph`) und mehrere Diagramme teilen; ein Diagramm mit mehr als etwa 40 Knoten ist unlesbar.
* Jedes Diagramm besteht die **Syntaxprüfung** (99.5, `mermaid-cli`).
* Keine Behauptung ohne Beleg: Ist etwas nur geplant (z. B. Task 43/44/47), steht es als „geplant (Task N)“ und gestrichelt (`-.->`).

## 99.3 Generatoren (maschinell erzeugte Teile)

Ziel: Teile, die sich aus Code ableiten lassen, **nicht von Hand pflegen**.

1. **Markierungen:** In handgeschriebenen Dokumenten stehen generierte Blöcke zwischen
   `<!-- BEGIN GENERATED: <name> -->` und `<!-- END GENERATED: <name> -->`. Der Generator ersetzt **nur** den Inhalt zwischen den Markierungen; Text außerhalb bleibt unangetastet. Fehlt ein Block, meldet der Generator das (kein stilles Anlegen). Vollständig generierte Dateien liegen in `docs/generated/`.
2. **Modulgraph** (`modul-graph`): Projektabhängigkeiten aus Gradle (Konfigurationen `api`, `implementation`, `compileOnly` mit `project(":…")`) → Mermaid-`flowchart`, Kanten nach Art unterschiedlich (`api` durchgezogen, `implementation` gestrichelt), Knoten nach Gruppe eingefärbt/gruppiert. Umsetzung als Gradle-Task in `build-logic` (konfigurationscache-kompatibel; prüfe das) **oder**, wenn das zu aufwendig ist, als Skript, das `build.gradle.kts` der Module liest (Regex auf `project(":…")`). Entscheidung im Bericht begründen (Genauigkeit vs. Aufwand). Zusätzlich Prüfung auf **Schichtverstöße**: `:core:domain` darf nicht von `:feature:*` oder `:libs:*` abhängen, `:feature:*` nicht von anderen `:feature:*` (falls das die Regel im Repo ist; **Regel zuerst im Bestand prüfen**, `docs/architecture-decisions.md`). Verstöße als Liste im Dokument, ohne den Build zu brechen (Hinweis, später Gate).
3. **Navigationsgraph** (`navigation-graph`): Routen aus `CodeForgeNavHost.kt` (`Routes`-Konstanten, `composable(...)`, `navController.navigate(...)`) → Mermaid. Bei Muster-Routen (`TERMINAL_PATTERN`, `GIT_PATTERN`, `LAYOUT_DESIGNER_PATTERN`) Argumente anzeigen. Skript mit klar dokumentierten Annahmen; bei Quelltextmustern, die er nicht versteht, **Warnung** statt falschem Diagramm.
4. **Proto-Klassendiagramm** (`settings-proto`): `settings.proto` → Mermaid-`classDiagram`, inklusive Feldnummern und `reserved`.
5. **Task-Übersicht** (`tasks-status`): Tabelle aus den Kopfzeilen der Dateien in `agy-tasks/` (Nummer, Titel, Status laut `TASKS-STATUS.md`); damit entfällt die doppelte Pflege. Ob `TASKS-STATUS.md` selbst generiert wird, ist **eine Entscheidung des Inhabers**; hier nur die Prüfung „jede Task-Datei hat eine Zeile“ (aus 99.1.3).
6. **Gruppen-/Etappenübersicht** (`apt-groups`): `apt-groups.json` und `apt-stages.conf` → Tabelle für `package-pipeline.md`.
7. Alle Generatoren sind **deterministisch** (sortierte Ausgabe, kein Zeitstempel im Inhalt, Zeilenenden `\n`), damit `git diff` nur echte Änderungen zeigt. Ein Zeitstempel steht höchstens in einer Kopfzeile, die der Vergleich ignoriert.
8. Aufruf: `./gradlew docsGenerate` ruft alle Generatoren auf (Python-Skripte werden über `Exec` angestoßen; das Repo setzt dafür `python3` voraus: im Bericht vermerken, ob die CI-Umgebung und Termux es haben).

## 99.4 API-Dokumentation mit Dokka

**Umfang (öffentliche Schnittstellen zuerst):**
* `:libs:plugin-api` – das eigentliche öffentliche API für Plugins (Paket `plugin_api`, Beispiele unter `examples/`).
* `:core:domain` – Modelle und Repository-Schnittstellen (`LspClientRepository`, `CodeFormatterRepository`, `SigningRepository`, …).
* `:libs:indexing-api`, `:libs:dependency-updater-api` – die `-api`-Module.
* Danach nach Bedarf `:libs:lsp-client`, `:libs:template-engine`, `:libs:code-tools`. UI-Module (`:feature:*`) **nicht**: Sie sind kein API; Ausnahme: öffentliche Verträge, die Plugins nutzen.

**Einrichtung:**
1. Dokka-Gradle-Plugin der aktuellen stabilen **2.x**-Linie wählen und die **Kompatibilität** mit Kotlin `2.1.21`, AGP `8.13.2` und Gradle `8.14.3` (Werte aus `libs.versions.toml` und `gradle-wrapper.properties` im Snapshot, prüfen) **vor** dem Eintrag belegen (Release-Hinweise, Testlauf). Version im Version Catalog; Anwendung über ein Convention-Plugin in `build-logic` (`codeforge.docs.dokka` o. ä.), nicht in jedem Modul einzeln.
2. **Mehrmodul-Ausgabe:** ein aggregiertes Dokka-Ergebnis im Root-Projekt, das die oben genannten Module einsammelt. Ausgabe nach `build/dokka/` (HTML) – **nicht** im Repo einchecken; die CI veröffentlicht sie (99.5).
3. **Konfiguration je Modul:** `moduleName`, Paketbeschreibungen (`package-info`-Markdown-Dateien oder Dokka-Include-Dateien), Quelllinks (`sourceLink`) auf das GitHub-Repo mit dem Commit-Hash, `suppressedFiles`/`perPackageOption` für `internal`-Pakete und generierten Code (Hilt-/Proto-Klassen), `documentedVisibilities = PUBLIC`.
4. **Warnungen:** `reportUndocumented` je Modul einschalten und die Zahl **je Modul in den Bericht** schreiben (Anfangsstand notieren, Ziel für `:libs:plugin-api` und `:core:domain`-Schnittstellen: **alle** öffentlichen Typen und Funktionen mit KDoc). `failOnWarning` zunächst **aus**; im Bericht beschreiben, wann es zum Gate wird.
5. **KDoc schreiben** für die oben genannten Pflichtmodule: Zweck, Parameter (`@param`), Rückgabe (`@return`), Fehler (`@throws`), Threading (Hauptthread/`suspend`), Stabilität (`@sample` oder Beispielcode, `@since`). Keine erfundene Semantik: Unklares wird im Bericht als Frage an den Inhaber aufgelistet, nicht geraten. Der Verhaltensinhalt des Codes wird **nicht** geändert.
6. **Plugin-Entwicklerhandbuch** `docs/app/plugins.md` (aus 99.2) verlinkt auf die Dokka-Ausgabe und erklärt anhand der Beispiele `examples/kotlin-lsp-plugin` den Weg von der Schnittstelle zum lauffähigen Plugin. Beispielcode im Handbuch muss **kompilieren**: Er stammt aus den Beispielmodulen (Auszug mit Dateiverweis), nicht neu erfunden.
7. Sprache und Layout: Dokka-Seiten bleiben Englisch (Dokka-Standard), KDoc folgt der Regel aus den gemeinsamen Regeln; Logo/Name „CodeForge Mobile“ nur aus eigenen Ressourcen.

## 99.5 Wiederkehrend: Befehle, CI und Prüfung

**Gradle-Aufgaben (Root):**
| Aufgabe | Wirkung |
|---|---|
| `./gradlew docsGenerate` | alle Generatoren (99.3) schreiben `docs/generated/` und die Blöcke in `docs/app/` |
| `./gradlew dokkaGenerate` (bzw. der Aufgabenname der gewählten Dokka-Version; im Bericht nennen) | API-HTML nach `build/dokka/` |
| `./gradlew docs` | `docsGenerate` + Dokka |
| `./gradlew docsCheck` | **ändert nichts**: prüft (a) generierte Teile sind aktuell (Generator erneut laufen lassen, Vergleich mit dem Stand im Arbeitsverzeichnis, bei Abweichung Fehler mit Hinweis `./gradlew docsGenerate`), (b) Mermaid-Syntax aller Blöcke, (c) Fakten-Drift (`scripts/docs_facts_check.py`), (d) tote relative Links in `docs/` und `README*.md`, (e) KDoc-Abdeckung der Pflichtmodule nicht schlechter als der Basiswert im Bericht |

**Mermaid-Syntaxprüfung:** `@mermaid-js/mermaid-cli` (`mmdc`) rendert jeden ```` ```mermaid ````-Block nach SVG und meldet Fehler; Version fest im `package.json`/Skript pinnen (kein `latest`), Headless-Browser-Voraussetzung auf dem Runner prüfen (Chromium ist dort vorhanden, im Bericht belegen). Lokal in Termux ist `mmdc` vermutlich **nicht** lauffähig; deshalb gibt es einen leichten Vorab-Check (`scripts/docs_mermaid_lint.py`: Klammern, erlaubte Diagramm-Schlüsselwörter, Blockgrenzen), der ohne Browser läuft.

**GitHub Actions** (`.github/workflows/docs.yml`):
* Auslöser: `pull_request` (nur Pfade `docs/**`, `**/*.kt`, `**/*.proto`, `settings.gradle.kts`, `*.kts`, `apt-*`, `agy-tasks/**`): führt `docsCheck` aus, **ohne** zu veröffentlichen; `push` auf `main`/`dev`; `workflow_dispatch`; **wöchentlicher** Zeitplan (`schedule`, Cron), der `docsCheck` und die Dokka-Erzeugung ausführt und bei Abweichung ein Issue öffnet oder den Lauf rot färbt (Entscheidung im Bericht; Standard: Lauf rot).
* Veröffentlichung: Dokka-HTML als **Workflow-Artefakt** (Aufbewahrung festlegen). Ob zusätzlich GitHub Pages dieses Repos genutzt wird (`actions/deploy-pages`), ist **eine Entscheidung des Inhabers**: Pages sind öffentlich, auch wenn das Repo privat ist; nichts veröffentlichen, was nicht öffentlich sein soll. Standard im Task: nur Artefakt, Pages als vorbereiteter, ausgeschalteter Schritt (Eingabe `deploy_pages`, Standard aus).
* Berechtigungen minimal (`contents: read`; bei Pages `pages: write`, `id-token: write` nur in diesem Job). Actions auf feste Versionen pinnen. Kein Zugriff auf Secrets nötig.
* `actionlint` auf die neue Datei.

**Pflegeprozess (dokumentiert in `docs/index.md` und im Bericht):**
1. Wer ein Modul, eine Route, ein Proto-Feld oder ein öffentliches API ändert, führt `./gradlew docsGenerate` aus und committet die Änderung mit (oder die CI meldet es).
2. Neue agy-Tasks erhalten eine Zeile in `TASKS-STATUS.md`; `docsCheck` findet fehlende Zeilen.
3. Berichte nach Abschluss eines Tasks bekommen die Kopfzeile „Momentaufnahme“ und werden im Index eingetragen.
4. Vorschlag für die **Gemeinsamen Regeln** künftiger Tasks (nicht selbst in alle Tasks schreiben, nur vorschlagen): „`./gradlew docsCheck` bleibt grün; bei Änderungen an Modulen, Routen, Proto oder öffentlichen Schnittstellen `./gradlew docsGenerate` ausführen.“

## 99.6 Tests und Abnahme

* **Generatoren:** Golden-File-Tests (kleine Testprojekte bzw. Fixtures: Beispiel-`settings.gradle.kts`/`build.gradle.kts`, Beispiel-NavHost, Beispiel-Proto, Beispiel-`apt-groups.json`) → erwartete Mermaid-/Tabellenausgabe; Determinismus (zweimal laufen lassen, identisches Ergebnis); Verhalten bei unbekannten Mustern (Warnung statt Absturz); Markierungsmechanismus (Text außerhalb bleibt unverändert, fehlende Markierung wird gemeldet).
* **`docs_facts_check.py`:** Tests mit Fixtures für „stimmt“ und „weicht ab“, inklusive des Bootstrap-Falls.
* **Mermaid-Lint:** gültige und ungültige Blöcke.
* **`docsCheck`:** ein absichtlich veralteter generierter Block lässt den Lauf fehlschlagen (im Bericht belegen), ein frisch generierter besteht.
* **Dokka:** Lauf erzeugt HTML für die Pflichtmodule; Stichprobe: Seiten für `LspClientRepository` und die Plugin-API-Einstiegspunkte enthalten KDoc-Text; Quelllinks zeigen auf existierende Dateien (Stichprobe).
* Optik der Diagramme und der Dokka-Seiten und das Verhalten des Wochenlaufs sind **nicht getestet**, bis der Inhaber sie im Browser bzw. nach dem ersten geplanten Lauf bestätigt (im Bericht ausdrücklich vermerken).

## 99.7 Wiederholter Lauf (Runbook)

Dieser Task wird **mehrfach** ausgeführt, nicht nur einmal. Ablauf jedes weiteren Laufs (kurz, 30 bis 60 Minuten Arbeit, wenn sich wenig geändert hat):
1. `./gradlew docsCheck` – Ausgangsstand notieren (was ist veraltet?).
2. `./gradlew docsGenerate`, Änderungen mit `git diff` lesen; **jede** Abweichung gegen den Code gegenprüfen (nicht blind übernehmen), besonders Navigations- und Modulgraph.
3. `python3 -I scripts/docs_facts_check.py` – gemeldete Widersprüche beheben (Referenzdokumente, nicht Berichte).
4. Neue/geänderte öffentliche Schnittstellen: KDoc ergänzen; Dokka-Warnungen je Modul im Bericht fortschreiben (Zahl vorher/nachher).
5. Neue Berichte/Tasks in `docs/index.md` eintragen, Status der Dokumente aktualisieren („zuletzt geprüft am“).
6. Auf Veröffentlichung prüfen (Artefakt, ggf. Pages); `docs/docs-generation-report.md` um einen datierten Abschnitt „Lauf <Datum>“ ergänzen: was geändert, was gefunden, was **nicht** geprüft.
**Auslöser für einen erneuten Lauf:** nach jedem Task-Block (z. B. 35–47 abgeschlossen), nach jedem Modulzugang oder -wegfall, nach Änderungen an `Routes`, `settings.proto`, `apt-groups.json`, nach einem Wechsel von Kotlin/AGP/Gradle und mindestens einmal im Quartal.

## 99.8 Betriebshandbuch für Einsteiger (Pflege, Schlüssel, Token, Wiederherstellung)

**Ziel:** Eine Person, die das Projekt nicht kennt und nur Anfängerkenntnisse in Git, GitHub Actions und Linux hat, kann mit diesem Handbuch das gesamte Paket- und Bootstrap-System **von Null aufbauen, warten und nach einem Verlust wiederherstellen**, ohne den Inhaber zu fragen. Das ist das Abnahmekriterium dieses Teils (siehe unten). Der Inhaber ist derzeit der einzige Entwickler; das Handbuch ersetzt sein Wissen im Kopf.
**Entscheidungsgrundlage (Inhaber, 2026-10-22):** Das System bleibt bei der bestehenden Lösung (GitHub Actions, Etappen/Gruppen, gh-pages). Ein Umstieg auf aptly ist erst wieder zu prüfen, wenn Community-Entwickler dazukommen oder der Aufwand wächst. Das Handbuch hält das fest (Abschnitt „Warum kein aptly“, Ausgangspunkt `docs/` des Repos und Hinweise aus 48).

Ablage: `docs/operations/` (neues Verzeichnis, Index in `docs/index.md`). Von Hand geschrieben; wo ein Wert aus dem Code stammt (Workflow-Eingaben, Gruppen, Bootstrap-Version), steht ein **generierter Block** (99.3) und `docs_facts_check.py` (99.1) prüft ihn mit.

### Dokumente

| Datei | Inhalt |
|---|---|
| `00-uebersicht.md` | Was das System tut, in einem Absatz; Begriffe (Glossar: Prefix, Bootstrap, APT-Repo, Stage, Gruppe, Etappe, Release, gh-pages, Secret, Token, GPG-Schlüssel, Keyring, Runner); **Architektur-Diagramm** (Mermaid): Quellen (termux-packages-Fork, `CodeForgeMobile`, `codeforge-tools`) → Workflows → Ziele (gh-pages, Releases, App); Zeit- und Kostenrahmen („ein Gruppenlauf dauert Stunden; das ist normal“, keine erfundenen Zahlen: nur aus echten Läufen, mit Datum) |
| `01-voraussetzungen.md` | Konten (GitHub), Repos und deren Sichtbarkeit (`terminal-packages-codeforge` **öffentlich**, sonst keine Pages), benötigte Werkzeuge (`git`, `gpg`, `base64`, `gh`), Bedienung der Befehle unter Termux **und** unter Linux/Windows (WSL), Checkliste „bin ich bereit?“ |
| `02-gpg-schluessel.md` | Schlüssel erzeugen, sichern, wiederherstellen (siehe unten) |
| `03-github-token.md` | Token erzeugen, Rechte, Ablauf, Erneuerung (siehe unten) |
| `04-secrets-einrichten.md` | `scripts/setup-gpg-secrets.sh`, die drei Secrets, Prüfung ohne Veröffentlichung (`NO_SECRETS=1`) |
| `05-erstaufbau.md` | Reihenfolge von Null: Fork prüfen → Secrets → Bootstrap bauen → Repo-Gruppen → Compiler-Runtime → Tools-Mirror → App-Build mit neuer Bootstrap-Version; je Schritt **Workflow, Eingaben (Tabelle), erwartetes Ergebnis, Prüfbefehl, typische Fehler** |
| `06-pflege-nach-ausloeser.md` | Tabelle „Was ist zu tun, wenn …“ (siehe unten) |
| `07-wiederherstellung.md` | Szenarien: Passphrase vergessen, Schlüssel verloren, Token abgelaufen/geleakt, gh-pages kaputt, Release gelöscht, Bootstrap-SHA falsch |
| `08-fehlersuche.md` | Tabelle Symptom → Ursache → Maßnahme; Startbestand aus den bekannten Fehlern (siehe unten) |
| `09-entscheidungen.md` | Warum Etappen/Gruppen, warum kein aptly (Stand 2026-10-22), was bei neuen Mitwirkenden neu zu bewerten ist |

Jedes Dokument beginnt mit **Zweck · Voraussetzungen · Dauer · Ergebnis** und endet mit **Prüfung („So erkennst du, dass es geklappt hat“)**. Befehle stehen vollständig in Codeblöcken, mit Platzhaltern in `<spitzen Klammern>`; nie ein echter Wert.

### 02 – GPG-Schlüssel (Erzeugung, Sicherung, Wiederherstellung)

Grundlage ist `scripts/setup-gpg-secrets.sh` (Bestand im Repo, nicht neu erfinden; Aufrufe und Umgebungsvariablen aus dem Skript übernehmen und gegen den aktuellen Stand prüfen). Inhalt:
1. **Wozu der Schlüssel dient:** Er signiert den Release-Index des APT-Repos; die App/der Bootstrap enthält den **öffentlichen** Teil (`codeforge.gpg`, installiert unter `$PREFIX/share/termux-keyring/`), damit `apt` dem Repo traut. Der **private** Teil liegt nur als GitHub-Secret und im Backup.
2. **Erzeugen (einmalig, lokal, nie auf dem Runner):** warum der Runner keinen Schlüssel erzeugen darf (er wäre nach dem Lauf weg, die Signatur änderte sich bei jedem Build). Schritte: Passphrase wählen (lang, nur im Passwortmanager) → `CODEFORGE_GPG_PASSPHRASE` nur in der Umgebung oder in `~/.bashrc` setzen, **nie** in Dateien im Repo, Chats oder Logs → Skript ausführen → Prüfung (Fingerabdruck anzeigen, öffentlichen Schlüssel exportieren).
3. **Sichern (Pflicht, sofort danach):** `BACKUP_DIR=<Ordner> bash scripts/setup-gpg-secrets.sh <owner>/<repo>` legt `codeforge-private.asc` (nur mit Passphrase nutzbar) und `codeforge-public.asc` ab. Dann: mindestens **zwei** Kopien an zwei getrennten Orten (z. B. verschlüsselter USB-Stick und Passwortmanager/Tresor), **nicht** in Git, **nicht** in Cloud-Ordnern ohne Verschlüsselung; Passphrase **getrennt** vom Backup aufbewahren. Tabelle „Was liegt wo“ (Orte als Platzhalter, vom Inhaber auszufüllen: das Handbuch nennt keine privaten Pfade).
4. **Sicherung prüfen (vierteljährlich, Termin in `06`):** Backup in ein Wegwerf-Verzeichnis importieren (`GNUPGHOME=<temp>`), Fingerabdruck mit dem im Secret/Repo vergleichen, Testsignatur erzeugen und prüfen, Verzeichnis löschen.
5. **Wiederherstellen:** Backup importieren → Secret `GPG_PRIVATE_KEY_B64` mit `gpg --armor --export-secret-keys <Fingerabdruck> | base64 -w0` neu setzen → Passphrase-Secret prüfen → Testlauf. Der Schlüssel bleibt derselbe, **kein** Neubau nötig.
6. **Ablauf des Schlüssels:** Gültigkeit des vorhandenen Schlüssels prüfen und im Handbuch eintragen (`gpg --list-keys`); Verlängerung mit demselben Schlüssel (`gpg --edit-key … expire`) ist **kein** neuer Schlüssel; danach öffentlichen Schlüssel neu verteilen und Secret aktualisieren. Entscheidung des Inhabers: gewählte Laufzeit festhalten. Nicht raten: Was das Skript heute erzeugt, aus dem Skript ablesen.

**Die Stolperfalle, deutlich hervorgehoben (Kasten am Anfang von `02` und in `06`, `07`, `08`):**

> **Neuer Schlüssel = alles neu.** Wird ein **neuer** GPG-Schlüssel erzeugt (auch durch `FORCE_NEW_KEY=1`, Verlust von Schlüssel und Backup, oder „ich erzeuge sicherheitshalber einen neuen“), traut keine bestehende Installation dem Repo mehr. Dann sind **alle** folgenden Schritte nötig, in dieser Reihenfolge:
> 1. Neuen öffentlichen Schlüssel in den Fork bringen (`codeforge.gpg`, Keyring-Paket).
> 2. **Bootstrap neu bauen: `build-bootstrap-repo.yml` mit `mode=full`** (der Bootstrap enthält den alten Keyring).
> 3. **Alle Stages/Gruppen neu bauen und veröffentlichen** (`build-all-stages.yml`, alle Gruppen, `mode` so, dass der Index vollständig neu signiert wird; Eingaben gemäß Tabelle im Dokument; `mode=full` ersetzt gh-pages und Release-Assets, `add` ergänzt nur).
> 4. Neue Bootstrap-Version und SHA-256 für aarch64, arm, x86_64 in `gradle.properties` (`codeforgeBootstrapVersion`) eintragen, App neu bauen und veröffentlichen.
> 5. Geräte mit der alten App/dem alten Bootstrap: App neu installieren oder den Keyring von Hand aktualisieren (Befehle im Dokument), sonst meldet `apt` „NO_PUBKEY“ bzw. „nicht signiert“.
> Deshalb: Backup prüfen, bevor ein neuer Schlüssel auch nur erwogen wird. Wer nur die **Passphrase** vergessen hat, kann den Schlüssel nicht retten (kein Neubau ohne Passphrase möglich): dann gilt dieser Kasten.

Der Agent prüft vor dem Schreiben im Code, **welche** dieser Schritte tatsächlich nötig sind (z. B. ob bei `add` der Release-Index über alle bestehenden Pakete neu signiert wird und ob die einzelnen `.deb` signiert sind), und schreibt nur das, was belegt ist; alles andere steht als „nicht geprüft“ im Bericht. Die Reihenfolge oben ist die Vorgabe des Inhabers.

### 03 – GitHub-Token

* **Wozu:** Secret `PACKAGES_REPO_TOKEN` erlaubt dem Workflow in `CodeForgeMobile`, ins Repo `terminal-packages-codeforge` zu veröffentlichen (Release und gh-pages). Nur bei `publish` nötig; wird zusätzlich lokal für `git push` und `gh` verwendet.
* **Erzeugen (Schritt-für-Schritt mit den GitHub-Menüpfaden, vor Veröffentlichung gegen die aktuelle GitHub-Oberfläche prüfen):** *Fine-grained PAT*: Repository access = nur `<owner>/terminal-packages-codeforge`, Permission **Contents: Read and write**, möglichst kurze Laufzeit. *Classic PAT* (nur wenn nötig): Scope `repo` (größere Rechte, daher zweite Wahl).
* **Namen:** GitHub erlaubt keine Secret-Namen mit Präfix `GITHUB_`; deshalb `PACKAGES_REPO_TOKEN`. Lokal als Umgebungsvariable `GITHUB_TOKEN` (oder anderer Name über `TOKEN_VAR`).
* **Ablegen:** über `setup-gpg-secrets.sh` (prüft vorab den Schreibzugriff) oder von Hand in *Settings → Secrets and variables → Actions*; nie in Dateien im Repo, Commits, Chats, Issues oder Logs.
* **Ablauf und Erneuerung:** Ablaufdatum im Kalender eintragen; vor Ablauf neuen Token erzeugen, Secret ersetzen, Testlauf mit `publish=false`, dann alten Token löschen. Ein abgelaufener Token zeigt sich beim Veröffentlichen als 401/403/„Permission denied“ (siehe `08`).
* **Verdacht auf Leck:** sofort widerrufen (GitHub → Developer settings), neuen erzeugen, Secret ersetzen, Aktivitätsprotokoll des Repos prüfen. Dasselbe gilt für die Passphrase: **jede** Passphrase, die in einem Chat, Log oder Commit gelandet ist, gilt als kompromittiert (neue Passphrase → bedeutet neuen oder umgeschützten Schlüssel; Umschützen mit `gpg --change-passphrase`, dann Secrets aktualisieren; Schlüssel selbst bleibt, **kein** Neubau, solange der private Schlüssel nicht selbst abgeflossen ist; ist er es, gilt der Kasten oben).
* Rechte-Tabelle: welcher Schritt welches Recht braucht (Workflow-Lauf, Release, gh-pages, Pull von öffentlichen Repos ohne Token).

### 06 – Pflege nach Auslöser (Kernstück)

Tabelle mit den Spalten **Auslöser · Betroffen · Schritte (Workflow + Eingaben) · Aufwand · Prüfung**. Mindestens diese Zeilen (Inhalte aus Code und Workflows belegen; Eingabenamen aus den Workflow-Dateien übernehmen, nicht aus dem Gedächtnis):

| Auslöser | Was zu tun ist |
|---|---|
| Android-SDK/NDK/CMake-Version ändert sich | **nur** `codeforge-tools`-Spiegel (`mirror-releases.yml`) und Versionseintrag im Katalog der App; **kein** APT-/Bootstrap-Lauf. Prüfung im SDK-Manager der App |
| Compose-BOM oder Kotlin-Version ändert sich | `build-compiler-runtime.yml` (Tag `compiler-runtime-<bom>-k<kotlin>`), `publish`, `with_extended_icons` nach Bedarf; Ziel `codeforge-tools` |
| Neues Paket ins Repo | Eintrag in `apt-stages.conf`/`apt-groups.json`, dann die betroffene Gruppe mit `mode=add`; Prüfung `check-apt-consistency.py` (Task 45), `apt-cache policy <paket>` auf dem Gerät |
| Ein Paket schlägt im Lauf fehl / fehlt im Repo | Log nach `FEHLGESCHLAGEN`/`FEHLER` durchsuchen; Task 48 macht verworfene Pakete sichtbar; Gruppe erneut mit `keep_going` oder nur das Paket (`repo_packages`) |
| Upstream-termux-packages soll gemerged werden | **Nur bei Anlass** (Sicherheitslücke, benötigtes Paket); Fork auf festem Commit lassen; Merge in eigenem Zweig, Patches des Forks (Prefix, Keyring, Quellen) erneut prüfen, betroffene Gruppen neu bauen; Aufwand ist der größte Posten und entfällt bei eingefrorenem Upstream |
| Bootstrap-Inhalt ändert sich | `build-bootstrap-repo.yml`, danach neue Version/SHA-256 in `gradle.properties`, App-Release; nur selten nötig, wenn der Bootstrap klein bleibt |
| **Neuer GPG-Schlüssel** | **Kasten „Neuer Schlüssel = alles neu“** (siehe 02) |
| Token läuft ab / ist geleakt | Abschnitt 03 |
| Prefix/App-Id ändert sich | Alles neu (Pakete, Bootstrap, Keyring); praktisch nie; Folgen auflisten |
| Artefakte laufen ab (14 Tage) | Nur Hinweis: Ergebnisse der Läufe vor Ablauf veröffentlichen (`publish`) oder erneut bauen |
| Paket größer als 95 MiB | Release `large-packages` mit `large-packages.txt(.asc)`, nicht in gh-pages |
| Regelmäßig | Wöchentlich: Wochenlauf der Doku-Prüfung (99.5) ansehen; monatlich: `check-apt-consistency.py` ausführen; vierteljährlich: GPG-Backup prüfen (02, Punkt 4), Token-Ablaufdatum prüfen, Abhängigkeiten der Workflows (Action-Versionen) ansehen |

Dazu ein **Entscheidungsbaum** (Mermaid-`flowchart`): „Was habe ich geändert?“ → führt zu den Zeilen oben.

### 08 – Fehlersuche (Startbestand, aus dem Bestand zu belegen)

Aufnehmen und mit Quelle (Datum, Lauf/Log) belegen, mindestens: Passphrase/Secrets fehlen („Secrets prüfen“ schlägt fehl); Token ohne Schreibrecht; `trusted.gpg.d/codeforge.gpg fehlt`; `.deb enthält 'com.termux'` (Prefix-Prüfung, Beispiel openssh, Task 48); Paket fehlt im Repo trotz grünem Lauf; apt-Zustand „broken“ durch `dpkg-perl`/`dpkg-scanpackages` mit unerfüllter Abhängigkeit `clang` (Task 45); „not going to be installed“ bei jedem Install; Absturz im Editor durch Initialisierungsreihenfolge (Task 46); Artefakte abgelaufen; Paket >95 MiB; Bootstrap-SHA stimmt nicht mit `gradle.properties`; `NO_PUBKEY`. Jede Zeile: Symptom · Wo sichtbar · Ursache · Maßnahme · Verweis.

### Qualitätskriterien für den Einsteigertest

* Kein Fachbegriff ohne Erklärung bei der ersten Nennung (Glossar in `00`); keine Abkürzung ohne Auflösung.
* Jeder Schritt nennt **wo** geklickt/getippt wird (GitHub-Menüpfad, Terminal, Ordner) und **was man danach sieht**.
* Befehle sind kopierbar und funktionieren unverändert, wenn die Platzhalter ersetzt sind; sie stehen in Codeblöcken und sind mit `shellcheck` geprüft, soweit Shell-Skripte.
* Keine Geheimnisse, keine privaten Pfade, keine E-Mail-Adressen; Beispielwerte sind erkennbar als Beispiel markiert.
* Ein „Trockenlauf“-Abschnitt ermöglicht, alle Prüfungen ohne Veröffentlichung auszuführen (`publish=false`, `NO_SECRETS=1`).
* **Abnahmetest durch einen Dritten:** Eine Person ohne Projektwissen führt `01` bis `05` in einem **neuen Fork** (eigene Konten, eigener Prefix) aus. Jede Stelle, an der sie stockt, wird als Fehler im Handbuch behandelt. Dieser Test kann der Agent nicht selbst durchführen; im Bericht steht ausdrücklich **nicht getestet**, bis der Inhaber oder eine dritte Person ihn gemacht hat.

### Pflege des Handbuchs

* Workflow-Eingaben, Gruppen, Bootstrap-Version und Stage-Liste sind **generierte Blöcke** (99.3, Generator `apt-groups` erweitern um die Workflow-Eingaben aus `.github/workflows/*.yml`), damit das Handbuch nicht veraltet.
* `docs_facts_check.py` prüft zusätzlich, dass jede in `06` genannte Workflow-Datei und jeder Eingabename existiert.
* Im Wiederholungslauf (99.7) kommt ein Schritt dazu: „Handbuch gegen die Workflows gegenlesen; Änderungen seit dem letzten Lauf eintragen“.

## Abnahme

* Struktur (`docs/index.md`, `docs/app/`, `docs/generated/`) angelegt, Index vollständig, bekannte Widersprüche korrigiert.
* Mindestens die Dokumente `architecture.md`, `modules.md`, `navigation.md`, `data-and-settings.md`, `package-pipeline.md`, `plugins.md` mit prüfbaren Mermaid-Diagrammen; generierte Teile nur über `docsGenerate` erzeugt.
* Dokka-Ausgabe für `:libs:plugin-api` und die Schnittstellen in `:core:domain`; öffentliche API dort vollständig mit KDoc, sonst im Bericht offene Fragen aufgelistet.
* `./gradlew docsCheck` lokal und in der CI grün; Wochenlauf konfiguriert; `./gradlew :app:assembleDebug` weiterhin grün.
* Betriebshandbuch `docs/operations/` (99.8) vollständig: GPG-Erzeugung, Sicherung und Wiederherstellung, GitHub-Token, Pflege nach Auslöser, Stolperfalle „neuer Schlüssel = Bootstrap `full` und alle Stages“ hervorgehoben, Fehlersuche; Einsteigertest durch eine dritte Person als offener Punkt vermerkt, bis er stattgefunden hat.
* Bericht mit **getestet / nicht getestet / offene Punkte** und dem Basiswert der KDoc-Abdeckung je Modul.
