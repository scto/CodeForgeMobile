# Auftrag: Indexierung, Dependency-Updater und Editor-Overlays verifizieren

## Kontext
Repo CodeForgeMobile (Projekt-Root als Arbeitsverzeichnis). Lies zuerst `docs/indexing-and-dependency-updater.md`.
Neue Module: `:libs:indexing-api/-impl`, `:libs:dependency-updater-api/-impl`, `:feature:dependencyupdates`;
geändert: `:core:navigation` (FileSyncBridge), `:feature:editor` (Overlay, Save, Update-Chips), `:app` (Wiring), `settings.gradle.kts`, `gradle/libs.versions.toml` (`junit`).
Die Logik wurde ohne Gradle/Android-SDK geschrieben; die reine JVM-Logik ist mit kotlinc getestet, der Rest NICHT gebaut.

## Aufgaben
1. `./gradlew :libs:indexing-api:test :libs:indexing-impl:testDebugUnitTest :libs:dependency-updater-api:build :libs:dependency-updater-impl:testDebugUnitTest :feature:editor:testDebugUnitTest` — Fehler beheben (Hilt/KSP, Imports, Plugin-Aliase; `kotlin.jvm` für die -api-Module).
2. `./gradlew :feature:dependencyupdates:assembleDebug :feature:editor:assembleDebug :app:assembleDebug` — Compile-Fehler beheben. Bekannte Risikostellen: `EditorOverlay.kt`, `SoraCodeEditor.kt` (Box/AndroidView/Overlay), `EditorViewModel.kt`, `DependencyUpdateDialog.kt`.
3. sora-Overlay prüfen (Gerät/Emulator): Farbkästchen stehen hinter dem Zeilenende (`colors.xml`), folgen Scroll, Zoom (Schriftgröße), Word-Wrap und Tab-Wechsel; kein Versatz um die Höhe einer Zeile (`getCharOffsetY` = Unterkante, im Code wird `rowHeight` abgezogen).
4. Update-Chips in `gradle/libs.versions.toml`: Chip erscheint, Klick → Dialog (Update / Update All), Datei wird geändert, Tab lädt neu; ungespeicherte Änderung wird vorher gespeichert.
5. Projekt öffnen (alle Wege) → Dialog Dismiss / Ask later / Update; Dismiss bleibt nach App-Neustart, Ask later fragt beim nächsten Öffnen erneut.
6. Prüfen, ob die TreeSitter-/TextMate-Erkennung für `.toml` im Editor (aktuell `EditorLanguageType.PLAIN`) sinnvoll erweitert werden soll — nur melden, nicht ändern.

## Akzeptanz
Alle Build-/Testtasks grün; keine Änderung an der Modul-Dependency-Regel (Features hängen nur von `-api`/`:core:*`, nie voneinander).
