# Auftrag: Project Wizard + Template Engine verifizieren

## Kontext
Repo CodeForgeMobile. Lies zuerst `docs/project-wizard-and-template-engine.md`.
Neu aufgebaut (ohne Freemarker): `:libs:template-engine`, `:feature:projectwizard`, `:core:domain` (ProjectTemplate/ProjectRequest/ProjectInputValidator/TemplateEngineRepository), `:core:datastore` (`WizardConfig`).
Die reine JVM-Logik (Generator, Validator) ist mit kotlinc getestet (12 Tests grün); Compose-UI, Hilt/KSP, Proto-Codegen und Gradle-Build wurden NICHT gebaut.

## Aufgaben
1. `./gradlew :core:domain:test :libs:template-engine:testDebugUnitTest :feature:projectwizard:assembleDebug :app:assembleDebug` — Fehler beheben (Proto-Codegen für `WizardConfig`, Compose-Imports, `menuAnchor`/`ExposedDropdownMenu` je nach Material3-Version).
2. **Gradle-9.0.0 vs. Kotlin 2.1.0:** Die erzeugten Projekte verwenden Wrapper 9.0.0, AGP 8.13.0, Kotlin 2.1.0 (`libs/template-engine/src/main/assets/gradle/`). Auf einem erzeugten Beispielprojekt `./gradlew assembleDebug` ausführen. Falls KGP 2.1.0/KSP mit Gradle 9 nicht läuft: Versionen im Asset-Katalog und in `AppBuildGradle.kt`/`RootBuildGradle.kt` konsistent anheben (Kotlin ≥ 2.2) oder Wrapper auf 8.13 zurücksetzen.
3. Alle 9 Vorlagen × Kotlin/Java × KTS/Groovy auf dem Gerät erzeugen und mit dem IDE-Build bauen (Compose nur Kotlin).
4. Der Asset-Katalog `gradle/libs.versions.toml` (227 Zeilen, IDE-intern) wird in JEDES erzeugte Projekt kopiert. Entscheiden: durch einen schlanken, zur Vorlage passenden Katalog ersetzen (Dependency-Updater-Chips zeigen sonst Hunderte irrelevante Einträge).
5. Gerätetest Wizard: Vorlagenwahl → Formular (Auto-Package, Validierung, Compose erzwingt Kotlin) → Erstellen → Editor öffnet, Eintrag in „Zuletzt geöffnet“, Speicherort/Sprache/minSdk/DSL werden beim nächsten Start vorbelegt.
6. Nur melden: SAF-Ordnerauswahl für den Speicherort (aktuell Textfeld), Flutter-Wizard aus dem ZIP (nicht portiert).

## Akzeptanz
Alle Tasks grün; ein erzeugtes Projekt jeder Vorlage baut mit der IDE.
