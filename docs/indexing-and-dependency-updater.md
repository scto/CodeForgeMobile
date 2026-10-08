# Projekt-Indexierung, Dependency-Updater & Editor-Overlays

Stand: neu hinzugefügt. **Nichts davon wurde mit Gradle/Android gebaut** (kein SDK in der Entwicklungsumgebung);
die reine JVM-Logik (Parser, Versionsvergleich, Scanner, Repository-Fluss) ist mit `kotlinc` kompiliert und
per Unit-Tests geprüft (29 Tests Gradle-Module + 5 Farb-Scanner-Tests). Offene Verifikation: `agy-tasks/08-verify-indexing-updater-overlays.md`.

## Module

| Modul | Typ | Inhalt |
|---|---|---|
| `:libs:indexing-api` | JVM | `ProjectIndexer`, `ProjectIndex` (Dateien, Gradle-Module, Kataloge, Repositories), `GradleSourceUtils` |
| `:libs:indexing-impl` | Android-Lib + Hilt | Scanner (ignoriert `.git`, `.gradle`, `build`, …), `settings.gradle(.kts)`-Parser (`include`, `projectDir`, Katalog-`from(files())`), Repository-Parser (Scope PLUGINS/DEPENDENCIES), gecachter `ProjectIndexerImpl` |
| `:libs:dependency-updater-api` | JVM | `DependencyUpdate`, `ProjectUpdateState`, `DependencyUpdateRepository` |
| `:libs:dependency-updater-impl` | Android-Lib + Hilt | TOML-Parser, `build.gradle(.kts)`-Parser (inkl. Variablen/`gradle.properties`), `maven-metadata.xml`-Client, Update-Berechnung, Anwenden, Dismiss-Speicher |
| `:feature:dependencyupdates` | Compose | Dialog **Dismiss / Ask later / Update**, `DependencyUpdatesHost` |
| `:core:navigation` | – | neu: `FileSyncBridge` (Flush ungespeicherter Puffer + Reload nach externer Änderung) |

Namensabweichung: Der Wunsch nannte `dependency-impl`; umgesetzt als `dependency-updater-impl` (symmetrisch zu `-api`).

## Ablauf

1. Jeder Weg ins Projekt (SAF-Import, Wizard, Clone, Wieder-Öffnen) landet in `ProjectWorkspaceRoute` →
   `DependencyUpdatesHost(rootPath)` → `DependencyUpdateRepository.onProjectOpened()` (Hintergrund-Check).
2. **Mit TOML-Katalog:** nur der Katalog wird geprüft (`[versions]`, `[libraries]`, `[plugins]`; String-, Inline-Table-, `version.ref`-Notation).
   **Ohne Katalog:** `build.gradle(.kts)` jedes Moduls aus `settings.gradle(.kts)`, settings-Datei (pluginManagement), weitere `*.gradle(.kts)`;
   String-/Map-Notation, `plugins { id() version }`, `$variable`/`${variable}` aus `val`/`def`/`ext`/`extra`/`gradle.properties`.
3. Repositories kommen aus den Deklarationen in settings/build (`google()`, `mavenCentral()`, `gradlePluginPortal()`, `maven { url }`); Scope PLUGINS vs. DEPENDENCIES; ohne Deklaration Gradle-Defaults. Abfrage: `<repo>/<group>/<name>/maven-metadata.xml`.
4. Versionswahl: nur stabile Versionen von stabilen aus; von Vorabversionen aus mindestens gleicher Reifegrad. Dynamische Versionen (`+`, Ranges) werden nie angefasst.
   Teilen sich mehrere Bibliotheken eine `version.ref`, wird auf die höchste für **alle** verfügbare Version aktualisiert.
5. Dialog: Bibliothek, aktuelle Version, Update-Version, **Dismiss** (dauerhaft, pro Bibliothek + Zielversion), **Ask later**
   (bis zum nächsten Projektöffnen), **Update** (schreibt auf die Platte, atomar je Datei, frisch neu eingelesen + validiert).

## Editor-Overlays (`:feature:editor`, Paket `overlay`)

* **Update-Chip** `4.0.1 -> 4.0.3` rechts neben der Zeile in `*.versions.toml` (aus dem Live-Puffer geparst, 250 ms Debounce);
  Klick → Dialog **Update** / **Update All**. Vor dem Anwenden werden ungespeicherte Puffer gespeichert, danach Tabs neu geladen.
* **Farbkästchen** (exakte Farbe inkl. Alpha, Schachbrett bei Transparenz) hinter Zeilen mit `#RGB/#ARGB/#RRGGBB/#AARRGGBB`
  und Kotlin-`0xAARRGGBB` in xml/json/kt/kts/java/gradle/css/scss/svg/yml/toml/properties (z. B. `colors.xml`, sora-Editor-Schemes).
* Umsetzung als Compose-Layer, weil sora-InlayHints erst ab 0.24 existieren (gepinnt: 0.23.4). Positionen über `getCharOffsetX/Y`, `getRowHeight`
  (gegen den 0.23.4-Quelltext geprüft; `getCharOffsetY` liefert die **Unterkante** der Zeile), Neuberechnung bei `ScrollEvent`/`ContentChangeEvent`.

## Entscheidungen / Folgen

* **Dismiss pro Version:** Erscheint später eine noch neuere Version, wird erneut gefragt. Alternative (Bibliothek komplett ignorieren) wäre eine Zeile in `DismissalStore`/`DependencyUpdate.key`.
* **Nur Katalog bei vorhandenem Katalog:** fest verdrahtete Versionen in Build-Dateien (z. B. in diesem Projekt `sora-editor:…:0.23.4`, `material-icons-extended:1.7.3`) werden dann **nicht** geprüft.
  Umschaltbar: `DependencyScanner.scan(..., includeBuildFilesWithCatalog = true)`.
* **Chips auf Build-Dateien** (ohne Katalog) erscheinen nur, solange die Zeile noch die alte Version enthält (gespeicherte Position, kein Live-Parse).
* Der Editor hatte kein Speichern → ergänzt: Save-Button (`EditorUiEvent.Save`), Voraussetzung für sicheres Update bei offenen Dateien.
* Beim Durchsehen mitgefixt: `subscribeEvent`-Lambda/Import in `SoraCodeEditor`, fehlender `padding`-Import in `EditorScreen`,
  „dirty“-Flag wurde bei programmatischem `setText` (Tab-Wechsel) fälschlich gesetzt.
