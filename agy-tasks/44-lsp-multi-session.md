# Task 44 – LSP: mehrere Server gleichzeitig (eine Sitzung pro Server und Workspace)

**Ziel:** Mehrere Language Server laufen **gleichzeitig**, je einer pro Server-Art und Workspace. Ein Wechsel zwischen Tabs verschiedener Sprachen (z. B. Kotlin → YAML → Kotlin) startet nichts neu und verliert keinen Index. Die Zahl gleichzeitiger Server ist begrenzt (RAM/Akku), ungenutzte werden beendet.
**Warum jetzt:** Mit Task 43 kommen vier weitere Server dazu (XML, YAML, TOML, Bash). Heute gibt es **eine** Sitzung; jeder Sprachwechsel beendet den laufenden Server und startet den nächsten. Beim Kotlin-Server und `jdtls` ist das ein JVM-Neustart samt Neuaufbau des Index. Task 43 ist unabhängig davon umsetzbar, lohnt sich aber erst mit diesem Task richtig.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:libs:*` bzw. im Feature-Modul, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog, Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) bleibt grün.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen; prüfe sie vor dem Start selbst und korrigiere sie im Bericht.
* Geheimnisse nie ins Log, nie in Dateien im Projektordner, nie in Berichte.
* Bericht in `docs/lsp-multi-session-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash eintragen).
* Build grün: `./gradlew :app:assembleDebug` und `./gradlew :libs:lsp-client:test :feature:editor:testDebugUnitTest :core:data:testDebugUnitTest :libs:plugin-api:testDebugUnitTest`.

## 44.1 Ausgangslage (erst selbst prüfen)

* `:core:domain` → `LspClientRepository` (Interface): `serverState`, `serverCapabilities`, `diagnostics` (Flow aus `Pair<Pfad, Diagnosen>`), `applyEditHandler`, `start(serverCommand, workspaceRootPath, extraProcessEnv)`, `stop()`, `didOpen/didChange/didClose`, `request*`-Methoden. **Alle Anfragen sind pfadbasiert**, eine Sprach-ID kommt nur bei `didOpen`.
* `:libs:lsp-client` → `LspClientRepositoryImpl` (`@Singleton`, rund 420 Zeilen): hält **einen** `Process`, **eine** `LspRpcConnection`, `documentVersions`, `serverState`, `serverCapabilities`. `start` erzeugt den Prozess und sendet `initialize`; `stop` sendet `shutdown`/`exit`. Das Interface wird über `LspClientModule` gebunden. Vorhandene Tests: `LspFakeServerTest`, `LspJsonMapperTest`, `LspWorkspaceEditApplierTest` (nutzt `attachConnection(...)` als Testzugang).
* `:feature:editor` → `LspSessionController` (`@Singleton`): merkt sich **eine** `activeWorkspaceRoot` und **eine** `activeLanguageId`; bei Wechsel ruft er `stop()` und danach `start(...)`. Er löst den Startbefehl über `LspServerResolver.resolve(languageId)` auf (`LspServerLaunch(command, env)`).
* `EditorViewModel` nutzt `lspClient` direkt (Diagnosen, Capabilities, `didOpen/didChange/didClose`, Code-Aktionen, Rename, Symbole, Definition, Referenzen) und den `LspSessionController` (Start bei Datei öffnen, Tab-Wechsel, Projektwurzel setzen, Tab schließen → `stop()` bei letztem Tab).
* **Weitere Nutzer von `LspClientRepository`**, die nicht brechen dürfen: `LspCompletionProvider`, `CodeFormatterRepositoryImpl` (`:core:data`; liest `serverState.value` und `serverCapabilities.value` global, **nicht** pro Datei) und `:libs:plugin-api` (`CodeForgePlugin`/`PluginRuntime` reichen das Repository an **Plugins** durch; Beispiele in `examples/`).
* Server-Zuordnung: `LspServerResolverImpl` (`:feature:settings`) bildet Sprach-ID auf Katalogeintrag ab. `html`, `css` und `json` teilen sich denselben Katalogeintrag, starten aber **unterschiedliche** Skripte; `c` und `cpp` teilen sich `clangd`.

## 44.2 Entwurf

**Sitzungsschlüssel:** `(serverId, workspaceRoot)`, **nicht** die Sprach-ID. `c` und `cpp` nutzen dieselbe `clangd`-Sitzung; `html`, `css` und `json` sind drei Sitzungen, weil es drei Prozesse sind. `LspServerLaunch` bekommt dafür ein Feld `serverId: String` (Standardwert aus dem Befehl abgeleitet, damit bestehende Aufrufer und Tests unverändert kompilieren); der Resolver füllt es mit der Paket-`id` bzw. dem Skriptnamen.

**`LspServerSession` (neu, `:libs:lsp-client`):** Der bisherige Inhalt von `LspClientRepositoryImpl` (Prozess, Verbindung, `documentVersions`, Capabilities, Zustand, `request*`, Benachrichtigungs- und Server-Anfrage-Behandlung) wandert in eine Klasse **ohne** `@Singleton`, die genau eine Sitzung führt. Kein Verhalten ändern, nur verschieben; die vorhandenen Tests laufen darauf weiter.

**`LspClientRepositoryImpl` wird Router:** Er implementiert weiter `LspClientRepository`, hält `Map<SessionKey, LspServerSession>` und eine Zuordnung `Pfad → Sitzung` (gefüllt bei `didOpen`, geleert bei `didClose`). Pfadbasierte Anfragen gehen an die Sitzung des Pfades; gibt es keine, kommt `Result.failure` bzw. ein leeres Ergebnis wie heute bei „kein Server“, **kein** Absturz. Neue Methoden im Interface, alle mit Standardimplementierung, damit Plugins und Beispiele **quellkompatibel** bleiben:
* `suspend fun startSession(sessionId: String, languageIds: Set<String>, serverCommand: List<String>, workspaceRootPath: String, extraProcessEnv: Map<String, String> = emptyMap()): Result<Unit>` und `suspend fun stopSession(sessionId: String)`, `suspend fun stopAll()`. Das alte `start/stop` bleibt und arbeitet auf einer Standardsitzung (Verhalten wie bisher, bis alle Aufrufer umgestellt sind).
* `val sessionStates: StateFlow<Map<String, LspServerState>>`
* `fun capabilitiesFor(path: String): LspServerCapabilities?` und `fun stateFor(path: String): LspServerState`
* `serverState` und `serverCapabilities` bedeuten künftig „die Sitzung des **aktiven** Dokuments“; Setzer: `fun setActiveDocument(path: String?)`. So ändert sich für bestehende Beobachter (Editor-UI) nichts.
* `diagnostics` fasst die Flüsse aller Sitzungen zusammen. Beim Beenden einer Sitzung werden die Diagnosen **ihrer** Dateien geleert (sonst bleiben Fehler stehen, die niemand mehr aktualisiert).
* `applyEditHandler` gilt für alle Sitzungen.

**`LspSessionController`:** Statt „stoppe alten, starte neuen“ gilt „stelle sicher, dass die Sitzung für diese Sprache läuft“. Er löst über `LspServerResolver` auf, startet die Sitzung nur, wenn es sie noch nicht gibt, und beendet **keine** anderen. Ein Wechsel der Projektwurzel beendet alle Sitzungen des alten Workspaces. Ein Fehler beim Start betrifft nur diese Sitzung (andere laufen weiter); Fehlerzustand pro Sitzung merken und nicht bei jedem Tab-Wechsel erneut versuchen (kurze Sperrfrist, manuell „Server neu starten“ zulassen).

**Begrenzung und Leerlauf:**
* Höchstzahl gleichzeitiger Sitzungen als Einstellung (Standard 3; Wert 1 entspricht dem heutigen Verhalten). Wird sie überschritten, beendet der Controller die am **längsten nicht genutzte** Sitzung (zuletzt benutzt = letzte Anfrage oder letztes `didOpen/didChange` an diese Sitzung), außer der des aktiven Dokuments.
* Leerlauf-Timeout (Standard z. B. 10 Minuten ohne offene Dokumente dieser Sitzung); danach `stopSession`. Zeitgeber über einen injizierbaren `CoroutineScope`/`TimeSource`, damit Tests mit virtueller Zeit laufen.
* Beide Werte im Einstellungsbildschirm (`:feature:settings`) mit Strings in en/de. Schwer gewichtete Server (JVM: Kotlin, jdtls, LemMinX) in der Statusanzeige als solche kennzeichnen, damit der Nutzer versteht, warum sie als erste ausgelagert werden; eine Gewichtung in die Verdrängungsregel einzubauen ist erlaubt, aber nur wenn es einfach bleibt und im Bericht begründet ist.

**Dokumente beim Neustart wieder anmelden:** Wird eine verdrängte Sitzung später wieder gebraucht, kennt der neue Server die offenen Dateien nicht. Der Controller braucht eine Schnittstelle zum Editor (z. B. `OpenDocumentsProvider` in `:core:domain`, vom `EditorViewModel` implementiert), liefert `(Pfad, Sprach-ID, aktueller Inhalt)` aller offenen Tabs dieser Sitzung, und sendet nach `initialize` ein `didOpen` mit dem **aktuellen** (ungespeicherten) Inhalt. Versionszähler pro Dokument bei Neuanmeldung neu beginnen.

**Anzeige:** Die bestehende Statusanzeige (`lspServerState`) zeigt den Zustand der Sitzung des aktiven Tabs. Zusätzlich eine kleine Liste „laufende Server“ (Name, Zustand, Aktion „Beenden“/„Neu starten“), z. B. im Einstellungsbildschirm oder als Menü an der Statusanzeige.

## 44.3 Anpassungen an den Nutzern

* `EditorViewModel`: `setActiveDocument(path)` beim Tab-Wechsel; `lspSessionController.stop()` nur noch bei geschlossenem Projekt, nicht beim Schließen des letzten Tabs einer Sprache (Leerlauf-Timeout übernimmt); Diagnosen pro Pfad bleiben wie sind.
* `CodeFormatterRepositoryImpl`: statt globalem `serverState/serverCapabilities` die pfadbezogenen `stateFor(path)`/`capabilitiesFor(path)` verwenden. Sonst formatiert eine YAML-Datei mit den Fähigkeiten des Kotlin-Servers (oder gar nicht).
* `LspCompletionProvider`: unverändert nutzbar, weil pfadbasiert; prüfen, ob er einen globalen Zustand liest.
* `:libs:plugin-api` und `examples/*-lsp-plugin`: müssen **unverändert kompilieren** (Quellkompatibilität). Prüfe `./gradlew :examples:kotlin-lsp-plugin:assemble :examples:java-lsp-plugin:assemble` (bzw. die dort vorhandenen Tasks) und dokumentiere im Bericht, falls Plugins das Verhalten „ein Server“ voraussetzen.

## 44.4 Tests (Pflicht, JVM)

* **Router:** Pfad → richtige Sitzung für `request*`, `didChange`, `didClose`; unbekannter Pfad → kontrolliertes Ergebnis ohne Ausnahme; zwei Sitzungen gleichzeitig, Anfragen vermischen sich nicht (Fake-Sitzungen, die Aufrufe zählen).
* **Sitzungsschlüssel:** `c` und `cpp` teilen eine Sitzung; `html`, `css`, `json` sind drei; gleicher Server in zwei Workspaces sind zwei Sitzungen.
* **Controller:** Tab-Wechsel Kotlin → YAML → Kotlin startet jeden Server **genau einmal**; Wechsel der Projektwurzel beendet die alten Sitzungen; Startfehler einer Sitzung lässt die anderen laufen; Sperrfrist nach Fehler; manueller Neustart.
* **Begrenzung/Leerlauf:** Mit Höchstzahl 2 verdrängt die dritte Sitzung die am längsten ungenutzte, nie die des aktiven Dokuments; Leerlauf-Timeout mit virtueller Zeit; Höchstzahl 1 reproduziert das alte Verhalten (bestehende Controller-Tests laufen mit dieser Einstellung unverändert grün, Änderungen daran begründen).
* **Wiederanmelden:** Nach Verdrängung und erneutem Start bekommt der Fake-Server `didOpen` für alle offenen Dokumente mit aktuellem Inhalt.
* **Diagnosen:** Beim Beenden einer Sitzung werden deren Diagnosen geleert, die anderer bleiben.
* **Formatierung:** `CodeFormatterRepositoryImpl` wählt Fähigkeiten pro Pfad.
* Vorhandene Tests (`LspFakeServerTest`, `LspSessionControllerTest`, Formatter- und Plugin-Tests) laufen weiter grün oder sind mit Begründung angepasst.
* **Nicht in JVM-Tests prüfbar** und im Bericht ehrlich als nicht getestet auszuweisen, solange nicht auf dem Gerät gemessen: tatsächlicher Speicher- und Akkuverbrauch mit mehreren echten Servern (Kotlin-LS + jdtls gleichzeitig), Verhalten unter Androids Prozessbeschränkungen (Prozesse von Termux-Kindern können vom System beendet werden, „Phantom-Process-Killer“ ab Android 12), Reaktionszeit beim Tab-Wechsel.

## Abnahme

Build und Tests grün; Bericht mit (1) dem tatsächlichen Datenfluss nach dem Umbau (Router, Sitzungen, Controller), (2) der Liste der Interface-Änderungen und der Bestätigung, dass Plugins und Beispiele unverändert kompilieren, (3) Standardwerten für Höchstzahl und Leerlauf samt Begründung, (4) ehrlicher Aussage, welche Messungen auf dem Gerät gemacht wurden (sonst „nicht getestet“).
