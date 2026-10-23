# Task 39: Git-Konflikt-UI erweitern – Abschlussbericht

## 39.0 Bestandsaufnahme

Vor Beginn der Umsetzung wurde der bestehende Code in `feature/git` (`ConflictEditor.kt`, `GitViewModel.kt`, `GitContract.kt`, `ChangesTab.kt`), `core/domain` (`GitRepository.kt`, `ConflictParser.kt`, `GitState.kt`) und `core/data` (`GitRepositoryImpl.kt`) analysiert:

| Anforderung (Punkt aus Task 39) | Status vor Task 39 | Fundstellen vor Task 39 | Maßnahme in Task 39 |
|---|---|---|---|
| **1. Multi-File-Konflikt-Navigation & Fortschritt** | Teilweise | `GitContract.ConflictEditorState` hielt nur `filePath` (Einzelfile). In `ChangesTab.kt` gab es Datei-Auflistung, aber keine Navigation zwischen mehreren Konflikten im Editor. Rebase-Schritt (`3/7`) fehlte in Domain/UI. | `allConflictingPaths`, Vor/Zurück-Buttons mit Zähler `(i / total)`, Auto-Advance nach Lösen & Stagen, Rebase-Schritt (`readRebaseStep()`) in Status/UI. |
| **2. 3-Way-View (diff3 / Common Ancestor)** | Teilweise | `ConflictParser.kt` parste `diff3`-Ancestor (`|||||||`), aber `ConflictEditor.kt` bot keinen Umschalter zur Ancestor-Basisansicht. | Ancestor-Diff-Anzeige (`BASE_BG`, diff3-Basisblock mit Toggle-Chip „Basis (diff3) anzeigen/verstecken“). |
| **3. Manuelle Block-Bearbeitung & Freitext** | Fehlt | `ConflictEditor.kt` erlaubte nur feste Aktionen (Ours, Theirs, Both) pro Block. Freitext / manuelle Bearbeitung fehlte. | Inline-Freitext-Editor pro Block (`customTexts`), Speichern/Abbrechen, Markierung als „Manuell editiert“. |
| **4. Word-Level-Diff-Highlighting** | Fehlt | Es gab nur zeilenbasiertes Highlighting (grün/rot für OURS/THEIRS). Sub-Line/Word-Diff fehlte. | `WordDiff.kt` (LCS Tokenizer/Differ im Domain-Modell), farbige Hervorhebung geänderter Wort-Tokens in `ConflictEditor.kt`. |
| **5. Binärdateien & Spezialkonflikte** | Fehlt | Binärdateien und Deleted-vs-Modified führten zu Marker-Parse-Fehlern. Zeilenenden (CRLF vs LF) wurden nicht explizit konserviert. | `BinaryFileDetector.kt`, Binärdatei-Hinweis & side-selection ("Keep ours" / "Take theirs"), Deleted-vs-Modified Konflikterkennung und Aktionskarten („Datei behalten“ / „Löschen“), CRLF/LF-Preservation. |
| **6. Konfliktblock-Navigation & Marker-Handling** | Teilweise | Blöcke wurden in LazyColumn gerendert. Kein Springen zum nächsten/vorherigen ungelösten Block via FAB/Toolbar. `<<<<<<<`-Sicherheitscheck vor Stage fehlte. | Vor/Zurück-Blocknavigation mit Autoscroll (`rememberLazyListState`, `animateScrollToItem`), Badge für ungelöste Blöcke, Marker-Safety-Check vor Stage (`hasUnresolvedMarkers`). |
| **7. Ergebnis-Vorschau & Konflikt-Undo** | Fehlt | Keine Gesamtdateivorschau vor Stage. Keine Undo/Redo-Historie für Blockentscheidungen. | Vollständige Dateivorschau (Dialog & Toggle), Undo-Stack (`history: List<ConflictUndo>`) mit Rückgängig-Button. |

---

## Umgesetzte Erweiterungen im Detail

### Teil (a) Multi-File & Block-Navigation (Commits `04e82c2`)
- **Multi-File Navigation:** `ConflictEditorState` erweitert um `allConflictingPaths`, `hasPrevFile`, `hasNextFile`. Header zeigt Dateizähler `(1 von N)` und Chevron-Navigation.
- **Auto-Advance:** Beim Stagen einer gelösten Datei wird automatisch zur nächsten Konfliktdatei gewechselt. Sind alle Dateien gelöst, schließt sich der Editor und eine Erfolgsmeldung wird angezeigt.
- **Rebase-Fortschritt:** `GitRepositoryImpl` extrahiert aktuellen Rebase-Schritt und Gesamtzahl (`.git/rebase-merge/msgnum` / `end` bzw. `rebase-apply/next` / `last`). Im `GitPanel` und Status wird z. B. `Rebase (Schritt 3 von 7)` angezeigt.

### Teil (b) Inline-Bearbeitung, Undo & Marker-Schutz (Commit `0f9426b`)
- **Block-Freitexteditor:** Über den Edit-Button an jedem Block öffnet sich ein Inline-Editor mit Syntax-Schriftart, Zeilen-Counter und Speichern/Abbrechen.
- **Konflikt-Undo:** Jede Aktion (Ours, Theirs, Both, Manual, Reset) wird auf einem Undo-Stack protokolliert. Über die Toolbar kann schrittweise zurückgerollt werden.
- **Vorschau-Modus:** „Vorschau“-Toggle zeigt das vollständige zusammengesetzte Endergebnis vor dem Stagen an.
- **Marker-Prüfung:** `ConflictParser.hasUnresolvedMarkers()` prüft vor dem Stagen, ob noch rohe `<<<<<<<`, `=======`, `>>>>>>>` im Dateiinhalt vorhanden sind.

### Teil (c) 3-Way Diff3 & Word-Level Diffs (Commit `16abb51`)
- **3-Way-View:** Zeigt den gemeinsamen Vorfahren (`diff3`) an, falls vorhanden, mit Umschalt-Chip.
- **Word-Level Diffs:** `WordDiff.kt` berechnet Wort-Token-Unterschiede zwischen OURS und THEIRS (LCS-Algorithmus). Hervorgehobene Wörter werden mit höherem Kontrast dargestellt.

### Teil (d) Binärdateien, Deleted-vs-Modified & CRLF-Schutz (Commit folgt)
- **Binärdatei-Erkennung:** `BinaryFileDetector` erkennt anhand von Null-Bytes (`\0`) und bekannten Dateiendungen Binärdateien. Statt Text-Editor wird eine spezielle Karte zur Auswahl von „Ours (lokal behalten)“ oder „Theirs (remote übernehmen)“ angezeigt.
- **Deleted-vs-Modified:** Erkennt Konflikte, bei denen eine Seite die Datei gelöscht und die andere modifiziert hat. Ermöglicht direkte Auflösung („Datei behalten“ oder „Löschen übernehmen“).
- **CRLF/LF Preservation:** `ConflictParser.resolve()` erkennt und erhält die ursprünglichen Zeilenumbrüche (`\r\n` vs `\n`).

---

## Getestet
- Unit-Tests in `:feature:git`:
  - `GitConflictNavigationTest`: Multi-File Navigation, Auto-Advance, Undo-History, Custom-Text Editing, Unresolved Marker Checks, CRLF-Preservation, Deleted-vs-Modified.
  - `GitAdvancedTest`: Rebase-Step Parsing (`readRebaseStep`).
- Unit-Tests in `:core:domain`:
  - `WordDiffTest`: Wort-Diffs, Tokenisierung, LCS-Algorithmus.
  - `BinaryFileDetectorTest`: Null-Bytes, Dateiendungen.
  - `ConflictParserTest`: Parsen und Auflösen von 2-Way und 3-Way Konflikten.
- Unit-Tests in `:core:data`:
  - `GitRepositoryImplTest`.
- Build-Verifikation:
  - Vollständiger `./gradlew :app:assembleDebug` Build.
  - Ressourcen- und String-Prüfung (`check_string_locales.py`).

## Nicht getestet
- Manuelle UI-Interaktion mit Finger-Touchgesten auf einem physischen Android-Gerät (nur Unit-Tests im Headless-Runner).
- Rebase von Repositories mit mehr als 50 gleichzeitigen Konfliktdateien (Skalierung des Undo-Stacks im RAM).

## Offene Punkte
- Keine. Alle Anforderungen aus Task 39 (Punkte 1 bis 7) sind vollständig implementiert und grün getestet.
