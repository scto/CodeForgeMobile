# Task 10 – Build & Verifikation: Editor-Werkzeuge, Git-Panel, Drawer, Submodule Maker

Kontext: `docs/editor-tools-git-drawer.md`. Nichts davon wurde bisher mit Gradle gebaut.

1. `./gradlew assembleDebug` — alle Compile-Fehler in `:libs:code-tools`, `:feature:search`, `:feature:modulemaker`,
   `:feature:git`, `:feature:settings`, `:feature:editor`, `:app`, `:core:data` beheben (ohne Architektur zu ändern).
2. `./gradlew :libs:code-tools:test :core:domain:test :core:data:test` — alle grün (JGit-Tests: ggf. `user.home`/
   `gpg.format` der Umgebung beachten, Commits laufen mit `setSign(false)`).
3. sora-editor 0.23.4: Signaturen in `EditorController` prüfen (`Content.replace`, `indexer.getCharPosition`,
   `beginBatchEdit/endBatchEdit`, `cursor.left/right`, `setSelectionRegion`); Undo nach „Ersetzen/Formatieren“
   muss EINEN Schritt zurücknehmen.
4. Gerät/Emulator: Drawer-Leiste (wischen bei schmalem Display), Git: init/commit/push mit Token (Keystore),
   Projektsuche inkl. Sprung zur Zeile, „Alle ersetzen“ mit offenem, ungespeichertem Tab, Submodule Maker mit
   `:lib:core:libcoredu` in einem Wizard-Projekt (danach Gradle-Sync).
5. Abweichungen und Fixes kurz in `docs/editor-tools-git-drawer.md` unter „Bekannte Risiken“ nachtragen.
6. Git-Erweiterungen (Stash, Tags, Rebase, Cherry-Pick, Revert, Reset, Amend, Hunks, Blame, Verlauf, Konflikt-Editor):
   - JGit-6.10-Kompilierung von `GitRepositoryImpl` prüfen (siehe „Bekannte Risiken“ Punkt 3); die Tests in
     `core/data/.../GitAdvancedTest.kt` (23) müssen grün sein – bei Abweichungen zuerst die Nachrichten-Formatierung von
     `stashCreate` (`{0}` = Branch) und den `DirCacheEditor`-Teil von `applyHunks` ansehen.
   - Gerät, in einem Test-Repo: (a) zwei Hunks einer Datei, nur einen vormerken → Commit enthält nur diesen;
     (b) Stash mit untracked Datei, Pop; (c) Merge-Konflikt → Konflikt-Editor „Beide“ → Commit; (d) Rebase mit Konflikt →
     lösen → „Fortsetzen“; (e) Cherry-Pick mit Konflikt → „Abbrechen“; (f) Amend, danach Push (erwartet: abgelehnt, wenn
     vorher gepusht); (g) Tag anlegen und pushen, Remote-Branch löschen; (h) Blame/Verlauf einer Datei, Tippen auf Commit.
   - Editor-Sync: Datei im Editor ändern (ungespeichert), dann Checkout/Stash/Pull im Git-Panel → Änderung muss vorher
     gespeichert sein und der Tab danach den neuen Inhalt zeigen (`FileSyncBridge.requestFlushDirectory` / `notifyDirectoryChange`).
   - Compose: `ScrollableTabRow` mit 4 Tabs, verschachtelte Dialoge (Commit-Details → Datei-Diff, Verlauf → Commit),
     Z-Reihenfolge der Bestätigungsdialoge über den Vollbild-Dialogen.
