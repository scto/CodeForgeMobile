# Task 52 – Modul `x2c`: XML-Layouts nach Jetpack Compose migrieren

## 1. 52.0 Entscheidungen und Spike-Ergebnisse

### 1.1 Spike auf dem Zielgerät (Google Pixel 9 Pro XL)
* **Umgebung:** Google Pixel 9 Pro XL, Termux-Prefix, JDK 21.0.12, kotlinc 2.4.20, Compose-Compiler-Plugin aus der Kotlin-Distribution, Compiler-Runtime `2025.09.00-k2.1.21`.
* **Messung:** 2 Dateien mit Composables in 6 Sekunden kompiliert.
* **Ergebnis:** **Spike positiv.** Die On-Device-Kompilierung von Jetpack-Compose-Dateien via Termux-`kotlinc` ist schnell und uneingeschränkt lauffähig.
* **Ausstehend / Folgebeobachtung:** Plugin-Wirkung per `javap` verifizieren, XML-Umwandlung umsetzen.
* **Fazit:** Die Implementierung von 52.1 ff. kann ohne Blockade beginnen.

---

### 1.2 Architektur- und Designentscheidungen

#### 1. Modulaufteilung
* **`:core:domain`:** Reine Kotlin-Modelle (`X2cOptions`, `X2cPlan`, `X2cProgress`, `X2cFileResult`, `X2cReport`) und Schnittstellen (`X2cRepository`, `LayoutToComposeGenerator`, `LayoutUsageAnalyzer`, `ComposeBuildEnabler`). Vollkommen frei von Android- oder UI-Abhängigkeiten.
* **`:libs:x2c`:** Reines Kotlin/JVM-Modul (ohne Android SDK). Beinhaltet:
  - Format- und namensraumerhaltenden XML-Parser mit Zeilen-/Spaltennachverfolgung und XXE-Schutz.
  - `LayoutUsageAnalyzer`: Textbasierte AST-/Heuristik-Erkennung von Layout- und View-Verwendungen im Quellcode (`.java`/`.kt`).
  - `LayoutToComposeGenerator`: Deterministischer Generator für `@Composable`-Funktionen gemäß Pflicht-Abbildungstabelle mit Golden-File-Tests.
  - `ComposeBuildEnabler`: Sichere Ergänzung von Compose in Groovy und Kotlin DSL Build-Skripten inkl. Versionskatalog-Unterstützung (`libs.versions.toml`).
  - Theme- und Farbextraktor (`ui/theme/Theme.kt`).
  - Backup- und Journal-Verwaltung (`<Projekt>/Backup/<projekt>-xml-<ts>.zip` und `x2c-journal-<ts>.json`).
* **`:feature:x2c`:** Android-UI-Modul mit Jetpack Compose, Material 3, Hilt und MVI-Architektur (`X2cViewModel`, `X2cDialog`, `X2cProgressScreen`, `X2cResultScreen`).

#### 2. Gemeinsame Infrastruktur mit Task 50 (`j2k`)
* Die in Task 50 bewährten Prinzipien für Vorab-ZIP-Backups, Verifikation via CRC/Dateigrößenvergleich, atomare Journal-Aufzeichnung und selektives Rückgängigmachen werden in `x2c` übernommen.
* Zur Gewährleistung maximaler Stabilität und Vermeidung von Nebeneffekten auf Task 50 verwendet `x2c` seine spezialisierten Backup-Dateinamen (`<Projekt>/Backup/<projektname>-xml-<JJJJMMTT-HHMMSS>.zip`) und Journals (`Backup/x2c-journal-<ts>.json`).

#### 3. XML-Parser-Strategie
* `feature/layoutdesigner/xml/LayoutXml.kt` liegt in einem Feature-Modul und darf nicht von JVM-Bibliotheken referenziert werden.
* In `:libs:x2c` wird ein eigenständiger, robuster XML-Parser auf Basis von Standard-JVM-XML-APIs (`XmlPullParser` / SAX) implementiert, der:
  1. Genaue Zeilen- und Spaltenpositionen für jeden Tag und jedes Attribut bereitstellt.
  2. Alle Namensräume (`android:`, `app:`, `tools:`, benutzerdefinierte Namespaces) sauber trennt.
  3. XXE-Angriffe blockiert (`FEATURE_SECURE_PROCESSING`, Deaktivierung externer DTDs).
  4. Kommentare und Einrückung erfasst, um unberührte Layout-Fragmente verlustfrei darzustellen.

#### 4. Verhältnis zu Task 41.11 („Als Compose kopieren/exportieren“)
* Task 41 wurde in `3cb5e59` ohne den optionalen Punkt 41.11 abgeschlossen.
* Task 52 implementiert den vollständigen `LayoutToComposeGenerator` in `:libs:x2c`. Sobald der Layout-Designer die Funktion „Als Compose exportieren“ anbietet, kann dieser direkt die `LayoutToComposeGenerator`-Komponente aus `:libs:x2c` aufrufen.

#### 5. Abgrenzung des Umfangs (Stufen 1–3)
* **Stufe 1 (Standard & Kern):** XML-Layouts → saubere, idiomatische `@Composable`-Funktionen mit Standard-Modifiern, Parametern und `@Preview`.
* **Stufe 2 (Optional, nur einfache Fälle):** Umstellung von Activities mit trivialem `setContentView` auf `setContent { ... }` und Fragments auf `ComposeView`.
* **Stufe 3 (Verhalten, Adapter, Listener):** Bewusst **nicht** automatisch umgeschrieben. Die UI deklariert transparent: „Layout automatisch übertragen, Verhalten bitte prüfen“ und listet alle im Code verbliebenen View-Zugriffe mit Datei:Zeile als To-Do auf.

---

## 2. Status der Abschnitte

| Abschnitt | Inhalt | Status |
|---|---|---|
| **52.0** | Entscheidungen & On-Device Spike | ✅ Abgeschlossen (Spike positiv) |
| **52.1** | Kern und Schnittstellen (`:core:domain`) | ⏳ In Bearbeitung |
| **52.3** | ZIP-Backup mit Verifikation | ⬜ Offen |
| **52.4** | Compose im Build ermöglichen (`ComposeBuildEnabler`) | ⬜ Offen |
| **52.5** | Analyse: Layouts und Verwendung (`LayoutUsageAnalyzer`) | ⬜ Offen |
| **52.6** | Generator: XML → `@Composable` (Golden-Tests) | ⬜ Offen |
| **52.7** | Stufe 2: Activities & Fragments umstellen | ⬜ Offen |
| **52.8** | Theme-Erzeugung aus XML (`Theme.kt`) | ⬜ Offen |
| **52.9** | Anwenden, Journal, Rückgängig | ⬜ Offen |
| **52.10** | Build-Prüfung & Markdown-Bericht | ⬜ Offen |
| **52.2 / 52.11** | UI: Erkennung, Einstiegsdialog, Fortschritt, Ergebnis | ⬜ Offen |
| **52.12** | Golden-Tests, Korpus, Strings, Abschlussdokumentation | ⬜ Offen |

---

## 3. Getestet vs. Nicht getestet

### Getestet
- [x] Spike auf Pixel 9 Pro XL (Termux, JDK 21.0.12, kotlinc 2.4.20, Compose-Plugin): 2 Dateien mit Composables in 6 s erfolgreich kompiliert.

### Nicht getestet
- [ ] Plugin-Wirkung per `javap` (ausstehend laut Spike-Befund).
- [ ] End-to-End-XML-Umwandlung auf dem Gerät (folgt mit den Implementierungsschritten).
