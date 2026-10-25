# Task 52 – Modul `x2c`: XML-Layouts nach Jetpack Compose migrieren (mit Rückfrage, Backup, Rückgängig)

**Ziel:** Enthält ein importiertes oder geklontes Android-Projekt View-Layouts in XML (`res/layout*/`), bietet die IDE nach Rückfrage die Umstellung auf Jetpack Compose an. Der Dialog zeigt die gefundenen Layouts mit Einstufung (automatisch / mit Hinweisen / nicht umwandelbar) und Schalter für die Optionen. Bei „Umwandeln“ wird zuerst ein ZIP-Backup des Originals unter `Backup/` im Projektordner angelegt, dann werden aus den gewählten Layouts `@Composable`-Funktionen erzeugt, die Build-Dateien für Compose vorbereitet und das Ergebnis per Gradle geprüft. Optional werden einfache Activities/Fragments auf `setContent { … }` umgestellt. Jeder Schritt ist rückgängig zu machen.
**Warum jetzt:** Auftrag des Inhabers (2026-10-25). Baut auf der Infrastruktur von Task 50 (`j2k`: Erkennung, Backup, Journal, Build-Prüfung, Dialogmuster) auf.
**Ehrlichkeit gegenüber dem Benutzer:** XML → Compose ist **keine mechanische Eins-zu-eins-Übersetzung**. Das Layout (Struktur, Abstände, Ressourcen) lässt sich weitgehend übertragen. **Verhalten** (Klick-Listener, Adapter, Datenbindung, Animationen, Custom Views) steckt im Java-/Kotlin-Code und wird nicht automatisch umgeschrieben. Weder Task noch UI behaupten „gleichwertig“ oder „fertig migriert“; die UI sagt „Layout automatisch übertragen, Verhalten bitte prüfen“ und listet, was noch von Hand zu tun ist.

**Gemeinsame Regeln:** wie Task 50 (Clean Architecture, `@Immutable`-States, MVI, Material 3, `modifier: Modifier = Modifier` als erster optionaler Parameter, keine Logik in Composables; Gradle Kotlin DSL; UI-Texte zweisprachig über `:core:resources`, String-Check Task 17 grün). Nichts erfinden: Was nicht selbst ausgeführt wurde, steht im Bericht unter **nicht getestet** (Gerätetests: Pixel 9 Pro XL, Android 17, durch den Inhaber). Aussagen dieses Tasks über den Bestand stammen aus einer Durchsicht eines Repo-Snapshots (`main` `4f51813`); prüfe sie vor dem Start und korrigiere sie im Bericht. Aussagen über Bibliotheken und Versionen (Compose-BOM, Compose-Compiler, Mindest-`minSdk`, Kotlin-Kompatibilität) belegst du mit Quelle und Datum aus der offiziellen Dokumentation, nicht aus dem Gedächtnis. Geheimnisse nie ins Log, in Berichte oder ins Backup-ZIP (soweit nicht ohnehin Projektinhalt). Keine Pushes auf GitHub ohne Auftrag des Inhabers.
**Projekte aus `git clone` sind nicht vertrauenswürdig:** Die Umwandlung selbst führt keinen Projektcode aus (nur Dateien lesen und schreiben). Nur die Build-Prüfung (52.10) startet Gradle und damit die Build-Skripte des Projekts; sie ist ein eigener, ausdrücklich bestätigter Schritt.
Bericht in `docs/x2c-report.md` (getestet / nicht getestet / offene Punkte); `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash). Build grün: `./gradlew :app:assembleDebug` und die neuen Modultests.

**Reihenfolge:** 52.0 (Entscheidungen, Spike) zuerst und einzeln committen. Danach 52.1 → 52.3 → 52.4 → 52.5 → 52.6 → 52.7 und 52.8 (parallel möglich) → 52.9 → 52.10 → 52.2 und 52.11 (UI) → 52.12.

---

## 52.0 Entscheidungen und Spike

1. **Modulaufteilung** (Vorschlag, wie Task 50): `:libs:x2c` (reines Kotlin/JVM ohne Android: Layout-Parser, Analyse, Generator, Themen-Erzeugung, Berichte) und `:feature:x2c` (Dialog, ViewModel, Fortschritt, Ergebnis). Schnittstellen und Modelle in `:core:domain`. Dokumentiere die Entscheidung.
2. **Gemeinsame Infrastruktur mit Task 50 prüfen (nicht doppelt bauen):** In `libs/j2k` liegen `ProjectBackupRepository`, Journal/Rückgängig, `J2kBuildVerifier`-Anbindung (Task 28) und Teile des Scanners (Schnittstellen liegen im Paket `com.codeforge.core.domain.repository`, die Dateien in `libs/j2k/…/domain/`: prüfe, wo sie wirklich stehen). Empfehlung: gemeinsamen Teil in ein neutrales Modul auslagern (z. B. `:libs:migration-core`), `:libs:j2k` und `:libs:x2c` hängen daran; die Tests von Task 50 bleiben grün. Ist das zu groß, ist die Alternative „x2c hängt an `:libs:j2k`“ zu begründen. Entscheide und dokumentiere.
3. **Layout-Parser:** `feature/layoutdesigner/…/xml/LayoutXml.kt` (Task 15/41) parst Layouts, liegt aber in einem Feature-Modul (`:libs` dürfen nicht von `:feature` abhängen). Bewerte: (a) Parser nach `:libs:x2c` (oder eigenes `:libs:layout-xml`) auslagern, sodass der Layout-Designer ihn nutzt, (b) eigener Parser in x2c. Anforderungen: Zeilen-/Spaltenposition je Knoten, Namensräume (`android:`, `app:`, `tools:`), XXE abgelehnt, Kommentare erhalten. Belege die Wahl.
4. **Schnittstelle zu Task 41.11** („Als Compose kopieren/exportieren“ für ein einzelnes Layout): Der Generator aus 52.6 ist **derselbe**. Ist Task 41 noch offen, baut 52 den Generator, 41.11 ruft ihn auf. Ist 41.11 schon umgesetzt, wird dessen Generator nach `:libs:x2c` verschoben und erweitert (Tests bleiben grün). Vermerke es in beiden Berichten; ändere die Task-Datei 41 nicht ohne Auftrag.
5. **Abdeckung festlegen (Umfang der ersten Version)**: Stufe 1 = Layout → Composable (Standard, ungefährlich: neue Dateien, Projekt-Code unverändert). Stufe 2 = einfache Activities/Fragments auf `setContent` umstellen (Schalter, 52.7). **Stufe 3 (Verhalten, Adapter, Datenbindung umschreiben) ist nicht Teil dieses Tasks**; sie steht als Hinweisliste im Bericht.
6. **Spike auf dem Gerät** (Inhaber führt aus; du lieferst Skript und Anleitung): ein kleines View-Projekt mit 3 Layouts (LinearLayout, ConstraintLayout, RecyclerView-Item) wird umgewandelt und in Termux gebaut (Muster: Task 27a, 37a, 50.0). Dauer und Ergebnis in den Bericht. Fällt der Spike negativ aus (z. B. Compose-Plugin lässt sich in Termux-Builds nicht auflösen), melde das und stoppe, bevor UI gebaut wird.

## 52.1 Kern und Schnittstellen

* `:core:domain`: `X2cOptions` (`wireActivities`, `generateTheme`, `removeXmlAfterSuccess`, `useVersionCatalog`, `runBuildCheck`, `createGitBranch`, `selectedLayouts`), `X2cPlan` (Layoutliste mit Einstufung, Verwendungen im Code, Build-Befunde), `X2cProgress` (`@Immutable`, Sealed, Phasen), `X2cFileResult` (`GENERATED`, `GENERATED_WITH_NOTES`, `SKIPPED`, `FAILED`), `X2cReport`; Schnittstellen `X2cRepository`, `LayoutToComposeGenerator`, `LayoutUsageAnalyzer`, `ComposeBuildEnabler`. Keine Android-Abhängigkeit in `:core:domain`.
* **Plan** = reine Analyse ohne Schreiben, wiederholbar, Grundlage von Dialog und Bericht.
* Abbruch (`CancellationException`) ist in jeder Phase sicher (Journal, 52.9).

## 52.2 Erkennung, Einstieg und Dialog

1. **Einstiegspunkte:** nach erfolgreichem Import per Dateiauswahl und nach erfolgreichem `git clone`; manuell im Projektmenü („Layouts nach Compose umwandeln …“) und im Dateibaum per Kontextmenü auf einer Layoutdatei (dann ist nur diese vorgewählt). Stelle im Repo selbst finden (wie 50.2) und im Bericht nennen.
2. **Erkennung (`ComposeMigrationScanner`, reines Kotlin, getestet):** Android-Projekt (AGP-Plugin in einer Build-Datei), mindestens eine Datei unter `src/*/res/layout*/*.xml`, ignoriert `build/`, `.gradle/`, `Backup/`. Zählt Layouts und prüft, ob Compose schon aktiv ist (`buildFeatures.compose`, Compose-Abhängigkeit oder Compose-Compiler-Plugin).
3. **Automatische Nachfrage** nur, wenn Layouts vorhanden sind, Compose im Projekt **noch nicht** aktiv ist und „Nicht mehr fragen“ nicht gesetzt ist; sonst nur über das Menü. Ist Compose schon aktiv, zeigt das Menü die Layouts, die noch übrig sind. **Nur ein Dialog zur Zeit:** Fragt Task 50 (Java → Kotlin) gerade, kommt dieser Dialog erst danach (Reihenfolge der Fragen im Bericht festhalten; 52 braucht Task 50 nicht, die erzeugten Composables sind Kotlin-Dateien auch in Java-Projekten).
4. **Dialog (Material 3):**
   * Zusammenfassung („N Layouts in M Modulen, davon A automatisch, B mit Hinweisen, C nicht umwandelbar“), Hinweis, wohin das Backup geht.
   * **Layoutliste** mit Auswahlhaken (Standard: alle außer „nicht umwandelbar“), Suche, Einstufung und Kurzgrund je Eintrag, Sprung in die Datei.
   * Schalter **„Activities/Fragments auf Compose umstellen (nur einfache Fälle)“** (Standard: aus; Zähler der Kandidaten; Untertext: komplexe Fälle bleiben unverändert und stehen im Bericht).
   * Schalter **„Theme aus XML-Farben und -Themes erzeugen“** (Standard: an, wenn ein Material-Theme erkannt wird; erzeugt nur neue Dateien).
   * Schalter **„XML-Layouts nach erfolgreicher Umstellung entfernen“** (Standard: aus; entfernt nur Layouts, die nach der Umstellung nirgends mehr verwendet werden, siehe 52.9).
   * Schalter **„Versionskatalog verwenden“** (Standard: an, wenn `gradle/libs.versions.toml` existiert, sonst ausgeschaltet; nutzt die Funktionen aus 50.6a).
   * Schalter „Nach dem Umwandeln bauen und prüfen“ (Standard: an, Hinweis: führt Projekt-Skripte aus), optional „Git-Zweig anlegen“ (nur bei Git-Projekt mit sauberem Arbeitsverzeichnis), „.git ins Backup einschließen“.
   * Schaltflächen **Umwandeln**, **Später**, **Nicht mehr fragen**. Compose-Unterstützung im Build wird bei Erzeugen von Composables **immer** ergänzt (52.4); der Dialog sagt das.
5. Gespeichert wird je Projekt nur „Nicht mehr fragen“ (DataStore/Proto wie bei den übrigen Einstellungen; Feldnummern in `settings.proto` nicht wiederverwenden).

## 52.3 Sicherung als ZIP

Wie 50.3, mit eigenem Namen: `<Projekt>/Backup/<projektname>-xml-<JJJJMMTT-HHMMSS>.zip`. Gleiche Regeln (Inhalt ohne `Backup/`, `**/build/`, `.gradle/`, `.cxx/`, `node_modules`; `.git/` standardmäßig dabei; freier Speicher mindestens das Doppelte; ZIP nach dem Schreiben wieder öffnen und CRC/Größe aller Quelldateien prüfen; Zip-Slip-Schutz beim Wiederherstellen). **Schlägt die Prüfung fehl, wird nichts verändert.** Das Backup wird nie automatisch gelöscht. Nutze die gemeinsame Implementierung aus 52.0 Punkt 2, keine Kopie.

## 52.4 Compose im Build ermöglichen (immer, wenn Composables erzeugt werden)

In der vorhandenen Skriptsprache (Groovy bleibt Groovy, Kotlin DSL bleibt Kotlin DSL); ohne Katalog-Schalter bleibt der Stil des Projekts:
* **Voraussetzungen prüfen, sonst klare Meldung statt Teilumbau:** `minSdk` (Compose verlangt eine Mindestversion; Wert aus der offiziellen Dokumentation mit Quelle und Datum belegen), Java-Zielversion, AGP-/Gradle-Version.
* **Kotlin-Plugin** ergänzen, falls nicht vorhanden (wie 50.4; Java-Projekte können Kotlin-Dateien nebenher haben, der Rest bleibt Java).
* `buildFeatures { compose = true }`; **Compose-Compiler** je nach Kotlin-Version: ab Kotlin 2.0 über das Compose-Compiler-Gradle-Plugin (`org.jetbrains.kotlin.plugin.compose`), davor über `composeOptions` mit der passenden Compiler-Erweiterung. Die Zuordnung steht in einer Datendatei `x2c-compose-compat.json` (Kotlin ↔ Compiler-Version, AGP ↔ BOM; Quelle und Datum aus der Dokumentation zum Implementierungszeitpunkt, **keine Werte aus dem Gedächtnis**). Unbekannte Kombination: Dialog fragt nach der Version, nichts raten.
* **Abhängigkeiten:** Compose-BOM, `material3`, `ui`, `ui-tooling-preview` (+ `ui-tooling` als `debugImplementation`), `activity-compose` (nur wenn Stufe 2 aktiv), `constraintlayout-compose` (nur wenn ein ConstraintLayout umgewandelt wird), bei Fragment-Umstellung `lifecycle`-Compose-Teile nur bei Bedarf. Vor dem Eintragen auf Maven Google prüfen, dass Koordinate und Version existieren.
* Bei aktivem Katalog-Schalter: Versionen/Bibliotheken/Plugins über den Katalog (`libs.…`) anlegen, ohne bestehende Einträge oder Kommentare zu verlieren, Konflikte melden (kein stiller Versionswechsel), wie 50.6a.
* **Kein Eingriff in Dateien, die der Lauf nicht braucht**; vorhandene Compose-Konfiguration wird erkannt und nicht doppelt ergänzt. Netzwerkhinweis: Das Auflösen der Abhängigkeiten braucht beim ersten Build Netz.

## 52.5 Analyse: Layouts und ihre Verwendung (Plan)

Für jedes Layout (Standard-Ordner `layout/`; Varianten wie `layout-land/`, `layout-sw600dp/`, `layout-v21/` werden gelistet, **nicht** automatisch zusammengeführt: sie bekommen den Hinweis „Variante, manuell mit `WindowSizeClass`/Konfiguration lösen“):
1. **Einstufung:**
   * *Automatisch*: nur Tags aus der Pflicht-Tabelle (52.6) und einfache Attribute.
   * *Mit Hinweisen*: enthält unbekannte Attribute/Tags (werden als `// TODO(x2c)` mit Quelle übernommen), `include`/`merge`, Styles (`style="…"`), `tools:`-Attribute, Custom Views (→ `AndroidView`-Rahmen).
   * *Nicht umwandelbar*: Data-Binding-Layout (`<layout>` mit `@{…}`), `MotionLayout`, `ViewStub` mit Logik, Layouts für **RemoteViews** (App-Widgets, benutzerdefinierte Benachrichtigungen: dort ist Compose technisch nicht möglich; erkennbar an `appwidget-provider` `initialLayout`, `RemoteViews(` im Code), Layouts, die ein Custom-View-Konstruktor per `inflate` zieht.
2. **Verwendung im Code (`LayoutUsageAnalyzer`, textbasiert auf `.java`/`.kt`, im Bericht als Heuristik gekennzeichnet):** `setContentView(R.layout.x)`, `LayoutInflater.inflate(R.layout.x, …)`, `DataBindingUtil`/`XBinding.inflate`, ViewBinding-Klasse `XBinding`, `findViewById(R.id.y)` und Zugriffe `binding.y`, Fragment-`onCreateView`, Adapter (`onCreateViewHolder`), `<include>`/`<fragment>`/`FragmentContainerView` im XML, Navigation (`navGraph`, `android:name`), Manifest-Verweise.
3. **Ergebnis je Layout:** Liste der Verwender (Datei:Zeile), Liste der **IDs, die der Code anfasst** (werden in 52.6 zu Parametern), Stufe-2-Eignung (52.7), Hinderungsgründe. Die Analyse führt keinen Projektcode aus.

## 52.6 Generator: XML → `@Composable`

Reines Kotlin/JVM, deterministisch, getestet (Golden-Files). Ausgabe je Layout eine Datei `<Name>Screen.kt` (z. B. `activity_main.xml` → `MainScreen.kt`; `item_user.xml` → `UserItem.kt`; Namenskollisionen auflösen und melden) im Paket der Hauptverwender-Klasse bzw. `<applicationId>.ui.compose`; Verzeichnis nach Projektkonvention (`src/main/kotlin`, sonst `src/main/java`). Jede Datei: Imports, `@Composable fun X(modifier: Modifier = Modifier, …)`, `@Preview`-Funktion mit Standardwerten, KDoc mit Quelle (`Generiert aus res/layout/x.xml von CodeForge x2c, bitte prüfen`).

**Pflicht-Abbildungen (jede mit Golden-Test):**
| XML | Compose |
|---|---|
| `LinearLayout` (vertical/horizontal) | `Column` / `Row`, `layout_weight` → `Modifier.weight(…)` (nur direkte Kinder), `gravity` → `verticalArrangement`/`horizontalAlignment` bzw. `Alignment`, `layout_gravity` → `Modifier.align(…)`, `divider`/`showDividers` → Hinweis |
| `FrameLayout` | `Box` (+ `Modifier.align`) |
| `RelativeLayout` | `Box`/`ConstraintLayout`-Näherung, **Hinweis** (Regeln `layout_below` usw. nur teilweise abbildbar; sonst TODO) |
| `ConstraintLayout` | `ConstraintLayout` (`constraintlayout-compose`) mit `createRefs`, `constrainAs`, `linkTo`, Bias, `Dimension.fillToConstraints` (0dp), Verhältnis (`dimensionRatio` → `aspectRatio`), `Guideline` → `createGuidelineFrom…`, `Barrier` → `createBarrier`, Ketten → `createHorizontalChain`/`createVerticalChain`; nicht Abbildbares als TODO |
| `ScrollView` / `NestedScrollView` / `HorizontalScrollView` | `Modifier.verticalScroll(rememberScrollState())` / `horizontalScroll` |
| `TextView` / `MaterialTextView` | `Text` (`text`, `textSize`→`sp`, `textColor`, `textStyle`, `maxLines`, `ellipsize`, `gravity`/`textAlignment`) |
| `Button` / `MaterialButton` (Stile Filled/Outlined/Text) | `Button` / `OutlinedButton` / `TextButton`; `onClick` als Parameter `onClick: () -> Unit` |
| `EditText` / `TextInputLayout`+`TextInputEditText` | `OutlinedTextField`/`TextField` mit `value`/`onValueChange` als Parameter (State-Hochziehen), Hint → `label`/`placeholder`, `inputType` → `KeyboardOptions` |
| `ImageView` / `ImageButton` / `ShapeableImageView` | `Image`/`IconButton` mit `painterResource`, `contentDescription` aus `android:contentDescription`, `scaleType` → `ContentScale` |
| `CheckBox`, `Switch`/`SwitchMaterial`, `RadioButton`/`RadioGroup`, `ProgressBar`, `Slider`/`SeekBar` | `Checkbox`, `Switch`, `RadioButton`-Gruppe, `CircularProgressIndicator`/`LinearProgressIndicator`, `Slider` (mit Zustandsparametern) |
| `CardView` / `MaterialCardView` | `Card`/`ElevatedCard` |
| `View` als Trennlinie / Platzhalter | `HorizontalDivider`/`Spacer` (nur eindeutige Fälle, sonst `Box` + Hinweis) |
| `RecyclerView` | `LazyColumn`/`LazyRow`-Gerüst mit `items(...)` über eine Parameterliste; das Item-Layout wird als eigene Composable erzeugt (`item_*.xml`); **Adapter-/ViewHolder-Logik wird nicht umgeschrieben** (TODO + Bericht) |
| `include` | Aufruf der erzeugten Composable des eingebundenen Layouts; `merge` → Inhalt in den Verwender |
| `ViewStub`, `fragment`/`FragmentContainerView`, `WebView`, `MapView`, Custom Views | `AndroidView(factory = { … })`-Rahmen mit TODO (Interop), niemals still verworfen |

**Querschnittsregeln (je mit Test):**
* **Maße/Einheiten:** `match_parent` → `fillMaxWidth()`/`fillMaxHeight()`/`fillMaxSize()`, `wrap_content` → Standard, feste `dp` → `.width()`/`.height()`/`.size()`, `sp` → `.sp`; `@dimen/x` → `dimensionResource(R.dimen.x)`.
* **Modifier-Reihenfolge korrekt:** äußeres Padding (Margin) → Größe/Hintergrund → inneres Padding; Hintergrund und Klick (`clickable`) in der richtigen Lage. Reihenfolge ist in Compose bedeutungsvoll und im Test festgehalten.
* **Ressourcen:** `@string/x` → `stringResource(R.string.x)`, `@color/x` → `colorResource(R.color.x)`, `@drawable/x` → `painterResource(R.drawable.x)`, `@style/…` → Hinweis (Styles werden nur dann in Parameter aufgelöst, wenn eindeutig; sonst TODO mit Quelle). Hartcodierte Texte bleiben wie im XML, **mit Hinweis auf Task 42/17**, keine stille Auslagerung.
* **Sichtbarkeit:** `gone` → Element weglassen **oder**, wenn der Code `setVisibility` auf diese ID aufruft, Parameter `visible: Boolean` (Standardwert aus XML); `invisible` → Platz behalten (`Modifier.alpha(0f)`) mit Kommentar.
* **State-Hochziehen (Stateless-Muster):** IDs, die der Code verwendet (aus 52.5) werden zu Parametern mit Standardwerten aus dem XML (`text: String`, `onClick: () -> Unit`, `checked: Boolean`, `onCheckedChange`, `visible`, `enabled`). Unberührte Views behalten ihre XML-Werte fest. Die Zuordnung steht im KDoc und im Bericht.
* **Unbekannte Tags/Attribute:** nie still verwerfen; `// TODO(x2c): <tag|attribut> manuell umsetzen (Quelle: <datei>:<zeile>)`. Zahl der TODOs im Bericht.
* **`tools:`-Attribute** (`tools:text`, `tools:listitem` …) fließen nur in die `@Preview`-Daten, nicht in die Laufzeit.
* **Barrierefreiheit:** `contentDescription`, `importantForAccessibility`, `labelFor` → `semantics { … }` soweit abbildbar.
* Ausgabe ist formatiert (Formatter aus Task 32 `ktlint`, falls vorhanden; sonst konsistenter eigener Stil) und kompiliert **ohne Warnungen aus dem Generator** (Imports minimal und vollständig).

## 52.7 Stufe 2 (optional): Activities und Fragments auf Compose umstellen

Nur mit Schalter, nur in **einfachen Fällen** (Kriterien in einer Konstante, im Bericht begründet): Die Activity ruft `setContentView(R.layout.x)` auf und greift danach **nicht** per `findViewById`/Binding auf Views zu (oder nur auf IDs, die in 52.6 zu Parametern wurden und deren Setter-Aufrufe trivial sind). Dann:
* `setContentView(R.layout.x)` → `setContent { <AppTheme> { XScreen() } }` (Theme aus 52.8, sonst `MaterialTheme`); Basisklasse `AppCompatActivity` bleibt oder wird `ComponentActivity` (nur wenn nichts AppCompat-spezifisches genutzt wird; sonst bleibt sie).
* Fragment: `onCreateView` → `ComposeView(requireContext()).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed); setContent { … } }`.
* Alle Kandidaten und **alle nicht umgestellten Verwender mit Grund** erscheinen im Dialog (vorher) und im Bericht (nachher). Verhaltensänderungen (Listener, Adapter) werden **nicht** umgeschrieben, sondern als „bitte prüfen“-Liste ausgegeben (Datei:Zeile).
* Java-Activities: nur mit Hinweis; Compose-`setContent` braucht Kotlin. Standard: Java-Activities werden **nicht** angefasst (Task 50 zuerst), außer der Benutzer bestätigt ausdrücklich im Dialog; im Zweifel überspringen und berichten.

## 52.8 Theme (optional, nur neue Dateien)

Aus `res/values/colors.xml` und dem erkannten Material-Theme (`Theme.MaterialComponents…`/`Theme.Material3…`) eine `ui/theme/Theme.kt` (+ `Color.kt`, `Type.kt` nur bei Bedarf) mit `lightColorScheme`/`darkColorScheme` aus den Primär-/Sekundärfarben; Namen der Quellattribute im KDoc. Was sich nicht eindeutig zuordnen lässt, bleibt auf Material-3-Vorgaben mit TODO. **Kein Eingriff in `themes.xml`** (die Activities bleiben auf ihrem XML-Theme, solange sie nicht umgestellt sind). Existiert bereits ein `ui/theme/Theme.kt`, wird nichts überschrieben (Hinweis).

## 52.9 Anwenden, Journal, Rückgängig, XML entfernen

* Wie 50.7: Arbeitsverzeichnis, dann eine Übernahme in einem Durchgang; **Journal** `Backup/x2c-journal-<ts>.json` mit allen Änderungen (neue Dateien, geänderte Dateien, entfernte Dateien) und Hashes vorher/nachher. Fehler während der Übernahme → automatische Rücknahme anhand des Journals; Abbruch vor der Übernahme ändert nichts.
* **Rückgängig** (Ergebnis und Projektmenü, solange das Journal existiert): neue Dateien löschen, geänderte und entfernte aus dem Backup-ZIP wiederherstellen, **ohne** spätere Änderungen des Benutzers an anderen Dateien anzutasten; war eine Datei danach verändert, fragt die IDE nach (behalten / überschreiben / als `.orig` sichern).
* **XML entfernen (Schalter):** nur Layouts, die nach der Umstellung **nirgends mehr** verwendet werden (keine `R.layout.x`-Referenz, kein `include`, kein Manifest-/Navigationsverweis, kein Binding-Verwender, nicht RemoteViews). Vor dem Entfernen erneute Verwendungsprüfung, Aufnahme ins Journal (Wiederherstellung aus dem ZIP). Ressourcen (`strings`, `colors`, `drawables`), die die Composables brauchen, bleiben immer.
* Bei Git-Projekten mit Schalter: Zweig `x2c/<Datum>` anlegen und das Ergebnis committen (Nachricht ohne Geheimnisse); kein Push.
* Zweiter Lauf: bereits erzeugte Composables werden nicht überschrieben (Hinweis „existiert, übersprungen“, Schalter „überschreiben“ nur mit Bestätigung), keine leeren Backups, kein Dateichaos.

## 52.10 Prüfung per Build und Bericht

* Prüfung (nur mit Zustimmung): Infrastruktur aus Task 28 (`./gradlew …`, `GradleOutputParser`, `ProblemsTab`). Ablauf: `compileDebugKotlin`, danach `assembleDebug`, danach vorhandene Unit-Tests. Optional „Vorher auch bauen“ (Standard aus), damit klar ist, ob ein Fehler schon vorher bestand. Fehler mit Datei und Zeile, klickbar.
* **Bericht** `Backup/x2c-report-<ts>.md` im Projekt: Zusammenfassung (erzeugt / mit Hinweisen / übersprungen / fehlgeschlagen), je Layout Grund und Anzahl `TODO(x2c)`, erzeugte Dateien, gewählte Compose-/Kotlin-Versionen mit Quelle, Build-Änderungen (Datei, was), Stufe 2: umgestellte und **nicht** umgestellte Verwender mit Grund, **„Code, der noch auf Views zugreift“** (Datei:Zeile, die offene Handarbeit der Stufe 3), entfernte XML-Dateien, Pfad von Backup und Journal, Ergebnis der Build-Prüfung, Hinweis „Layout automatisch übertragen, Verhalten bitte prüfen“. Keine Geheimnisse.
* Optional (nicht Pflicht): Vorher-/Nachher-Vorschau über den Layout-Designer (Task 15/41) und die Compose-Vorschau (Task 26/27) nebeneinander; wenn nicht umgesetzt, im Bericht als offen vermerken.

## 52.11 UI: Fortschritt und Ergebnis

* **Modul `:feature:x2c`:** `X2cViewModel`, `X2cContract` (State/Intent/Effect), `X2cDialog`, `X2cProgressScreen`, `X2cResultScreen`. Phasen: Analyse → Backup → Build-Vorbereitung → Layouts (x/N) → Verdrahtung (x/N) → Anwenden → Prüfen. Abbruch jederzeit mit klarer Aussage, was dann bleibt.
* Langläufer in einem Foreground-Service (Muster: `BuildForegroundService`), Benachrichtigung mit Abbruchaktion.
* Ergebnis: Zusammenfassung, Dateiliste mit Filter (Hinweise/Fehler/TODO), Sprung in die Datei (`OpenFileRequestBridge`), Schaltflächen **Build ausführen**, **Bericht öffnen**, **Rückgängig**, **Backup-Ordner zeigen**, **Compose-Vorschau öffnen** (falls Task 27 verfügbar).
* Barrierefrei, Textgrößen, Dark/Light, Hoch- und Querformat; Previews für Dialog und Ergebnis.

## 52.12 Tests, Strings, Doku

* **Golden-Tests** (reines JVM, `:libs:x2c`): je Zeile der Pflicht-Tabelle und je Querschnittsregel eine Eingabe/Erwartungs-Datei; mindestens drei vollständige Beispiel-Layouts (einfaches LinearLayout-Formular, ConstraintLayout-Bildschirm, `item_*.xml` für eine Liste). Erwartete `.kt`-Dateien müssen **kompilieren**: Prüfung im Beispielprojekt per Gradle auf dem Gerät (Skript für den Inhaber); in der CI nur Textvergleich, wenn kein Android-Build möglich ist (im Bericht angeben).
* **Beispielkorpus:** mindestens zwei kleine View-Projekte unter `examples/`: (1) Kotlin-Projekt mit 3 Layouts (Activity, Fragment, Listen-Item), (2) Java-Projekt mit ViewBinding. Umwandeln, bauen, starten (Gerät); Ergebnis im Bericht, ausdrücklich **nicht getestet**, bis der Inhaber es ausgeführt hat.
* **Analyse:** `ComposeMigrationScanner` (Schwellen, Ausschlüsse, „Compose schon aktiv“), `LayoutUsageAnalyzer` (alle Muster aus 52.5, Heuristik-Grenzen, RemoteViews-Erkennung, Data Binding), Einstufung.
* **Build-Änderungen:** Golden-Tests für Groovy und Kotlin DSL, mit und ohne Katalog, vorhandenes Compose nicht doppelt, `minSdk` zu niedrig → klare Meldung, unbekannte Kotlin-Version → Rückfrage.
* **Stufe 2:** Fälle einfach/komplex für Activity und Fragment, Java-Activity wird nicht angefasst.
* **Backup/Journal/Rückgängig/Abbruch/zweiter Lauf:** wie 50.10, inklusive „XML entfernen“ und Wiederherstellung.
* **ViewModel:** Dialogzustände, Layout-Auswahl, Schalter-Sichtbarkeit (Katalog nur mit Katalogdatei, Stufe 2 nur mit Kandidaten).
* **Strings** zweisprachig (Task 17/18); keine harten Texte. **Doku:** `docs/x2c-report.md` (Entscheidungen 52.0, Abbildungstabelle mit Status je Zeile, bekannte Grenzen, Stufe-3-Hinweise), Hilfetext in der App zum Backup-/Rückgängig-Verhalten, Seite `docs/app/x2c.md` (Task 99), sobald vorhanden.

## Abnahme

* Spike (52.0) belegt, Entscheidungen (Modulaufteilung, gemeinsame Infrastruktur mit Task 50, Parser, Verhältnis zu 41.11) begründet; `:app:assembleDebug` grün.
* Dialog erscheint nach Import/Clone bei Projekten mit Layouts und ohne aktives Compose (nie parallel zum Task-50-Dialog); „Später“ und „Nicht mehr fragen“ funktionieren.
* Backup-ZIP wird vor jeder Änderung angelegt und verifiziert; ohne gültiges Backup keine Änderung.
* Pflicht-Abbildungen erzeugen Composables nach 52.6 (Golden-Tests grün); Unbekanntes steht als `TODO(x2c)`, nichts verschwindet still; RemoteViews- und Data-Binding-Layouts werden nicht umgewandelt und erklärt.
* Compose-Unterstützung wird in den Build-Dateien ergänzt (Groovy/Kotlin DSL, mit/ohne Katalog), Voraussetzungen werden vorab geprüft.
* Stufe 2 stellt nur einfache Fälle um; alle übrigen Verwender stehen mit Grund im Bericht; Stufe 3 ist nicht enthalten.
* Journal, Rückgängig, Abbruch und Bericht funktionieren (Tests); „XML entfernen“ nur bei nicht mehr verwendeten Layouts.
* Build-Prüfung über Task-28-Infrastruktur, Ergebnis im Bericht; Aussage „Layout automatisch übertragen, Verhalten bitte prüfen“.
* Bericht mit **getestet / nicht getestet / offene Punkte**; Gerätetests und Beispielkorpus-Läufe als offen vermerkt, bis der Inhaber sie bestätigt.

## Commit-Vorschlag (je Teil)
```
feat(x2c): <Teil>

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
