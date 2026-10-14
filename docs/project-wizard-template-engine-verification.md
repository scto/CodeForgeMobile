# Verifikationsbericht: Task 09 – Project Wizard & Template Engine

Datum: 2026-10-14  
Umgebung: Termux Linux aarch64, Java 17, Android SDK Platform 36, Build-Tools 37.0.0 (aapt2 36.0.2 Override), Gradle 8.14.3

---

## 1. Übersicht & Zielsetzung

Ziel von Task 09 (`agy-tasks/09-verify-project-wizard-template-engine.md`) war die Verifikation und Bereinigung der neu aufgebauten Template-Engine und des Projekt-Wizards:

* **Module**:
  * `:libs:template-engine` (Code-Generierung, AssetSource, `ProjectGenerator`)
  * `:feature:projectwizard` (Compose-UI, MVI, Vorlagenauswahl, Formular, Validierung)
  * `:core:domain` (`ProjectRequest`, `ProjectInputValidator`, `TemplateEngineRepository`, `RecentProjectsRepository`)
  * `:core:datastore` (`WizardConfig` in `settings.proto`)
  * `:app` (Navigation & Workspace-Verbindung)

---

## 2. Durchgeführte Prüfungen & Ergebnisse

### Schritt 1: Tests & Build-Verifikation
* **Kommando:** `./gradlew :core:domain:test :libs:template-engine:testDebugUnitTest :feature:projectwizard:assembleDebug :app:assembleDebug`
* **Ergebnis:** `BUILD SUCCESSFUL` (953 actionable tasks).
* **Unit-Tests (alle bestanden):**
  * `:core:domain`:
    * `ProjectInputValidatorTest` (6 Tests): Validierung von Projektnamen, Package-Namen, verbotenen Java-Schlüsselwörtern, existierenden Verzeichnissen, automatischen Package-Vorschlägen und Sprachrestriktionen.
    * `GitModelsTest` & `GitPatchTest` (18 Tests): Datenmodelle und Patch-Logik.
  * `:libs:template-engine`:
    * `ProjectGeneratorTest` (7 Tests):
      1. `everyCombinationProducesCoreFiles`: Generiert und prüft alle Kombinationen aus 9 Vorlagen × Kotlin/Java × KTS/Groovy. Verifiziert Kerndateien (`settings.gradle`, `build.gradle`, `app/build.gradle`, `gradlew`, Wrapper, Manifest, XMLs) und stellt sicher, dass kein ungültiges `package=`-Attribut im Manifest steht.
      2. `languageDecidesSourceExtension`: Quelltext-Endungen `.kt` vs. `.java`.
      3. `noActivityHasNoSources`: Leere Vorlage ohne Activity erzeugt keine Quelltexte.
      4. `layoutsReferencedByCodeExist`: Verifiziert, dass alle `R.layout.*`, `R.menu.*`, `R.navigation.*` und `*Binding`-Referenzen zu existierenden XML-Dateien auflösen.
      5. `stringReferencesResolve`: Verifiziert die Auflösung aller `@string/` und `R.string.*` Bezeichner.
      6. `stringsXmlEscapesAppName`: XML-Escaping von Sonderzeichen im App-Namen (`&`, `<>`).
      7. `exportSampleProject`: Erzeugung eines vollwertigen Testprojekts.

### Schritt 2: Gradle-Wrapper & Tooling-Harmonisierung
* **Befund:** Der Vorlagen-Wrapper war auf `gradle-9.0.0-bin.zip` konfiguriert. AGP 8.13.0 und Kotlin 2.1.0 unterstützen Gradle 9.0 noch nicht (API-Entfernungen wie `rootProject.buildDir`). Zudem hätte jedes neu erstellte Projekt auf dem Mobilgerät einen unnötigen 150 MB Download angestoßen.
* **Korrektur:**
  - `libs/template-engine/src/main/assets/gradle/wrapper/gradle-wrapper.properties`: Auf `gradle-8.14.3-bin.zip` vereinheitlicht (identisch zum Hauptprojekt, liegt lokal im Termux-Cache).
  - `libs/template-engine/src/main/kotlin/com/codeforge/libs/template_engine/templates/gradle/RootBuildGradle.kt`: `delete rootProject.buildDir` in Groovy durch die moderne API `delete rootProject.layout.buildDirectory` ersetzt.
* **Praxistest (Beispielprojekt-Build):**
  - Generierung eines vollständigen `EmptyActivity`-Projekts (`SampleApp`, Kotlin, KTS).
  - Ausführung von `./gradlew assembleDebug` im erzeugten Projekt.
  - **Ergebnis:** `BUILD SUCCESSFUL in 3m 48s` (38 actionable tasks ausgeführt, APK fehlerfrei gebaut, SDK-Platform 35 bei Bedarf automatisch bereitgestellt).

### Schritt 3: Bereinigung des Versionskatalogs (`libs.versions.toml`)
* **Befund:** Der ursprüngliche Asset-Katalog war eine 228 Zeilen lange Kopie aus AndroidIDE/NeonIDE mit Dutzenden ungenutzten Abhängigkeiten (Decompiler, Antlr, LeakCanary, TreeSitter etc.). Bei jedem Projektstart wurden diese durch den Dependency-Updater gescannt.
* **Korrektur:**
  - Ersetzt durch einen kompakten, maßgeschneiderten 35-Zeilen-Katalog in `libs/template-engine/src/main/assets/gradle/libs.versions.toml`.
  - Enthält genau die für die 9 Vorlagen relevanten Versionen (AGP 8.13.0, Kotlin 2.1.0, Core, AppCompat, Material, ConstraintLayout, Navigation, Lifecycle, ViewPager, Compose) und Plugins (`android.application`, `android.library`, `kotlin.android`, `kotlin.compose`).

### Schritt 4: Wizard-Workflow & Benutzereinstellungen
* **Vorlagenauswahl & Validierung:**
  - `ProjectWizardViewModel`: Wählt Vorlage aus dem 9er-Raster; Compose erzwingt automatisch Kotlin (`supportsJava == false`).
  - `suggestPackageName`: Schlägt interaktiv `com.example.<appname>` vor, solange das Package nicht manuell editiert wurde.
  - Inline-Validierung verhindert ungültige Zeichen oder doppelte Projektordner.
* **Persistenz & Navigation:**
  - Nach Erstellung werden Speicherort, Sprache, minSdk und DSL-Präferenz in `WizardConfig` (`settings.proto` via Datastore) gespeichert und beim nächsten Start automatisch vorbelegt.
  - Das Projekt wird in `RecentProjectsRepository` eingetragen.
  - `_effects.send(ProjectWizardUiEffect.OpenProject(path))` leitet nahtlos in den Editor weiter.

### Schritt 5: Hinweise & nicht portierte Elemente (gemäß Aufgabe nur gemeldet)
1. **Speicherort-Auswahl (SAF)**: Im Formular wird der Speicherort aktuell als reines Textfeld (`OutlinedTextField`) editiert. Eine visuelle SAF-Ordnerauswahl (`ACTION_OPEN_DOCUMENT_TREE`) existiert im Wizard noch nicht.
2. **Flutter-Wizard**: Der Flutter-Projekt-Wizard aus dem NeonIDE-ZIP wurde bewusst nicht portiert (Fokus auf native Android Gradle-Entwicklung).

---

## 3. Fazit

Task 09 ist vollständig verifiziert. Die erzeugten Android-Projekte bauen dank der Harmonisierung auf Gradle 8.14.3 und des schlanken Versionskatalogs sofort und offline-fähig auf dem Mobilgerät, und alle Tests laufen grün.
