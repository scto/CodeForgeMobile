# Checkliste: Manuelle Gerätetests (CodeForge Mobile)

> **Dokumenttyp:** Test-Spezifikation & Abnahme-Checkliste  
> **Status:** Alle Punkte initial „ungeprüft" (gemäß Task 22)  
> **Zielplattform:** Android 10 (API 29) bis Android 15 (API 35) auf `aarch64` und `arm` (`armeabi-v7a`)  
> **App Target-SDK:** `targetSdk = 28` (erforderlich für W^X Executable-Ausführung der Termux-Binaries)

---

## 1. Übersicht & Testumgebung

Diese Checkliste dient der manuellen Qualitätssicherung auf echten physischen Android-Geräten und Emulatoren. Da automatisierte Unit-Tests in CI/Termux-Umgebungen die hardwarenahe Android-Laufzeit (Display Cutouts, IME-Insets, Touch-Gesten, W^X-Kernel-Restriktionen) nicht vollständig abbilden können, decken diese Tests das reale Endnutzer-Verhalten ab.

### Empfohlene Testgeräte:
- **Phone (Compact):** Physisches Smartphone (z. B. Google Pixel 7/8/9, Samsung Galaxy S23/S24, Android 13–15, `aarch64`)
- **Legacy Phone (32-Bit ARM):** Älteres Gerät oder 32-Bit ARM Emulator (Android 10/11, `armeabi-v7a`)
- **Tablet (Expanded):** Android-Tablet (z. B. Galaxy Tab S8/S9, Pixel Tablet, Displaybreite ≥ 840dp)
- **Foldable / Split-Screen (Medium):** Faltbares Gerät oder Split-Screen Modus (Displaybreite 600–839dp)
- **Desktop-Modus:** Samsung DeX oder Android Freeform Windowing

---

## 2. Test-Matrix

### A. Bootstrap & Termux-Laufzeitumgebung (`codeforge-env`)

| ID | Testfall | Schritte & Szenario | Erwartetes Ergebnis | Testgerät / OS | Status |
|---|---|---|---|---|---|
| **ENV-01** | Bootstrap-Installation `aarch64` | Erststart der App nach Neuinstallation auf 64-Bit ARM-Gerät. Onboarding durchlaufen. | `bootstrap-aarch64.zip` entpackt sauber nach `<filesDir>/usr`; Shell initialisiert fehlerfrei. | Pixel 8 (Android 14, aarch64) | ungeprüft |
| **ENV-02** | Bootstrap-Installation `arm` (32-Bit) | Erststart auf 32-Bit ARM-Gerät oder Emulator. Onboarding durchlaufen. | `bootstrap-arm.zip` entpackt fehlerfrei; 32-Bit Binaries starten ohne Linker-Fehler. | Galaxy A10 (Android 10, arm) | ungeprüft |
| **ENV-03** | `bash` & Executable Start unter `targetSdk 28` | Im Terminal `bash -version` und `uname -a` ausführen. | Bash startet; keine `W^X` SecurityException oder `SIGSEGV` vom Android-Kernel. | Pixel 7 (Android 15, aarch64) | ungeprüft |
| **ENV-04** | Java / OpenJDK Ausführung | Im Terminal `java -version` aufrufen. | OpenJDK 17/21 Runtime gibt Version und VM-Informationen korrekt aus. | Galaxy S23 (Android 14) | ungeprüft |
| **ENV-05** | Git CLI Ausführung | Im Terminal `git --version` und `git status` in Test-Repo aufrufen. | Git liefert Versionsnummer und reagiert auf Befehle ohne Permission-Fehler. | Pixel 8 (Android 14) | ungeprüft |
| **ENV-06** | `aapt2` & Build-Tools Start | Im Terminal `$PREFIX/bin/aapt2 version` aufrufen. | Android Asset Packaging Tool 2 meldet seine Version fehlerfrei. | Pixel 8 (Android 14) | ungeprüft |
| **ENV-07** | `codeforge-env setup` Durchlauf | In den Einstellungen oder im Terminal `codeforge-env setup` anstoßen. | Download & Einrichtung von SDK, JDK und NDK laufen durch; Exit Code 0. | Pixel 8 (Android 14) | ungeprüft |

---

### B. Adaptive Layouts & Formfaktoren

| ID | Testfall | Schritte & Szenario | Erwartetes Ergebnis | Testgerät / OS | Status |
|---|---|---|---|---|---|
| **LAY-01** | Compact Phone (Hochformat) | App auf Standard-Smartphone im Porträt-Modus öffnen. | WelcomeScreen zeigt einspaltige Liste der Projekte; Navigation Drawer ist modal (ausblendbar). | Pixel 8 (Android 14) | ungeprüft |
| **LAY-02** | Compact Phone (Querformat) | Smartphone um 90° ins Querformat drehen. | UI passt sich flüssig an; keine abgeschnittenen Buttons oder unlesbare Texte. | Pixel 8 (Android 14) | ungeprüft |
| **LAY-03** | Tablet (Expanded Layout) | App auf Tablet mit Bildbreite ≥ 840dp starten. | WelcomeScreen nutzt Two-Pane (Liste links, Details/Vorlagen rechts); Drawer im Editor permanent sichtbar. | Galaxy Tab S9 (Android 14) | ungeprüft |
| **LAY-04** | Foldable / Split-Screen (Medium) | App im Android-Splitscreen oder auf Foldable (Cover Screen vs. Inner Screen) betreiben. | `rememberIsExpandedWidth()` schaltet dynamisch zwischen modalem und permanentem Drawer um; kein Absturz. | Galaxy Z Fold 5 (Android 14) | ungeprüft |
| **LAY-05** | Desktop-Modus (Samsung DeX) | Gerät per DeX an Monitor anschließen, Fenstergröße beliebig verändern. | Responsive Neuanordnung der Panes; kein Recompose-Flackern oder Memory-Leak. | Galaxy S24 DeX (Android 14) | ungeprüft |
| **LAY-06** | Edge-to-Edge & Display Cutouts | Gerät mit Notch / Kamera-Loch im Querformat drehen. | Status-/Navleisten transparent; Inhalt liegt nicht unleserlich unter Cutouts (`shortEdges`). | Pixel 8 (Android 14) | ungeprüft |

---

### C. Eingabe, Tastatur & Bildschirmleisten

| ID | Testfall | Schritte & Szenario | Erwartetes Ergebnis | Testgerät / OS | Status |
|---|---|---|---|---|---|
| **INP-01** | Editor: Symbolleiste (`EditorSymbolBar`) | Textdatei im Sora-Editor öffnen und Bildschirmtastatur einblenden. | Symbolleiste erscheint bündig über der Tastatur; Tabs (Kotlin, Java, Python, XML, Symbole) umschaltbar. | Pixel 8 (Android 14) | ungeprüft |
| **INP-02** | Editor: Symbol-Einfügen & Undo | Beliebiges Symbol (`{`, `}`, `->`, `;`) in der Symbolleiste antippen. | Symbol wird exakt an Cursorposition eingefügt; Undo-Button macht den Einschub rückgängig. | Pixel 8 (Android 14) | ungeprüft |
| **INP-03** | Editor: Cursorsichtbarkeit bei IME | Langen Text editieren und Cursor an das untere Bildschirmende setzen; Tastatur öffnen. | Editor scrollt automatisch hoch (`imePadding`); Cursor bleibt vollständig im sichtbaren Bereich. | Pixel 8 (Android 14) | ungeprüft |
| **INP-04** | Terminal: Extra-Keys Bar | Terminal öffnen und Tastatur einblenden. | Tasten `ESC`, `TAB`, `CTRL`, `ALT`, `↑`, `↓`, `←`, `→` reagieren; `CTRL+C` bricht laufenden Prozess ab. | Pixel 8 (Android 14) | ungeprüft |
| **INP-05** | Terminal: IME-Texteingabe | Deutsche Tastatur mit Umlauten (`ä`, `ö`, `ü`, `ß`) und Autokorrektur im Terminal verwenden. | Zeichen erscheinen unverfälscht in der Shell; keine verdoppelten Eingaben oder Phantomzeichen. | Pixel 8 (Android 14) | ungeprüft |

---

### D. Themes, Farben & Design-System

| ID | Testfall | Schritte & Szenario | Erwartetes Ergebnis | Testgerät / OS | Status |
|---|---|---|---|---|---|
| **THM-01** | Dunkel-/Hell-Modus Wechsel | In Einstellungen `Dark` ↔ `Light` umschalten. | Alle Bildschirme, Editor, Menüs und Dialoge passen Farben sofort an; Statusleisten-Icons invertieren korrekt. | Pixel 8 (Android 14) | ungeprüft |
| **THM-02** | System-Theme Folge | Einstellung auf `System` setzen; Android-Systemtheme in den Schnelleinstellungen wechseln. | App folgt ohne Neustart sofort dem Systemzustand. | Pixel 8 (Android 14) | ungeprüft |
| **THM-03** | Dynamische Farben (Material You) | Auf Android 12+ `Dynamische Farben` aktivieren; System-Wallpaper wechseln. | Akzentfarben der App passen sich harmonisch an die Wallpaper-Palette an. | Pixel 8 (Android 14) | ungeprüft |
| **THM-04** | Theme-Studio: Colorpicker & Presets | In Einstellungen → Theme ein Preset wählen oder RGB-Slider manuell verändern. | Horizontal Preview zeigt Echtzeit-Vorschau der Komponenten; Farbwerte werden persistent gespeichert. | Galaxy Tab S9 (Android 14) | ungeprüft |

---

### E. Sora-Editor Kernfunktionen

| ID | Testfall | Schritte & Szenario | Erwartetes Ergebnis | Testgerät / OS | Status |
|---|---|---|---|---|---|
| **EDT-01** | Minimap Navigation | Große Datei (> 500 Zeilen) öffnen; Minimap aktivieren. | Minimap zeigt Miniaturvorschau am rechten Rand; Scrollen per Wischen auf der Minimap funktioniert flüssig. | Pixel 8 (Android 14) | ungeprüft |
| **EDT-02** | Rainbow Brackets Matching | Verschachtelten Code mit mehreren Klammerebenen `(( { [ ] } ))` betrachten. | Gepaarte Klammern erhalten identische Farben; Cursor auf Klammer hebt Partner hervor. | Pixel 8 (Android 14) | ungeprüft |
| **EDT-03** | Pinch-to-Zoom (Schriftgröße) | Mit zwei Fingern im Editor hinein- oder herauszoomen. | Schriftgröße ändert sich stufenlos und flüssig; Layout passt sich an; neue Größe bleibt erhalten. | Galaxy Tab S9 (Android 14) | ungeprüft |
| **EDT-04** | Sticky Scroll Header | Durch lange Klasse mit vielen Methoden scrollen. | Klassen- und Methodenkopf bleiben oben am Editorrand angeheftet (`Sticky Scroll`), solange Scope aktiv ist. | Pixel 8 (Android 14) | ungeprüft |
| **EDT-05** | TextActionWindow (Kontextleiste) | Wort durch Doppeltippen oder langes Drücken markieren. | Sora-Aktionsleiste erscheint mit `Kopieren`, `Ausschneiden`, `Einfügen`, `Alles auswählen`. | Pixel 8 (Android 14) | ungeprüft |
| **EDT-06** | In-Editor Suchen & Ersetzen | Suchleiste im Editor öffnen (`Strg+F` oder Menü); Begriff mit Regex suchen und ersetzen. | Treffer werden im Text markiert; Weiterschalten (`Vorige`/`Nächste`) und Ersetzen funktionieren präzise. | Pixel 8 (Android 14) | ungeprüft |

---

### F. Dateibaum (Bonsai), Dialoge & Projekt-Wizard

| ID | Testfall | Schritte & Szenario | Erwartetes Ergebnis | Testgerät / OS | Status |
|---|---|---|---|---|---|
| **DIR-01** | Dateibaum Navigation | Projekt im Workspace öffnen; Verzeichnisbaum im Drawer auf- und zuklappen. | Ordnerstrukturen öffnen/schließen sich verzögerungsfrei; Datei-Icons stimmen mit Dateityp überein. | Pixel 8 (Android 14) | ungeprüft |
| **DIR-02** | Datei-Operationen (Neu / Rename / Delete) | Über Kontextmenü im Dateibaum: neue Datei anlegen, umbenennen, löschen. | Änderungen erscheinen sofort im Baum; gelöschte Dateien verschwinden aus geöffneten Tabs. | Pixel 8 (Android 14) | ungeprüft |
| **DIR-03** | Projekt-Wizard: Projekt-Erstellung | Projekt-Wizard öffnen, Vorlage `Empty Activity` wählen, Projektname eingeben, erstellen. | Projekt wird in `<filesDir>/projects` angelegt; Gradle-Struktur vorhanden; Editor öffnet Projekt automatisch. | Pixel 8 (Android 14) | ungeprüft |
| **DIR-04** | Projekt-Wizard: Ordnerauswahl | Im Wizard-Konfigurationsschritt den Button für die Speicherort-Auswahl betätigen. | Ordnerauswahl öffnet sich; Verzeichnisse unter `<filesDir>` können gewählt oder neu erstellt werden. | Pixel 8 (Android 14) | ungeprüft |
| **DIR-05** | Projekt-Import (SAF) | In WelcomeScreen auf „Importieren" tippen und externen Ordner via SAF auswählen. | Verzeichnis wird rekursiv nach `<filesDir>/projects` kopiert; Fortschrittsanzeige; Projekt öffnet sich. | Pixel 8 (Android 14) | ungeprüft |

---

## 3. Protokollierung

Ergebnisse aus manuellen Testdurchläufen sind im Format:
`[DATUM] [TEST-ID] [GERÄT] [BESTANDEN/FEHLGESCHLAGEN] [KOMMENTAR / BUG-ID]`
in dieses Dokument oder in die GitHub Issues einzutragen.
