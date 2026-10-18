# Task 27a – Compose-Preview: Kotlin-Compiler als Prozess in Termux statt im App-Prozess

**Lies zuerst:** `docs/compose-preview.md` (Abschnitt 4) und Task 26. **Voraussetzung:** Task 26 (Jar-Bundle); für den Gerätetest ein fertig gebautes `openjdk-21` und `kotlin` im APT-Repo (Gruppen H2 bzw. A).
**Begründung:** `PreviewCompiler` startet `K2JVMCompiler` aus `kotlin-compiler-embeddable` im App-Prozess. Ob das in ART läuft, ist ungeprüft; es kostet ~80 MB APK (Kommentar im `build.gradle.kts`) und läuft mit der Speichergrenze der App. Das Termux-Paket `kotlin` bringt `kotlinc` samt `lib/compose-compiler-plugin.jar` mit und läuft auf einer echten JVM. In der Sandbox geprüft (JVM, nicht Gerät): `kotlinc` 2.1.21 mit diesem Plugin kompiliert in ca. 4 s; ohne Compose-Runtime im Klassenpfad bricht es mit „requires the Compose Runtime“ ab.
**Regeln:** nichts behaupten, was nicht auf dem Gerät lief; Ergebnis in `docs/compose-preview-kotlinc-report.md` mit **getestet / nicht getestet**; Texte nur über `:core:resources` (en/de).

## 27a.0 Spike zuerst (entscheidet, ob es weitergeht)
Das Skript `scripts/compose-preview-spike.sh` liegt bei. Es prüft `java`, `kotlinc` (Version), Plugin-Jar, freien Speicher, kompiliert eine `@Composable`-Datei mit den Jars aus `compiler-runtime` plus `android.jar` und meldet Dauer, Exit-Code und die Signatur (`Composer`-Parameter vorhanden?). Modus `plain` prüft nur Compiler und JVM.
1. Skript ins Repo übernehmen, in der Termux-Shell auf dem Gerät ausführen: erst `plain`, dann `compose` (nach Task 26).
2. Ergebnis, Dauer und Speicher in den Bericht. **Wenn es scheitert:** Ursache nennen (Version? Speicher?) und hier abbrechen, nicht weiterbauen.
3. **Versionsabweichung klären:** Das Termux-Paket `kotlin` im Fork steht auf **2.4.21**, die App auf **2.1.21**. Prüfen, ob Code vom Plugin 2.4 mit der Compose-Runtime aus der BOM `2025.09.00` läuft (Preview wirklich geladen, kein `NoSuchMethodError`). Falls nicht: Rezept `packages/kotlin` im Fork auf 2.1.21 pinnen oder eine passende Distribution in `codeforge-tools` bereitstellen; Entscheidung im Bericht begründen.

## 27a.1 Neue Compiler-Umsetzung
1. Schnittstelle `PreviewCompiling { fun compile(sourceFile, outputDir, classpath): CompileResult }` einführen; `PreviewCompiler` (embeddable) wird eine Implementierung, neu: `KotlincProcessCompiler`.
2. `KotlincProcessCompiler`: Prozess per `ProcessBuilder` mit der Umgebung aus `TermuxEnvironment.environment()` (`libs/terminal-engine`), Befehl: `$PREFIX/opt/kotlin/bin/kotlinc -no-stdlib -no-reflect -Xplugin=$PREFIX/opt/kotlin/lib/compose-compiler-plugin.jar -classpath <Jars>:<android.jar> <Quelle> -d <Ausgabe>`; `kotlin-stdlib.jar` aus `$PREFIX/opt/kotlin/lib/` (oder aus dem Bundle, aber nicht doppelt).
3. Speicher begrenzen (`JAVA_OPTS=-Xmx…`, Wert aus dem Spike), Zeitlimit (z. B. 120 s) mit sauberem Abbruch des Prozessbaums, stdout/stderr zusammenführen und Zeilen `datei:zeile:spalte: error: …` in `CompileResult.errorMessages` überführen (Parser mit Unit-Test).
4. Vorabprüfung: `java` und `kotlinc` vorhanden? Sonst Meldung „Kotlin-Compiler fehlt – im Terminal `pkg install kotlin` ausführen (große Pakete werden automatisch aus dem Release geladen)“ als neuer String. Kein stilles Nachinstallieren.
5. `ComposePreviewRendererImpl` wählt die Implementierung (Standard: Prozess). Der eingebettete Compiler bleibt nur, wenn der Spike ihn als lauffähig zeigt; andernfalls Abhängigkeiten `kotlin-compiler-embeddable` und `kotlin-compose-compiler-plugin-embeddable` aus `feature/composepreview/build.gradle.kts` und aus `PreviewCompiler` entfernen. **APK-Größe vorher/nachher messen und berichten.**
6. Task 26 anpassen: Das Plugin-Jar entfällt aus dem Bundle (Plugin kommt aus der Distribution); `resolveComposeCompilerPluginJar()` und der zugehörige String werden entfernt.

## 27a.2 Dauer und Bedienung
1. Die Preview darf die UI nicht blockieren: Rendern läuft bereits in `Dispatchers.Default`; Fortschritt „Kompiliere …“ anzeigen, Abbrechen ermöglichen.
2. Messen (nicht schätzen): Dauer beim ersten und zweiten Lauf; wenn mehr als ca. 15 s, im Bericht Optionen nennen (Kotlin-Daemon, Debouncing des Neu-Renderns). Nicht ungefragt umsetzen.

## 27a.3 Tests
* JVM-Unit-Tests: Fehlerparser (echte `kotlinc`-Ausgaben als Testdaten, z. B. „unresolved reference“), Befehlsaufbau, Zeitlimit.
* Gerät: Spike und ein Lauf der Preview mit einer einfachen Composable. Falls kein Gerät verfügbar ist: als **nicht getestet** ausweisen.

## Abnahme
Build grün, Unit-Tests grün, String-Check grün (`python3 scripts/check_string_locales.py`), Spike-Ergebnis im Bericht, APK-Größenvergleich, `TASKS-STATUS.md` und `README.md` um Task 27a ergänzt.
