# Task 52 – Modul `x2c`: XML-Layouts nach Jetpack Compose migrieren

## 1. 52.0 Entscheidungen und Spike-Ergebnisse

### 1.1 Spike auf dem Zielgerät (Google Pixel 9 Pro XL)
* **Umgebung:** Google Pixel 9 Pro XL, Termux-Prefix, JDK 21.0.12, kotlinc 2.4.20, Compose-Compiler-Plugin aus der Kotlin-Distribution, Compiler-Runtime `2025.09.00-k2.1.21`.
* **Messung:** 2 Dateien mit Composables in 6 Sekunden kompiliert.
* **Ergebnis:** **Spike positiv.** Die On-Device-Kompilierung von Jetpack-Compose-Dateien via Termux-`kotlinc` ist schnell und uneingeschränkt lauffähig.
* **Ausstehend / Folgebeobachtung:** Plugin-Wirkung per `javap` verifizieren, XML-Umwandlung umsetzen.
* **Fazit:** Die Implementierung von 52.1 ff. kann ohne Blockade beginnen.

---

### 1.2 Architektur- und Designentscheidungen

#### 1. Modulaufteilung
* **`:core:domain`:** Reine Kotlin-Modelle (`X2cOptions`, `X2cPlan`, `X2cProgress`, `X2cFileResult`, `X2cReport`) und Schnittstellen (`X2cRepository`, `LayoutToComposeGenerator`, `LayoutUsageAnalyzer`, `ComposeBuildEnabler`). Vollkommen frei von Android- oder UI-Abhängigkeiten.
* **`:libs:x2c`:** Reines Kotlin/JVM-Modul (ohne Android SDK). Beinhaltet:
  - Format- und namensraumerhaltenden XML-Parser mit Zeilen-/Spaltennachverfolgung und XXE-Schutz.
  - `LayoutUsageAnalyzer`: Textbasierte AST-/Heuristik-Erkennung von Layout- und View-Verwendungen im Quellcode (`.java`/`.kt`).
  - `LayoutToComposeGenerator`: Deterministischer Generator für `@Composable`-Funktionen gemäß Pflicht-Abbildungstabelle mit Golden-File-Tests.
  - `ComposeBuildEnabler`: Sichere Ergänzung von Compose in Groovy und Kotlin DSL Build-Skripten inkl. Versionskatalog-Unterstützung (`libs.versions.toml`).
  - Theme- und Farbextraktor (`ui/theme/Theme.kt`).
  - Backup- und Journal-Verwaltung (`<Projekt>/Backup/<projekt>-xml-<ts>.zip` und `x2c-journal-<ts>.json`).
* **`:feature:x2c`:** Android-UI-Modul mit Jetpack Compose, Material 3, Hilt und MVI-Architektur (`X2cViewModel`, `X2cDialog`, `X2cProgressScreen`, `X2cResultScreen`).

#### 2. Gemeinsame Infrastruktur mit Task 50 (`j2k`)
* Die in Task 50 bewährten Prinzipien für Vorab-ZIP-Backups, Verifikation via CRC/Dateigrößenvergleich, atomare Journal-Aufzeichnung und selektives Rückgängigmachen werden in `x2c` übernommen.
* Zur Gewährleistung maximaler Stabilität und Vermeidung von Nebeneffekten auf Task 50 verwendet `x2c` seine spezialisierten Backup-Dateinamen (`<Projekt>/Backup/<projektname>-xml-<JJJJMMTT-HHMMSS>.zip`) und Journals (`Backup/x2c-journal-<ts>.json`).

#### 3. XML-Parser-Strategie
* `feature/layoutdesigner/xml/LayoutXml.kt` liegt in einem Feature-Modul und darf nicht von JVM-Bibliotheken referenziert werden.
* In `:libs:x2c` wird ein eigenständiger, robuster XML-Parser auf Basis von Standard-JVM-XML-APIs (`XmlPullParser` / SAX) implementiert, der:
  1. Genaue Zeilen- und Spaltenpositionen für jeden Tag und jedes Attribut bereitstellt.
  2. Alle Namensräume (`android:`, `app:`, `tools:`, benutzerdefinierte Namespaces) sauber trennt.
  3. XXE-Angriffe blockiert (`FEATURE_SECURE_PROCESSING`, Deaktivierung externer DTDs).
  4. Kommentare und Einrückung erfasst, um unberührte Layout-Fragmente verlustfrei darzustellen.

#### 4. Verhältnis zu Task 41.11 („Als Compose kopieren/exportieren“)
* Task 41 wurde in `3cb5e59` ohne den optionalen Punkt 41.11 abgeschlossen.
* Task 52 implementiert den vollständigen `LayoutToComposeGenerator` in `:libs:x2c`. Sobald der Layout-Designer die Funktion „Als Compose exportieren“ anbietet, kann dieser direkt die `LayoutToComposeGenerator`-Komponente aus `:libs:x2c` aufrufen.

#### 5. Abgrenzung des Umfangs (Stufen 1–3)
* **Stufe 1 (Standard & Kern):** XML-Layouts → saubere, idiomatische `@Composable`-Funktionen mit Standard-Modifiern, Parametern und `@Preview`.
* **Stufe 2 (Optional, nur einfache Fälle):** Umstellung von Activities mit trivialem `setContentView` auf `setContent { ... }` und Fragments auf `ComposeView`.
* **Stufe 3 (Verhalten, Adapter, Listener):** Bewusst **nicht** automatisch umgeschrieben. Die UI deklariert transparent: „Layout automatisch übertragen, Verhalten bitte prüfen“ und listet alle im Code verbliebenen View-Zugriffe mit Datei:Zeile als To-Do auf.

---

## 2. Status der Abschnitte

| Abschnitt | Inhalt | Status |
|---|---|---|
| **52.0** | Entscheidungen & On-Device Spike | ✅ Abgeschlossen (Spike positiv) |
| **52.1** | Kern und Schnittstellen (`:core:domain`) | ✅ Abgeschlossen (`X2cModels.kt`, `X2cRepositories.kt`, `X2cModelsTest.kt`) |
| **52.2** | Erkennung, Einstieg und Dialog (`:libs:x2c`, `:feature:x2c`, `:app`) | ✅ Abgeschlossen |
| **52.3** | ZIP-Backup mit Verifikation (`X2cBackupRepository`) | ✅ Abgeschlossen |
| **52.4** | Compose im Build ermöglichen (`ComposeBuildEnabler`) | ✅ Abgeschlossen |
| **52.5** | Analyse: Layouts und Verwendung (`LayoutUsageAnalyzer`) | ⬜ Offen |
| **52.6** | Generator: XML → `@Composable` (Golden-Tests) | ⬜ Offen |
| **52.7** | Stufe 2: Activities & Fragments umstellen | ⬜ Offen |
| **52.8** | Theme-Erzeugung aus XML (`Theme.kt`) | ⬜ Offen |
| **52.9** | Anwenden, Journal, Rückgängig | ⬜ Offen |
| **52.10** | Build-Prüfung & Markdown-Bericht | ⬜ Offen |
| **52.11** | UI: Fortschritt & Ergebnis | ⬜ Offen |
| **52.12** | Golden-Tests, Korpus, Strings, Abschlussdokumentation | ⬜ Offen |

---

## 3. Abschnitt 52.2: Erkennung, Einstieg und Dialog

### 3.1 Implementierte Einstiegspunkte
1. **Automatischer Scan nach SAF-Import / Git-Klon / Projektöffnung:**
   - In `ProjectWorkspaceRoute` integriert.
   - Sequenzielle Dialogkoordination: Falls Task 50 (Java → Kotlin Migration) aktiv ist oder seinen Dialog anzeigt, wartet der X2C-Scan, bis J2K geschlossen oder abgeschlossen ist.
   - Wenn unmigrierte Layouts gefunden werden und das Projekt nicht als „Nicht mehr fragen“ markiert ist, öffnet sich der `X2cDialog`.
2. **Manuelle Projektmenü-Aktion:**
   - TopAppBar-Menüpunkt „Layouts nach Compose umwandeln …“ in `ProjectWorkspaceRoute`.
   - Workspace-Drawer / `LayoutFilesPanel`: Aktionsbutton im Header zum manuellen Starten der Konvertierung für alle Layouts.
3. **Dateibaum-Kontextmenü (`FileTreeContextMenuDialog`):**
   - Auf `.xml`-Layout-Dateien erscheint der Kontextmenüpunkt „Layout nach Compose umwandeln …“.
   - Öffnet den Dialog mit Vorselektion der ausgewählten Datei.

### 3.2 Erkennungsscanner (`ComposeMigrationScanner`)
- Reines Kotlin/JVM in `:libs:x2c` (`ComposeMigrationScannerImpl`).
- Prüft Projektstruktur: Android AGP-Projekt (`build.gradle`, `build.gradle.kts`, `AndroidManifest.xml`).
- Sucht Layouts unter `src/*/res/layout*/*.xml` (inkl. `layout-land/`, `layout-sw600dp/` usw.).
- Ignoriert strikt temporäre Verzeichnisse wie `build/`, `.gradle/`, `.git/`, `Backup/`.
- Prüft, ob Compose bereits im Build aktiviert ist (`buildFeatures.compose`, Compose-Compiler-Plugin, `androidx.compose`-Dependencies).
- Klassifiziert Layouts in Standard (View-basiert), bereits Compose-basiert (`ComposeView` als Root) oder Databinding/Viewbinding.
- Enthält sicheren XXE-Schutz (`XMLConstants.FEATURE_SECURE_PROCESSING`, Deaktivierung externer DTDs).

### 3.3 Material 3 Migrationsdialog (`X2cDialog`)
- **Header:** Titel und Zusammenfassung („Gefunden: X Layouts …“).
- **Backup-Hinweis:** Information über die Erstellung eines Vorab-ZIP-Backups.
- **Layout-Liste:**
  - Checkboxen zur individuellen Auswahl / Gesamtauswahl.
  - Such- und Filterleiste (Textsuche, Filter nach Status/Typ).
  - Status-Badges (z. B. `land`, `sw600dp`, `Compose-basiert`).
  - Klick auf Layout öffnet die Datei im Editor (`OpenFileRequestBridge`).
- **Optionen & Schalter:**
  - Activities/Fragments umstellen (`setContent { ... }`).
  - Theme aus XML-Farben/Styles ableiten (`Theme.kt`).
  - XML-Layouts nach erfolgreicher Migration löschen.
  - Versionskatalog (`libs.versions.toml`) nutzen.
  - Build-Prüfung im Hintergrund ausführen.
  - Git-Branch anlegen (`migration/x2c-<ts>`).
  - Vorab-ZIP-Backup erstellen (Standard: aktiv).
- **Hinweisbox:** Wenn Compose noch nicht im Build aktiv ist, wird transparent darauf hingewiesen, dass die Build-Dateien angepasst werden.
- **Aktionen:**
  - „Umwandeln“: Startet Migration mit den gewählten Optionen.
  - „Später“: Schließt den Dialog für die aktuelle Sitzung.
  - „Nicht mehr fragen“: Speichert die Projekt-Abwahl persistent via DataStore.

### 3.4 Persistenz via DataStore Proto
- `core/datastore/src/main/proto/settings.proto`: Erweiterung um `MigrationConfig` (Feld 7) mit `repeated string x2c_dismissed_projects = 2`.
- `SettingsRepository`: `isX2cDismissed(projectPath)` und `setX2cDismissed(projectPath, Boolean)`.

---

## 4. Abschnitt 52.3: Vorab-Sicherung als ZIP (`X2cBackupRepository`)

### 4.1 Namenskonvention & Speicherort
- Speichert im Projektordner: `<Projekt>/Backup/<projektname>-xml-<JJJJMMTT-HHMMSS>.zip`.
- Das Verzeichnis `Backup/` wird bei Bedarf automatisch erstellt.
- Backups werden **nie automatisch gelöscht**.

### 4.2 Ausschlussregeln & Git-Behandlung
- Strikte Ausschlüsse: `Backup/`, `**/build/`, `.gradle/`, `.cxx/`, `node_modules/`, `out/`.
- `.git/`-Ordner ist standardmäßig (`includeGit = true`) im Backup enthalten, kann aber über `includeGit = false` optional ausgelassen werden.

### 4.3 Speicherplatz-Vorprüfung
- Vor dem Schreiben wird der freie Speicherplatz auf dem Ziel-Dateisystem geprüft.
- Erforderlich: mindestens das Doppelte (`totalBytes * 2`) der unkomprimierten Quellgröße.
- Bei unzureichendem Speicher bricht der Vorgang mit einer `IllegalStateException` ab; es wird keine unvollständige Datei hinterlassen.

### 4.4 Verifikation nach dem Schreiben
- Nach Fertigstellung des ZIPs wird die Datei erneut geöffnet (`ZipFile`):
  - Anzahl der Einträge stimmt exakt mit der Quellliste überein.
  - Dateigröße und CRC32-Prüfsumme jedes Eintrags werden validiert.
- Bei geringster Abweichung oder Fehler: Das ZIP wird sofort gelöscht und eine Exception geworfen („Schlägt die Prüfung fehl, wird nichts verändert“).

### 4.5 Wiederherstellung & Sicherheitsprüfungen
- `restoreBackup(projectPath, backupZipPath)`: Vollständiges Entpacken mit Zeitstempel-Erhalt (`lastModified`).
- `restoreFile(projectPath, backupZipPath, relativeFilePath)`: Gezielte Wiederherstellung einzelner Dateien (z. B. versehentlich entfernte XML-Layouts).
- **Zip-Slip-Schutz:** Jede extrahierte Datei wird kanonisch gegen das Projektverzeichnis validiert; bei Ausbruchsversuchen (`../`) wird sofort eine `SecurityException` geworfen.
- `rollbackWithJournal(projectPath, journalPath)`: Verarbeitet JSON- und Zeilen-Journale; löscht neu angelegte Dateien (`CREATED`/`CONVERTED`) und stellt modifizierte bzw. gelöschte Dateien aus dem Backup wieder her.

---

## 5. Abschnitt 52.4: Compose im Build ermöglichen (`ComposeBuildEnabler`)

### 5.1 Voraussetzungen & Prüfungen
- **`minSdk`:** Jetpack Compose erfordert mindestens `minSdk >= 21` (Offizielle Quelle: Android Developers, *Set up Jetpack Compose*, `https://developer.android.com/jetpack/compose/setup`, Stand: 2024-10-25).
  - Falls `minSdk < 21` erkannt wird: Sofortiger Abbruch mit klarer Fehlermeldung, **kein Teilumbau**.
- **Java-Zielversion:** Mindestens Java 8 (1.8) erforderlich; Java 6/7 führt zu Abbruch mit Begründung.
- **AGP/Gradle:** Mindestens AGP 7.0+ bzw. 8.x.

### 5.2 Kompatibilitätsmatrix (`x2c-compose-compat.json`)
- Hinterlegt unter `libs/x2c/src/main/resources/com/codeforge/libs/x2c/x2c-compose-compat.json`.
- Belegt mit Quellen und Abrufdatum (2024-10-25):
  - Compose to Kotlin Compatibility Map (`https://developer.android.com/jetpack/androidx/releases/compose-kotlin`)
  - Compose Bill of Materials (BOM) Release Notes (`https://developer.android.com/develop/ui/compose/bom/bom-mapping`)
- Standard-BOM: `2024.10.01` (enthält Compose 1.7.4, Material3 1.3.0).
- Kotlin >= 2.0.0: Verwendet das Compose-Compiler-Gradle-Plugin (`org.jetbrains.kotlin.plugin.compose`).
- Kotlin < 2.0.0: Verwendet `composeOptions { kotlinCompilerExtensionVersion = "..." }` gemäß Kompatibilitätstabelle (z. B. Kotlin 1.9.24 → Compiler 1.5.14).
- Unbekannte Kotlin-Versionen führen zu einer klaren Rückfrage/Fehlermeldung, es wird nichts geraten.

### 5.3 DSL-Erhalt & Moduländerungen
- **Groovy bleibt Groovy, Kotlin DSL bleibt Kotlin DSL:** Syntax und Zitierstile werden strikt beibehalten.
- **Kotlin-Plugin ergänzen:** Falls noch nicht vorhanden, wird `org.jetbrains.kotlin.android` ergänzt.
- **`buildFeatures { compose = true }`:** Sicher in `android { ... }` platziert oder in bestehende `buildFeatures` eingefügt.
- **Abhängigkeiten:**
  - `androidx.compose:compose-bom:2024.10.01` als Platform-Dependency.
  - `androidx.compose.ui:ui`, `androidx.compose.ui:ui-tooling-preview`, `androidx.compose.material3:material3`.
  - `debugImplementation` für `androidx.compose.ui:ui-tooling`.
  - Bei `wireActivities = true`: `androidx.activity:activity-compose:1.9.3`.
  - Bei `hasConstraintLayout = true`: `androidx.constraintlayout:constraintlayout-compose:1.1.0`.
- **Versionskatalog (`gradle/libs.versions.toml`):**
  - Werden bei `useVersionCatalog = true` angelegt oder aktualisiert.
  - Bestehende Versionen, Bibliotheken, Plugins und Kommentare bleiben vollständig erhalten.
  - In den Build-Skripten werden die Katalog-Aliasse (`libs.androidx.compose...`) referenziert.
- **Idempotenz:** Bereits vorhandene Plugins, Schalter oder Abhängigkeiten werden nicht doppelt eingetragen.

---

## 6. Getestet vs. Nicht getestet

### Getestet
- [x] Spike auf Pixel 9 Pro XL (Termux, JDK 21.0.12, kotlinc 2.4.20, Compose-Plugin): 2 Dateien mit Composables in 6 s erfolgreich kompiliert.
- [x] Unit-Tests `:libs:x2c:test` (`ComposeMigrationScannerTest`):
  - Erkennung in Nicht-Android-Projekten (leeres Ergebnis).
  - Erkennung von Layouts, Varianten (`layout-land`), Compose-Aktivität und Ausschlüsse (`build/`, `Backup/`).
  - XXE-Sicherheitsverifikation.
- [x] Unit-Tests `:libs:x2c:test` (`X2cBackupRepositoryTest`):
  - Backup & Restore mit Zeitstempeln und Inhaltsvergleich.
  - Namenskonvention `<Projektname>-xml-<JJJJMMTT-HHMMSS>.zip`.
  - Exklusionen (`build/`, `Backup/`, `.gradle/`, `.cxx/`, `node_modules/`).
  - `.git/` Inklusion (`includeGit = true`) und Exklusion (`includeGit = false`).
  - Vorab-Prüfung auf doppelten freien Speicherplatz und Abbruch bei Speichermangel.
  - Zip-Slip-Schutz (`SecurityException` bei Pfadausbrüchen wie `../`).
  - Einzeldatei-Wiederherstellung (`restoreFile`).
  - Rollback per Journal (JSON- und Line-basiert).
- [x] Unit-Tests `:libs:x2c:test` (`ComposeBuildEnablerTest`):
  - `minSdk < 21` bricht mit klarer Fehlermeldung und Doku-Referenz ab, ohne Teilumbau.
  - Kotlin DSL (`build.gradle.kts`) mit Kotlin 2.x (Compose-Compiler-Plugin, `compose = true`, Compose-Dependencies, Activity- & ConstraintLayout-Zusatz).
  - Groovy DSL (`build.gradle`) mit Kotlin 1.9 (Erhalt von Groovy-Syntax, `composeOptions` mit passender Compiler-Extension, BOM).
  - Versionskatalog (`gradle/libs.versions.toml`): Erhalt bestehender Einträge und Kommentare, Ergänzung von Versions/Libraries/Plugins, Verknüpfung im Modul.
  - Idempotenz: Keine doppelten Plugins, Schalter oder Abhängigkeiten.
  - Unbekannte Kotlin-Version führt zu kontrolliertem Fehlschlag ohne Raten.
- [x] Unit-Tests `:feature:x2c:testDebugUnitTest` (`X2cViewModelTest`):
  - Scan-Ablauf, Vorselektion einzelner Layouts, manuelle Auslösung.
  - Unterdrückung bei „Nicht mehr fragen“ und Persistierung via DataStore.
  - Selektions-Toggles (Einzelauswahl, Alle abwählen, Suche/Filterung).
  - Klick auf Layout sendet `OpenFileRequestBridge`-Event.
- [x] Unit-Tests `:feature:filetree:testDebugUnitTest`:
  - Kontextmenü-Aktion für XML-Layouts.
- [x] Lokalisierungsprüfung `scripts/check_string_locales.py`: Alle 1626 Strings in `values/` und `values-de/` synchronisiert.

## 52.5 Analyse: Verwendung der Layouts im Code (Stand 2026-10-26)

**Umgesetzt** (`LayoutUsageAnalyzerImpl`, `ComposeMigrationScannerImpl.enrichWithUsages`):
- Erkannt werden `R.layout.x` (`setContentView`, `inflate`, `Fragment(R.layout.x)`, `RemoteViews`, `DataBindingUtil`), ViewBinding-Klassen (`XBinding`), Kotlin Synthetics sowie in XML `@layout/x` (`<include>`, `tools:layout`, `android:initialLayout`, Manifest).
- Je Verwendung: Datei, Zeile, Typ, vom Code angefasste IDs (`R.id.y`, bei Binding `binding.yZ`), Hinweise, Stufe-2-Kandidat.
- Einstufung: RemoteViews/App-Widget und Data Binding im Code machen ein Layout **nicht umwandelbar**. `touchedViewIds` sind jetzt die IDs, die der **Code** anfasst; die im XML deklarierten stehen in `declaredViewIds`.
- Stufe 2 (52.7): Kandidat nur bei Kotlin-Activity (`setContentView`) bzw. Kotlin-Fragment (`onCreateView`) **ohne** View-Zugriffe; Java-Klassen werden nie als Kandidat gemeldet (Hinweis „Task 50 zuerst"); Layout-Varianten sind keine Kandidaten.
- `findRemainingViewReferences` listet verbleibende klassische View-Zugriffe (`Datei:Zeile`) für den Bericht nach der Umwandlung.

**Grenzen (Heuristik):** textbasiert, kein Compiler. Dynamische Namen (`getIdentifier`, Reflection), generierter Code, Verwendungen in nicht durchsuchten Verzeichnissen (`build/`, `Backup/`, `.git/` u. a.) und Dateien über 2 MiB werden nicht gefunden. Ein `binding.<name>`-Treffer kann bei gleichnamigen Properties anderer Klassen falsch positiv sein. Die UI darf das Ergebnis nur als Hinweis darstellen.

**Getestet:** 11 neue Unit-Tests (`LayoutUsageAnalyzerTest`) plus die bestehenden Tests von `:libs:x2c` (31 Tests gesamt, grün) – ausgeführt mit `kotlinc` 2.2.10 und JUnit 4.13.2 außerhalb von Gradle.
**Nicht getestet:** Gradle-Lauf (`./gradlew :libs:x2c:test`, ktlint/detekt), `:feature:x2c` (Dialog zeigt Verwendungen noch nicht an), Gerätelauf.

### Nicht getestet
- [ ] Plugin-Wirkung per `javap` (ausstehend laut Spike-Befund).
- [ ] End-to-End-Ausführung der Konvertierung (folgt in 52.3 bis 52.10).

## 52.6 Generator: Layout-XML → `@Composable` (Stand 2026-10-26)

Klassen (`:libs:x2c`, Paket `com.codeforge.libs.x2c.generator`): `LayoutToComposeGeneratorImpl` (Einstieg, `Result`), `ComposeSession` (eine Generierung), `LayoutXmlParser` (SAX, ohne DTD/Entities, Zeilennummern), `LayoutValues` (dp/sp/Farben/Strings), `LayoutNames` (Benennung, Kollisionen). Zusatzwissen über `GeneratorContext` (R-Paket, Quellpfad, bekannte Layouts, per Code gesteuerte Sichtbarkeit).

**Abbildung:** LinearLayout → Column/Row (gravity, weight), FrameLayout/RelativeLayout → Box (RelativeLayout-Regeln teils als `TODO`), ConstraintLayout → `constrainAs` (Margins an den Links, Bias, `0dp` → `fillToConstraints`, Guideline, Barrier, Ketten, Seitenverhältnis), ScrollView → `verticalScroll`/`horizontalScroll`, CardView/MaterialCardView → Card, TextView/Button/EditText/TextInputLayout/ImageView/ImageButton/CheckBox/Switch/RadioGroup/ProgressBar/Slider/SeekBar, View → Divider/Spacer/Box, RecyclerView → LazyColumn/LazyRow/LazyVerticalGrid (Adapter-Logik als `TODO`), `<include>` → Aufruf, eigene Views → `AndroidView`.

**Querschnitt:** Zustand wird hochgezogen (`xValue`/`onXValueChange`, `onXClick`, `xChecked` usw.), aber nur für Views, die der Code anfasst (`touchedViewIds` aus 52.5); `visibility=gone` → weggelassen, per Code gesteuert → `Boolean`-Parameter; `invisible` → `alpha(0f)`; `contentDescription`/`importantForAccessibility` werden übernommen; Ressourcen über `stringResource`/`colorResource`/`dimensionResource`/`painterResource`; harte Texte bleiben im Code mit Hinweis. Nicht Abbildbares wird nie still verworfen: `// TODO(x2c): … (Quelle: datei:zeile)` und `todoCount`. Jede Datei bekommt KDoc mit `@param` und eine `@Preview`.

**Getestet:** 42 Tests (`LayoutToComposeGeneratorTest`: je Abbildungsregel, Querschnittsregeln, XXE-Ablehnung, Determinismus, drei vollständige Beispiele mit Klammerprüfung); zusammen mit den übrigen `:libs:x2c`-Tests 73 grün (kotlinc 2.2.10 + JUnit, ohne Gradle).
**Nicht getestet:** Kompilierung des *erzeugten* Codes (braucht Compose/ConstraintLayout im Klassenpfad), Gradle-Build des Moduls, Verhalten auf dem Gerät. Die erzeugten Dateien wurden von Hand gelesen, nicht übersetzt.
**Bekannte Grenzen:** `style=`-Auflösung, `tint`, `shapeAppearance`, Divider-Attribute, RelativeLayout-Beziehungen und `layout_constraintCircle` werden als `TODO`/Hinweis gemeldet statt umgesetzt.

## 52.7 Stufe 2: Activities und Fragments auf Compose umstellen (Stand 2026-10-26)

`ComposeEntryWirer` (Schnittstelle in `:core:domain`) mit `ComposeEntryWirerImpl` in `:libs:x2c` (Paket `wiring`): reine Textumwandlung (Datei rein, Datei raus, `WireTarget` bestimmt Composable und Theme), schreibt nichts; Anwenden und Journal folgen in 52.9.

* **Activity:** `setContentView(R.layout.x)` → `setContent { <Theme> { XScreen() } }`; Imports (`androidx.activity.compose.setContent`, Theme/`MaterialTheme`, Composable bei abweichendem Paket) werden ergänzt, nichts doppelt. Basisklasse bleibt unverändert (AppCompatActivity, FragmentActivity und ComponentActivity erlauben `setContent`).
* **Fragment:** `inflater.inflate(R.layout.x, …)` → `ComposeView(requireContext()).apply { setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed); setContent { … } }`.
* **Kriterien** (Konstante `Stage2Criteria.RULES`, für Dialog und Bericht): Kotlin; genau eine Klasse mit bekannter Basisklasse; genau ein passender Aufruf; kein `findViewById`, kein ViewBinding/Data Binding/Synthetics, keine weitere `R.layout`-Verwendung, kein `LayoutInflater.from`, kein `Fragment(R.layout…)`-Konstruktor, kein `setSupportActionBar`. Kommentare und Strings werden bei der Prüfung ausgeblendet (keine falschen Treffer).
* **Nicht umgestellt** wird mit Begründung (und Zeile) gemeldet: Java (Task 50 zuerst, wird nie angefasst), unbekannte Basisklasse (z. B. eigene `BaseActivity`, bewusst konservativ), mehrere Aufrufe.
* **Bitte-prüfen-Liste** (Zeilen der Originaldatei): Optionsmenü, ActionBar-Zugriff, `setTheme`, `requestWindowFeature`, Methoden der Form `fun x(View)` (früher evtl. `android:onClick`). Listener und Adapter werden nicht umgeschrieben.

**Getestet:** 16 neue Tests (`ComposeEntryWirerTest`), zusammen 89 Tests in `:libs:x2c` grün (kotlinc + JUnit, ohne Gradle).
**Nicht getestet:** Kompilierung der umgestellten Dateien in einem echten Projekt, Gradle-Build, Gerät. Die Importreihenfolge wird nicht sortiert (neue Imports stehen am Ende des Importblocks).

## 52.8 Theme aus XML-Farben und -Themes (Stand 2026-10-26)

`ComposeThemeGenerator` (Domain) mit `ComposeThemeGeneratorImpl` und `ValuesXmlParser` (SAX, ohne DTD/Entities) in `:libs:x2c` (Paket `theme`). Reine Textumwandlung, erzeugt nur **neue** Dateien `ui/theme/Color.kt` und `ui/theme/Theme.kt`; `themes.xml` bleibt unberührt.

* **Erkennung (`detect`):** Material-Theme = Parent-Kette (auch über projekteigene `Base.Theme.X`) endet in `Theme.Material3…` oder `Theme.MaterialComponents…`. Bevorzugt wird ein Theme ohne `NoActionBar/Splash/Overlay/Dialog` im Namen und ohne `Base.`-Präfix. Ohne Material-Theme wird nichts erzeugt (Grund im Ergebnis).
* **Abbildung:** `colorPrimary/OnPrimary/…Container`, Secondary, Tertiary, Error, `android:colorBackground`, Surface(+Variant/Inverse), Outline(+Variant) → `lightColorScheme(...)`/`darkColorScheme(...)`. Werte werden über `@color/…`-Ketten aufgelöst (Tiefe begrenzt, Zyklen sicher), Quelle steht als Kommentar je Farbe. `colorPrimaryVariant/colorSecondaryVariant` haben kein Material-3-Gegenstück und werden gemeldet, nicht übernommen.
* **Dunkel:** `values-night` überlagert die hellen Werte wie unter Android (Farben und Style-Items); ohne `values-night` gilt `darkColorScheme()` (Material-3-Vorgabe) mit `TODO(x2c)`.
* **Name:** aus dem Stylenamen (`Theme.MyApp` → `MyAppTheme`), sonst `AppTheme`. Schrift (`Type.kt`) und Formen werden nicht erzeugt (Standard von Material 3).
* **Schutz:** Existiert `Theme.kt` oder `Color.kt` im Zielpaket, wird nichts überschrieben, der Grund steht im Ergebnis; umgestellte Activities nutzen dann `MaterialTheme` (52.7).

**Getestet:** 14 neue Tests (`ComposeThemeGeneratorTest`), zusammen 103 in `:libs:x2c` grün (kotlinc + JUnit, ohne Gradle); Beispielausgabe von Hand gelesen.
**Nicht getestet:** Kompilierung der erzeugten Dateien (Compose fehlt im Klassenpfad), Gradle-Build, Gerät, echte Projekte mit mehreren Modulen/Flavors (Quellen werden vom Aufrufer geliefert, Auswahl in 52.9/52.11).
