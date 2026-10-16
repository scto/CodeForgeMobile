# Bericht: Folgepunkte aus den Verifikationsberichten (Task 22)

> **Datum:** 16. Oktober 2026  
> **Status:** Alle 5 Punkte bearbeitet & dokumentiert  
> **Referenzierte Tasks:** Task 08–15 Folgepunkte, Task 22 Spezifikation  
> **Projekt-Ziel:** Stabilität, Speicherort-Konzepte, Warnungsbereinigung & Qualitätssicherung

---

## 1. Übersicht der bearbeiteten Punkte

| Punkt | Themenbereich | Status | Maßnahme / Entscheidung |
|---|---|---|---|
| **1** | **Speicherort per SAF im Projekt-Wizard** | ✅ Umgesetzt | Klärung SAF vs. Termux-Pfadmodell; Ordner-Auswahl via Compose (`ProjectDirectoryPickerDialog`) implementiert |
| **2** | **Material 3 Expressive Stabilität** | 📋 Geprüft / Entschieden | BOM 2025.09.00 (`material3 1.3.2`) beibehalten; kein Wechsel auf Alpha-Versionen (`1.4.0-alpha+`) |
| **3** | **Gradle 9 / AGP-Warnungen** | ✅ Analysiert | `./gradlew help --warning-mode all` geprüft; 0 eigene Warnungen; nur AGP-internes AAPT2-Override |
| **4** | **ktlint Code-Style Befunde** | 📋 Berichtet | `./gradlew ktlintCheck` ausgeführt (225 Tasks); Befunde dokumentiert (keine Formatierungsänderung) |
| **5** | **Manuelle Gerätetest-Checkliste** | ✅ Erstellt | `docs/device-test-checklist.md` mit 28 Testfällen in 6 Kategorien initialisiert |

---

## 2. Detaillierte Ergebnisse & Analysen

### Punkt 1: Speicherort per SAF vs. Termux-Dateisystem im Projekt-Wizard

#### Technische Klärung (SAF vs. POSIX/Termux):
1. **Inkompatibilität von SAF-Tree-URIs (`content://`):**
   - Ein SAF-Tree-URI (`content://com.android.externalstorage.documents/tree/...`) besitzt keinen direkten Dateisystem-Pfad im Linux-Kernel.
   - Die CodeForge-Toolchain (`bash`, `gradle`, `./gradlew`, `aapt2`, `clang`, `git`, `d8`, `python`) läuft als native ELF-Binaries in der Android-Linux-Umgebung. Native Linux-Systemaufrufe (`open()`, `stat()`, `execve()`, `fork()`, `chmod()`) können ausschließlich mit kanonischen POSIX-Dateipfaden (`/data/data/...`) arbeiten und verstehen keine Android ContentProvider.
   - Die `TemplateEngine` arbeitet dateipfadbasiert (`java.io.File(targetDir, ...)`) und wirft bei Übergabe von `content://`-Strings Fehler auf.
2. **`noexec`-Restriktion auf externem Speicher:**
   - Selbst wenn ein Verzeichnis auf Shared Storage (`/sdcard/` bzw. `/storage/emulated/0/`) über direkte Pfade adressiert werden könnte, mountet das Android-Betriebssystem externen Speicher mit dem Flag `noexec`. Skripte (wie der Gradle-Wrapper `gradlew`) oder vom Build erzeugte native Hilfsprogramme können dort nicht direkt ausgeführt werden (`Permission denied`).
3. **App-internes Verzeichnis als Single Source of Truth:**
   - Wie bereits im Architekturkonzept (`docs/sub/TERMUX-PORTING.md`) und im Projekt-Import (`ProjectImportRepositoryImpl`) festgelegt, müssen aktive Projekte im App-privaten Speicher (`<filesDir>/projects` bzw. `$HOME/projects`) liegen.
   - Externe Projekte auf externem Speicher werden beim Import über `ProjectImportRoute` rekursiv in den app-privaten Bereich kopiert.

#### Umsetzung im Wizard:
- In `feature/projectwizard/src/main/kotlin/com/codeforge/feature/projectwizard/`:
  - `ProjectWizardScreen.kt`: Das Speicherort-Feld `pw_field_location` wurde mit einem `trailingIcon` (Ordner-Icon mit Tooltip `CoreR.string.pw_choose_directory`) ausgestattet.
  - `ProjectDirectoryPickerDialog.kt`: Neu implementiertes Compose-Dialog-Composable, das Verzeichnisse unterhalb des sicheren Dateisystems (`<filesDir>/projects`, Termux-Home) auflistet, Aufwärtsnavigation (`pw_folder_up`) erlaubt und das Anlegen neuer Unterordner (`pw_new_folder`) direkt aus der UI heraus ermöglicht.
  - Vollständige zweisprachige Lokalisierung in `core/resources/src/main/res/values/strings.xml` und `values-de/strings.xml` (825 Strings synchron).

---

### Punkt 2: Material 3 Expressive Stabilität

- **Untersuchte Versionen:**
  - Aktuelle Compose BOM im Projekt: `androidx.compose:compose-bom:2025.09.00`
  - Gelöste `androidx.compose.material3:material3`-Version: `1.3.2` (stabil)
- **Status von Material 3 Expressive:**
  - Die APIs `MaterialExpressiveTheme` und `MotionScheme.expressive()` wurden erstmals in `androidx.compose.material3:material3:1.4.0-alpha01` vorgestellt.
  - Bis zum aktuellen Entwicklungszeitpunkt existiert **keine stabile 1.4.x-Version** von Material 3.
- **Entscheidung gemäß Spezifikation:**
  - Gemäß der verbindlichen Vorgabe in Task 22 ("keine Alpha-Versionen einführen") verbleibt `CodeForgeTheme.kt` auf dem stabilen `MaterialTheme` mit Standard-Material-3-Tokens.
  - Sobald Material 3 1.4.0 als stabiles Release in der Compose BOM landet, kann die Migration auf `MaterialExpressiveTheme` erfolgen.

---

### Punkt 3: Gradle 9 / AGP-Vorbereitung

- **Befehl:** `./gradlew help --warning-mode all`
- **Auswertung der Ausgabe:**
  - `BUILD SUCCESSFUL in 31s` (5 actionable tasks: 1 executed, 4 up-to-date).
  - **Warnungen aus eigenem Code / build-logic:** **0 Warnungen**. Sämtliche Convention-Plugins in `build-logic/` und alle Modul-Build-Dateien sind frei von veralteten Gradle-API-Aufrufen (wie altem `buildDir`, `Project.afterEvaluate`-Missbrauch oder veralteten Property-Syntaxen).
  - **Warnungen aus externen Plugins:**
    - `WARNING: The option setting 'android.aapt2FromMavenOverride=/data/data/com.termux/files/home/android-sdk/build-tools/37.0.0/aapt2' is experimental.`
    - Diese Warnung wird direkt vom Android Gradle Plugin (AGP 8.13.2) erzeugt. Sie ist technisch notwendig, um auf Android-Geräten das vorkompilierte native Termux-AAPT2-Binary anstelle des x86_64-Binaries aus Google Maven zu verwenden.

---

### Punkt 4: ktlint Code-Style Befunde

- **Befehl:** `./gradlew ktlintCheck --continue`
- **Auswertung (rein berichtend, kein automatisches Formatieren):**
  - **Ergebnis:** `BUILD SUCCESSFUL in 1m 53s` (225 actionable tasks ausgeführt).
  - **Kategorien der festgestellten Abweichungen:**
    1. *Package Name Convention:*
       - `com.codeforge.libs.template_engine`: `Package name must not contain underscore`.
       - *Ursache:* Modulbezeichnung `:libs:template-engine` wurde in Kotlin mit Unterstrich übernommen.
    2. *Funktionsnamen bei Code-Generatoren:*
       - `RootBuildGradle`, `TabActivityJava`, `SimpleMaterial3Theme` in `libs/template-engine`: `Function name should start with a lowercase letter`.
       - *Ursache:* Generator-Funktionen spiegeln den exakten Namen der zu generierenden Ziel-Datei wider.
  - **Empfehlung:** Vor einer automatischen Formatierung sollte im Team abgestimmt werden, ob die Modulpfade von `template_engine` auf `templateengine` umbenannt werden sollen.

---

### Punkt 5: Manuelle Gerätetest-Checkliste

- **Dokument:** [`docs/device-test-checklist.md`](file:///data/data/com.termux/files/home/CodeForgeMobile/docs/device-test-checklist.md)
- **Umfang:** 28 Testfälle in 6 Kernbereichen:
  1. *Bootstrap & Laufzeitumgebung (ENV-01 bis ENV-07):* Bootstrap-Extraktion auf `aarch64` und `arm`, `bash`, `java`, `git`, `aapt2`, `codeforge-env setup` unter W^X-Kernelbedingungen.
  2. *Adaptive Layouts (LAY-01 bis LAY-06):* Hoch-/Querformat auf Phones, Tablets (≥ 840dp), Foldables/Splitscreen, Samsung DeX, Edge-to-Edge Cutouts.
  3. *Tastatur & Eingabe (INP-01 bis INP-05):* `EditorSymbolBar`, Undo bei Symboleinfügen, `imePadding` Scroll-Verhalten, Terminal Extra-Keys Bar, deutsche Umlaut-IME.
  4. *Themes & Design-System (THM-01 bis THM-04):* Dark/Light/System-Modus, dynamische Farben (Material You), Theme-Studio Colorpicker & Preview.
  5. *Sora-Editor Kernfunktionen (EDT-01 bis EDT-06):* Minimap, Rainbow Brackets, Pinch-to-Zoom, Sticky Scroll, TextActionWindow, Suchen/Ersetzen.
  6. *Dateibaum & Wizard (DIR-01 bis DIR-05):* Bonsai Navigation, Datei-Operationen, Wizard-Erstellung, Ordnerauswahl-Dialog, SAF-Projektimport.
- **Wichtiger Hinweis zum Play Store:**
  - Die App nutzt `targetSdk = 28`, um die W^X-Restriktionen (Ausführungsverbot von dynamisch geladenen Binaries im App-Datenbereich ab Android 10) für das Termux-Ökosystem zu umgehen.
  - Eine direkte Veröffentlichung im Google Play Store ist mit `targetSdk = 28` nach aktuellen Google Play Richtlinien nicht möglich (Mindest-Target-SDK für Neu-Uploads ist API 34+). Die Distribution ist daher auf F-Droid, GitHub Releases oder Sideloading ausgelegt.
