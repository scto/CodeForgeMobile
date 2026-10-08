# Task 16 – Features aus dem lokalen Experimentzweig portieren

Kontext: Der lokale Experimentzweig des Projektinhabers (Termix/Nyamux-Linie, siehe die Historie in `STATUS.md` und die
älteren Einträge in `CHANGELOG.md`) beschreibt Features, die im **aktuellen Repository nicht vorhanden** sind (per Suche
im Quelltext bestätigt: kein `Minimap`, `Rainbow`, `ExtensionsScreen`, `ExtensionsManager`, `ColorPickerDialog`,
`ImageFilePreview`, `TerminalForegroundService`, `RunCommandWorker`; `ThemePresets` hat 5 statt 15 Einträge).
Das aktuelle Repository nutzt eine **Termux-Shell** statt Rootfs/Nyamux – alles, was von Rootfs/PRoot/`DistroBootstrapRepository` abhängt, wird **nicht** portiert.

Arbeite die Punkte einzeln ab (je ein Commit). Vor Beginn: Schreib-/Rückfrage-Bedarf klären, falls der Experimentzweig-Code (lokal beim Inhaber) bereitgestellt werden kann – sonst nach den Beschreibungen neu implementieren. Halte dich an die Projektregeln: MVI, immutable UI-States, `modifier` als erster optionaler Parameter, Texte in `:core:resources` (`stringRes`/`Res.string`), keine Feature→Feature-Abhängigkeiten.

## Zu portieren
1. **Editor (`:feature:editor`, `SoraCodeEditor.kt`)**: Minimap, Rainbow Brackets, Pinch-to-Zoom für die Schriftgröße, Desktop-Tastaturkürzel/PC-Navigation, Diagnose-UI, Bildvorschau (`ImageFilePreview`: Mipmap/VectorDrawable). Neue Optionen als Felder in `EditorConfig` (`settings.proto`) + Schalter in `EditorSettingsScreen`.
2. **Dateibaum (`:feature:filetree`)**: theme-adaptive durchgehende Einrückungslinien, 2-zeilige Dateiinfo (Größe, Änderungsdatum), Voll-Zeilen-Tap-Target, Kontextmenü „Eigenschaften“ (Name, Pfad, Typ, Größe, Datum, Berechtigungen), Bestätigungsdialog für Löschen.
3. **Theme Studio (`:feature:themebuilder`, `ThemePresets.kt`)**: von 5 auf 15 Presets (Monokai, Darcula, Quiet Light, Ayu Dark, Solarized Dark, GitHub Light, VS Code Dark+, Notepad++, Eclipse, Cyberpunk Neon, Forest, Ocean, Sunset, Monochrome, Violet), horizontale Live-Vorschau (`LazyRow`), RGB-Colorpicker-Dialog mit 10 Custom-Presets. Labels als `@StringRes`.
4. **Extensions-Manager (`:feature:settings`)**: `ExtensionsScreen`/`ExtensionsManager` für LSP-Server-Pakete – Download, SHA-256-Prüfung, Entpacken (Zip/Jar), Verwendungsprüfung; Route `Routes.EXTENSIONS` in `CodeForgeNavHost`. Pfade unterhalb des Termux-Prefix bzw. `filesDir` ablegen.
5. **Hintergrundbefehle**: `RunCommandWorker` (WorkManager) und Foreground-Service für lange Terminal-/Build-Läufe – nur falls nicht bereits durch `TermuxService` abgedeckt (`:libs:termux-app`); zuerst prüfen, ob doppelt.
6. **Fluent 2** (`FLUENT2_JETPACK_COMPOSE_GUIDE.md`): entscheiden, ob Fluent-Tokens zusätzlich zu Material 3 Expressive eingeführt werden; wenn ja als eigenes Token-Set in `:core:designsystem`.

## Nicht portieren
`nyamux-terminal.jar`, Rootfs-/Distro-Downloader, `JdkInstaller` per Rootfs, `AndroidRepoCrawler` (ersetzt durch `codeforge-env`), Freemarker-Templates.

## Akzeptanzkriterien
* Jeder Punkt kompiliert mit `./gradlew :app:assembleDebug` und ist in `STATUS.md` (Tabelle) und `CHANGELOG.md` ([Unreleased]) vermerkt.
* Neue Texte stehen in `core/resources/.../strings.xml`; keine hartkodierten Strings.
* Reine Logik (z. B. SHA-256-Prüfung, Pfad-Auflösung) hat JVM-Unit-Tests.
