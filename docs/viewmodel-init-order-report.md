# ViewModel-Initialisierungsreihenfolge-Report (Task 46)

**Datum:** 2026-10-21  
**Status:** Implementiert & verifiziert  
**Referenz-Task:** `agy-tasks/46-viewmodel-init-order-crash.md`

---

## 1. Problemursache & Befund

### 1.1 Stacktrace auf dem Gerät (Pixel 9 Pro XL)
Beim Öffnen des Editors stürzte die App mit einem harten Fatal Exception auf dem Main-Thread ab:
```
FATAL EXCEPTION: main
java.lang.NullPointerException: Attempt to invoke interface method
  'java.lang.Object kotlinx.coroutines.flow.Flow.collect(FlowCollector, Continuation)' on a null object reference
  at kotlinx.coroutines.flow.FlowKt__TransformKt$filterNotNull$$inlined$unsafeTransform$1.collect(SafeCollector.common.kt:109)
  at kotlinx.coroutines.flow.internal.ChannelFlowTransformLatest$flowCollect$3.invokeSuspend(Merge.kt:23)
  ...
  at com.codeforge.feature.editor.EditorViewModel.observeUpdateAnnotations(EditorViewModel.kt:212)
  at com.codeforge.feature.editor.EditorViewModel.<init>(EditorViewModel.kt:108)
  at com.codeforge.feature.editor.EditorScreenKt.EditorRoute(EditorScreen.kt:654)
```

### 1.2 Ursache im Code
1. `EditorViewModel.init` rief u. a. `observeUpdateAnnotations()` auf (Zeile 108).
2. `observeUpdateAnnotations()` startete eine Coroutine mit `viewModelScope.launch { projectRoot.filterNotNull() ... }`.
3. Auf Android läuft die Initialisierung des ViewModels im Main-Thread (über Hilt / Compose Navigation). `viewModelScope` verwendet standardmäßig `Dispatchers.Main.immediate`.
4. Bei `Dispatchers.Main.immediate` wird der Coroutinen-Block **sofort synchron** auf dem aktuellen Thread ausgeführt, anstatt in die Message-Queue eingereiht zu werden.
5. In Kotlin werden Klassenfelder und `init`-Blöcke streng in **Quelltext-Reihenfolge (top-to-bottom)** initialisiert:
   - Die Eigenschaften vor `init` (Zeilen 79–95) wurden initialisiert.
   - Der `init`-Block (Zeilen 97–111) wurde ausgeführt.
   - Die Eigenschaften `projectRoot` (Zeile 133) und `pendingUpdates` (Zeile 136) standen **unterhalb** des `init`-Blocks.
6. Zum Zeitpunkt, an dem `observeUpdateAnnotations()` synchron ausgeführt wurde, war das Bytecode-Feld `this.projectRoot` noch `null`. Der Aufruf von `.filterNotNull()` auf einer `null`-Referenz führte unmittelbar zur `NullPointerException`.

### 1.3 Warum die existierenden Tests den Fehler nicht gefunden hatten
In den bestehenden Unit-Tests (`EditorViewModelLspTest` und `EditorViewModelFormatTest`) wurde der Dispatcher mit:
```kotlin
val testDispatcher = StandardTestDispatcher()
Dispatchers.setMain(testDispatcher)
```
konfiguriert.
- `StandardTestDispatcher` führt Coroutinen bei `launch` **nicht sofort** aus, sondern reiht sie in die Scheduler-Warteschlange ein.
- Die Coroutinen liefen erst, wenn im Test explizit `advanceUntilIdle()` aufgerufen wurde.
- Zu diesem Zeitpunkt war der Konstruktor `EditorViewModel.<init>` längst vollständig beendet, und `projectRoot` war initialisiert.
- Dadurch wurde das synchrone Ausführungsverhalten von `Dispatchers.Main.immediate` in den bisherigen Tests maskiert.

---

## 2. Der Fix

In [`EditorViewModel.kt`](file:///data/data/com.termux/files/home/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt):
1. `projectRoot` und `pendingUpdates` wurden **vor** den `init`-Block verschoben.
2. Ein ausführlicher Dokumentationskommentar wurde eingefügt, der auf die Initialisierungsreihenfolge in Kotlin und das Verhalten von `Dispatchers.Main.immediate` hinweist.
3. Die doppelten Deklarationen unterhalb von `observeLspSessions()` wurden entfernt.
4. Alle anderen in `init` aufgerufenen Methoden (`setupOpenDocumentsProvider`, `observeSettings`, `observeDiagnostics`, `observeLspSession`, `observeLspSessions`, `observeOpenFileRequests`, `observeJumpRequests`, `observeFlushRequests`, `observeExternalChanges`, `observeDirectoryChanges`, `observeServerCapabilities`, `setupApplyEditHandler`) wurden geprüft: Alle von ihnen referenzierten Properties (`lspSessionController`, `settingsRepository`, `lspClient`, `_uiState`, `currentAppSettings`, `openFileRequestBridge`, `fileSyncBridge`) sind vor dem `init`-Block deklariert.

---

## 3. Durchsicht aller weiteren Klassen (Audit 46.3)

Mit dem Prüfskript `scripts/audit_init_order.py` wurden alle `*ViewModel.kt`, `*Controller.kt`, `*Manager.kt`, `*Repository*Impl.kt` sowie `@Singleton`-Klassen des Projekts untersucht:

| Klasse | Zeile `init` | Betroffene Eigenschaft | Befund |
|---|---|---|---|
| `EditorViewModel` | 97 | `projectRoot`, `pendingUpdates` | **Behoben** (vor `init` verschoben) |
| `SigningRepositoryImpl` | 52 | `_activeBuildSigning`, `processLauncher` | **Ok** (im `init` wird nur `keystoresDir.mkdirs()` aufgerufen, kein Coroutinen-Launch, keine Property-Zugriffe) |
| `CircularLineBuffer` | 16 | `lock`, `buffer`, `head`, `count` | **Ok** (im `init` wird nur `require(maxLines > 0)` aufgerufen, keine nachfolgenden Properties gelesen) |
| `GitAdvancedTest` | 30 | `repo` | **Ok** (Testklasse, `init` ruft nur Basis-Setup auf) |
| `GitRepositoryImplTest` | 24 | `settings`, `repo` | **Ok** (Testklasse) |
| `GitSettingsRepositoryTest` | 18 | `store`, `box` | **Ok** (Testklasse) |
| `GitPatchTest` | 12 | `old`, `diff`, `conflicted` | **Ok** (Testklasse) |

Alle übrigen ViewModels, Controller, Manager und Repository-Implementierungen deklarieren entweder keine Properties nach `init` oder besitzen keinen `init`-Block.

---

## 4. Test-Status

### Getestet
- [x] **Regressionstest `EditorViewModelInitTest` (`:feature:editor`):**
  - Verwendet `UnconfinedTestDispatcher()`, um die synchrone Ausführung von `Dispatchers.Main.immediate` während der Konstruktion nachzubilden.
  - **Vor dem Fix:** Test schlug fehl mit:
    ```
    java.lang.NullPointerException: Cannot invoke "kotlinx.coroutines.flow.Flow.collect(kotlinx.coroutines.flow.FlowCollector, kotlin.coroutines.Continuation)" because "this.$this_unsafeTransform$inlined" is null
        at kotlinx.coroutines.flow.FlowKt__TransformKt$filterNotNull$$inlined$unsafeTransform$1.collect(SafeCollector.common.kt:109)
        at com.codeforge.feature.editor.EditorViewModel.observeUpdateAnnotations(EditorViewModel.kt:212)
        at com.codeforge.feature.editor.EditorViewModel.<init>(EditorViewModel.kt:108)
    ```
  - **Nach dem Fix:** Konstruktion läuft ohne Fehler durch; anschließendes Öffnen einer Datei und Setzen des `projectRoot` führt erfolgreich zur Aktualisierung von `pendingUpdateCount = 1`.
- [x] **Vollständige Modultests:** Alle 45 Tests in `:feature:editor:testDebugUnitTest` erfolgreich (`BUILD SUCCESSFUL`).
- [x] **Anpassung in `EditorViewModelLspTest`:** Überprüfung von `state.codeActions` angepasst, da Task 42 („Zeichenkette in Strings extrahieren“) als QuickFix für `.kt`-Dateien hinzugekommen war.

### Nicht getestet (Checkliste für Pixel 9 Pro XL)
- [ ] Frische Installation oder App-Daten behalten: Projekt aus einer Vorlage anlegen → Editor öffnet ohne Absturz.
- [ ] Datei im Editor öffnen, tippen, Tab wechseln.
- [ ] Projekt aus dem Dateibaum öffnen (nicht über Vorlage), mehrere Dateien/Tabs öffnen.

---

## 5. Offene Punkte
- Keine. Der Fix ist minimal-invasiv, ändert keine Laufzeitlogik abseits der Initialisierungsreihenfolge und ist durch automatisierte Tests abgesichert.
