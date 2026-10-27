# Task 60 P3 – Verifikation der Terminal-Einstellungen (agy-tasks/59 Teil C)

**Datum:** 2026-10-27  
**Branch:** `main`  
**Host / Plattform:** Android Termux (aarch64 / Linux)

---

## 1. Ausgangslage & Zielsetzung

Gemäß **Task 60 (P3)** aus `agy-tasks/60-agy-prompts-offene-punkte.md` und den Vorgaben aus `agy-tasks/59` (Teil C):
* Überprüfung der Terminal-Einstellungen in:
  - `feature/settings/.../TerminalSettings*.kt`
  - `feature/settings/.../TermuxProperties.kt`
  - `feature/settings/.../TerminalFontInstaller.kt`
  - `libs/termux-app/.../TermuxActivity.java`
  - `libs/termux-shared/.../ClipboardShortcuts.java`
  - `libs/terminal-engine/.../TerminalBackup.kt`
* Prüfung der 6 geforderten Punkte (Scrollback/Cursor, Schriftgröße/-art, Zusatztasten, Kill-on-Exit, Zwischenablage-Kürzel, Sicherung/Wiederherstellung).
* Fehler im Code minimal beheben, einzeln committen, Nicht-Prüfbares ehrlich unter "nicht getestet" festhalten.

---

## 2. Detaillierte Prüfung und Befunde zu den 6 Punkten

### Punkt 1: Scrollback & Cursorstil (Wirkung & Reload)

* **Befund im Code:**
  - `TermuxPropertiesStore` schreibt `terminal-transcript-rows` und `terminal-cursor-style` in `~/.termux/termux.properties`.
  - Bei Aktivitätsstart liest `TermuxActivity.onCreate()` via `reloadProperties()` die Datei ein.
  - *Verbesserung vorgenommen:* Um sicherzustellen, dass Änderungen auch im bereits laufenden Terminal sofort wirksam werden, wurde in `TerminalSettingsViewModel` die Methode `notifyTermuxStylingReload()` ergänzt. Diese sendet den Broadcast `TERMUX_ACTIVITY.ACTION_RELOAD_STYLE` an das eigene Paket. `TermuxActivity` fängt diesen ab und ruft `reloadActivityStyling(false)` $\rightarrow$ `reloadProperties()` $\rightarrow$ `setTerminalCursorStyle()` auf, ohne dass die Activity oder App neu gestartet werden muss.
  - *Commit:* `036014b` (`feat(terminal-settings): Reload-Broadcast bei Änderung von Cursor, Scrollback, Zusatztasten und Schriftart senden`).

---

### Punkt 2: Schriftgröße und Schriftart

* **Schriftgröße (sp ↔ px in 2er-Schritten):**
  - `TerminalFontSize.spToPx(sp, density)` rundet exakt auf gerade Pixelzahlen (`px % 2 == 1 -> px - 1`), da die Termux-TerminalView Schriftgrößen ausschließlich in 2-Pixel-Schritten skaliert.
  - `TerminalFontSize.pxToSp` rechnet verlässlich zurück und begrenzt auf `[MIN_SP, MAX_SP]` (8–32 sp).
  - Unit-Test: `TerminalFontSizeTest.sp zu px ist gerade und rechnet zurueck` bestanden.
* **Schriftdatei wählen, JetBrains Mono, Zurücksetzen:**
  - `TerminalFontInstaller.install(InputStream)` prüft den Header (`0x00 0x01 0x00 0x00`, `OTTO`, `true`, `ttcf`) in einer temporären Datei `font.ttf.tmp`, bevor `~/.termux/font.ttf` überschrieben wird. Ungültige Dateien werden abgewiesen; die bestehende Schrift bleibt unberührt.
  - `TerminalSettingsUiEvent.BundledFontChosen` lädt das Asset `fonts/jetbrainsmono_regular.ttf` (JetBrains Mono 2.304, OFL) und installiert es.
  - `TerminalSettingsUiEvent.FontReset` löscht `~/.termux/font.ttf` (`reset()`), sodass Termux auf den Android-Systemstandard zurückgreift.
  - Unit-Test: `TerminalFontInstallerTest` (`gueltige Schrift wird installiert`, `ungueltige Datei wird abgelehnt`, `reset entfernt die Schrift`) bestanden.

---

### Punkt 3: Zusatztasten (Extra-Keys)

* **Presets und Textfeld:**
  - Verfügbare Presets in `ExtraKeysConfig`:
    - Standard: `[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]`
    - Eine Zeile: `[['ESC','TAB','CTRL','ALT',{key: '-', popup: '|'},'DOWN','UP']]`
    - Coding: `[['ESC','TAB','CTRL','ALT','{','}','(',')'], ['/','-','_','=',';','\"','LEFT','RIGHT']]`
* **Formprüfung & Schutz vor ungültiger Eingabe:**
  - `ExtraKeysConfig.isValid(text)` prüft:
    - Äußere Umschließung mit `[[` und `]]`.
    - Ausgeglichene eckige `[...]` und geschweifte `{...}` Klammern.
    - Anführungszeichen `'...'` und `"..."` inklusive Escape-Sequenzen `\"`.
  - Bei ungültiger Eingabe ignoriert `TerminalSettingsViewModel` das Speichern (`isValid(text) == false`); fehlerhafte Syntax kann die Datei `termux.properties` somit nicht beschädigen.
  - Bei Reset wird `extra-keys` aus `termux.properties` entfernt, wodurch Termux-intern wieder das Standard-Layout greift.
  - Unit-Tests: `ExtraKeysConfigTest` (`presetsAreValid`, `rejectsBrokenInput`, `bracketsInsideStringsAreIgnoredAndNewlinesCollapse`) bestanden.

---

### Punkt 4: „Alle Sitzungen beim Schließen beenden“ (kill_sessions_on_exit)

* **Implementierung:**
  - Preference `kill_sessions_on_exit` in `TermuxAppSharedPreferences`.
  - In `TermuxActivity.onDestroy()`:
    ```java
    if (isFinishing() && mPreferences != null && mPreferences.shouldKillSessionsOnExit()) {
        startService(new Intent(this, TermuxService.class)
            .setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE));
    }
    ```
* **Verhalten:**
  - **Echtes Beenden (Zurück-Taste / finish()):** `isFinishing()` ist `true`. `TermuxService` verarbeitet `ACTION_STOP_SERVICE`, führt `killAllTermuxExecutionCommands()` aus (sendet `SIGKILL` an alle PIDs) und beendet den Foreground-Service sowie die Benachrichtigung.
  - **Drehen (Konfigurationsänderung):** Im Android-Lifecycle ist `isFinishing()` bei einer `Activity`-Neuerstellung infolge Bildschirmdrehung `false`. Die Bedingung greift nicht; laufende Prozesse (z. B. `sleep 1000`) und Sessions bleiben erhalten.
  - **Einstellung deaktiviert (Standard):** Der Service läuft nach Verlassen der Activity wie gewohnt im Hintergrund weiter.

---

### Punkt 5: Zwischenablage-Kürzel

* **Implementierung & Trennung:**
  - `com.codeforge.shared.termux.terminal.io.ClipboardShortcuts.resolve(enabled, keyCode, ctrl, shift, alt)`:
    - Liefert `COPY` nur bei `enabled && ctrl && shift && !alt && keyCode == KEYCODE_C`.
    - Liefert `PASTE` nur bei `enabled && ctrl && shift && !alt && keyCode == KEYCODE_V`.
    - Bei `ctrl && !shift && keyCode == KEYCODE_C` (einfaches Strg+C) liefert die Funktion `NONE`.
  - In `TermuxTerminalViewClient.onKeyDown()` wird bei `NONE` die normale Verarbeitung fortgesetzt, sodass Strg+C unverändert als `\x03` (SIGINT) an den Shell-Prozess übertragen wird.
  - Wenn der Schalter ausgeschaltet ist, gibt `resolve()` stets `NONE` zurück.
  - Unit-Tests: `ClipboardShortcutsTest` (`copyAndPasteOnlyWithCtrlShift`, `plainCtrlCStaysInterrupt`, `disabledOrAltPressedDoesNothing`) bestanden.

---

### Punkt 6: Terminal-Sicherung und Wiederherstellung

* **Sicherung (`TerminalBackup.backup`):**
  - Archiviert `$HOME` (`home/…`) und `$PREFIX` (`usr/…`) in ein `.tar.gz` (GNU-Tar-kompatibel).
  - Pfad `usr/tmp` wird explizit übersprungen (`FileVisitResult.SKIP_SUBTREE`).
  - Symlinks werden als Symlink-Header (`tar.writeSymlink`) gesichert.
  - Ausführbare Dateien behalten ihr Executable-Bit (`0b001_000_000`).
  - Ein Authentifizierungs-Marker `codeforge-backup-v1` wird als erste Datei in das Archiv geschrieben.
* **Wiederherstellung & Schutz (`TerminalBackup.restore` / `validate`):**
  - `validate(input)` parst das gesamte Archiv vorab im Speicher, ohne Dateisystemoperationen auszuführen.
  - Beliebige andere Dateien (z. B. Bilder, Fremdarchive) ohne gültigen Marker oder mit unvollständigem Gzip lösen eine `InvalidBackupException("keine CodeForge-Sicherung")` bzw. `("kein gzip")` aus und werden abgewiesen.
  - Zip-Slip-Schutz: Pfadsegmente mit `..`, absolute Pfade oder Pfade außerhalb von `home/` und `usr/` werden sofort verworfen.
  - Vor dem Anlegen von Dateien werden eventuell vorhandene Symlinks am Ziel gelöscht, um Path-Traversal durch Symlinks zu verhindern.
  - Abbrechen (`isCancelled() == true`) wirft eine `BackupCancelledException`, die im ViewModel abgefangen und als „Abgebrochen“ quittiert wird.
* **Unit-Tests:**
  - `TerminalBackupTest` in `:libs:terminal-engine` prüft:
    - Erstellung und Prüfung von Markern, Verzeichnissen, regulären Dateien und Symlinks.
    - Ausschluss von `tmp`.
    - Ablehnung manipulierter Pfade und ungültiger Archive.
  - Tests bestanden (`BUILD SUCCESSFUL in 1m 5s`).

---

## 3. Nicht getestet (Ehrliche Abgrenzung)

Folgende Aspekte konnten in der vorliegenden reinen Termux-CLI-Umgebung **nicht** auf physischer Hardware getestet werden:

1. **Hardware-Tastatur:**
   - Tastendrücke auf einer externen Tastatur (Strg+Umschalt+C / Strg+Umschalt+V) zur Prüfung der physischen KeyEvents.
2. **Visuelle Darstellung im Terminal-Fenster:**
   - Prüfung, ob die Schriftgrößenänderung auf dem Display optisch angenehm wirkt und ob Pinch-Zoom auf dem Touchscreen ruckelfrei bleibt.
3. **Zeitmessung und Dateigröße für vollständiges System-Backup:**
   - Eine echte Vollsicherung des installierten Termux-Userspace (`$PREFIX`) auf Massenspeicher mit konkreter Megabyte- und Sekunden-Messung erfordert eine vollständige Laufzeit-Bootstrap-Installation.
