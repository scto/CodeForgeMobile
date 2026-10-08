# Editor-Werkzeuge, Git-Panel, Drawer-Leiste, Settings, Submodule Maker

Stand: 2026-10-06 · Autor: Thomas Schmid

> **Ehrlich vorab:** Nichts davon wurde mit Gradle/Android gebaut. Verifiziert sind nur die reine
> JVM-Logik (`:libs:code-tools`: 53 Tests; `:core:domain`: 18 [Git-Modelle, Hunk-Patcher, Konflikt-Parser]; `:core:data`
> Git: 35 gegen echte JGit-Repos [JGit 5.13 lokal, Projekt nutzt 6.10] und 6 für die Credential-Ablage mit Fakes).
> `GitViewModel` wurde zusätzlich mit Stubs für Hilt/Lifecycle gegen ein echtes Repo durchgespielt (Hunk-Staging, Amend,
> Stash, Tags, Commit-Details, Merge-Konflikt + Konflikt-Editor). Alle Compose-/Hilt-/KSP-/sora-Teile sind ungebaut →
> siehe `agy-tasks/10-verify-editor-git-drawer.md`.

## Überblick

| Bereich | Modul | Kern |
|---|---|---|
| Such-/Format-/Zeilen-/Modul-Logik (reines Kotlin/JVM) | `:libs:code-tools` | `TextSearch`, `ProjectSearcher`, `CodeFormatter`, `LineEdits`, `ModuleMaker` |
| Editor (Suchleiste, Format, Zeilenbefehle, Sprung) | `:feature:editor` | `EditorSearchBar`, `EditorController`, `EditorScreen` |
| Projektsuche & -ersetzung | `:feature:search` | `SearchViewModel`, `SearchScreen` |
| Git-Panel | `:feature:git` | `GitPanel` + Tabs Änderungen / Graph / Branches / Stash, Diff-Viewer, Commit-Details, Konflikt-Editor |
| Git-Backend | `:core:domain` / `:core:data` | `GitRepository` (JGit), `GitSettingsRepository` (Keystore) |
| Git-Einstellungen (Name, E-Mail, Token) | `:feature:settings` | `GitSettingsRoute` |
| Submodule Maker | `:feature:modulemaker` | `ModuleMakerRoute` |
| Drawer mit Icon-Leiste | `:app` | `WorkspaceDrawer` |

## Drawer
`ProjectWorkspaceRoute` → `WorkspaceDrawer`: eigene `Surface` (Breite `min(92 % Bildschirm, 420 dp)`, da
`ModalDrawerSheet` bei 360 dp kappt). Oben eine `LazyRow` (horizontal wischbar, wenn breiter als der Drawer)
mit **Files · Suche · Git · Module · Terminal · Settings**. Neue Bereiche = ein Eintrag in `enum DrawerSection`
+ ein `when`-Zweig. Files/Suche/Git/Module sind Panels (Auswahl via `rememberSaveable`); Terminal und
Settings schließen den Drawer und navigieren. Der Git-Button im Dateibaum wurde entfernt.

## Editor
* **Suchen/Ersetzen (Datei):** eigene Engine (`java.util.regex`): Aa / ganzes Wort / Regex, `$1`, `${name}`,
  Trefferzähler „3 / 12“, verständliche Regex-Fehler, Timeout gegen katastrophales Backtracking. Treffer
  werden per Selektion angesteuert; Ersetzen ist ein **Undo-fähiger** Edit (`applyTextChange` ändert nur den
  Mittelteil). *Bewusst nicht* sora `EditorSearcher` (API-Unsicherheit, mögliche invertierte Case-Flag im alten Code);
  Folge: kein Dauer-Highlighting aller Treffer, nur der aktuelle ist markiert.
* **Formatter** (Button „Formatieren“, ohne LSP nutzbar): Kotlin/Java/C-artig/Gradle (Klammer-/Einrückungslogik mit
  Lexer für Strings, Raw-Strings, Kommentare), JSON (Pretty-Print), XML (konservativ), Markdown/Plain
  (Whitespace). Invariante: nur Whitespace ändert sich. Grenzen: kein Umbruch langer Zeilen, keine Import-
  Sortierung, keine semantische Formatierung; bei Zweifel Warnung statt Umbau. Der LSP-Button erscheint nur bei
  `isLspConnected`.
* **Zeilenbefehle:** Kommentar umschalten (sprachabhängig), Ein-/Ausrücken, Duplizieren, Löschen, Verschieben
  hoch/runter, Gehe zu Zeile.
* **Highlighter:** bleibt TextMate/Tree-Sitter (unverändert).
* **Sprung aus Suche:** `OpenFileRequestBridge.requestOpenAt(...)` → `JumpTarget` im Editor-State →
  `EditorRoute` springt, sobald Datei aktiv und Text im Widget ist.

## Projektsuche
Debounced (350 ms), IO-Dispatcher, abbrechbar. Filter (Globs include/exclude, kommagetrennt), Standard-Ausschlüsse
(`build`, `.git`, `.gradle` …), nur streng UTF-8-lesbare Dateien < 2 MB (nie Binär-/Fremdkodierung beschädigen),
max. 5000 Treffer. Ersetzen: ein Treffer / Datei / alles (Bestätigungsdialog); vorher `FileSyncBridge.requestFlush`
(ungespeicherte Editor-Puffer), danach `notifyExternalChange` (offene Tabs laden neu); Schreiben atomar;
Ersetzungssyntax wird vorab validiert (kein Teilergebnis).

## Git
JGit, **HTTPS + Token** (kein SSH). Backend `GitRepository` (`:core:domain`) / `GitRepositoryImpl` (`:core:data`), UI `:feature:git`.

**Panel:** Header (Branch, ↑/↓, Fetch/Pull/Push/Refresh/Settings), Operations-Banner, Identitäts-Hinweis, Tabs
**Änderungen · Graph · Branches · Stash**.

| Bereich | Funktionen |
|---|---|
| Änderungen | Stage/Unstage/Discard je Datei oder alle, **Hunk-weises Vormerken/Zurücknehmen/Verwerfen** im Diff-Viewer, Commit, **Amend** (Nachricht wird vorbelegt, Warnung bei bereits gepushtem Commit), Datei-Menü: Verlauf, Blame, im Editor öffnen, zu `.gitignore` |
| Konflikte | Konflikt-Editor je Block (Aktuell / Eingehend / Beide / Beide umgekehrt, „Alle: …“), Übernehmen schreibt die Datei und merkt sie vor, sobald alle Blöcke gewählt sind; ganze Datei per „Aktuelle/Eingehende Version“ (auch Löschen-vs-Ändern-Konflikte) |
| Operationen | Merge, **Rebase** (auf Branch; Fortsetzen / Überspringen / Abbrechen im Banner), **Cherry-Pick**, **Revert**, **Reset** (soft/mixed/hard), Commit auschecken (loser HEAD); Abbrechen für Merge/Cherry-Pick/Revert; die Commit-Nachricht einer laufenden Operation wird aus `MERGE_MSG` vorbelegt |
| Graph | Lanes per Canvas, Ref-Chips; Tippen auf einen Commit öffnet **Commit-Details** (volle Nachricht, geänderte Dateien, Datei-Diff, Aktionen Cherry-Pick/Revert/Reset/Branch hier/Tag hier/Auschecken) |
| Branches | anlegen (auch an einem Commit), auschecken, mergen, **rebasen**, **umbenennen**, löschen, Remote-Branch auf dem Server löschen, Remote setzen/entfernen, **Pull mit Rebase** (Schalter; sonst `pull.rebase`/`branch.<x>.rebase` aus der Config) |
| Tags | Liste (neueste Commits zuerst), anlegen (einfach oder annotiert, auch an einem Commit), löschen, **pushen**, Commit ansehen/auschecken |
| Stash | stashen (optional mit Nachricht, inkl. untracked), anwenden, **Pop**, löschen; Tippen zeigt den Inhalt (nur lesend) |
| Datei | **Verlauf** (Commits, die die Datei geändert haben), **Blame** (Stand des letzten Commits; Tippen öffnet den Commit) |

* Diff: Unified-Diff-Parser, Vollbild-Dialog mit Zeilennummern; Untracked/ohne HEAD → synthetischer Diff.
* **Hunk-Logik** ist reines Kotlin (`HunkPatcher`, gegen echte `git diff`-Ausgaben fuzz-geprüft inkl. CRLF und fehlendem
  End-Newline); der Index wird per `DirCacheEditor` geschrieben. Untracked-Dateien werden ganz vorgemerkt/verworfen,
  Nicht-UTF-8- und Binärdateien lehnen hunk-weises Bearbeiten ab. Hunk-Indizes beziehen sich auf den frisch geladenen Diff.
* **Editor-Sync:** Git-Aktionen, die Dateien ändern, speichern vorher ungespeicherte Editor-Puffer
  (`FileSyncBridge.requestFlushDirectory`) und laden danach offene Tabs unter dem Repo neu (`notifyDirectoryChange`).
* Bewusst nicht enthalten: interaktiver Rebase, Submodule, Worktrees, SSH, Zeilen-weises Staging, GPG-Signaturen,
  Stash einzelner Dateien, `git bisect`. Rebase/Cherry-Pick von Merge-Commits wird abgelehnt.
* **Git-Settings:** Name/E-Mail (SharedPreferences), Tokens je Host AES-256-GCM via Android Keystore
  (`KeystoreSecretBox`) — **auf Gerät ungetestet**. Identität wird beim Commit ins Repo-Config geschrieben.
* **git init bei Projekterstellung:** Schalter im Wizard (Standard an, persistiert als `skip_git_init`);
  `TemplateEngineRepositoryImpl` ruft nach der Generierung `git.init(dir, "Initial commit")`; Fehler verwerfen
  das Projekt nicht (Hinweis in `ProjectHandle.gitNote`). Fehlt die Identität, zeigt der Wizard einen Hinweis.

## Submodule Maker
Eingabe `:lib:core:libcoredu` → legt `lib/core/libcoredu/` an mit `build.gradle(.kts)` (DSL, Namespace, compileSdk/
minSdk, Plugin-Aliase aus Projekt/Katalog erkannt), `src/main/kotlin/<pkg>/Libcoredu.kt`, `.gitignore`, bei
Android-Library Manifest + Proguard-Dateien; trägt `include(":lib:core:libcoredu")` in `settings.gradle(.kts)` ein
(idempotent), ergänzt fehlende Root-Plugins (`apply false`) nur wenn im Katalog vorhanden, sonst Warnung. Verweigert
existierende nicht-leere Ordner. Danach ist ein Gradle-Sync nötig.

## Bekannte Risiken / ungeprüft
1. Gesamter Gradle-Build (neue Module `:feature:search`, `:feature:modulemaker`, `:libs:code-tools`).
2. sora-API: `Content.replace(line,col,line,col,text)`, `text.indexer.getCharPosition`, `beginBatchEdit`, `cursor.left/right`,
   `setSelectionRegion` — nach 0.23.4-Signaturen prüfen.
3. Icons (`material-icons-extended` ist in den Modulen vorhanden; neu genutzt: `Sell`, `Inventory2`, `MoveToInbox`, `Unarchive`,
   `RestartAlt`), JGit-6.10-API-Unterschiede (Tests liefen gegen 5.13: u. a. `StashCreateCommand.setIncludeUntracked`/
   `setWorkingDirectoryMessage` [MessageFormat `{0}`=Branch], `DirCacheEntry.setLength`, `DiffFormatter.scan(RevTree?, RevTree)`),
   Keystore auf Gerät.
4. Drawer-UX bei sehr schmalen Displays; Git-Graph bei großen Historien (aktuell begrenztes Log).
5. Regex läuft mit `java.util.regex` (Java-Syntax), nicht RE2.
