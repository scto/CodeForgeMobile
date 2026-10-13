# Verifikationsbericht: Task 14 – Adaptive Layouts, Edge-to-Edge & Expressive

Datum: 2026-10-13  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, Build-Tools 37.0.0 (aapt2 36.0.2 Override), Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 14 (`agy-tasks/14-verify-adaptive-expressive.md`) war die vollständige Überprüfung und Verifikation von:
1. **Toolchain- und Bibliotheksversionen** in `gradle/libs.versions.toml`:
   - Kotlin 2.1.21, KSP 2.1.21-2.0.1, Hilt 2.56.2
   - Compose BOM 2025.09.00 (löst Compose Material3 1.3.2 auf)
   - Material3 Adaptive 1.1.0 (`adaptive`, `adaptive-layout`, `adaptive-navigation`)
   - Bereinigung ungenutzter Legacy-Einträge (`composeCompiler = "1.5.15"`).
2. **Material 3 Expressive & Theme-Verhalten**:
   - Prüfung von `MaterialExpressiveTheme` / `MotionScheme.expressive()`. Da Compose BOM 2025.09.00 auf Material3 1.3.2 basiert (in welchem Expressive APIs noch im Alpha-Stadium vorliegen), nutzt `CodeForgeTheme.kt` sauber das stabile `MaterialTheme` mit dynamischer und benutzerdefinierter Farbpalette.
3. **Adaptive 1.1.0 in `WelcomeScreen`**:
   - Korrekte Paket-Imports (`androidx.compose.material3.adaptive.layout.AnimatedPane`, `ListDetailPaneScaffoldRole`, `androidx.compose.material3.adaptive.navigation.*`).
   - Verifikation von `navigator.navigateTo` als Suspend-Funktion innerhalb einer Coroutine-Scope.
4. **Adaptive Breakpoints & Edge-to-Edge**:
   - Breakpoint-Logik in `WindowSizeUtils.kt` (Compact < 600dp, Medium < 840dp, Expanded >= 840dp).
   - Permanente Navigation im Workspace (`ProjectWorkspaceRoute.kt`) ab 840dp (Permanent Drawer in `Row` ohne Menü-Icon), modale Navigation auf Phones/Compact.
   - Vermeidung von doppeltem Padding durch `consumeWindowInsets(padding)` und Tastaturunterstützung mittels `imePadding()`.
   - Inset-Handling im `WorkspaceDrawer` (`safeDrawing.only(Top + Bottom + Start)`).
   - Display-Cutout-Unterstützung (`shortEdges` ab API 28) und Deaktivierung von Kontrast-Erzwingung (`enforceNavigationBarContrast = false` ab API 29).
5. **Dynamische Systemleisten (Dark/Light)**:
   - Synchronisation der Status- und Navigationsleisten-Icons in `MainActivity.kt` mit dem App-Theme (`settings.theme.resolveIsDark`) via `SystemBarStyle.auto(...) { isDark }`.
6. **AutoMirrored-Icons-Migration**:
   - Ersetzung aller veralteten Compose-Icon-Aufrufe (`Icons.Filled.ArrowBack`, `Undo`, `Redo`, `Send`, `CallSplit`, `FormatAlignLeft`, `Comment`, `FormatIndentIncrease`, `FormatIndentDecrease`) durch `Icons.AutoMirrored.Filled.*`.

---

## 2. Durchgeführte Prüfungen & Ergebnisse

### Schritt 1: Versionen & Bereinigung (`gradle/libs.versions.toml`)
* **Compose BOM:** `androidx.compose:compose-bom:2025.09.00` ist auf Google Maven verfügbar und löst `material3:1.3.2` auf.
* **Material3 Adaptive:** `androidx.compose.material3.adaptive:adaptive*:1.1.0` wird sauber aufgelöst.
* **Legacy-Bereinigung:** `composeCompiler = "1.5.15"` wurde entfernt, da der Compose-Compiler seit Kotlin 2.0+ als Gradle-Plugin (`org.jetbrains.kotlin.plugin.compose`) verwaltet wird.

### Schritt 2: WelcomeScreen Adaptive 1.1.0
* Datei: `feature/welcome/src/main/kotlin/com/codeforge/feature/welcome/WelcomeScreen.kt`
* Verwendet:
  * `NavigableListDetailPaneScaffold`
  * `AnimatedPane`
  * `rememberListDetailPaneScaffoldNavigator<String>()`
  * `scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, project.id) }`
* Ergebnis: Kompiliert fehlerfrei mit korrekter Suspend-Ausführung.

### Schritt 3: Edge-to-Edge & Inset-Architektur
* **MainActivity:**
  * `enableEdgeToEdge()` vor `super.onCreate()`.
  * `LaunchedEffect(isDark)` aktualisiert Statusleiste und Navigationsleiste dynamisch bei Theme-Wechsel.
* **Layout & Insets:**
  * `ProjectWorkspaceRoute`: Bei `rememberIsExpandedWidth() == true` wird `WorkspaceDrawer(permanent = true)` in einer horizontalen `Row` gerendert; andernfalls `ModalNavigationDrawer`.
  * Insets werden mit `consumeWindowInsets(padding)` konsumiert, sodass verschachtelte Scaffolds im Editor nicht doppelt padden.
  * `imePadding()` garantiert die Sichtbarkeit des Cursors bei geöffneter Bildschirmtastatur.
* **Themes XML:**
  * `res/values/themes.xml`: Transparente Systemleisten.
  * `res/values-v28/themes.xml`: `android:windowLayoutInDisplayCutoutMode = shortEdges`.
  * `res/values-v29/themes.xml`: `android:enforceNavigationBarContrast = false` & `android:enforceStatusBarContrast = false`.

### Schritt 4: Icon-Modernisierung (AutoMirrored)
Folgende Icons wurden zur Vermeidung von Deprecation-Warnungen auf moderne RTL-fähige Compose-Pendants umgestellt:
- `ProjectImportRoute.kt`: `Icons.AutoMirrored.Filled.ArrowBack`
- `EditorScreen.kt`: `Icons.AutoMirrored.Filled.Undo`, `Icons.AutoMirrored.Filled.Redo`, `Icons.AutoMirrored.Filled.FormatAlignLeft`, `Icons.AutoMirrored.Filled.Comment`, `Icons.AutoMirrored.Filled.FormatIndentIncrease`, `Icons.AutoMirrored.Filled.FormatIndentDecrease`
- `ChangesTab.kt`: `Icons.AutoMirrored.Filled.Undo`
- `CommitViews.kt`: `Icons.AutoMirrored.Filled.Undo`, `Icons.AutoMirrored.Filled.CallSplit`
- `DiffViewer.kt`: `Icons.AutoMirrored.Filled.Undo`
- `TerminalScreen.kt`: `Icons.AutoMirrored.Filled.Send`

### Schritt 5: Build- und Test-Validierung
- **Unit-Tests:** `./gradlew testDebugUnitTest`
  - **Ergebnis:** `BUILD SUCCESSFUL` (849 actionable tasks, alle Tests grün).
- **String-Check:** `python3 scripts/find_hardcoded_strings.py --check`
  - **Ergebnis:** 0 Funde (100% sauber externalisiert).
- **Assemble Debug:** `./gradlew :app:assembleDebug`
  - **Ergebnis:** `BUILD SUCCESSFUL` (`app-debug.apk` gebaut).

---

## 3. Fazit

Task 14 ist vollständig erfüllt. Alle Adaptiv-, Edge-to-Edge- und Compose-Material3-Komponenten entsprechen den Richtlinien, binden moderne Bibliotheksversionen ein und bauen fehler- und warnungsfrei.
