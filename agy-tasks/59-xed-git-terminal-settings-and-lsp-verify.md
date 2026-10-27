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
- Noch offen: „Alle Sitzungen beim Schließen beenden“, Zwischenablage-Tastenkombinationen, Schriftart/Schriftgröße-Auswertung (`TerminalConfig.font_size`).
- „Projekt als Arbeitsverzeichnis“: beim Öffnen einer Terminal-Sitzung aus dem Workspace `cwd = rootPath`.
- Zusatztasten bearbeiten (Extra-Keys-Layout), Terminal-Backup/Wiederherstellen (`$HOME` + `usr` als tar.gz).

## Akzeptanz
A abgehakt; B und C als einzelne Commits mit Tests (ViewModel) und Gerätecheck.
