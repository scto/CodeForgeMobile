# Task 46 – Absturz beim Öffnen des Editors: Initialisierungsreihenfolge im `EditorViewModel` (und gleiche Fehler anderswo)

**Ziel:** Das Öffnen des Editors (u. a. nach dem Anlegen eines Projekts aus einer Vorlage) stürzt nicht mehr ab. Die Ursache wird behoben, durch einen Test abgesichert, und alle übrigen ViewModels und `@Singleton`-Klassen werden auf dasselbe Muster geprüft.
**Warum jetzt:** Gerät des Inhabers (Pixel 9 Pro XL, 2026-10-21 17:28): harter Absturz (`FATAL EXCEPTION: main`) in `EditorViewModel.<init>`. Das Projekt aus der Vorlage ist dadurch nicht nutzbar.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos; UI-States `@Immutable`, `StateFlow`, Material 3, `modifier: Modifier = Modifier` als erster optionaler Parameter, keine Logik in Composables. Gradle Kotlin DSL, Version Catalogs.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) bleibt grün.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät: Pixel 9 Pro XL, Android 17). Aussagen dieses Tasks über den Bestand stammen aus dem Stacktrace des Inhabers und einer Durchsicht eines Repo-Snapshots; prüfe sie vor dem Start selbst und korrigiere sie im Bericht (die Zeilennummern des Inhabers, 108 und 212, weichen vom Snapshot, 104 und 188, ab).
* Geheimnisse nie ins Log, nie in Dateien im Projektordner, nie in Berichte. Keine Pushes auf GitHub ohne Auftrag des Inhabers.
* Bericht in `docs/viewmodel-init-order-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash eintragen).
* Build grün: `./gradlew :app:assembleDebug :feature:editor:testDebugUnitTest`.

## 46.1 Befund (erst selbst prüfen)

Stacktrace vom Gerät (`logcat`, Prozess 4930, 17:28:01; gekürzt):

```
FATAL EXCEPTION: main
java.lang.NullPointerException: Attempt to invoke interface method
  'java.lang.Object kotlinx.coroutines.flow.Flow.collect(FlowCollector, Continuation)' on a null object reference
  at kotlinx.coroutines.flow.FlowKt__TransformKt$filterNotNull$$inlined$unsafeTransform$1.collect(SafeCollector.common.kt:109)
  at kotlinx.coroutines.flow.internal.ChannelFlowTransformLatest$flowCollect$3.invokeSuspend(Merge.kt:23)
  …
  at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch$default(Builders.common.kt:43)
  at com.codeforge.feature.editor.EditorViewModel.observeUpdateAnnotations(EditorViewModel.kt:212)
  at com.codeforge.feature.editor.EditorViewModel.<init>(EditorViewModel.kt:108)
  at com.codeforge.app.DaggerCodeForgeApplication_HiltComponents_SingletonC$ViewModelCImpl$SwitchingProvider.get(…)
  …
  at com.codeforge.feature.editor.EditorScreenKt.EditorRoute(EditorScreen.kt:654)
```

Ursache laut Durchsicht (Snapshot, `feature/editor/.../EditorViewModel.kt`):
* Der `init`-Block (Zeilen ~95–107) ruft u. a. `observeUpdateAnnotations()` auf. Das ist `viewModelScope.launch { projectRoot.filterNotNull()… }`.
* `viewModelScope` nutzt `Dispatchers.Main.immediate`; der Rumpf läuft deshalb **sofort im Konstruktor**, solange die Hauptschleife der aufrufende Thread ist.
* `private val projectRoot = MutableStateFlow<String?>(null)` und `private var pendingUpdates` stehen aber **unterhalb** des `init`-Blocks (Zeilen ~109 und ~112). Kotlin initialisiert Eigenschaften und `init`-Blöcke in Quelltext-Reihenfolge, `projectRoot` ist beim Zugriff also noch `null`. Der Compiler meldet das nicht, weil der Zugriff in einem Lambda steht.

Prüfe außerdem, **warum die Tests das nicht gefunden haben** (vermutlich erzeugen sie das ViewModel mit einem Dispatcher, der `launch` erst später ausführt, oder gar nicht). Das gehört in den Bericht.

## 46.2 Fix

1. In `EditorViewModel`: `projectRoot` und `pendingUpdates` **vor** den `init`-Block verschieben, mit Kommentar, der den Grund nennt (Initialisierungsreihenfolge, `Main.immediate`). Fertiges Patch vom Inhaber: `editor-viewmodel-init-order.patch` (gegen den Snapshot geprüft, nicht gegen dein Repo).
2. Zusätzlich prüfen: Gibt es weitere Eigenschaften unterhalb von `init` (auch geerbte, Delegates, `lazy`, `by`, `lateinit`), die von den `observe*()`-Funktionen im Konstruktor gelesen werden? In der Durchsicht stehen nach dem Block nur `projectRoot` und `pendingUpdates`; verifiziere das im Bericht für **alle** Funktionen, die im `init` aufgerufen werden, einschließlich der Aufrufketten (`observeSettings`, `observeDiagnostics`, `observeLspSession`, …, `setupApplyEditHandler`).
3. Kein neues Verhalten einführen: kein `Dispatchers.Main` statt `.immediate`, keine Verzögerung der Observer.

## 46.3 Gleiches Muster im restlichen Code (Durchsicht)

Suche in allen `*ViewModel.kt`, `*Controller.kt`, `*Manager.kt`, `*Repository*Impl.kt` und `@Singleton`-Klassen nach `init { … }`-Blöcken (und Konstruktoraufrufen), die `viewModelScope.launch`, `scope.launch`, `CoroutineScope(…).launch` oder `flow.stateIn/shareIn` verwenden, **und** Eigenschaften, die **unterhalb** dieses Blocks deklariert sind und darin (auch indirekt) gelesen werden.
* Ergebnis als Tabelle im Bericht: Klasse, Zeile des `init`, betroffene Eigenschaft, Befund (ok / behoben / Risiko).
* Funde im selben Task beheben, wenn der Fix eine reine Verschiebung ist; sonst als offener Punkt im Bericht.
* Optional: Detekt- oder Lint-Regel gegen „Eigenschaft nach `init` verwendet“ nur dann einbauen, wenn das Repo bereits Detekt/Lint-Regeln pflegt; sonst nicht neu einführen.

## 46.4 Tests (Pflicht, JVM)

* **Regressionstest `EditorViewModelInitTest`** (`:feature:editor`, `src/test`): Das ViewModel wird mit `Dispatchers.setMain(Dispatchers.Unconfined)` bzw. `UnconfinedTestDispatcher` erzeugt (so läuft `launch` sofort im Konstruktor), mit Fakes für die Repositories. Erwartung: Konstruktion wirft nicht; danach `projectRoot` setzen (z. B. über das Ereignis, das `projectRoot.value = event.rootPath` auslöst) und prüfen, dass `updateAnnotations`/`pendingUpdateCount` ankommen. Vor dem Fix muss der Test **fehlschlagen** (belegen, z. B. im Bericht mit der Fehlermeldung des ersten Laufs), danach grün sein.
* Existierende Tests des Moduls bleiben grün; bei Änderungen an Test-Fakes begründen.
* Für jeden weiteren Fund aus 46.3 ein kleiner Test nach demselben Muster, sofern die Klasse sich ohne Android-Abhängigkeiten erzeugen lässt; sonst im Bericht begründen.

## 46.5 Gerätetest (nicht vom Agenten, Checkliste für den Inhaber)

* Frische Installation oder App-Daten behalten: Projekt aus einer Vorlage anlegen → Editor öffnet → Datei öffnen, tippen, Tab wechseln.
* Projekt aus dem Dateibaum öffnen (nicht über Vorlage), mehrere Dateien/Tabs.
* Falls es erneut abstürzt: `logcat -d | grep -E " <PID> " | grep AndroidRuntime | head -40` und die Ausgabe in den Bericht legen.

## Abnahme
* Fix im `EditorViewModel`, Regressionstest fehlschlagend vorher und grün nachher, Tabelle aus 46.3 im Bericht, Build grün.
* Der Bericht nennt, warum die Tests den Fehler vorher nicht fanden.
