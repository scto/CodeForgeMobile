# Abschlussbericht Task 50: Java to Kotlin Migration (`j2k`)

**Datum:** 2026-10-25  
**Autor:** Antigravity / CodeForge Mobile  
**Modul:** `:libs:j2k`, `:feature:j2k`

---

## 1. Übersicht & Zielsetzung

Task 50 implementiert die vollständige Unterstützung zur Migration von Java-Projekten nach Kotlin innerhalb von CodeForge Mobile:
1. **Erkennung:** Scannen von Projekten auf Java- und Kotlin-Dateien; automatischer Dialog-Vorschlag, wenn der Java-Anteil an den JVM-Quellzeilen mindestens 70 % beträgt.
2. **Sicherung & Journal:** Vor jeder Umwandlung wird ein verifiziertes ZIP-Backup unter `Backup/<projekt>-java-<JJJJMMTT-HHMMSS>.zip` erstellt. Jede Dateiänderung wird in einem atomaren Journal `Backup/j2k-journal-<ts>.json` mit Prüfsummen protokolliert und kann vollständig rückgängig gemacht werden.
3. **Java → Kotlin Konverter:** Regelbasierte Transformation aller typischen Java-Konstrukte (Klassen, Interfaces, Enums, Records, Annotationen, Eigenschaften, Konstruktoren, Lambdas, Generics, Kontrollfluss, Try-with-resources, Nullbarkeitshinweise).
4. **Gradle Groovy → Kotlin DSL:** Optionale Konvertierung von `.gradle` zu `.gradle.kts` (Plugins, Abhängigkeiten, Repositories, Android-DSL-Blöcke).
5. **Versionskatalog (50.6a):** Optionale automatische Erstellung/Ergänzung von `gradle/libs.versions.toml` und Migration von Abhängigkeiten auf `libs.*`.
6. **UI & ViewModel:** Material 3 Dialog (`J2kDialog`), Fortschrittsanzeige (`J2kProgressScreen`), und detaillierte Ergebnisansicht (`J2kResultScreen`) mit Klick-Navigation via `OpenFileRequestBridge` und Rückgängig-Aktion.

---

## 2. Architekturentscheidungen (50.0)

### 2.1 Modulaufteilung
- `:libs:j2k`: Reines Kotlin/JVM-Modul (ohne Android-Abhängigkeiten) für Konverter, Scanner, Backup-Engine, Journal und Berichterstellung. Dies garantiert Unabhängigkeit von Android-Ressourcen und extrem schnelle, isolierte Ausführung in JVM-Unit-Tests.
- `:feature:j2k`: Android-Library mit Jetpack Compose UI, Material 3, Hilt-Dependency-Injection und `J2kViewModel`.
- `:core:domain`: Bindet `:libs:j2k` ein (`api(project(":libs:j2k"))`), sodass die Domain-Modelle und Interfaces (`J2kRepository`, `J2kOptions`, `J2kPlan`, `J2kReport`, etc.) repo-weit zur Verfügung stehen.

### 2.2 Konverter-Technik Java → Kotlin
- **JetBrains J2K:** Ist fester Bestandteil des IntelliJ IDEA / Android Studio Kotlin-Plugins und nicht als isoliertes Maven-Artefakt verfügbar (Quelle: JetBrains Kotlin GitHub Repo, `kotlin-compiler-embeddable` enthält kein J2K-Tooling).
- **Entscheidung:** Eigener regelbasierter Token- und Syntaxmuster-Konverter (`JavaToKotlinConverterImpl`). Er arbeitet vollkommen deterministisch, benötigt keine Netzwerkverbindung (im Gegensatz zu KI-Lösungen) und fügt dem APK nur wenige Kilobytes hinzu. Unsichere Stellen werden transparent als `// TODO(j2k)` markiert, anstatt fehlerhafte Annahmen zu treffen.

### 2.3 Konverter-Technik Groovy → Kotlin DSL
- **Entscheidung:** Eigener toleranter Token- und Block-Transformator (`GradleDslConverterImpl`). Konvertiert Quoting, Methodenaufrufe ohne Klammern, `project(...)`, Android-DSL (`compileSdk`, `isMinifyEnabled = true`, `getByName("release")`) und setzt bei unbekannten Konstrukten `// TODO(j2k-gradle)`.

### 2.4 Versionskatalog (50.6a)
- **Mindestversion:** Gradle 7.4+ (stabil ab Version 7.4, 2022-02; Quelle: Gradle Release Notes 7.4).
- Extrahiert statisch auflösbare Strings (`group:artifact:version`), Maps und Variablen in `gradle/libs.versions.toml` (`[versions]`, `[libraries]`, `[plugins]`). Bestehende Kataloge werden textuell ergänzt; Versionskonflikte werden im Bericht ausgewiesen, ohne bestehende Konfigurationen still zu überschreiben.

---

## 3. Getestet (Automatisierte Tests & Verifikation)

Folgende automatisierte Unit-Tests wurden implementiert und erfolgreich ausgeführt:

1. **`JavaToKotlinConverterTest` (`:libs:j2k`):**
   - Umwandlung von Klassen, Feldern (`final` → `val`, mutable → `var`) und Methoden (`getName()`, `setAge()`).
   - Umwandlung von Interfaces (`void greet(String name);` → `fun greet(name: String)`) und Enums (`enum class Color`).
   - Kontrollfluss-Transformation: `for (int i = 0; i < n; i++)` → `for (i in 0 until n)`, For-Each-Schleifen.
   - Try-with-resources: `try (FileInputStream fis = new FileInputStream("file.txt"))` → `FileInputStream("file.txt").use { fis -> ... }`.
2. **`GradleDslConverterTest` (`:libs:j2k`):**
   - Groovy `plugins {}`, `apply plugin:`, `buildTypes { release { minifyEnabled true } }` → `getByName("release") { isMinifyEnabled = true }`.
   - String-Quoting und Methodenaufrufe mit Klammern.
3. **`VersionCatalogMigratorTest` (`:libs:j2k`):**
   - Extraktion von Abhängigkeiten in TOML (`[libraries]`, `[plugins]`).
   - Aktualisierung von Gradle-Skripten auf `libs.*`.
4. **`ProjectBackupRepositoryTest` (`:libs:j2k`):**
   - Erstellung des ZIP-Archivs im Ordner `Backup/`.
   - Ausschluss von `build/`, `.gradle/`, `.cxx/`, `node_modules/`, `Backup/`.
   - Verifikation und Wiederherstellung.
5. **`ProjectLanguageScannerTest` (`:libs:j2k`):**
   - 70%-Schwelle für Java-Anteil.
   - Ausschluss von generierten Quellen, `package-info.java` und `module-info.java`.
6. **`J2kViewModelTest` (`:feature:j2k`):**
   - MVI StateFlow-Handling, Dialoganzeige bei >= 70% Java.
   - Umschalten von Optionen (Groovy DSL, Version Catalog, Build Check, Git Branch).
   - Ausführung der Migration, Ergebnis-Filterung und `OpenFileRequestBridge`-Integration.
7. **Lokalisierung:**
   - `python3 -I scripts/check_string_locales.py` mit 0 Fehlern bestanden (1520 Strings synchron in `values` und `values-de`).

---

## 4. Nicht getestet (Endgeräte- und Live-Tests)

- Echte Ausführung eines vollständigen Gradle-Builds auf dem Android-Gerät (Pixel 9 Pro XL, Android 17 durch Inhaber).
- Interaktives Öffnen des `J2kDialog` beim realen Dateibaum-Import oder `git clone` auf einem Android-Gerät.
- Verhalten bei gigantischen Multi-Module-Projekten (> 100.000 Zeilen Java).

---

## 5. Offene Punkte

- Optional: KI-gestützte Nachbearbeitung (Task 38) als nachgelagerter, freiwilliger Schritt für komplexe Algorithmen.
- Erweiterung des Versionskatalog-Generators um Bündel (`[bundles]`).
