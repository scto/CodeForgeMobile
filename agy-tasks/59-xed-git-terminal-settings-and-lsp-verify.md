# Task 59 — Xed-Vorbild: Git-/Terminal-Einstellungen + Verifikation der neuen Editor-/LSP-Funktionen

Kontext: Editor-Einstellungen (Zeilennummern, Zeilennummern anheften, Cursor-Animation, Zeilenabstand, Lesemodus,
TXT umbrechen, Farbvorschauen, Tastaturvorschläge, virtuelle Tastatur bei Hardware-Tastatur) sind umgesetzt
(Proto-Felder 23–31, `SoraCodeEditor.applyDisplaySettings`). Neu im Editor-Toolbar (LSP-Menü „ⓘ“):
Dokumentation (Hover), Typ-Definition, Implementierungen, Parameter-Info (automatisch bei `(` und `,`).
Nicht in der Cloud verifiziert: Gerätetest, `:app:assembleDebug`.

## A. Verifikation auf dem Gerät
1. Dialoge „Dependency-Update verfügbar“ und „Laufende LSP-Server“: volle Breite, Buttons/Badges ohne Zeilenumbruch pro Zeichen.
2. Editor-Einstellungen: jede neue Option wirkt sofort im geöffneten Editor; „Lesemodus“ blockiert Eingabe.
3. LSP (z. B. Kotlin-LSP): Hover-Sheet, Typ-Definition, Implementierungen (Liste bei mehreren Treffern), Parameter-Hinweiszeile.

## B. Git-Einstellungen (Vorbild Xed: Dateinamen einfärben, Gutter-Markierungen, Konfliktkennung)
Noch NICHT vorhanden und größer als eine Einstellung — je ein eigenes Feature:
- ~~Dateinamen im Dateibaum einfärben~~ — umgesetzt (`FileTreeGitStatus`; Aktualisierung beim Öffnen/Aktualisieren/Datei-Mutation, nicht nach jedem Speichern im Editor). Gerätetest offen.
- ~~Gutter-Markierungen im Editor~~ — umgesetzt (`GitLineDiff`, `GitGutterOverlayLayer`, Schalter in den Git-Einstellungen).
  Aktualisierung beim Tab-Wechsel und nach dem Speichern (gespeicherter Stand gegen HEAD, nicht live beim Tippen). Gerätetest offen.
- ~~Konfliktkennung~~ — umgesetzt (`MergeConflicts`, `ConflictOverlayLayer`, `ConflictBar`: Eigene/Fremde/Beide übernehmen, nächster Konflikt; Schalter in den Git-Einstellungen). Gerätetest offen.
- Je ein Schalter in `GitSettingsScreen` (Proto `GitConfig`, Standard „an“ invertiert speichern).

## C. Terminal-Einstellungen (Vorbild Xed: Scrollback-Größe, Cursorstil, Sitzungen beim Schließen beenden, Projekt als Arbeitsverzeichnis, Tasten für Zwischenablage)
Das Terminal ist der Termux-Fork (`libs:termux-app`); Optionen laufen über `termux.properties`
(`terminal-transcript-rows`, `terminal-cursor-style`, `extra-keys`). `TerminalConfig.font_size` wird dort derzeit nicht ausgewertet.
- ~~Scrollback-Größe und Cursorstil~~ — umgesetzt (`TermuxPropertiesStore` schreibt `terminal-transcript-rows`/`terminal-cursor-style` in `~/.termux/termux.properties`; wirkt für NEUE Sitzungen). Gerätetest offen: wirkt es wirklich nach Neustart des Terminals? Falls nicht, Reload-Broadcast ergänzen.
- Erledigt, Gerätetest offen: „Alle Sitzungen beim Schließen beenden“ (Pref `kill_sessions_on_exit`, `TermuxActivity.onDestroy` sendet ACTION_STOP_SERVICE nur bei `isFinishing()`). Zwischenablage-Tastenkombinationen erledigt (Strg+Umschalt+C/V, Pref `clipboard_shortcuts`, Gerätetest offen). Terminal-Backup erledigt (`TerminalBackup` in terminal-engine, tar.gz mit Symlinks, Zip-Slip-Schutz; Gerätetest offen: Sichern/Wiederherstellen mit echtem usr, Größe/Dauer). Noch offen: ~~Schriftgröße~~ jetzt an die Termux-Preferences gebunden (`TerminalSettingsViewModel`, sp↔px). Gerätetest offen: Größe ändern → Terminal neu öffnen → stimmt die Größe, bleibt Pinch-Zoom konsistent? ~~Terminal-Schriftart~~: Dateiauswahl (TTF/OTF) → `~/.termux/font.ttf`, Zurücksetzen auf Systemstandard (`TerminalFontInstaller`). Gerätetest offen.
- JetBrains Mono (OFL, v2.304) liegt jetzt als Asset `feature/editor/src/main/assets/fonts/` (Editor-Option + Terminal-Button „JetBrains Mono“). Gerätetest offen.
- „Projekt als Arbeitsverzeichnis“: beim Öffnen einer Terminal-Sitzung aus dem Workspace `cwd = rootPath`.
- ~~Zusatztasten bearbeiten~~ erledigt (Presets + Textfeld, schreibt `extra-keys` in termux.properties; Gerätetest offen: wirkt es nach Neustart des Terminals?). Offen (alt, jetzt erledigt): Terminal-Backup/Wiederherstellen (`$HOME` + `usr` als tar.gz).

## Akzeptanz
A abgehakt; B und C als einzelne Commits mit Tests (ViewModel) und Gerätecheck.

## E. Tooltips, Hilfe, Über (Claude, 2026-10-27)
- `TooltipIconButton` (`:core:ui`) ersetzt `IconButton` in Editor-Zeile, Git-Leiste, Debug-Leiste, Terminal, Dateibaum und Projekt-Kopfzeile (55 Stellen). Gerätetest offen: Tooltip beim langen Drücken sichtbar, kein Konflikt mit anderen Long-Press-Aktionen.
- Neue Seiten in den Einstellungen: „Hilfe“ (`HelpScreen`: grafische Leiste + Beschreibung je Schaltfläche, Daten in `HelpCatalog`) und „Über CodeForge Mobile“ (`AboutSettingsScreen`).
- Wer eine Schaltfläche hinzufügt, ergänzt sie in `HelpCatalog` (und nutzt `TooltipIconButton`).

## F. LSP-Extensions-Details und Store (Claude, 2026-10-27)
- Extensions-Karte: Buttons „Details“ und „Update prüfen“. Die Prüfung vergleicht die im Katalog gepinnte Version mit npm-Registry / GitHub-Release bzw. -Tag / `apt-cache policy` (Termux-Pakete) und **installiert nichts** (Katalog pinnt Version und SHA-256). Gerätetest offen (braucht Netz).
- Detailansicht (Vollbild-Dialog): Kopf mit Icon, Kurzbeschreibung, Autor, Homepage, GitHub; Tabs Beschreibung (Fähigkeiten/Einschränkungen), Einstellungen (nur globale LSP-Werte und Startbefehl/Ort, es gibt noch keine Einstellungen je Server), Info (Version, Lizenz, Quelle, SHA-256, Voraussetzungen).
- Store: Einstellungen → Store lädt `StoreConfig.CATALOG_URL` (`https://scto.github.io/codeforge-store/store.json`, Seite existiert noch nicht; Vorlage in `store-site/`), Tabs je Kategorie, Offline-Cache. Download/Homepage öffnen im Browser; keine Direktinstallation.
