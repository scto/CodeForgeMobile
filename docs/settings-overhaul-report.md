# Settings Overhaul Report (Task 47)

Dieser Bericht dokumentiert die Umsetzung, Bestandsaufnahmen, Architekturentscheidungen und Tests zu **Task 47 – Einstellungen überarbeiten: Zurück-Navigation, Editor-Einstellungen, Willkommensbildschirm, Projekteinstellungen, Schlüsselverwaltung**.

---

## 47.1 Zurück-Navigation in den Einstellungen

### Bestandsaufnahme & Befund
- Routen wie `SETTINGS`, `SETTINGS_THEME`, `SETTINGS_EDITOR`, `SETTINGS_TERMINAL`, `SETTINGS_SDK_MANAGER`, `SETTINGS_GIT`, `SETTINGS_PLUGINS` in `app/.../CodeForgeNavHost.kt` riefen ihre Ziel-Screens teilweise ohne `onBack` auf.
- Screens wie `EditorSettingsScreen`, `GitSettingsScreen`, `TerminalSettingsScreen`, `SettingsHubScreen`, `SdkManagerScreen`, `PluginsScreen`, `ThemeBuilderScreen` hatten keine einheitliche NavigationTopBar mit Zurück-Pfeil.
- Dialoge wie der Anmeldedaten-Dialog in `GitSettingsScreen` oder der Color-Picker-Slot in `ThemeBuilderScreen` fingen den System-Zurück-Befehl nicht per `BackHandler` ab.

### Umsetzung
1. **Gemeinsamer Baustein:** `SettingsTopBar` in `:core:ui` (`core/ui/src/main/kotlin/com/codeforge/core/ui/SettingsTopBar.kt`) implementiert.
   - Verwendet `Icons.AutoMirrored.Filled.ArrowBack` für automatische RTL-Spiegelung.
   - `contentDescription` ist über `:core:resources` lokalisiert (`common_zurueck` in Englisch "Back" und Deutsch "Zurück").
   - Unterstützt optionale `actions`-Inhalte und M3 `TopAppBarDefaults.topAppBarColors()`.
2. **Einbindung in alle relevanten Screens:**
   - `:feature:settings`: `SettingsHubScreen`, `EditorSettingsScreen`, `TerminalSettingsScreen`, `GitSettingsScreen`, `ExtensionsScreen`, `SnippetSettingsScreen`
   - `:feature:themebuilder`: `ThemeBuilderScreen` (inkl. `BackHandler` für aktiven Farbpicker-Slot)
   - `:feature:sdkmanager`: `SdkManagerScreen`
   - `:feature:plugins`: `PluginsScreen`
   - `:feature:signing`: `SigningScreen`
3. **Sichere Rücknavigation im NavHost:**
   - In `CodeForgeNavHost.kt` navigiert `navigateBackOrStart()` bei leerem Back-Stack (`navController.popBackStack() == false`) zur `startDestination`, wodurch weiße/leere Bildschirme nach Process Death oder Deep Links verhindert werden.
   - `GitSettingsRoute` führt über `navController.navigateBackOrStart()` direkt zurück zur aufrufenden `GitRoute`.

### Getestet
- Unit-Tests in `feature/settings/src/test/kotlin/com/codeforge/feature/settings/SettingsNavigationTest.kt`:
  - `back navigation invokes onBack callback`: Überprüfung des Callbacks bei Navigation.
  - `navigateBackOrStart falls back to startDestination when popBackStack returns false`: Überprüfung des Fallback-Verhaltens bei leerem Nav-Stack.
  - `dialog back handler closes dialog without popping screen`: Überprüfung, dass Dialoge / Picker zuerst geschlossen werden.
- Gradle-Builds: `:feature:settings:testDebugUnitTest` und `:app:assembleDebug`.

### Nicht getestet
- Manuelle Wischgesten-Navigation und physische Hardware-Tasten auf Google Pixel 9 Pro XL (Android 17).
- Visuelle RTL-Darstellung auf physischem arabischen/hebräischen System-Locale.

### Offene Punkte
- Keine.

---

## 47.2 Editor-Einstellungen vervollständigen und garantieren

### Bestandsaufnahme (sora-editor 0.23.4 & Repo-Snapshot)

| Option | im Proto | in der UI | im ViewModel | in SoraCodeEditor angewendet | Getestet |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tabulatorbreite** (`tab_size`) | Ja (Feld 1) | Ja (IconButton +/-) | Ja | Ja (`editor.tabWidth`) | Ja (`EditorDisplaySettingsWiringTest`, `EditorSettingsViewModelTest`) |
| **Tree-Sitter Syntax-Engine** (`use_tree_sitter`) | Ja (Feld 2) | Ja (Switch) | Ja | Ja (`EditorLanguageFactory.create`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **TextMate Theme** (`textmate_theme`) | Ja (Feld 3) | Ja (ExposedDropdownMenu) | Ja | Ja (`TextMateAssetLoader.setActiveTheme`) | Ja (`TextMateThemeCatalogTest`) |
| **LSP Server Pfad** (`lsp_server_path`) | Ja (Feld 4) | Nein (entfernt aus UI; Pfad wird per Extension/Katalog verwaltet) | Ja | Intern via ExtensionsManager | Ja (Abwärtskompatibilität im VM-Test) |
| **Schriftgröße** (`font_size`) | Ja (Feld 5) | Ja (Slider 8–32sp + Live-Code-Vorschau) | Ja | Ja (`editor.setTextSize`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Schriftart** (`font_family`) | Ja (Feld 6) | Ja (Dropdown: System Monospace, JetBrains Mono) | Ja | Ja (`editor.typefaceText`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Wortumbruch** (`word_wrap`) | Ja (Feld 7) | Ja (Switch) | Ja | Ja (`editor.isWordwrap`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Nicht druckbare Zeichen** (`show_non_printable_chars`) | Ja (Feld 8) | Ja (Switch) | Ja | Ja (`editor.nonPrintablePaintingFlags`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Sticky Scroll** (`sticky_scroll_enabled`) | Ja (Feld 9) | Ja (Switch) | Ja | Ja (`EditorStickyScroll.isEnabled`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Text-Lupe (Magnifier)** (`magnifier_enabled`) | Ja (Feld 10) | Ja (Switch) | Ja | Ja (`Magnifier.isEnabled`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Symbolpaare schließen** (`symbol_pair_autocomplete_enabled`) | Ja (Feld 11) | Ja (Switch) | Ja | **Ja** (`props.symbolPairAutoCompletion`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Minimap** (`minimap_enabled`) | Ja (Feld 12) | Ja (Switch) | Ja | Ja (`EditorMinimap.isEnabled`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Rainbow Brackets** (`rainbow_brackets_enabled`) | Ja (Feld 13) | Ja (Switch) | Ja | Ja (`isHighlightBracketPair`, `BracketPairs.isEnabled`, `boldMatchingDelimiters`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Pinch to Zoom** (`pinch_to_zoom_enabled`) | Ja (Feld 14) | Ja (Switch) | Ja | Ja (`setScaleTextEnabled`, `setScaleTextSizes`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Desktop-Tastaturkürzel** (`desktop_shortcuts_enabled`) | Ja (Feld 15) | Ja (Switch) | Ja | Ja (`props.navigationMode`) | Ja (`EditorDisplaySettingsWiringTest`) |
| **Format beim Speichern** (Kotlin, Java, XML, JSON) | Ja (Felder 16-19) | Ja (4 Switche) | Ja | Ja (`EditorViewModelFormatTest`) | Ja (`EditorSettingsViewModelTest`) |
| **LSP Max Sitzungen & Timeout** | Ja (Felder 20-21) | Ja (Zähler +/-) | Ja | Ja (`LspSessionController`) | Ja (`EditorSettingsViewModelTest`) |
| **Dateikopf für neue Dateien** | Ja (Feld 6 in `AppSettings`: `FileHeaderConfig`) | Ja (Schalter, Autor, Live-Vorschau) | Ja | Ja (`FileTreeViewModel.confirmAction` via `FileHeaderRenderer`) | Ja (`FileHeaderRendererTest`, `FileTreeViewModelTest`) |
| **Inlay-Hints** | Nein (in LSP nicht angebunden) | Nein | Nein | sora-editor 0.23.4 besitzt bereits `InlayHint.class`, aber `LspClientRepository` implementiert `textDocument/inlayHint` nicht. | Nicht verfügbar (als compose overlay möglich) |
| **Hover** | Nein | Nein | Nein | `LspClientRepository.requestHover` existiert, aber Editor hat kein Tooltip-Hover-Overlay dafür. | Nicht verfügbar in UI |
| **Zeilennummern fixieren (Pin)** | Nein | Nein | Nein | In sora-editor vorhanden (`editor.setPinLineNumber(boolean)`). | Nicht in UI exponiert |
| **ICU Textsegmentierung** | Nein | Nein | Nein | In sora-editor vorhanden (`editor.props.useICULibToSelectWords`). | Nicht in UI exponiert |

### Umsetzung
1. **Einheitliche Anwendung der Einstellungen:**
   - Erstellung von `applyDisplaySettings(editor: CodeEditor, settings: EditorDisplaySettings)` in `SoraCodeEditor.kt`.
   - Sowohl die Erzeugung (`factory`) als auch jede Recomposition (`update`) rufen ausschließlich diese Methode auf.
   - `symbolPairAutocompleteEnabled` wird nun direkt auf `editor.props.symbolPairAutoCompletion` angewendet.
2. **TextMate-Theme-Katalog & Dropdown:**
   - Erstellung von `TextMateThemeCatalog` in `:core:designsystem`.
   - Erkennt gebündelte Themes (`dark_plus`, `light_plus`) und scannt installierte Themes aus `filesDir/extensions/themes`.
   - Statt manueller Pfadeingabe (`OutlinedTextField`) nutzt `EditorSettingsScreen` eine `ExposedDropdownMenu`-Komponente.
   - Option „Automatisch (folgt dem App-Design)“ löst je nach `ThemeMode` und System-Dark-Status zuverlässig auf. Unbekannte Werte fallen sicher auf "Automatisch" zurück.
3. **Schriftgröße & Schriftart:**
   - Schriftgröße wird über einen Slider (8–32 sp) gesteuert mit interaktiver Live-Code-Vorschau.
   - Schriftart wird über eine Auswahlliste (System Monospace, JetBrains Mono) gewählt.
4. **Abschnitt-Gruppierung & Reset:**
   - Strukturierung in Abschnitte: *Darstellung*, *Eingabe*, *Sprachfunktionen*, *Format beim Speichern*, *Snippets* und *Dateikopf*.
   - Jeder Abschnitt verfügt über einen „Auf Standard zurücksetzen“-Button.
5. **Dateikopf für neue Dateien:**
   - `FileHeaderRenderer` in `:core:common` unterstützt Standardvorlagen für Kotlin, Java, XML, Shell, YAML, TOML, KTS, C, C++ und ignoriert JSON (da dort Kommentare unzulässig sind).
   - Sonderbehandlung: XML-Kopf nach `<?xml...?>`, Shell-Kopf nach `#!/...`, Kotlin/Java vor `package`.
   - Neue Proto-Nachricht `FileHeaderConfig` in `settings.proto` (Feld 6 in `AppSettings`).
   - Automatische Anwendung in `FileTreeViewModel` beim Anlegen neuer Dateien.

### Getestet
- `EditorSettingsViewModelTest`: Speichern und Auslesen aller Optionen, Reset der Abschnitte, Abwärtskompatibilität bei leerem Proto.
- `EditorDisplaySettingsWiringTest`: Vollständige Überprüfung, dass jedes Feld von `EditorDisplaySettings` dokumentiert und im Editor gemappt ist.
- `TextMateThemeCatalogTest`: Gebündelte Themes, installierte Themes aus Ordner, Fallback auf "auto", Auflösung nach ThemeMode.
- `FileHeaderRendererTest`: Template-Generierung für alle Sprachen, Platzhalterersetzung, Zeilenentfernung bei fehlenden Werten, XML-/Shell-Positionierung.
- Gradle-Build: `:feature:settings:test`, `:feature:editor:test`, `:core:common:test`, `:core:designsystem:test`.

### Nicht getestet
- Manuelle Wischgesten für Pinch-to-Zoom auf physischem Multitouch-Display.
- Installation von externen `.ttf`-Schriftdateien über den Storage Access Framework (SAF) Systemdialog auf einem realen Gerät.

### Offene Punkte
- Keine.

---

## 47.3 Willkommensbildschirm überarbeiten

### Bestandsaufnahme & Vorlage
- Der frühere Build (Commit `2aa0cc6` aus Task 24) führte das originale hochauflösende CodeForge-Icon ein. Die adaptive Zwei-Spalten-Architektur (`NavigableListDetailPaneScaffold`) war funktional vorhanden, das UI wirkte jedoch karg.

### Umsetzung
1. **Kopfbereich:**
   - Zentriertes CodeForge-Logo (`DesignSystemR.drawable.icon`), Titel `CodeForge Mobile`, Versionschip `v1.0.0` und Untertitel.
2. **Hauptaktionen:**
   - Prominente, große Aktionskacheln in einem responsiven Raster für die 3 Primäraktionen: *Neues Projekt*, *Importieren*, *Klonen*.
   - Getrennte Sekundärleiste für *Terminal* und *Einstellungen*.
3. **Zuletzt geöffnete Projekte:**
   - Jede Karte zeigt ein farbiges Projekt-Initialen-Badge (z.B. "CF" für "Code-Forge").
   - Projektname, gekürzter Pfad, Modulanzahl-Chip (`Module: X`) und relative Zeitangabe („Gerade eben“, „vor 15 Min.“, „vor 2 Std.“, „vor 3 Tagen“).
   - 3-Punkte-Kontextmenü auf jeder Projektkarte mit Schnellzugriff: *Öffnen*, *Projekteinstellungen* und *Aus Liste entfernen*.
4. **Leerzustand (`EmptyState`):**
   - Ist die Projektliste leer, erscheint der M3-`EmptyState` mit Erläuterungstext und primärem Call-to-Action-Button zur Projekterstellung.
5. **Adaptive Detail-Ansicht:**
   - Auf Tablets und Foldables zeigt die rechte Spalte Projekt-Metadaten und Direktschaltflächen für Öffnen, Projekteinstellungen und Entfernen.
   - Anbindung an die NavHost-Route `Routes.projectSettings(projectPath)`.
6. **Previews:**
   - Previews für kompakte Ansicht (Hell), leere Ansicht (Dunkel) und adaptive Layouts.

### Getestet
- `WelcomeViewModelTest`: Aktionen (Erstellen, Importieren, Klonen, Terminal, Einstellungen), Öffnen von Projekten, Öffnen von Projekteinstellungen, Entfernen von Projekten, `formatRelativeTime` und `getProjectInitials`.
- Gradle-Build: `:feature:welcome:test`.

### Nicht getestet
- Visuelle Begutachtung des Renderings auf dem physischen Display des Google Pixel 9 Pro XL (Android 17).
- Adaptive Falt-Animationen beim Aufklappen eines Foldables während der Laufzeit.

### Offene Punkte
- Finale visuelle Bestätigung der Optik durch den Nutzer auf dem physischen Gerät.

---

## 47.4 Projekteinstellungen (`:feature:projectsettings`)

### Bestandsaufnahme & Architektur
- Bisher gab es im Projekt keinen Ort für projektspezifische Build-Einstellungen.
- Signierung wurde bisher global konfiguriert und wirkte auf alle Builds gleichermaßen.
- Nicht-geheime Build-Einstellungen gehören ins Projekt (`.codeforge/project-settings.json`), damit sie versionskontrolliert werden können.
- Geheimnisse (Keystore-Passwörter) dürfen unter keinen Umständen in der Projektdatei gespeichert werden.

### Umsetzung
1. **Domänenmodell & Schnittstelle:**
   - `ProjectBuildSettings` in `:core:domain` mit Feldern: `isMinifyEnabled`, `isShrinkResources`, `isCrunchPngs`, `isDebuggable`, `customProguardRulesPath`, `targetModuleName`.
   - `ProjectSettingsRepository` in `:core:domain`.
2. **Implementierung in `:core:data`:**
   - `ProjectSettingsRepositoryImpl` persistiert Einstellungen nach `<project>/.codeforge/project-settings.json`.
   - Pure-Kotlin JSON-Serialisierung und -Parsing (verhindert Runtime-Exceptions auf der JVM ohne gemockte Android-Framework-Klassen).
   - `generateInitScript(settings)` erzeugt ein standardkonformes Gradle-Init-Script (`--init-script`), das Build-Typ-Einstellungen setzt, ohne `build.gradle(.kts)` des Nutzers zu verändern.
   - `validateSettings(settings)` prüft Konsistenz (z.B. ShrinkResources erfordert MinifyEnabled).
3. **Feature-Modul `:feature:projectsettings`:**
   - Neues Modul in `settings.gradle.kts` und Version Catalog registriert.
   - MVI-Architektur mit `ProjectSettingsContract`, `ProjectSettingsViewModel`, `ProjectSettingsScreen`.
   - Umschalter zwischen *Release* und *Debug*.
   - Intelligente UI-Abhängigkeiten: Ressourcenverkleinerung wird deaktiviert und mit Hinweis versehen, wenn Code-Verkleinerung ausgeschaltet ist.
   - Direkte Verknüpfung zur Schlüsselverwaltung (`Routes.SETTINGS_SIGNING`).
   - Registrierung von `Routes.PROJECT_SETTINGS_PATTERN` in `CodeForgeNavHost.kt`.

### Getestet
- `ProjectSettingsRepositoryTest`: Speichern, Laden, Standardwerte, fehlerhafte JSON-Dateien, Validierungsregeln, Generierung des Gradle-Init-Scripts (inkl. Pfad-Escaping).
- `ProjectSettingsViewModelTest`: Laden der Einstellungen, Ändern von Switches, Erzwingen der ShrinkResources/Minify-Abhängigkeit.
- Gradle-Builds: `:core:data:test`, `:feature:projectsettings:test`.

### Nicht getestet
- Ausführung eines vollständigen Gradle-Builds auf dem Android-Gerät mit dem generierten Init-Script über AGP.

### Offene Punkte
- Keine.

---

## 47.5 Schlüsselverwaltung: Zuweisung zu Projekten

### Bestandsaufnahme & Befund
- `SigningScreen` bot Erstellung, Import, Export und globale Build-Signing-Aktivierung (`activeBuildSigning`).
- Es gab keine Möglichkeit, einen bestimmten Keystore und Alias einem konkreten Projekt zuzuweisen.
- Keystores und Aliase konnten gelöscht werden, ohne dass davor gewarnt wurde, welche Projekte diesen Schlüssel verwenden.

### Umsetzung
1. **Zuweisungsmodell:**
   - `ProjectSigningAssignment(projectPath, keystoreId, alias)` in `:core:domain`.
   - Schnittstelle im `SigningRepository`: `assignProject`, `unassignProject`, `getAssignmentsForKeystore`, `getAssignmentForProject`, `getAllAssignments`.
2. **Implementierung in `SigningRepositoryImpl`:**
   - Speicherung IDE-intern im privaten App-Speicher (`filesDir/keystores/project-signing-assignments.json`).
   - Keine Passwörter im Modell oder der Datei. Passwörter verbleiben im geschützten Speicher.
   - Pure-Kotlin-Serialisierung via `ProjectSigningAssignmentJson`.
3. **Auflösungsreihenfolge beim Bauen:**
   - 1. Projektspezifische Zuweisung (`ProjectSigningAssignment`)
   - 2. Globale Build-Signing-Aktivierung (Standardschlüssel)
   - 3. Kein Signing
   - Passwörter werden nie in Logs ausgegeben.
4. **UI-Erweiterungen in `SigningScreen`:**
   - Neuer Abschnitt „Verwendet in Projekten“ in den Keystore-Details.
   - Dialog „Projekt zuweisen“ mit Auswahl aus den zuletzt geöffneten Projekten (`RecentProject`) und Alias-Auswahl.
   - Löschwarnung: Beim Löschen eines Schlüssels werden alle betroffenen Projekte aufgeführt. Nach Bestätigung werden die Zuweisungen kaskadierend bereinigt.

### Getestet
- `SigningViewModelTest`: Anlegen, Abfragen und Entfernen von Projektzuweisungen, Warnung und Bereinigung beim Löschen von Keystores.
- Keine Passwörter in Log-Ausgaben verifiziert.
- Gradle-Build: `:feature:signing:test`.

### Nicht getestet
- SAF File-Picker-Dialoge (`OpenDocument`, `CreateDocument`) auf einem echten physischen Pixel 9 Pro XL.

### Offene Punkte
- Keine.
