# Task 54 – Absturz im Editor: `ScrollableTabRow` ohne Tabs (`IndexOutOfBoundsException: Index 0 out of bounds for length 0`)

**Ziel:** Der Editor stürzt nicht mehr ab, wenn keine Datei geöffnet ist (letzten Tab geschlossen, Editor ohne Datei geöffnet, Projektwechsel). Ursache beheben, Regressionstest, ähnliche Stellen durchsehen.

**Befund (Absturzbericht vom 2026-10-25, 23:10, Thread `main`):**
```
java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
  at java.util.ArrayList.get
  at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(TabRow.kt:503)
  … ScrollableTabRowWithSubcomposeImpl … ScaffoldKt$ScaffoldLayout
```
Das Standard-Indikator-Lambda von `ScrollableTabRow` liest `tabPositions[selectedTabIndex]`. Ist die Tab-Liste leer, gibt es keine Position, und der Zugriff auf Index 0 (oder -1) stürzt ab.

**Wahrscheinliche Stelle (selbst prüfen, nicht blind übernehmen):**
* `feature/editor/.../EditorScreen.kt` (ca. Zeile 215): `ScrollableTabRow(selectedTabIndex = uiState.activeFileIndex)` wird immer gezeichnet, auch bei `uiState.openFiles.isEmpty()`.
* `EditorViewModel.closeTab` setzt bei leerer Liste `activeFileIndex = 0`; `EditorUiState.activeFile` nutzt bereits `getOrNull` und ist sicher.
* Verwandt: `EditorViewModel` Zeile ca. 1296 (`updated[s.activeFileIndex]`) ist durch `isEmpty()`-Prüfung geschützt, aber nicht gegen einen Index außerhalb der Liste.

**Gemeinsame Regeln:** wie Task 46 (Absturz-Fix mit Regressionstest). Ursache zuerst **reproduzieren** (Test oder Compose-Test, der vorher rot ist), dann beheben. Nichts erfinden; was nicht ausgeführt wurde, steht im Bericht unter **nicht getestet**. Material Design 3, UI-States unveränderlich, keine Geschäftslogik im Composable.

## 54.1 Reproduktion
1. Compose-UI-Test (oder, falls nicht möglich, Test des Zustands plus Begründung): Editor mit `openFiles = emptyList()` rendern → vor dem Fix `IndexOutOfBoundsException`.
2. Zusätzlich: letzten Tab über `EditorUiEvent` schließen → erwartet kein Absturz.

## 54.2 Fix
1. Im `EditorScreen` die `ScrollableTabRow` nur zeichnen, wenn `uiState.openFiles.isNotEmpty()`. Bei leerer Liste ein ruhiger Leerzustand (Text „Keine Datei geöffnet“ und Hinweis, wie man eine Datei öffnet; Texte als String-Ressourcen, `de` und Standard).
2. `selectedTabIndex = uiState.activeFileIndex.coerceIn(0, uiState.openFiles.lastIndex)` (nur innerhalb des `isNotEmpty()`-Zweigs).
3. `EditorViewModel`: Der Zustand `activeFileIndex` bleibt bei leerer Liste 0; ein `require`/Test stellt sicher, dass `activeFileIndex` nie größer als `openFiles.lastIndex` ist, wenn Dateien offen sind (`closeTab`, `openFile`, Wiederherstellung der Sitzung).
4. Die Toolbar und die Editorfläche dürfen bei leerer Liste nicht auf `activeFile!!` zugreifen; prüfen und absichern.

## 54.3 Ähnliche Stellen
Suche alle Verwendungen (`grep -rn "TabRow\|selectedTabIndex" --include=*.kt`): `GitPanel.kt` und `ApkAnalyzerScreen.kt` nutzen feste Enum-Tabs und sind unkritisch; bestätige das im Bericht. Prüfe außerdem `pager`/`HorizontalPager`-Stellen mit `pageCount = list.size`.

## 54.4 Tests
* Regressionstest aus 54.1 (vorher rot, nachher grün).
* Unit-Test `EditorViewModel`: Tab schließen bis leer, danach Datei öffnen, danach wieder schließen; `activeFileIndex` bleibt gültig.
* `./gradlew :feature:editor:testDebugUnitTest` und die Compose-Tests (falls vorhanden) grün.

## 54.5 Bericht
`docs/editor-tabrow-crash-report.md` mit Ursache, Beleg (Stacktrace-Zeile), Fix, **getestet / nicht getestet** (Gerätetest auf dem Pixel 9 Pro XL ist offen, bis der Inhaber ihn bestätigt). `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash).

## Akzeptanz
* Editor ohne offene Datei zeigt den Leerzustand und stürzt nicht ab; letzten Tab schließen führt nicht zum Absturz.
* Regressionstest war vor dem Fix rot.
* Keine weitere `TabRow`-/Pager-Stelle kann bei leerer Liste abstürzen (belegt im Bericht).

## Commit-Vorschlag
```
fix(editor): crash when no file is open (ScrollableTabRow with empty tab list)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
