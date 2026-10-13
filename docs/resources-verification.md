# Verifikationsbericht: Task 13 – :core:resources (Zentrale Strings)

Datum: 2026-10-13  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, Build-Tools 37.0.0 (aapt2 36.0.2 Override), Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 13 (`agy-tasks/13-verify-resources.md`) war die vollständige Verifikation des Moduls `:core:resources` sowie der Ausschluss jeglicher verbliebener, fest kodierter UI-Strings in `feature/` und `app/`:
1. Build- und AAPT2-Validierung von `:core:resources:assembleDebug`.
2. Gesamtergebnis-Kompilierung von `assembleDebug`.
3. JVM-Unit-Tests (`testDebugUnitTest`) inklusive `TestRes`-Reflection in `:core:data` und `:core:domain`.
4. Vollständige Migration aller verbliebenen sichtbaren UI-Literale in `feature/` und `app/` nach `core/resources/src/main/res/values/strings.xml`.
5. Überprüfung der String-Initialisierung (`ResInitializer` / `Res.init`) im Gradle-Bridge-Service (`:gradletooling`-Prozess).

---

## 2. Durchgeführte Schritte & Ergebnisse

### Schritt 1: AAPT2-Validierung (`:core:resources:assembleDebug`)
* **Kommando:** `./gradlew :core:resources:assembleDebug -PcodeforgeBootstrapSkip=true`
* **Ergebnis:** `BUILD SUCCESSFUL` (AAPT2 parst alle 745 String-Ressourcen fehlerfrei; Escapes, Anführungszeichen und Argument-Platzhalter sind valide).
* **Sanity-Check:** XML-Wohlgeformtheitsprüfung per `xml.dom.minidom` und Duplikatprüfung (`grep -o 'name="[^"]*"' | sort | uniq -d`) bestanden (0 Duplikate).

### Schritt 2: Kompilierung aller Module (`assembleDebug`)
* **Kommando:** `./gradlew assembleDebug -PcodeforgeBootstrapSkip=true`
* **Ergebnis:** `BUILD SUCCESSFUL` (1278 actionable tasks).
* Alle 44 Module und die finale APK (`app-debug.apk`) bauten ohne Ressourcen-, Import- oder R-Klassen-Kollisionsfehler.

### Schritt 3: JVM-Unit-Tests & TestRes-Reflection
* **Kommando:** `./gradlew testDebugUnitTest -PcodeforgeBootstrapSkip=true`
* **Ergebnis:** `BUILD SUCCESSFUL` (848 actionable tasks, alle Tests grün).
* **Details:**
  * `:core:data:testDebugUnitTest`: Tests (`GitRepositoryImplTest`, `GitAdvancedTest`, `GitSettingsRepositoryTest`) nutzen `TestRes.install()` und lösen echte Texte aus `strings.xml` per Reflection auf `com.codeforge.core.resources.R$string` auf.
  * `:core:domain:testDebugUnitTest`: `GitPatchTest` erfolgreich.
  * Warnung in `TestRes.kt` bezüglich `System.getProperty("user.dir")` behoben (`?: "."`).
  * Warnungen in `GitAdvancedTest.kt` und `GitRepositoryImplTest.kt` (`parentFile?.mkdirs()`) bereinigt.

### Schritt 4: UI-String-Externalisierung
* **Heuristik & Grep:** Prüfung mit `scripts/find_hardcoded_strings.py` und `grep -rn '"[A-ZÄÖÜ][a-zäöüß]' --include=*.kt feature app`.
* **Neu angelegte Ressourcen (25 Keys):**
  * `common_im_editor_oeffnen` („Im Editor öffnen“)
  * `common_lade` („Lade …“)
  * `editor_suchen_ersetzen` („Suchen/Ersetzen“)
  * `git_remote_branches_count` („Remote-Branches (%1$s)“)
  * `git_branch_aktionen` („Branch-Aktionen“)
  * `git_tag_aktionen` („Tag-Aktionen“)
  * `git_merge_nachricht` („Merge-Nachricht“)
  * `git_cherry_pick_nachricht` („Cherry-Pick-Nachricht“)
  * `git_revert_nachricht` („Revert-Nachricht“)
  * `git_commit_nachricht` („Commit-Nachricht“)
  * `git_ziel_branch_version` („Ziel-Branch-Version“)
  * `git_merge_praefix` („Merge · “)
  * `git_eltern` („Eltern: %1$s“)
  * `git_cherry_pick` („Cherry-Pick“)
  * `git_repository_url` („Repository-URL“)
  * `git_einstellungen` („Git-Einstellungen“)
  * `layout_kein_wurzelelement` („Kein Wurzelelement“)
  * `layout_xml_ungueltig` („XML ungültig“)
  * `modulemaker_gradle_pfad` („Gradle-Pfad“)
  * `plugins_geladen` („Geladen“)
  * `plugins_nicht_geladen` („Nicht geladen“)
  * `plugins_version_status` („v%1$s · %2$s“)
  * `settings_textmate_theme` („TextMate-Theme“)
  * `settings_lsp_server_pfad` („LSP-Server-Pfad“)
  * `themebuilder_beispiel_karte` („Beispiel-Karte“)
* **Ersetzte Dateien:**
  * `feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/BranchesTab.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/ChangesTab.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/CommitViews.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/ConflictEditor.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/DiffViewer.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/GitCloneScreen.kt`
  * `feature/git/src/main/kotlin/com/codeforge/feature/git/GitPanel.kt`
  * `feature/layoutdesigner/src/main/kotlin/com/codeforge/feature/layoutdesigner/xml/LayoutXml.kt`
  * `feature/modulemaker/src/main/kotlin/com/codeforge/feature/modulemaker/ModuleMakerScreen.kt`
  * `feature/plugins/src/main/kotlin/com/codeforge/feature/plugins/PluginsScreen.kt`
  * `feature/settings/src/main/kotlin/com/codeforge/feature/settings/EditorSettingsScreen.kt`
  * `feature/themebuilder/src/main/kotlin/com/codeforge/feature/themebuilder/ThemeBuilderScreen.kt`
* **Ergebnis:** `python3 scripts/find_hardcoded_strings.py --check` liefert Exit 0 (0 Funde).

### Schritt 5: Gradle-Bridge-Service im isolierten Prozess
* `GradleBridgeService` läuft mit `android:process=":gradletooling"`.
* Da `androidx.startup.InitializationProvider` standardmäßig nur im Haupt-App-Prozess ausgeführt wird, wurde `Res.init(this)` direkt in `GradleBridgeService.onCreate()` verankert.
* Dies stellt sicher, dass IPC-Aufrufe und Fehlertexte (`gradle_keine_aktive_verbindung_connect_zuerst`, `gradle_build_fehlgeschlagen`) auch im Daemon-/Service-Prozess vollständig zu Text statt zu `@string/...` aufgelöst werden.

---

## 3. Akzeptanzkriterien-Abgleich

| Kriterium | Status | Nachweis |
|---|---|---|
| `:core:resources:assembleDebug` erfolgreich | ✅ Erfüllt | AAPT2 gebaut in 35s |
| `assembleDebug` erfolgreich | ✅ Erfüllt | Vollständiger APK-Build in 2m 2s |
| `testDebugUnitTest` erfolgreich | ✅ Erfüllt | Alle 848 Tasks erfolgreich, `TestRes` verifiziert |
| Keine harten UI-Strings in `feature/` und `app/` | ✅ Erfüllt | `find_hardcoded_strings.py --check` -> 0 Funde |
| `ResInitializer` in `GradleBridgeService` | ✅ Erfüllt | `Res.init(this)` in `onCreate()` |
