# Task 50 – Modul `j2k`: Java-Projekte nach Kotlin migrieren (optional mit Gradle Groovy → Kotlin DSL)

**Ziel:** Importiert der Benutzer ein Projekt (Dateiauswahl oder `git clone`) und ist es überwiegend in Java geschrieben, bietet die IDE die Umstellung nach Kotlin an. Gibt es Gradle-Dateien in Groovy, zeigt der Dialog zusätzlich einen Schalter „Gradle-Dateien nach Kotlin DSL konvertieren“. Ein weiterer Schalter „Versionskatalog erstellen und verwenden“ scannt die Gradle-Dateien des Projekts, legt `gradle/libs.versions.toml` an und stellt die Gradle-Dateien auf den Katalog um (50.6a). Bei „Konvertieren“ wird zuerst ein ZIP-Backup des Originals unter `Backup/` im Projektordner angelegt, dann das ganze Projekt umgestellt (alle Java-Quelldateien; bei aktivem Schalter alle Gradle-Dateien projektweit; bei aktivem Katalog-Schalter zusätzlich die Abhängigkeiten und Plugins), anschließend per Gradle geprüft und ein Bericht erzeugt. Jeder Schritt ist rückgängig zu machen.
**Warum jetzt:** Auftrag des Inhabers (2026-10-24). Neues Modul **j2k** (Aufteilung siehe 50.0).
**Ehrlichkeit gegenüber dem Benutzer:** Eine automatische Umwandlung ist nie ganz fehlerfrei. Weder Task noch UI behaupten „gleichwertig“ oder „fehlerfrei“; die UI sagt „automatisch umgewandelt, bitte prüfen“ und zeigt das Ergebnis der Build-Prüfung.

**Gemeinsame Regeln (gelten für jeden Task):**
* Clean Architecture wie im Rest des Repos; UI-States `@Immutable`, MVI/`StateFlow`, Material 3, `modifier: Modifier = Modifier` als erster optionaler Parameter, keine Logik in Composables. Gradle Kotlin DSL, Versionen im Version Catalog.
* Alle UI-Texte über `:core:resources` (Englisch Standard, Deutsch in `values-de`); der String-Check (Task 17) bleibt grün.
* Nichts erfinden: Was du nicht selbst ausgeführt hast, steht im Bericht unter **nicht getestet** (Gerätetests: Pixel 9 Pro XL, Android 17, durch den Inhaber). Aussagen dieses Tasks über den Bestand stammen aus einer Durchsicht eines Repo-Snapshots; prüfe sie vor dem Start selbst und korrigiere sie im Bericht. Aussagen über Bibliotheken (Parser, Lizenzen, Android-Tauglichkeit) belegst du mit Quelle und Datum, nicht aus dem Gedächtnis.
* Geheimnisse (Schlüsselspeicher, Passwörter, Token) nie ins Log, nie in Berichte, nie ins Backup-ZIP, wenn sie nicht ohnehin Teil des Projektordners sind (siehe 50.3). Keine Pushes auf GitHub ohne Auftrag des Inhabers.
* Projekte aus `git clone` sind **nicht vertrauenswürdig**: Die Umwandlung selbst führt keinen Projektcode aus. Nur die Build-Prüfung (50.8) startet Gradle und damit die Build-Skripte des Projekts; sie ist ein eigener, ausdrücklich bestätigter Schritt.
* Bericht in `docs/j2k-report.md` mit Abschnitten **getestet / nicht getestet / offene Punkte**; `agy-tasks/TASKS-STATUS.md` und `agy-tasks/README.md` ergänzen (Commit-Hash). Doku: `docs/app/` (Task 99) um eine Seite `j2k.md` ergänzen, sobald vorhanden.
* Build grün: `./gradlew :app:assembleDebug` und die neuen Modultests.

**Reihenfolge:** 50.0 (Spike) zuerst und einzeln committen. Danach 50.1 → 50.3 → 50.4 → 50.5 und 50.6 (parallel möglich) → 50.6a (nach 50.6, wenn beide Schalter aktiv sind, auf den dann vorhandenen Dateien) → 50.7 → 50.8 → 50.2 und 50.9 (UI) → 50.10.

---

## 50.0 Entscheidungen und Spike (vor allem anderen, Ergebnis in den Bericht)

1. **Modulaufteilung** (Vorschlag, passend zu den Regeln des Repos; der Inhaber nannte „ein Submodul j2k“): `:libs:j2k` (Kern: reines Kotlin/JVM, ohne Android, enthält Konverter, Sicherung, Journal, Berichte) und `:feature:j2k` (UI, ViewModel, Dialog, Foreground-Service). Schnittstellen (`J2kRepository` o. ä.) und Modelle in `:core:domain`. Sollte der Inhaber ein einziges Modul bevorzugen, ist das mit dieser Trennung innerhalb eines Pakets `com.codeforge.j2k` leicht zusammenzulegen; dokumentiere die Entscheidung.
2. **Konverter-Technik für Java → Kotlin.** Bewerte mindestens: (a) eigener regelbasierter Konverter auf einem Java-Parser (z. B. JavaParser, falls Lizenz, Java-Sprachstand und Android-Tauglichkeit passen), (b) Parser über die vorhandene Tree-sitter-Anbindung (Task 02/20: nur Syntaxbaum, keine Typauflösung), (c) externe Prozesse in Termux (wie `kotlinc` in Task 27a, `ktlint` in Task 32), (d) KI-gestützt (Task 38; Quelltext verlässt das Gerät, nicht deterministisch: **nicht** als Kern, höchstens später als freiwillige Nachbesserung). Der JetBrains-Konverter steckt nach meinem Kenntnisstand im Kotlin-Plugin von IntelliJ und ist keine einzeln einbindbare Bibliothek: **prüfe das** (Quelle, Datum) und nimm ihn nur, wenn er sich lizenz- und technisch sauber einbinden lässt. Maßstab: Qualität auf dem Beispielkorpus (50.10), Laufzeit auf dem Gerät, APK-Größe, Lizenz, Wartbarkeit. Entscheidung begründen.
3. **Technik für Groovy → Kotlin DSL.** Bewerte: (a) die Groovy-Grammatik (Apache Groovy-Parser, als Prozess in Termux oder als Bibliothek), (b) ein eigener toleranter Tokenizer für die Gradle-Subsprache (Blöcke, Aufrufe ohne Klammern, Listen/Maps, Strings). Gradle selbst liefert nach meinem Kenntnisstand keine Konvertierungsfunktion; die Gradle-Dokumentation hat nur eine Migrationsanleitung (Quelle prüfen und im Bericht nennen).
4. **Spike auf dem Gerät** (der Inhaber führt aus; du lieferst Skript und Anleitung): Ein kleines Java-Projekt mit Groovy-Gradle wird mit dem gewählten Verfahren umgewandelt und in Termux gebaut. Dauer, Speicherbedarf und Ergebnis kommen in den Bericht (Muster: Task 27a, 37a). Fällt der Spike negativ aus, melde das und stoppe den Task, bevor UI gebaut wird.

## 50.1 Kern und Schnittstellen

* `:core:domain`: Modelle (`J2kOptions` mit `convertGradle`, `useVersionCatalog`, `includeGit`, `runBuildCheck`, `createGitBranch`; `J2kPlan` mit Dateilisten; `J2kProgress` als `@Immutable` Sealed-Interface mit Phasen; `J2kFileResult` mit Status `CONVERTED`, `CONVERTED_WITH_NOTES`, `SKIPPED`, `FAILED`; `J2kReport`), Schnittstellen `J2kRepository`, `ProjectBackupRepository`, `JavaToKotlinConverter`, `GradleDslConverter`, `J2kBuildVerifier`. Keine Android-Abhängigkeit in `:core:domain`.
* Pro Lauf ein **Plan** (reine Analyse ohne Schreiben): Liste der Java-Dateien je Modul/Source-Set mit Zeilenzahl, Liste der Gradle-Dateien (Groovy/Kotlin DSL), erkannte Hindernisse (50.5/50.6). Der Plan ist die Grundlage von Dialog und Bericht und kann ohne Seiteneffekt wiederholt erzeugt werden.
* Abbruch (`CancellationException`) ist in jeder Phase sicher (siehe 50.7).

## 50.2 Erkennung, Einstieg und Dialog

1. **Einstiegspunkte:** nach erfolgreichem Import per Dateiauswahl und nach erfolgreichem `git clone`; zusätzlich manuell im Projektmenü („In Kotlin umwandeln …“) und in den Projekteinstellungen (Task 47.4, falls vorhanden). Finde die Stellen im Repo selbst (Projekt öffnen/Workspace, Git-Clone-Abschluss, Wizard); nenne sie im Bericht.
2. **Erkennung (`ProjectLanguageScanner`, reines Kotlin, getestet):** zählt `.java`/`.kt`-Dateien und Zeilen in Source-Sets (`src/*/java`, `src/*/kotlin`, Flavor-Varianten), ignoriert `build/`, `.gradle/`, `Backup/`, generierte Quellen und `module-info.java`/`package-info.java`. Gradle: `build.gradle`, `settings.gradle`, `*.gradle` in Unterprojekten, `buildSrc`, mit `apply from:` eingebundene Skripte; vorhandene `.gradle.kts` zählen als „schon Kotlin DSL“.
3. **Automatische Nachfrage** nur, wenn Java-Anteil an den JVM-Quellzeilen mindestens 70 % beträgt (Wert in einer Konstante, im Bericht begründen) und für das Projekt nicht „Nicht mehr fragen“ gesetzt ist; sonst nur über das Menü.
4. **Dialog (Material 3):** Zusammenfassung („N Java-Dateien in M Modulen“); Hinweis, wohin das Backup geht; bei vorhandenen Groovy-Gradle-Dateien der Schalter **„Gradle-Dateien nach Kotlin DSL konvertieren“** (Standard: aus, Zähler der betroffenen Dateien); Untertext, dass Kotlin-Unterstützung in den Build-Dateien so oder so ergänzt wird (50.4); weiterer Schalter **„Versionskatalog erstellen und verwenden“** (Standard: aus; sichtbar, wenn Gradle-Dateien vorhanden sind, unabhängig vom Groovy-Schalter, weil Kataloge in Groovy und Kotlin DSL funktionieren; ist `gradle/libs.versions.toml` schon da, lautet er „Versionskatalog ergänzen“; ist die Gradle-Version des Projekts zu alt, ist er ausgeschaltet mit Erklärung, siehe 50.6a); Schalter „Nach dem Umwandeln bauen und prüfen“ (Standard: an, Hinweis: führt Projekt-Skripte aus); optional „Git-Zweig anlegen“ (nur bei Git-Projekt mit sauberem Arbeitsverzeichnis). Schaltflächen: **Konvertieren**, **Später**, **Nicht mehr fragen**. Ist keine Java-Datei da oder alles Kotlin, erscheint der Dialog nicht, das Menü zeigt einen erklärenden Hinweis.
5. **Gespeichert** wird je Projekt nur „Nicht mehr fragen“ (Proto/DataStore wie die übrigen Einstellungen; Feldnummern in `settings.proto` nicht wiederverwenden).

## 50.3 Sicherung als ZIP

* Ziel: `<Projekt>/Backup/<projektname>-java-<JJJJMMTT-HHMMSS>.zip` (Ordner anlegen, falls nötig). Pfade im ZIP relativ zum Projekt, UTF-8-Namen, Zeitstempel der Dateien erhalten.
* **Inhalt:** das ganze Projekt **ohne** `Backup/` selbst und ohne wiederherstellbare Build-Verzeichnisse (`**/build/`, `.gradle/`, `.cxx/`, `**/node_modules/`). `.git/` gehört standardmäßig **hinein** (vollständiges Original; Größe im Dialog anzeigen, Schalter „.git einschließen“). Die Ausschlussliste steht in einer Konstante und im Bericht. Symbolische Links werden nicht verfolgt (als Link oder gar nicht, begründen).
* **Vor** dem Schreiben: freier Speicher mindestens das Doppelte der geschätzten Größe, sonst klare Meldung.
* **Nach** dem Schreiben: ZIP wieder öffnen, Eintragsanzahl und für alle Quelldateien CRC/Größe gegen das Original prüfen. **Schlägt das fehl, wird nichts umgewandelt** (Abbruch vor 50.4).
* `Backup/` in `.gitignore` des Projekts eintragen? Nur auf ausdrückliche Zustimmung im Dialog (Schalter, Standard: aus, mit Hinweis), weil dadurch eine Projektdatei verändert wird.
* Das Backup wird von der IDE **nie** automatisch gelöscht. Wiederherstellung: 50.7.
* Beim Wiederherstellen aus ZIP Zip-Slip-Schutz (kein Pfad außerhalb des Projekts), wie im Compiler-Runtime-Provider (Task 26).

## 50.4 Kotlin im Build ermöglichen (immer, auch ohne Gradle-Schalter)

Ohne Kotlin-Plugin kompilieren die `.kt`-Dateien nicht. Deshalb ergänzt der Lauf **in jedem Fall** das Nötige in den Build-Dateien, in der vorhandenen Sprache (Groovy bleibt Groovy, wenn der Schalter aus ist):
* Kotlin-Plugin je Modultyp (Android-Anwendung/-Bibliothek: `org.jetbrains.kotlin.android`; reine JVM-Module: `org.jetbrains.kotlin.jvm`), bei Plugin-Blöcken im `plugins {}`-Block, bei `buildscript`/`apply plugin` im vorhandenen Stil.
* `jvmTarget`/`kotlinOptions`/`compilerOptions` passend zu `sourceCompatibility` bzw. `compileOptions`.
* **Annotation-Prozessoren:** `annotationProcessor` → `kapt` (oder `ksp`, wo die Bibliothek es unterstützt und der Build es schon nutzt) für Dagger/Hilt, Room, Glide, Butterknife u. ä.; Liste der bekannten Fälle als Datendatei mit Quelle, unbekannte Fälle als Hinweis im Bericht. Kapt-Plugin ergänzen.
* **Versionswahl:** Kotlin-Version aus einer Kompatibilitätsdatei (`j2k-kotlin-compat.json`: AGP-Version, Gradle-Version → Kotlin-Version, mit Quelle und Datum aus der offiziellen Dokumentation zum Implementierungszeitpunkt; **keine Werte aus dem Gedächtnis**). Das Ergebnis erscheint im Dialog und ist änderbar; ist die Kombination unbekannt, fragt der Dialog nach der Version.
* Version Catalog (`gradle/libs.versions.toml`): nur bei aktivem Katalog-Schalter (50.6a) werden das Kotlin-Plugin und `kapt` über den Katalog angelegt und mit `alias(libs.plugins.…)` eingebunden; ohne den Schalter bleibt der vorhandene Stil. Kein Eingriff in Dateien, die der Lauf nicht braucht.
* Netzwerkhinweis: Das Auflösen des Plugins braucht beim ersten Build Netz; im Bericht vermerken.

## 50.5 Java → Kotlin (Konverter)

**Umfang:** alle `.java` in Source-Sets (main, test, androidTest, Flavors), außer generierten Quellen, `module-info.java`, `package-info.java` (bleiben Java) und Dateien, die sich nicht sicher parsen lassen (Status `SKIPPED` mit Grund, Datei bleibt Java; gemischte Projekte bauen). Kodierung UTF-8, Zeilenenden und Kopfkommentare (Lizenz) bleiben erhalten.
**Ausgabe:** `.kt` im **gleichen Verzeichnis** wie die `.java` (Kotlin kompiliert aus `src/*/java`; kein Verschieben nach `kotlin/`), Dateiname unverändert. Erst der Rename (bei Git: `git mv`-Äquivalent, damit die Historie folgt), dann der Inhalt.
**Pflicht-Konstrukte** (jedes mit Golden-Test, 50.10): Klassen/Interfaces/Enums (mit Konstruktorargumenten und Rümpfen)/Records/Annotationen; Felder → Eigenschaften (`final` → `val`, sonst `var`; `private`/Sichtbarkeit erhalten; `lateinit` nur, wenn ohne Initialisierung und erkennbar später gesetzt, sonst nullable); Konstruktoren → primär/sekundär (Initialisierungsreihenfolge beachten); `static` → `companion object` bzw. Top-Level; verschachtelte Klassen (`static` → normal, sonst `inner`); anonyme Klassen → `object : T`; Lambdas und SAM-Umwandlung; Generics (`? extends T` → `out T`, `? super T` → `in T`, Raw Types → Sternprojektion mit Hinweis); Arrays und Primitive; Casts (`(int) x` → `.toInt()`), `instanceof` + Cast → `is` mit Smart Cast; ternärer Operator → `if`; `switch` → `when` (Durchfall-Fälle ohne `break` **nicht raten**: Hinweis); `for`-Schleifen → `for`/`while` (nur wenn gleichwertig); try-with-Resources → `use`; `synchronized`, `volatile`, `transient`, `native` → `@Synchronized`/`synchronized {}`, `@Volatile`, `@Transient`, `external`; Varargs; Default-Methoden; Javadoc → KDoc (`{@link X}` → `[X]`); `==` auf Referenzen → `===`, `.equals(...)` → `==`; Bit-Operatoren (`shl`, `and`, …); Zeichenarithmetik; geprüfte Ausnahmen → `@Throws` bei öffentlicher API, solange gemischter Code (Java ruft Kotlin) möglich ist.
**Nullbarkeit (der heikelste Punkt, ausdrücklich dokumentieren):** `@Nullable`/`@NonNull`/`@NotNull` und Android-Annotationen werden übernommen. Ohne Annotation: einfache Flussanalyse (Zuweisung/Rückgabe von `null`, Vergleich mit `null`) → nullable, sonst nicht-null. Wo die Analyse unsicher ist, steht `// TODO(j2k): Nullbarkeit prüfen` am Element; Zahl der Hinweise je Datei im Bericht.
**Typ-Abbildung:** `java.util.List/Map/Set` usw. → Kotlin-Sammlungen: veränderlich (`MutableList`), wenn im Code verändert oder weitergereicht, sonst nur lesend; Regel dokumentieren und testen. `Object` → `Any`, `void` → `Unit`, Wrapper-Typen → Kotlin-Typen.
**Android-Besonderheiten:** `findViewById`-Casts, `R.*`, `BuildConfig`, Lifecycle-Methoden (`@Override` → `override`), `Parcelable`-Boilerplate bleibt (kein `@Parcelize`), View-/Data-Binding-Klassen unverändert.
**Nachbehandlung:** Der Formatter aus Task 32 (`ktlint`, falls vorhanden) formatiert die Ergebnisdatei; schlägt er fehl, bleibt die unformatierte Datei.
**Fehler:** Scheitert eine Datei, bleibt sie Java (`FAILED`/`SKIPPED`) und der Lauf geht weiter; das Projekt bleibt als Java/Kotlin-Mischung baubar. Kein `TODO()`-Platzhalter, der Code stillschweigend ändert; Unsicheres wird als Kommentar markiert, nie weggelassen.
**Nicht Teil dieses Tasks:** Änderung der Architektur (kein Umbau auf Coroutines o. ä.), Idiom-Politur über das Nötige hinaus, KI-Nachbesserung, Kotlin Multiplatform, Groovy/Java-Mischquellen außerhalb von Gradle.

## 50.6 Gradle Groovy → Kotlin DSL (nur bei aktivem Schalter)

**Umfang:** alle Groovy-Gradle-Dateien projektweit: `build.gradle`, `settings.gradle`, in Unterprojekten, in `buildSrc` und in eingebundenen Builds, sowie Skripte, die per `apply from:` geladen werden. `gradle.properties`, `libs.versions.toml`, Wrapper-Dateien bleiben unverändert. **Nicht Teil dieses Tasks:** Groovy-**Quellklassen** (z. B. `buildSrc/src/main/groovy/*.groovy`, Convention-Plugins in Groovy): werden erkannt, im Bericht als „manuell“ gelistet, nicht umgewandelt.
**Ausgabe:** `*.gradle` → `*.gradle.kts` (Rename plus Inhalt; `settings.gradle` → `settings.gradle.kts`). Gemischte Projekte (einzelne Dateien Groovy, andere Kotlin DSL) sind in Gradle möglich; deshalb darf eine Datei, die sich nicht sicher umwandeln lässt, **Groovy bleiben** (Status `SKIPPED` mit Grund).
**Pflicht-Abbildungen** (jede mit Golden-Test; versionsabhängige Namen nur nach Prüfung der AGP-Dokumentation der Projektversion, sonst die konservative, immer gültige Form): Strings (`'x'` → `"x"` mit Escapen von `$`), GString; Aufrufe ohne Klammern (`implementation 'g:a:v'` → `implementation("g:a:v")`); `implementation project(':x')` → `implementation(project(":x"))`; Dateisammlungen (`fileTree(dir: 'libs', include: ['*.jar'])`); Listen/Maps (`[a, b]` → `listOf`, `[a: b]` → `mapOf("a" to b)`); `def`/typisierte lokale Variablen → `val`/`var`; `ext {}`/`ext.x` → `extra`; `apply plugin:` → `apply(plugin = …)` bzw. in `plugins {}`, wo zulässig; `plugins {}`-Blöcke; Repositories (`maven { url 'x' }` → `maven { url = uri("x") }`); Android-DSL (`compileSdkVersion 34`, `minSdkVersion 21`, `versionCode`, `buildTypes { release { … } }` → `getByName("release") { … }`, `productFlavors`, `signingConfigs`, `buildConfigField`, `minifyEnabled true` → `isMinifyEnabled = true`, `proguardFiles`, `sourceSets`, `packagingOptions`/`packaging`, `testOptions`, `kotlinOptions`); `task name(type: X) {…}` → `tasks.register<X>("name") {…}`; Zugriffe auf `project`, `rootProject`, `System.getenv`, `file()`, `new File(…)`, Properties laden (`Properties().apply { load(…) }`); `if/else`, einfache Closures. Alles Unbekannte bleibt als Kommentar `// TODO(j2k-gradle): manuell prüfen: <Original>` erhalten, die Datei gilt dann als `CONVERTED_WITH_NOTES`; schlägt die Prüfung unten fehl, fällt sie auf Groovy zurück.
**Prüfung der Skripte:** nach der Umwandlung `./gradlew help` (Konfigurationsphase) bzw. `./gradlew projects`; Fehler pro Datei zuordnen und die betroffene Datei auf Groovy zurücksetzen (aus dem Journal/Backup), danach erneut prüfen. Kein stilles Weiterbauen mit kaputten Skripten.

## 50.6a Versionskatalog erstellen und verwenden (nur bei aktivem Katalog-Schalter)

**Zweck:** Das Projekt wird nach Abhängigkeiten und Plugins gescannt, daraus entsteht `gradle/libs.versions.toml`, und die Gradle-Dateien verwenden den Katalog (`libs.…`, `alias(libs.plugins.…)`) statt verstreuter Versionsangaben. Das gilt in **Groovy und in Kotlin DSL** (der Schalter hängt nicht vom DSL-Schalter ab; nach 50.6 wird auf den dann vorhandenen Dateien gearbeitet).
**Annahme zum Wort „scannt“ (im Bericht bestätigen lassen):** Gescannt werden die **Gradle-Dateien** und was sie einbinden (`build.gradle(.kts)`, `settings.gradle(.kts)`, `apply from:`-Skripte, `ext`-Blöcke, `gradle.properties`), nicht die `import`-Zeilen der Quelltexte. Fehlende Abhängigkeiten aus Quelltext-Importen zu erraten ist nicht Teil dieses Tasks.

**Voraussetzung prüfen (nicht raten):** Mindestversion von Gradle (und AGP) für Versionskataloge; nach meinem Kenntnisstand sind Kataloge ab Gradle 7.4 stabil und in früheren Versionen nur als Vorschaufunktion nutzbar. Prüfe das an der Gradle-Dokumentation, nenne Quelle und Datum und lege die Grenze in einer Konstante fest. Liegt die Gradle-Version des Wrappers darunter, wird der Schalter ausgeschaltet, mit Hinweis; ein Wrapper-Upgrade ist **nicht** Teil dieses Tasks. Prüfe außerdem, ob `gradle/libs.versions.toml` ohne Eintrag in `settings` automatisch als `libs` erkannt wird; wenn nicht, ergänzt der Lauf `versionCatalogs { create("libs") { from(files("gradle/libs.versions.toml")) } }` in `dependencyResolutionManagement` der Settings-Datei.

**1. Scan (reine Analyse, im Plan, ohne Schreiben).** Erfasse je Modul:
* Abhängigkeiten in allen Notationen: String (`group:artifact:version`), Map (`group: 'x', name: 'y', version: 'z'`), mit Variablen (`"$kotlin_version"`, `rootProject.ext.x`, `project.property`), ohne Version (BOM-verwaltet), `platform(...)`/`enforcedPlatform(...)`, `testImplementation`, `kapt`, `ksp`, `annotationProcessor`, `debugImplementation`, `androidTestImplementation`, `classpath` in `buildscript`.
* Plugins: `plugins { id … version … apply false }`, `id` ohne Version in Modulen, `kotlin("…")`, `buildscript`-/`apply plugin:`-Stil.
* Versionsquellen: `ext {…}`, `gradle.properties`, per `apply from:` geladene Versionsdateien. Werte werden **aufgelöst**, soweit statisch möglich.
* Nicht umwandelbare Fälle werden gelistet statt geraten: dynamische Versionen (`1.+`, `latest.release`, Bereiche), Versionen aus Berechnungen oder Funktionsaufrufen, `classifier`/`@aar`-Erweiterungen (Katalog hat dafür keine volle Entsprechung: im Bericht begründen, Deklaration bleibt unverändert), `project(...)`, `fileTree`, `gradleApi()`, `localGroovy()`, Abhängigkeiten in `buildSrc` und in eingebundenen Builds (nutzen den Katalog des Hauptprojekts nicht automatisch; „manuell“ im Bericht).

**2. Katalog erzeugen oder ergänzen (`gradle/libs.versions.toml`).**
* Abschnitte `[versions]`, `[libraries]`, `[plugins]`. **Keine `[bundles]`** in dieser Version (Bündel ändern die Struktur zu stark; als Folgearbeit im Bericht erwähnen).
* **Aliasnamen:** aus Gruppe und Artefakt abgeleitet, in Kleinbuchstaben mit Bindestrichen (`androidx-core-ktx`), eindeutig und frei von Konflikten mit reservierten Namen und Teilpfaden (prüfe die Regeln in der Gradle-Dokumentation und teste mit einem echten `help`-Lauf); die Ableitungsregel steht dokumentiert und getestet im Code. Bei Kollisionen wird die Gruppe vorangestellt, nie still ein Eintrag überschrieben.
* **Versionen:** Stammte die Version aus einer benannten Variable (`ext`, Property), entsteht dieselbe Bezeichnung als `version.ref`, damit gemeinsame Versionen gemeinsam bleiben. Bei wörtlich angegebenen Versionen entsteht ein gemeinsamer `version.ref` nur, wenn mindestens zwei Bibliotheken **dieselbe Gruppe** und **dieselbe Version** haben; sonst steht die Version beim Eintrag. Fehlt die Version (BOM), bleibt der Eintrag ohne Version.
* **Vorhandener Katalog:** Er wird **ergänzt**, nie ersetzt. Kommentare und Reihenfolge bleiben (textuelles Ergänzen statt komplettes Neuschreiben). Ist dieselbe Bibliothek (`group:name`) schon da, wird ihr Alias verwendet. Hat der Katalog dafür eine **andere** Version als das Projekt, ändert der Lauf nichts an dieser Deklaration (kein stiller Versionswechsel), listet den Konflikt im Bericht und lässt die Deklaration unverändert.
* TOML wird von einem kleinen, getesteten Schreiber im Modul erzeugt (nur diese drei Abschnitte, Escaping von Anführungszeichen); eine zusätzliche TOML-Bibliothek nur nach Lizenz-/Größenprüfung.

**3. Gradle-Dateien umstellen** (je nach aktuellem DSL):
* Kotlin DSL: `implementation("g:a:v")` → `implementation(libs.a.b)`; `platform("g:bom:v")` → `platform(libs.bom)`; `plugins { id("x") version "v" }` → `plugins { alias(libs.plugins.x) }`, in Modulen ohne Version ebenfalls `alias(...)`; `classpath("g:a:v")` → `classpath(libs.…)`.
* Groovy: `implementation libs.a.b`, `alias(libs.plugins.x)`, `classpath libs.…` (Syntax wie in der Gradle-Dokumentation zur Projektversion prüfen).
* Auch nach der Umstellung bleiben Ausschlüsse und Konfigurationsblöcke (`{ exclude … }`, `transitive = false`) an der Verwendungsstelle erhalten.
* **Kotlin/kapt aus 50.4:** Hat der Lauf das Kotlin-Plugin oder `kapt` ergänzt, erscheint es als Katalogeintrag und wird über `alias(libs.plugins.…)` eingebunden.
* `ext`-Versionsvariablen und Property-Versionen werden nur entfernt, wenn nach der Umstellung **keine** Verwendung mehr besteht (projektweit gesucht, auch in anderen Skripten); sonst bleiben sie bestehen und stehen im Bericht.
* Legacy-Buildskripte (`buildscript {…}` plus `apply plugin:`): Abhängigkeiten werden auf den Katalog umgestellt, die Struktur wird **nicht** in einen `plugins {}`-Block umgebaut (zu riskant); Hinweis im Bericht.
* Die Umstellung lässt sich pro Datei zurücksetzen: Bricht die Skriptprüfung (50.6/50.8) bei einer Datei, fällt sie auf den Stand vor 50.6a zurück, der Katalog behält seine Einträge (unbenutzte Einträge sind harmlos, im Bericht vermerken).

**4. Gleichwertigkeitsprüfung der Abhängigkeiten (Pflicht bei aktiver Build-Prüfung).** Vor der Übernahme werden die **aufgelösten** Abhängigkeiten des Originals festgehalten (`./gradlew <Modul>:dependencies --configuration <relevante Konfigurationen>`, etwa Debug-/Release-Laufzeitpfad; Auswahl aus dem Plan) und nach der Umstellung wiederholt. Der Bericht listet **Unterschiede** (Bibliothek, Version vorher/nachher). Erwartet: keine. Jede Abweichung ist ein Befund mit Datei und Zeile, kein stilles Weitermachen. Braucht Netz und führt Projekt-Skripte aus: gleiche Zustimmung wie die Build-Prüfung.

**Journal und Rückgängig:** Die neue oder geänderte Katalogdatei steht im Journal (50.7): neu erzeugt → beim Rückgängig gelöscht; ergänzt → aus dem Backup-ZIP zurückgesetzt.
**Nicht Teil dieses Tasks:** Wrapper- oder AGP-Upgrade, Bündel (`[bundles]`), automatisches Aktualisieren von Versionen, Abhängigkeiten in `buildSrc`/eingebundenen Builds, Rückschluss aus Quelltext-Importen.

## 50.7 Anwenden, Journal, Rückgängig

* **Atomar:** Zuerst alles in einem Arbeitsverzeichnis (z. B. `<App-Cache>/j2k/<Lauf>`) erzeugen und prüfen, dann in einem Durchgang ins Projekt übernehmen. Ein **Journal** (Datei im Backup-Ordner, `Backup/j2k-journal-<ts>.json`) listet jede Änderung (Umbenennung, neue Datei, geänderte Datei, gelöscht) mit Hash vorher/nachher.
* Abbruch vor der Übernahme ändert nichts. Fehler **während** der Übernahme führen automatisch zu Rücknahme anhand des Journals.
* **Rückgängig** (Schaltfläche im Ergebnis und im Projektmenü, solange das Journal existiert): stellt die vom Lauf geänderten Dateien aus dem Backup-ZIP wieder her, **ohne** spätere Änderungen des Benutzers an anderen Dateien anzutasten; war eine Datei nach dem Lauf verändert, fragt die IDE nach (behalten / überschreiben / als `.orig` sichern).
* Bei Git-Projekten mit Schalter: Zweig `j2k/<Datum>` anlegen, Ergebnis committen (Nachricht ohne Geheimnisse); der Benutzer behält die Kontrolle (kein Push).
* Zweiter Lauf auf demselben Projekt: findet keine Java-Dateien mehr, legt keine leeren Backups an und ändert nichts.

## 50.8 Prüfung per Build und Bericht

* Bei aktivem Katalog-Schalter gehört der Vorher-/Nachher-Vergleich der aufgelösten Abhängigkeiten (50.6a, Punkt 4) zu diesem Schritt.
* Schritt „Prüfen“ (nur mit Zustimmung, siehe Regeln): Nutzt die Build-Infrastruktur aus Task 28 (Termux-Prozess `./gradlew …`, `GradleOutputParser`, `ProblemsTab`). Ablauf: Skriptprüfung (50.6), dann `compile…Kotlin`/`assemble…` (Aufgabe aus dem Plan ableiten, z. B. Debug), danach vorhandene Unit-Tests. Ergebnisse unterscheiden **„vorher gebaut“/„nachher gebaut“**: Optional (Dialogschalter „Vorher auch bauen“, Standard aus) wird vor der Umwandlung gebaut, damit klar ist, ob ein Fehler schon vorher bestand.
* **Bericht** `Backup/j2k-report-<ts>.md` im Projekt (Markdown): Zusammenfassung (Anzahl umgewandelt/mit Hinweisen/übersprungen/fehlgeschlagen), Liste je Datei mit Grund, Anzahl der `TODO(j2k)`-Hinweise, gewählte Kotlin-Version und Quelle, Gradle-Dateien (umgewandelt/Groovy geblieben/manuell), Versionskatalog (Anzahl Versionen/Bibliotheken/Plugins, nicht umgewandelte Fälle mit Grund, Konflikte mit bestehendem Katalog, Ergebnis des Abhängigkeitsvergleichs), Ergebnis der Build-Prüfung (Aufgaben, Dauer, Fehler mit Datei und Zeile, klickbar im UI), Pfad des Backups und des Journals, Hinweis „automatisch umgewandelt, bitte prüfen“. Keine Geheimnisse, keine Passwörter.

## 50.9 UI: Fortschritt und Ergebnis

* **Modul `:feature:j2k`:** `J2kViewModel`, `J2kContract` (State/Intent/Effect), `J2kDialog`, `J2kProgressScreen` (BottomSheet oder Vollbild), `J2kResultScreen`. Phasen sichtbar: Analyse → Backup → Build-Vorbereitung → Java-Dateien (x/N) → Gradle-Dateien (x/N) → Anwenden → Prüfen. Abbrechen jederzeit, mit klarer Aussage, was dann bleibt.
* Langläufer in einem Foreground-Service (Muster: `BuildForegroundService`, Task 28), damit das Umwandeln beim Verlassen der App nicht abbricht; Benachrichtigung mit Abbruchaktion.
* Ergebnis: Zusammenfassung, Liste der Dateien mit Filter (Hinweise/Fehler), Sprung in die Datei (`OpenFileRequestBridge`), Schaltflächen **Build ausführen**, **Bericht öffnen**, **Rückgängig**, **Backup-Ordner zeigen**.
* Barrierefrei (Beschriftungen), Textgrößen, Dark/Light, Hochformat und Querformat; Previews für Dialog und Ergebnis.

## 50.10 Tests, Strings, Doku

* **Golden-Tests** (reines JVM, `:libs:j2k`): je Pflicht-Konstrukt aus 50.5 und 50.6 eine Eingabe/Erwartungs-Datei. Erwartete `.kt`-Dateien müssen **kompilieren**: Prüfung mit `kotlinc` auf dem Gerät (Skript für den Inhaber) und, wo es die CI erlaubt, ein Test, der nur dann läuft, wenn `kotlinc` vorhanden ist (sonst `Assume`; im Bericht angeben).
* **Beispielkorpus:** mindestens zwei kleine Projekte unter `examples/` bzw. Testressourcen: (1) reines Java-Gradle-Projekt (Groovy) mit JUnit-Test, (2) kleines Android-Projekt in Java mit Groovy-Gradle (inklusive `annotationProcessor`). Für beide: umwandeln, bauen, Tests laufen lassen (Gerät); Ergebnis im Bericht, ausdrücklich **nicht getestet**, bis der Inhaber es ausgeführt hat.
* **Versionskatalog (50.6a):** Golden-Tests für jede Notation (String, Map, Variable, ohne Version/BOM, `platform`, Plugins mit/ohne Version, `classpath`), für Aliasbildung und Kollisionen, für gemeinsame `version.ref`, für das Ergänzen eines bestehenden Katalogs (Kommentare bleiben, Konflikt wird gemeldet, kein stiller Versionswechsel), für nicht umwandelbare Fälle (dynamisch, `@aar`, `buildSrc`) sowie für beide DSL (Groovy und Kotlin). Der Abhängigkeitsvergleich wird mit einer Fixture (zwei aufgelöste Listen) getestet; ein echter Gradle-Lauf ist **nicht getestet**, bis der Inhaber ihn ausgeführt hat.
* **Weitere Tests:** `ProjectLanguageScanner` (Zähl- und Ausschlussregeln, 70-%-Schwelle), Backup (Inhalt, Ausschlüsse, Verifikation, Abbruch bei beschädigtem ZIP, Zip-Slip beim Wiederherstellen), Journal und Rückgängig (inklusive geänderter Dateien nach dem Lauf), Abbruch in jeder Phase, zweiter Lauf, Gradle-Fallback auf Groovy, ViewModel (Dialogzustände, Schalter nur bei Groovy sichtbar).
* **Strings** zweisprachig (Task 17/18); keine harten Texte.
* **Doku:** `docs/j2k-report.md` (Entscheidungen 50.0, Konstrukt-Tabelle mit Status je Konstrukt, bekannte Grenzen), Hinweis auf das Backup-/Rückgängig-Verhalten für Benutzer in der Hilfe der App.

## Abnahme

* Spike (50.0) belegt, Entscheidung begründet; Modul(e) angelegt, `:app:assembleDebug` grün.
* Dialog erscheint nach Import und Clone bei überwiegend Java, mit Gradle-Schalter nur bei Groovy-Dateien; „Später“ und „Nicht mehr fragen“ funktionieren.
* Backup-ZIP unter `Backup/` wird vor jeder Änderung angelegt und verifiziert; ohne gültiges Backup keine Änderung.
* Bei aktivem Katalog-Schalter: `gradle/libs.versions.toml` ist erstellt bzw. ergänzt (ohne bestehende Einträge oder Kommentare zu verlieren), die Gradle-Dateien verwenden `libs.…`, der Abhängigkeitsvergleich zeigt keine unerklärten Unterschiede, alle nicht umwandelbaren Fälle stehen im Bericht.
* Java-Quellen werden nach 50.5 umgewandelt; fehlgeschlagene Dateien bleiben Java; Gradle-Dateien nach 50.6 nur bei aktivem Schalter; Kotlin-Unterstützung in den Build-Dateien wird immer ergänzt (50.4).
* Journal, Rückgängig, Abbruch und Bericht funktionieren (Tests).
* Build-Prüfung über Task-28-Infrastruktur, Ergebnis im Bericht; Aussage „automatisch umgewandelt, bitte prüfen“.
* Bericht mit **getestet / nicht getestet / offene Punkte**; Gerätetests und Beispielkorpus-Läufe als offen vermerkt, bis der Inhaber sie bestätigt.

## Commit-Vorschlag (je Teil)
```
feat(j2k): <Teil>

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
