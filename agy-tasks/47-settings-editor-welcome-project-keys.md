# Task 47 – Einstellungen überarbeiten: Zurück-Navigation, Editor-Einstellungen, Willkommensbildschirm, Projekteinstellungen, Schlüsselverwaltung

**Ziel:** Die Einstellungs- und Startoberflächen sind vollständig, bedienbar und konsistent. Fünf Teile, die zum selben Thema gehören und getrennt umgesetzt, getestet und committet werden können (47.1 bis 47.5).
**Warum jetzt:** Mehrere Einstellungsseiten lassen sich nicht über die Oberfläche verlassen; einzelne Editor-Schalter wirken nicht; der Willkommensbildschirm wirkt unfertig; es gibt keinen Ort für projektspezifische Build-Einstellungen; Schlüssel lassen sich nicht einem Projekt zuweisen.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos: Schnittstellen und Modelle in `:core:domain`, Implementierung in `:core:data`/`:libs:*`, UI in `:feature:*` (MVVM/MVI mit Hilt, `@Immutable`-UI-States, `StateFlow`; Material 3; `modifier: Modifier = Modifier` als erster optionaler Parameter; keine Logik in Composables). Gradle Kotlin DSL, Versionen im Version Catalog, Convention-Plugins aus `build-logic`.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) bleibt grün.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (insbesondere Gerätetests; Zielgerät des Inhabers: Pixel 9 Pro XL, Android 17). Aussagen dieses Tasks über den Bestand stammen aus Stichwortsuchen in einem Repo-Snapshot; prüfe sie vor dem Start selbst und korrigiere sie im Bericht. Namen von sora-editor-Eigenschaften (Version `0.23.4`) schlägst du in der API selbst nach, sie stehen hier nicht fest.
* Geheimnisse (Schlüsselspeicher-Passwörter, Alias-Passwörter, Token) nie ins Log, nie in Dateien im Projektordner, nie in Berichte, nie in Proto/DataStore im Klartext.
* Proto-Änderungen (`core/datastore/src/main/proto/settings.proto`): nur **neue** Feldnummern, keine Nummer wiederverwenden (gelöschte Felder `reserved`), alte Datenspeicher müssen weiter lesbar sein (Serializer-Standardwerte prüfen).
* Bericht in `docs/settings-overhaul-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte** (je Teil 47.x); `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash eintragen, je Teil ein Commit).
* Build grün nach jedem Teil: `./gradlew :app:assembleDebug` plus die Tests der berührten Module.

**Reihenfolge:** 47.1 zuerst (klein, entsperrt die Bedienung). 47.5 vor 47.4 (47.4 nutzt die Zuweisung aus 47.5). 47.2 und 47.3 sind unabhängig.

---

## 47.1 Zurück-Navigation in den Einstellungen

**Befund (prüfen):** `Routes.SETTINGS`, `SETTINGS_THEME`, `SETTINGS_EDITOR`, `SETTINGS_TERMINAL`, `SETTINGS_SDK_MANAGER`, `SETTINGS_GIT`, `SETTINGS_PLUGINS` in `app/.../CodeForgeNavHost.kt` rufen ihre Routen ohne `onBack` auf. In `EditorSettingsScreen`, `GitSettingsScreen`, `TerminalSettingsScreen` und `SettingsHubScreen` besteht die `TopAppBar` nur aus dem `title`, ohne `navigationIcon`. `SdkManagerScreen`, `PluginsScreen` und `ThemeBuilderScreen` haben eine `TopAppBar` und (laut Stichwortsuche) weder `navigationIcon` noch `onBack`. **Vorbild:** `ExtensionsScreen` und `SnippetSettingsScreen` (`navigationIcon` mit `Icons.AutoMirrored.Filled.ArrowBack`, `onBack`) und `SigningRoute(onNavigateBack = …)`.

**Aufgaben:**
1. Einen gemeinsamen Baustein anlegen (z. B. `SettingsTopBar(title, onBack, actions)` im Modul für gemeinsame UI-Bausteine; prüfe, welches Modul das im Repo ist) und in **allen** oben genannten Screens verwenden. Zurück-Pfeil mit `contentDescription` aus den Strings (en/de), `AutoMirrored` für RTL.
2. Jede Route bekommt `onBack`; im NavHost `navController.popBackStack()`. Ist der Stapel leer (`popBackStack()` liefert `false`, z. B. nach Deep-Link oder Prozess-Neustart), zur Startroute navigieren, **kein** leerer Bildschirm.
3. **Systemzurück/Geste:** prüfen, dass jede dieser Seiten sie korrekt behandelt (Navigation Compose übernimmt das für Routen; zusätzlich `BackHandler` für offene Dialoge, Bottom Sheets, Suchfelder, ungespeicherte Eingaben in Unterseiten). Vorhersehbare Rückfahrt: `GitSettingsRoute` wird auch aus `GitRoute` (`onOpenSettings`) geöffnet und soll dorthin zurückführen, nicht in die Einstellungen.
4. Der Hub (`SETTINGS`) führt zurück zu dem Bildschirm, von dem er geöffnet wurde (Willkommensbildschirm oder Arbeitsbereich).

**Tests:** Compose-UI-Tests (oder, falls im Repo keine vorhanden sind, ViewModel-/NavHost-Tests mit `TestNavHostController`): Pfeil sichtbar auf jeder Seite, Klick ruft `onBack`; Systemzurück bei offenem Dialog schließt zuerst den Dialog; leerer Stapel führt zur Startroute. Geräteprüfung unter **nicht getestet** mit Checkliste je Seite.

---

## 47.2 Editor-Einstellungen vervollständigen und garantieren

**Befund (prüfen):**
* `EditorConfig` (Proto) hat 19 Felder: `tab_size`, `use_tree_sitter`, `textmate_theme`, `lsp_server_path`, `font_size`, `font_family`, `word_wrap`, `show_non_printable_chars`, `sticky_scroll_enabled`, `magnifier_enabled`, `symbol_pair_autocomplete_enabled`, `minimap_enabled`, `rainbow_brackets_enabled`, `pinch_to_zoom_enabled`, `desktop_shortcuts_enabled`, `format_on_save_{kotlin,java,xml,json}`. Die Oberfläche (`EditorSettingsScreen`, 290 Zeilen) zeigt sie an.
* **Angewendet** wird einiges nicht: `symbolPairAutocompleteEnabled` kommt in `SoraCodeEditor.kt` laut Stichwortsuche nur als Feld von `EditorDisplaySettings` vor, nirgends als Wirkung. `lspServerPath` wird nur in `EditorSettingsViewModel` gelesen und von nichts verbraucht. `fontFamily` wird als **Asset-Pfad** geladen (`Typeface.createFromAsset`), ohne Auswahlmöglichkeit.
* `EditorOverlay.kt` begründet, dass sora-editor `0.23.4` keine `InlayHint`-API hat; Farbkästchen und Update-Chips laufen als Compose-Overlay.
* Snippets: `SnippetSettingsScreen` (665 Zeilen) existiert und ist über `onNavigateToSnippets` aus `EditorSettingsScreen` erreichbar.

**Frühere Version (Hinweis des Inhabers, 2026-10-21):** Inlay-Hints, Zeilennummer-Fixierung und ICU-Option **gab es in einer früheren Version** der App. Im Repo-Verlauf (180 Commits, beginnend bei `reinit`) fand die Stichwortsuche nach `InlayHint`, `ICULib` und `pinLineNumber` **keinen** Treffer in der Historie, ebenso nicht im hochgeladenen `CodeAssist-KotlinIDE-vibecoded.zip`. Der Agent soll daher (a) zuerst fragen, wo die frühere Version liegt (Archiv, alter Branch, Release-APK, anderes Repo), und sie als Vorlage lesen, nicht raten; (b) klären, mit welcher **sora-editor-Version** sie lief. Das Projekt hat die Version in `feature/editor/build.gradle.kts` fest als `0.23.4` eingetragen (nicht im Version Catalog), und `EditorOverlay.kt` nennt `0.24.x` als erste Version mit `InlayHint`. Ein Versionssprung ist also möglich, aber ein eigener Schritt: erst Bestand und Aufwand bewerten (API-Änderungen, `language-textmate`/`language-treesitter` müssen dieselbe Version haben, Tests), dann mit dem Inhaber entscheiden, nicht stillschweigend anheben. Danach ins Version Catalog verschieben.

**Schritt 0 – Bestandsaufnahme (Pflicht, vor dem Bauen):** Tabelle im Bericht über **jede** Option, die der Editor kann oder kann könnte, mit Spalten: Option · im Proto · in der UI · im ViewModel · in `SoraCodeEditor` angewendet (bei Erzeugung **und** bei Aktualisierung, die Datei hat beide Pfade) · getestet. Kandidaten (jeweils prüfen, ob in sora-editor `0.23.4` und im Repo vorhanden, sonst „nicht verfügbar“ markieren, **nicht** vortäuschen):
* Darstellung: Schriftgröße, Schriftart, Zeilenhöhe/Zeilenabstand, Zeilennummern an/aus, **Zeilennummer fixieren (Pin)**, aktuelle Zeile hervorheben, Einrückungslinien/Code-Block-Linien, Wortumbruch, nicht druckbare Zeichen, Cursor-Animation/-Blinken, Minimap, Sticky Scroll, Rainbow-Klammern, Klammerpaar-Hervorhebung.
* Eingabe: **Klammern automatisch schließen / Symbolpaare**, automatische Einrückung, Tabulatorbreite, Tabs vs. Leerzeichen, Lupe (Magnifier), Zwei-Finger-Zoom, Desktop-Tastaturkürzel, **ICU-Bibliothek** (sora-Option zur Textsegmentierung; Name in der API nachschlagen).
* Sprachfunktionen (LSP): **Hover** (LSP `textDocument/hover`: `LspClientRepository.requestHover` existiert; prüfen, ob die Anzeige im Editor angebunden ist), **Inlay-Hints** (LSP `textDocument/inlayHint`: gibt es in `LspClientRepository`? Falls nicht, bleibt es bei „nicht verfügbar“ mit Verweis auf `EditorOverlay.kt`; im Bericht Machbarkeit über das Compose-Overlay bewerten), Vervollständigung (automatisch/auf Anforderung), Signaturhilfe, Diagnose-Anzeige, Code-Aktionen, Format-beim-Speichern (je Sprache, ausbaubar auf die Sprachen aus Task 43).
* Datei: Zeilenenden, Kodierung, Auto-Speichern (falls im Repo vorhanden).

**Aufgaben:**
1. **Jede Option ist wirksam.** Für alle Schalter: Proto-Feld → `EditorSettingsViewModel` → `EditorViewModel`/`EditorDisplaySettings` → `SoraCodeEditor` (Erzeugung und `update`-Zweig). Fehlende Verdrahtung (mindestens `symbolPairAutocompleteEnabled`) nachrüsten oder die Option entfernen, wenn sie technisch nicht umsetzbar ist, mit Begründung im Bericht.
2. **Neue Optionen** nur dort, wo das Bestandsergebnis „vorhanden und anwendbar“ lautet. Neue Proto-Felder ab Nummer 20.
3. **Gruppierung** der Seite in Abschnitte (Darstellung · Eingabe · Sprachfunktionen · Format beim Speichern · Snippets · Dateikopf); Suchfeld, wenn mehr als etwa 25 Einträge; Zurücksetzen auf Standard je Abschnitt.
4. **Schrift:** Schriftgröße als Regler mit Vorschau; Schriftart als **Auswahlliste**: gebündelte Schriften (Assets), installierte Schriften des Systems (Monospace-Familien), eigene `.ttf/.otf` aus dem Dateiauswahldialog (SAF) – kopiert in den App-Speicher. Die Liste statt Asset-Pfad-Eingabe; Altwerte weiter lesen.
5. **Theme-Auswahl statt Texteingabe:** Das Textfeld `textmateTheme` und das Feld `lspServerPath` entfallen aus der Oberfläche (dort steht kein Pfad mehr). Stattdessen eine **Auswahlliste** (`ExposedDropdownMenu`) mit
   * „Automatisch (folgt dem App-Design)“ als Standard: Hell/Dunkel aus `ThemeConfig` (`ThemeMode`), ergibt `light_plus` bzw. `dark_plus`; bei `SYSTEM` aus dem Systemzustand;
   * allen **verfügbaren** Themes: gebündelte (`feature/editor/src/main/assets/textmate/themes`, derzeit `dark_plus.json`, `light_plus.json`) und die über die Erweiterungsverwaltung (`:feature:settings/extensions`) installierten.
   Die Liste kommt aus **einer** Quelle (z. B. Methode im `TextMateAssetLoader`, die gebündelte und installierte Themes zurückgibt), nicht doppelt gepflegt. Ein gespeicherter Wert, der nicht (mehr) existiert, fällt auf „Automatisch“ zurück, ohne Absturz und ohne den Wert zu überschreiben, bis der Nutzer wählt. Tree-sitter: nur der Schalter bzw. eine Auswahl „Syntax-Engine“, **kein** Pfad.
6. **Snippets-Unterseite:** Die bestehende `SnippetSettingsScreen` bleibt als Unterseite. Prüfen und im Bericht belegen: Anlegen, Bearbeiten, Löschen, Import/Export, Anzeige nach Sprache, Platzhalter (`$1`, `${name}`), Wirkung in der Vervollständigung (`LspCompletionProvider`/`SnippetRepository`). Fehlt etwas aus dieser Liste, ergänzen. Der Einstieg im Editor-Bildschirm steht in einem eigenen Abschnitt.
7. **Dateikopf für neue Dateien (neu):** Abschnitt/Unterseite „Dateikopf“.
   * Einstellungen: global an/aus; Autor (Vorbelegung leer); je **Dateityp** (Endung) eine Vorlage mit Platzhaltern `{{filename}}`, `{{module}}`, `{{package}}`, `{{author}}`, `{{date}}`, `{{year}}`, `{{project}}`; Live-Vorschau mit Beispieldaten.
   * Mitgelieferte Standardvorlagen mit dem Stil des Repos (`/** Modul: … @author … */` für Kotlin/Java; `<!-- … -->` für XML/HTML; `#`-Kommentare für `sh`, `yaml`, `toml`, `py`, `properties`; `//` bzw. `/* */` für `kts`, `gradle`, `c`, `cpp`, `h`; JSON ohne Kopf, weil Kommentare dort ungültig sind). Pro Typ rücksetzbar; neue Typen hinzufügbar.
   * **Anwendung:** beim Anlegen einer **neuen leeren Datei** durch den Nutzer (Dateibaum: `FileTreeViewModel`, Aufruf `fileSystemRepository.createFile(...)`, rund Zeile 119 im Snapshot) über eine zentrale, testbare Funktion `FileHeaderRenderer.render(type, context)`; nicht auf bestehende Dateien, nicht auf Projektvorlagen (diese bringen ihren eigenen Inhalt mit; im Bericht begründen, falls anders entschieden).
   * Sonderfälle, die die Funktion beachten muss: XML: Kopf **nach** `<?xml …?>`; Shell: **nach** `#!`-Zeile; Kotlin/Java: **vor** `package`; Dateityp unbekannt: kein Kopf; Platzhalter ohne Wert (z. B. `{{package}}` bei `.xml`): Zeile weglassen statt leere Klammern.
   * Speicherung: neue Proto-Nachricht (z. B. `FileHeaderConfig`) in `settings.proto`, Standardwerte im Serializer; Vorlagentexte sind Nutzerdaten.
8. **Garantie der Funktion:** ein Test je Option (siehe unten) plus eine Geräte-Checkliste (nicht vom Agenten ausführbar) im Bericht.

**Tests (Pflicht, JVM):**
* `EditorSettingsViewModelTest`: jede Option wird im Datenspeicher gespeichert und wieder gelesen; Abwärtskompatibilität (alter Proto ohne neue Felder).
* **Verdrahtungstest:** Ein Test (oder strukturierter Check), der für **jedes** `EditorDisplaySettings`-Feld belegt, dass es in `SoraCodeEditor` gelesen wird. Praktisch: Reflexions-/Quelltext-freie Variante über eine kleine Funktion `applyDisplaySettings(editor, settings)`, die `SoraCodeEditor` für Erzeugung **und** Update nutzt, getestet mit einem Fake/Robolectric-Editor, falls das Repo Robolectric nutzt; sonst Funktion trennen und die Zuordnung Option → Editoreigenschaft als Tabelle testen. Die doppelte Anwendung (Erzeugung und Update) wird zu **einer** Funktion zusammengeführt, damit sie nicht mehr auseinanderlaufen kann.
* `TextMateThemeCatalogTest`: gebündelte Themes erscheinen; installierte erscheinen; unbekannter gespeicherter Wert → „Automatisch“; „Automatisch“ löst je `ThemeMode` korrekt auf.
* `FileHeaderRendererTest`: je Dateityp (Kotlin, Java, XML mit Deklaration, Shell mit Shebang, YAML, TOML, JSON, unbekannt), Platzhalter, fehlende Werte, Sonderzeichen im Namen, Zeilenenden.
* Snippet-Tests, soweit Lücken aus Aufgabe 6 geschlossen werden.

---

## 47.3 Willkommensbildschirm überarbeiten

**Befund (prüfen):** `feature/welcome/.../WelcomeScreen.kt` (249 Zeilen): Logo, Titel, `ElevatedCard`-Aktionen (Erstellen, Importieren, Klonen, Terminal, Einstellungen), darunter die Liste zuletzt geöffneter Projekte (`RecentProject`: Name, Pfad, `lastOpenedEpochMillis`, `moduleCount`); Detaildialog mit „Öffnen“ und „Aus Liste entfernen“. Der Inhaber findet das **zu schlicht**; ein **früherer Build** hatte eine deutlich schönere Gestaltung.

**Aufgaben:**
1. **Vorlage finden:** Suche die frühere Gestaltung in der Git-Historie (`git log --follow -- feature/welcome`, `git log -S "WelcomeScreen"`, alte Release-Tags/Builds, Berichte unter `docs/`). Finde sie nicht, **frage den Inhaber** nach Screenshot oder APK des früheren Builds, statt zu raten. Im Bericht festhalten, welche Version als Vorlage diente (Commit-Hash) und was übernommen wurde.
2. **Gestaltung** (Material 3, ohne Logik im Composable, `WelcomeUiState`/`-Event`/`-Effect` als Vertrag **unverändert** oder abwärtskompatibel erweitern):
   * Kopfbereich mit Logo, App-Name, Versionsnummer (`BuildConfig`), kurzer Untertitel;
   * Hauptaktionen als große Karten oder Kacheln in einem Raster (Neu, Importieren, Klonen) mit Symbol und Kurztext; Terminal/Einstellungen als zweite Reihe oder Symbolleiste;
   * Liste „Zuletzt geöffnet“ mit Name, gekürztem Pfad, relativer Zeit („vor 2 Std.“), Modulanzahl, optional Projekt-Initialen als Symbol; Kontextmenü statt nur Detaildialog (Öffnen, Projekteinstellungen (siehe 47.4), Aus Liste entfernen);
   * **Leerzustand** ohne Projekte (Hinweis und primäre Aktion);
   * adaptive Layouts (`WindowSizeClass`: kompakt, mittel, erweitert; Querformat; Faltgerät), Dynamic Color und Dunkel/Hell, Barrierefreiheit (Kontrast, Inhaltsbeschreibungen, Touch-Ziele ≥ 48 dp), Ladezustand ohne Flackern.
3. Texte en/de über `:core:resources`. Keine fremden Marken- oder Bilddaten. Keine neue schwere Bibliothek ohne Begründung.

**Tests:** Compose-Previews für kompakt/mittel/erweitert, hell/dunkel, Leerzustand und lange Namen; ViewModel-Tests bleiben grün; ein UI-Test pro Hauptaktion (löst den passenden `WelcomeUiEvent` aus), falls UI-Tests im Repo laufen. Screenshot-Vergleich nur, wenn das Repo bereits Screenshot-Tests nutzt. Optik ist **nicht getestet**, bis der Inhaber auf dem Gerät bestätigt (im Bericht ausdrücklich so vermerken).

---

## 47.4 Projekteinstellungen (neues Modul `:feature:projectsettings`)

**Befund (prüfen):** Es gibt keinen Ort für projektspezifische Build-Einstellungen. Die Schlüsselverwaltung aktiviert ein Build-Signing **global** (`SigningRepositoryImpl.configureBuildSigning` hält eine Map `activeBuildSigning` mit Umgebungsvariablen, nicht pro Projekt; siehe 47.5). Wie der Build Gradle startet, prüfst du in `:feature:build`.

**Ziel:** Ein Bildschirm (oder Bottom Sheet) „Projekteinstellungen“, erreichbar aus dem Arbeitsbereich (Projektmenü) und aus dem Kontextmenü eines Projekts auf dem Willkommensbildschirm. Er speichert Einstellungen **je Projekt** und wendet sie beim Bauen an.

**Aufgaben:**
1. **Datenmodell** in `:core:domain` (z. B. `ProjectBuildSettings`) und Schnittstelle `ProjectSettingsRepository`; Implementierung in `:core:data`. Speicherort je Projekt (IDE-intern im App-Speicher, Schlüssel = normalisierter Projektpfad) **oder** `<Projekt>/.codeforge/project-settings.json` für versionierbare Werte; **Geheimnisse nie in die Projektdatei**. Entscheidung im Bericht begründen (empfohlen: nicht geheime Werte in `.codeforge/project-settings.json`, die Schlüsselzuweisung IDE-intern, damit ein geteiltes Projekt keine Schlüsselverweise des Inhabers enthält).
2. **Einstellungen (Startumfang),** je Build-Typ `release`/`debug` und, wenn sinnvoll, je Modul vom Typ Anwendung:
   * Code verkleinern (`minifyEnabled`/`isMinifyEnabled`) an/aus;
   * Ressourcen verkleinern (`shrinkResources`), nur gültig, wenn Minify an ist (Abhängigkeit in der UI erzwingen);
   * PNG-Optimierung (`crunchPngs`);
   * debuggbar (`debuggable`);
   * ProGuard/R8-Regeldateien: Standard oder eigene Datei aus dem Projekt;
   * Signierung an/aus mit Schlüsselauswahl (Teil 47.5).
   Die Liste ist erweiterbar; weitere Optionen nur, wenn belegt ist, dass die Anwendung funktioniert. Optionen, die der installierte AGP nicht kennt, werden ausgeblendet oder als „nicht verfügbar“ markiert.
3. **Anwenden, ohne die Build-Dateien des Nutzers umzuschreiben:** Bevorzugt ein von der IDE erzeugtes **Gradle-Init-Script** (`--init-script`), das die Werte pro Build-Typ nach der Auswertung der Anwendungsmodule setzt, plus Umgebungsvariablen bzw. `-P`-Eigenschaften für Signierung. Prüfe zuerst, ob das mit dem installierten Gradle und AGP funktioniert (Version aus dem Projekt lesen), und belege es mit einem Test-Build, sonst als **nicht getestet** kennzeichnen. Alternative „In `build.gradle(.kts)` schreiben“ nur als ausdrücklich bestätigte Aktion mit Vorschau und Sicherung, nicht als Standard. Dateien des Nutzers, die den Wert bereits setzen, haben Vorrang oder werden im Bildschirm gekennzeichnet („im Build-Skript festgelegt“); das Verhalten ist im Bericht beschrieben.
4. **Validierung:** Konflikte erklären (z. B. Shrink ohne Minify), Hinweise zu Debug-Builds mit Minify, Hinweis bei fehlendem Schlüssel für Release.
5. **UI:** Bildschirm mit Abschnitten (Build-Typ-Umschalter Release/Debug, Erweiterungen unten); `@Immutable`-State, Zurück-Pfeil aus 47.1, Zurücksetzen auf Standard, Anzeige „geändert seit …“ entfällt. Als Bottom Sheet nur, wenn es auf kleinen Bildschirmen scrollbar und mit Zurück bedienbar bleibt.
6. Neues Modul in `settings.gradle.kts` und Version Catalog; Navigation: neue Route im NavHost mit Projektpfad als Argument.

**Tests (JVM):** Repository (Lesen/Schreiben, Standardwerte, kaputte Datei); Validierung; **Init-Script-Erzeugung** (Textvergleich je Kombination, Escaping von Pfaden); ViewModel (Zustandsänderungen, Abhängigkeit Shrink/Minify). Ein echter Gradle-Lauf ist ein Gerätetest und steht unter **nicht getestet**, falls er nicht in der Umgebung läuft.

---

## 47.5 Schlüsselverwaltung: Zuweisung zu Projekten (und fehlende Bedienung)

**Befund (prüfen):** `SigningScreen` (1149 Zeilen) und `SigningViewModel` bieten bereits: Schlüsselspeicher **erstellen** (`SubmitCreateKeystore`), **importieren** (`SubmitImportKeystore`), **exportieren** (`ConfirmExport`), **löschen** (`ConfirmDelete`), Details, APK signieren und `ActivateBuildSigning`/`DeactivateBuildSigning` (global). Auffällig: Import und Export laufen über **Pfadfelder** (`UpdateImportFilePath`, `UpdateExportTargetPath`), also manuelle Pfadeingabe. Eine **Zuweisung zu einem Projekt** fehlt; `activeBuildSigning` ist eine globale Map von Umgebungsvariablen.

**Aufgaben:**
1. **Import/Export bequem:** Dateiauswahl über den Systemdialog (SAF: `OpenDocument`, `CreateDocument`) statt Pfadeingabe (die Pfadfelder dürfen als „Erweitert“ bleiben). Unterstützte Formate laut Bestand prüfen (`JKS`, `PKCS12`, ggf. `BKS`) und in der Oberfläche benennen; Fehlertexte bei falschem Passwort oder Format verständlich. Beim Export nie das Passwort in Datei oder Log schreiben; Hinweis, dass die Datei den privaten Schlüssel enthält.
2. **Zuweisung Projekt ↔ Schlüssel:** neues Modell `ProjectSigningAssignment(projectPath, keystoreId, alias)` (in `:core:domain`), Schnittstelle am `SigningRepository`, Speicherung IDE-intern (App-Speicher), **ohne Passwörter**. Passwörter kommen weiter aus dem sicheren Speicher der Schlüsselverwaltung zum Bauzeitpunkt.
3. **Oberfläche in der Schlüsselverwaltung:** in den Details eines Schlüssels Abschnitt „Verwendet in Projekten“ (Liste, je Eintrag „Entfernen“), Aktion „Projekt zuweisen“ (Auswahl aus den zuletzt geöffneten Projekten `RecentProject` und aus dem aktuellen Projekt) mit Alias-Auswahl; Alias-Listen kommen aus dem Schlüsselspeicher.
4. **Auflösung beim Bauen (feste Reihenfolge, im Bericht dokumentieren):** Zuweisung des Projekts → bisherige globale Aktivierung („Standardschlüssel“, bleibt bestehen für Projekte ohne Zuweisung) → kein Signing. Die Umgebungsvariablen (`GradleSigningSnippet.ENV_*`) werden **je Build aus der Auflösung für das gebaute Projekt** erzeugt, nicht aus einer globalen Map.
5. **Löschen mit Folgen:** Wird ein Schlüssel gelöscht oder exportierend verschoben, warnt der Dialog mit der Liste der Projekte, die ihn verwenden; nach Bestätigung werden deren Zuweisungen entfernt. Beim Löschen eines Aliases dasselbe.
6. **Zusammenspiel mit 47.4:** Dort wählt „Signierung an/aus“ den Schlüssel aus derselben Zuweisung; es gibt **eine** Quelle der Wahrheit (`ProjectSigningAssignment`), keine zweite Kopie in den Projekteinstellungen.
7. Ein Einstieg aus der Seite „Projekteinstellungen“ führt in die Schlüsselverwaltung und zurück.

**Tests (JVM):** Zuweisung anlegen/ändern/entfernen; Auflösungsreihenfolge (Projekt, global, keine); Löschen entfernt Zuweisungen; Umgebungsvariablen enthalten **keine** Passwörter im Log (Test auf Log-Ausgabe); Alias-Löschen; Export/Import-Round-Trip mit einem Testschlüsselspeicher (nur Testdaten, nie echte Schlüssel). Dateiauswahl über SAF ist ein Gerätetest (**nicht getestet**).

---

## Abnahme (je Teil)
* 47.1: jeder Einstellungsbildschirm hat einen Zurück-Pfeil und reagiert auf Systemzurück; Tests grün.
* 47.2: Bestandstabelle im Bericht; jede sichtbare Option wirkt (Test oder begründete Entfernung); Theme-Dropdown statt Textfeld; Dateikopf funktioniert für die genannten Dateitypen.
* 47.3: Vorlage der früheren Gestaltung benannt; Previews; Vertrag des ViewModels kompatibel; Optik als nicht getestet gekennzeichnet bis zur Bestätigung.
* 47.4: Modul mit Persistenz und Validierung; Anwendungsweg belegt oder als nicht getestet markiert.
* 47.5: Zuweisung pro Projekt, Auflösungsreihenfolge, Warnung beim Löschen; keine Passwörter in Logs.
* Bericht beantwortet für jeden Teil: was tatsächlich getestet wurde, was nicht, und warum.
