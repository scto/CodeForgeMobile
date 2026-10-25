# Editor-ScrollableTabRow-Crash-Report (Task 54)

**Datum:** 2026-10-25  
**Status:** Implementiert & verifiziert  
**Referenz-Task:** `agy-tasks/54-editor-tabrow-empty-crash.md`

---

## 1. Problemursache & Befund

### 1.1 Stacktrace auf dem Gerät (2026-10-25, 23:10, Thread `main`)
Beim Öffnen des Editors ohne offene Datei (z. B. nach Erstellen eines Projekts aus einer Vorlage oder nach Schließen aller Tabs) stürzte die App reproduzierbar ab:
```
FATAL EXCEPTION: main
java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
  at java.util.ArrayList.get(ArrayList.java:433)
  at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(TabRow.kt:503)
  at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(TabRow.kt:500)
  at androidx.compose.material3.TabRowDefaults.tabIndicatorOffset(TabRow.kt:660)
  ...
  at com.codeforge.feature.editor.EditorScreenKt.EditorScreen(EditorScreen.kt:215)
  at com.codeforge.feature.editor.EditorScreenKt.EditorRoute(EditorScreen.kt:176)
  at com.codeforge.app.EditorWithPreviewHostKt.EditorWithPreviewHost(EditorWithPreviewHost.kt:62)
  at com.codeforge.app.ProjectWorkspaceRouteKt.ProjectWorkspaceRoute(ProjectWorkspaceRoute.kt:176)
```

### 1.2 Ursache im Code
1. In `feature/editor/.../EditorScreen.kt` (Zeile 215) wurde `ScrollableTabRow(selectedTabIndex = uiState.activeFileIndex)` immer gerendert, selbst wenn `uiState.openFiles.isEmpty()`.
2. Das Material 3 Standard-Indikator-Lambda von `ScrollableTabRow` liest `tabPositions[selectedTabIndex]` (`TabRow.kt:503`).
3. Bei einer leeren Tab-Liste erzeugt `ScrollableTabRow` keine `TabPosition`-Elemente (`tabPositions.isEmpty()`). Der Zugriff auf `tabPositions[0]` (bzw. jeden Index) wirft unmittelbar eine `IndexOutOfBoundsException: Index 0 out of bounds for length 0`.
4. Weiterhin bestand in `EditorViewModel.kt` (Zeile 1296) die Gefahr, dass `updated[s.activeFileIndex]` bei einer Inkonsistenz ungeprüft indiziert wurde, und `closeTab(index)` prüfte vor dem Entfernen nicht auf `index in s.openFiles.indices`.

---

## 2. Der Fix

### 2.1 UI & Compose-Ebene (`EditorScreen.kt`)
1. **Bedingtes Rendern:** `ScrollableTabRow` wird nur gerendert, wenn `uiState.openFiles.isNotEmpty()`.
2. **Indexbegrenzung:** `selectedTabIndex` wird im TabRow-Aufruf zusätzlich strikt begrenzt:
   ```kotlin
   if (uiState.openFiles.isNotEmpty()) {
       val safeTabIndex = uiState.activeFileIndex.coerceIn(0, uiState.openFiles.lastIndex)
       ScrollableTabRow(selectedTabIndex = safeTabIndex) { ... }
   }
   ```
3. **Ruhiger Leerzustand (`EditorEmptyState`):** Wenn `uiState.openFiles.isEmpty()`, wird statt einer leeren Fläche ein zentrierter Leerzustand gerendert (Material 3 `Description`-Icon mit 50 % Opazität, Titel *„Keine Datei geöffnet“* und Hilfetext *„Wähle eine Datei im Dateibaum links aus, um sie zu bearbeiten.“*).
4. **String-Ressourcen:** `editor_keine_datei_geoeffnet` und `editor_hinweis_datei_oeffnen` in `core/resources` (`values/strings.xml` und `values-de/strings.xml`) angelegt.
5. **Absicherung Toolbar & Fläche:** Keine `activeFile!!`-Zugriffe; `EditorToolbar` und Dialoge nutzen sichere Nullability-Prüfungen (`uiState.activeFile?.path`).

### 2.2 ViewModel-Ebene (`EditorViewModel.kt`)
1. **`closeTab`:**
   - Vorherige Indizierung abgesichert mit `if (index !in s.openFiles.indices) return@update s`.
   - Bei leerer Liste verbleibt `activeFileIndex = 0`.
   - Bei nicht-leerer Liste wird `activeFileIndex` auf `(updated.size - 1)` begrenzt.
2. **`updateBuffer`:**
   - Indizierung auf `updated` nutzt `val safeIndex = s.activeFileIndex.coerceIn(0, updated.lastIndex)`.

---

## 3. Durchsicht aller weiteren Stellen im Projekt (Audit 54.3)

Alle Verwendungen von `TabRow`, `ScrollableTabRow` und `Pager` im Projekt wurden geprüft:

| Datei | Komponente | Prüfung / Befund |
|---|---|---|
| `feature/editor/.../EditorScreen.kt` | `ScrollableTabRow` | **Behoben:** Dynamische Liste, nun durch `isNotEmpty()` und Leerzustand geschützt |
| `feature/git/.../GitPanel.kt` | `ScrollableTabRow` | **Sicher:** Feste Enum-Tabs (`GitPanelTab.values()`, Länge 4), nie leer |
| `feature/build/.../ApkAnalyzerScreen.kt` | `ScrollableTabRow` | **Sicher:** Feste Enum-Tabs (`ApkAnalyzerTab.values()`, Länge 5), nie leer |
| `feature/build/.../BuildScreen.kt` | `TabRow` | **Sicher:** Feste Enum-Tabs (`BuildTab.entries`, Länge 2), nie leer |
| `feature/signing/.../SigningScreen.kt` | `TabRow` | **Sicher:** Feste Enum-Tabs (`SigningTab.values()`, Länge 2), nie leer |
| `feature/debug/.../DebugScreen.kt` | `TabRow` | **Sicher:** Feste Tab-Liste (`tabs = listOf(...)`, Länge 4), nie leer |
| `app/.../EditorWithPreviewHost.kt` | `TabRow` | **Sicher:** Feste 2 Tabs (Editor / Vorschau), nie leer |
| `feature/layoutdesigner/.../LayoutDesignerScreen.kt` | `TabRow` | **Sicher:** Feste 3 Tabs (`DESIGN`, `SPLIT`, `CODE`), nie leer |
| `feature/projectsettings/.../ProjectSettingsScreen.kt` | `TabRow` | **Sicher:** Feste 2 Tabs (`RELEASE`, `DEBUG`), nie leer |
| `feature/onboarding/.../IntroPagerScreen.kt` | `HorizontalPager` | **Sicher:** Feste statische Liste von 4 Onboarding-Slides, nie leer |

---

## 4. Test-Status

### Getestet
1. **Root-Cause-Reproduktion (`EditorTabRowCrashRegressionTest.kt`):**
   - Test belegt, dass der ungeprüfte Zugriff auf `tabPositions[selectedTabIndex]` bei leerer Liste `IndexOutOfBoundsException` wirft.
   - Test belegt, dass die `isNotEmpty()`-Absicherung mit `coerceIn` den Fehler vollständig verhindert.
2. **ViewModel-Zustandstests (`EditorTabRowCrashRegressionTest.kt`):**
   - Initialer Zustand: `openFiles.isEmpty()`, `activeFileIndex == 0`, `activeFile == null`.
   - Schließen von Tabs bei leerer Liste wirft keinen Fehler.
   - Datei öffnen, letzten Tab schließen: Zustand wird sauber geleert, `activeFileIndex` bleibt 0.
   - Mehrere Dateien öffnen, Tabs schrittweise bis zum Leerzustand schließen, neue Datei öffnen und wieder schließen: Bei jeder Transition bleibt `activeFileIndex` gültig (`0` bei leer, `in 0..lastIndex` bei vorhandenen Dateien).
   - Ungültige Indizes (`-1`, `99`) bei `CloseTab` und `SelectTab` führen zu keinem Fehler oder inkonsistenten Zustand.
   - `TextChanged` bei leerer Dateiliste führt zu keinem Fehler.
3. **Gradle-Build & Testlauf:**
   - `./gradlew :feature:editor:testDebugUnitTest` erfolgreich (100 % bestanden).

### Nicht getestet
- Manueller Gerätetest auf dem Google Pixel 9 Pro XL des Nutzers (steht offen, bis der Nutzer die aktualisierte Debug-APK auf dem Gerät ausprobiert).
