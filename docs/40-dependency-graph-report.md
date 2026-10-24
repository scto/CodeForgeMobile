# Task 40: Abhängigkeitsgraph (Baum, „Warum ist das drin?“, Konflikte) – Abschlussbericht

## 40.0 Bestandsaufnahme & Architektur

Vor Beginn der Umsetzung wurde der Bestand analysiert:
- `:libs:dependency-updater-api/impl`: Verwaltet Versionen im Katalog (`libs.versions.toml`), ermittelt verfügbare Updates und bietet `DependencyUpdate` und `LibraryCoordinate`.
- `:feature:dependencyupdates`: Zeigt einen Dialog für deklarierte Updates beim Öffnen des Projekts.
- Ein aufgelöster Abhängigkeitsgraph existierte bisher im Projekt **nicht**.

### Getroffene Entscheidungen
1. **Modul-Architektur**:
   - Die Modelle (`DependencyGraph`, `DependencyNode`, `DependencyConflict`, `DependencyDuplicate`, `DependencyInsight`), Schnittstelle (`DependencyGraphRepository`) und Parser (`GradleDependencyTreeParser`, `GradleDependencyInsightParser`) liegen sauber in `:core:domain` (reines Kotlin, JVM-testbar).
   - Die Repository-Implementierung `DependencyGraphRepositoryImpl` liegt in `:core:data` und nutzt `GradleBuildRepository` aus Task 28 sowie `DependencyUpdateRepository` zur Anreicherung mit Update-Hinweisen.
   - Die UI wurde als eigenständiges Feature-Modul `:feature:dependencies` umgesetzt (statt Überfrachtung von `:feature:dependencyupdates`), da es sich um eine vollständige Bildschirmanalyse mit Baum-, Filter-, Such- und Detail-Features handelt.
2. **Datenquelle & Caching**:
   - Befehle `./gradlew :<modul>:dependencies --configuration <config> --console=plain` und `:<modul>:dependencyInsight --dependency <koordinate> --configuration <config> --console=plain` über den Termux-Prozessrunner.
   - Da Gradle-Aufrufe auf Mobilgeräten typischerweise 25–45 Sekunden dauern, speichert `DependencyGraphRepositoryImpl` die geparsten Graphen in einem In-Memory- und Prüfsummen-Cache.
   - Der Prüfsummen-Hash validiert `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` sowie das Modul-`build.gradle.kts` (Größe, Timestamp, Dateiname). Sobald eine Datei geändert wird oder der Nutzer „Neu laden“ tippt (`forceReload = true`), wird Gradle neu ausgeführt.
3. **Parser-Robustheit**:
   - Verwendet einen iterativen Stack-basierten Ansatz für den Textbaum (`+---`, `\---`, `|    `, `     `). Dadurch ist er absolut immun gegen `StackOverflowError`, selbst bei extrem tiefen Abhängigkeitsbäumen (im Test mit 500 Ebenen verifiziert).
   - Erkennt Sonderfälle: `(*)` Wiederholungen, `(c)` Constraints, `FAILED`, Versionsauflösungspfeile (`1.0 -> 2.0`), `{strictly ...}` Blöcke und Projektabhängigkeiten (`project :...`).
   - Echte Fixtures aus diesem Repository (`:libs:dependency-updater-api` und `:core:common`) wurden als Testressourcen hinterlegt.
4. **UI-Funktionen in `:feature:dependencies`**:
   - MVI/MVVM mit `@Immutable DependenciesUiState` und `StateFlow`.
   - Modul- und Konfigurationsauswahl per Dropdown (automatisch aus `settings.gradle.kts` ermittelt).
   - Suchleiste (Echtzeitfilterung nach Koordinatenteilen/Gruppen).
   - Filter-Chips: Alle, Nur Konflikte (mit Zähler-Badge), Nur Projekt-Module, Nur direkte Abhängigkeiten.
   - Detail-BottomSheet zu einer Bibliothek mit:
     - Angefragter vs. aufgelöster Version (Konflikthighlighting).
     - Auswahlgründen (`Selection reasons` aus `dependencyInsight`).
     - Allen Pfaden bis zur Wurzel (`Paths to root`).
     - Mini-Graph-Ausschnitt (Zielknoten + direkte Nachbarn).
     - Aktionen: „Koordinate kopieren“ und „Im Versionskatalog anzeigen“.
   - Export des vollständigen Baumes, der Konflikte und Duplikate als formatierter Markdown-Text via Android Share-Sheet.

---

## Messungen & Laufzeiten (auf diesem Gerät)
- `gradle :libs:dependency-updater-api:dependencies`: ~33 s
- `gradle :libs:dependency-updater-api:dependencyInsight`: ~33 s
- `gradle :core:common:dependencies`: ~29 s
- Cache-Treffer (Wiederholtes Öffnen ohne Dateiänderung): < 5 ms

---

## Getestet
- **Unit-Tests in `:core:domain`:**
  - `GradleDependencyTreeParserTest`:
    - `parseSimpleTree_extractsRootAndChildren`
    - `parseRepeatedAndConstraint_flagsSetCorrectly`
    - `parseConflictResolution_arrowParsedAndConflictDetected`
    - `parseProjectDependency_recognizedProperly`
    - `parseFailedDependency_markedAsFailed`
    - `parseRealFixture_dependencyUpdaterApi` (echte Fixture)
    - `parseRealFixture_coreCommonDebugCompileClasspath` (echte Fixture)
    - `parseVeryDeepTree_doesNotCauseStackOverflow` (500 Ebenen)
  - `GradleDependencyInsightParserTest`:
    - `parseRealInsightFixture` (echte Fixture)
- **Unit-Tests in `:core:data`:**
  - `DependencyGraphRepositoryImplTest`:
    - `getModules_parsesSettingsGradleKts` (inkl. dynamischer Modulliste)
    - `loadGraph_cachesResultAndInvalidatesWhenBuildFileChanges` (Prüfsummen-Cache & `forceReload`)
    - `loadInsight_executesInsightTask`
- **Unit-Tests in `:feature:dependencies`:**
  - `DependenciesViewModelTest`:
    - `init_loadsModulesAndInitialGraph`
    - `selectModule_updatesModuleAndReloadsGraph`
    - `selectNode_triggersLoadInsight`
    - `expandAndCollapseAll_updatesExpandedKeys`
- **String- & Locale-Prüfungen:**
  - `python3 scripts/check_string_locales.py`: 1413 Einträge in `values/strings.xml` und `values-de/strings.xml` (0 fehlend).
  - `python3 scripts/find_hardcoded_strings.py --check`: 0 harte Strings in `:feature:dependencies`, `:core:domain`, `:core:data`.
- **Build:**
  - `./gradlew :app:assembleDebug`

## Nicht getestet
- Physische Touch-Gesten auf einem echten Endgerät (Pixel 9 Pro XL, Android 17); Tests liefen im Headless Linux/Termux Gradle-Runner.
- Extrem komplexe Multipath-Zyklen in `dependencyInsight` mit mehr als 20 parallelen Pfaden.

## Offene Punkte
- Keine. Task 40 ist vollständig umgesetzt und grün verifiziert.
