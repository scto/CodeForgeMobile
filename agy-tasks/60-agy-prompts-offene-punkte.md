# Task 60 – Fertige agy-Prompts für alle offenen Punkte

Stand: P4 (Zwischenablage-Kürzel) und P5 (Terminal-Backup) wurden von Claude umgesetzt und entfernt (Commits a79b423, 26f03b8); ihr Gerätetest steht jetzt in P3 (Punkte 5 und 6); P12 läuft bereits bei agy. Die Nummern der übrigen Prompts bleiben unverändert.

Jeder Block ist ein eigenständiger Prompt: kopieren, in agy einfügen, fertig.
Gemeinsame Regeln (in jedem Prompt enthalten, hier nur zur Übersicht): Deutsch, Shell mit `set -euo pipefail`, Clean Architecture (UI / Domain / Data), Gradle Kotlin DSL + Version Catalogs, `modifier: Modifier = Modifier` als erster optionaler Parameter, `@Immutable` UI-States, keine Geheimnisse in Berichten/Commits, nichts erfinden: Nicht Ausgeführtes steht im Bericht unter **nicht getestet**. Vor jedem Push: `git fetch origin main && git rebase origin/main`. Commit-Nachrichten deutsch.

**Reihenfolge ohne Gerät/PC/WLAN (Stand 2026-10-27):** Jetzt möglich (reine Code-/Build-Arbeit): P1, P7 (Messung 56.1–56.2), P8, P9, P12. Zurückgestellt bis Gerät oder Netz verfügbar: P2, P3, P6 (Gerätetests), P10 (braucht Workflow-Rechte und Netz), P11 (braucht Netz für `apt update`). Zurückgestellte Punkte nicht als erledigt markieren.

---

## P1 – Build und Unit-Tests grün (Voraussetzung für alles andere)

```
Repo: scto/CodeForgeMobile, Branch main. Lies zuerst agy-tasks/58 und agy-tasks/59.
Aufgabe: Prüfe, dass der aktuelle Stand baut und die Tests laufen.
1. `./gradlew :app:assembleDebug` ausführen. Fehler beheben (nur minimal, keine Refactorings), jeden Fix einzeln committen.
2. `./gradlew testDebugUnitTest` für alle Module ausführen. Bekannt und umgebungsbedingt: ExtensionsManagerTest scheitert, wenn rust-analyzer im PATH liegt – das nicht "reparieren", sondern nur dokumentieren.
3. `./gradlew lintDebug` ausführen; neue Warnungen aus den Commits dbe72af und f804221 beheben.
Bericht in docs/task-60-p1-report.md: Befehle, Ergebnis, was nicht ausgeführt werden konnte (nicht getestet).
```

## P2 – Gerätetest Editor, LSP, Git, Dateibaum (agy-tasks/59, Teile A, B, D)

```
Repo: scto/CodeForgeMobile. Lies agy-tasks/59 komplett. Installiere die Debug-APK auf Emulator/Gerät (adb).
Prüfe und protokolliere pro Punkt "ok / Fehler / nicht prüfbar" mit Screenshot-Dateiname:
- Dialoge: Dependency-Update und LSP-Sitzungen (Vollbreite, lesbar, Hell/Dunkel).
- Editor-Einstellungen: Zeilennummern aus/fixiert, Cursor-Animation, Zeilenabstand, Lesemodus, Textumbruch für .txt, Farbvorschau, Softkeyboard bei Hardware-Tastatur, Schriftart JetBrains Mono (muss sichtbar von Monospace abweichen).
- LSP (mit laufendem Kotlin- oder TypeScript-Server): Hover, Typ-Definition, Implementierungen, Signature-Help beim Tippen von "(" und ",".
- Git: Gutter-Marker (neu/geändert/gelöscht) nach Speichern, Merge-Konflikt-Leiste mit "Aktuell/Eingehend/Beide", Dateibaum-Farben (geändert gelb, neu grün, Konflikt rot) inkl. Auto-Refresh nach Speichern.
Jeder Fehler: Ursache im Code suchen, minimal beheben, mit Test absichern, einzeln committen. Bericht docs/task-60-p2-report.md. Nicht Prüfbares ehrlich unter "nicht getestet".
```

## P3 – Gerätetest Terminal-Einstellungen (agy-tasks/59, Teil C)

```
Repo: scto/CodeForgeMobile. Lies agy-tasks/59 Teil C und die Dateien feature/settings/.../TerminalSettings*.kt, TermuxProperties.kt, TerminalFontInstaller.kt, libs/termux-app/.../TermuxActivity.java.
Prüfe auf dem Gerät:
1. Scrollback (terminal-transcript-rows) und Cursorstil (terminal-cursor-style) – wirken sie nach Neustart des Terminals ohne App-Neustart? Wenn nicht: Reload über TermuxActivity (reloadProperties / TermuxSharedProperties) auslösen und testen.
2. Schriftgröße (sp↔px, 2er-Schritte), Schriftdatei wählen, Button "JetBrains Mono", "Zurücksetzen".
3. Zusatztasten: Preset wählen, speichern, Terminal neu öffnen – ist die Zeile geändert? Ungültige Eingabe darf nicht gespeichert werden.
4. "Alle Sitzungen beim Schließen beenden": an, Sitzung mit `sleep 1000` starten, Zurück-Taste → Prozess muss weg sein (`ps`/Benachrichtigung). Aus: Sitzung bleibt. Gerät drehen darf nichts beenden.
5. Zwischenablage-Kürzel (Schalter "Zwischenablage-Tastenkombinationen", Standard aus): mit Hardware-Tastatur Text markieren, Strg+Umschalt+C kopiert, Strg+Umschalt+V fügt ein. Schalter aus: beide tun nichts Neues. Strg+C muss in beiden Fällen weiter laufende Prozesse unterbrechen.
6. Terminal-Sicherung (Karte "Terminal-Sicherung"): "Sichern…" erzeugt eine .tar.gz (Größe und Dauer notieren; `tar tzf` im Terminal zeigt `home/`, `usr/` und `codeforge-backup-v1`, keine `usr/tmp`-Einträge). Danach eine Testdatei in $HOME ändern und eine kleine Datei löschen, "Wiederherstellen…" mit dieser Sicherung: Bestätigungsdialog erscheint, Dateien sind zurück, Symlinks (`ls -l $PREFIX/bin`) und Ausführbar-Bit intakt, `apt-get check` ohne Meldung. Abbrechen während der Sicherung testen (Meldung "Abgebrochen."). Eine beliebige andere Datei (z. B. ein Foto) als Wiederherstellungsquelle wählen: muss mit "Keine gültige CodeForge-Sicherung" abgelehnt werden, ohne etwas zu verändern.
Fehler beheben, Tests ergänzen, einzeln committen. Bericht docs/task-60-p3-report.md.
```

## P6 – Task 52 / X2C: nur noch Gerätetest (Build und JVM-Tests sind laut docs/x2c-report.md 52.12 grün)

```
Repo: scto/CodeForgeMobile. Lies docs/x2c-report.md (Abschnitt 52.12) und docs/device-test-checklist.md (Abschnitt J, X2C-01 bis X2C-03).
Build und Unit-Tests gelten als erledigt; offen ist ausschließlich der Gerätetest. Installiere die aktuelle Debug-APK von main, importiere bzw. klone ein Beispielprojekt mit XML-Layouts und ohne aktives Compose und führe X2C-01 bis X2C-03 aus: Dialog erscheint nach Import/git clone, Layoutauswahl, Konvertierung, Ergebnis kompiliert. Der Dialog darf nie parallel zum Task-50-Dialog erscheinen. Jeden Fehler beheben (minimal, mit Test, einzeln committen).
Bericht: Ergebnisse je Test-ID mit Screenshot-Namen in docs/task-52-12-device-report.md; Nicht-Prüfbares unter "nicht getestet". Task-Status in TASKS-STATUS.md ergänzen ("Gerätetest ✅" erst nach bestandenem Test).
```

## P7 – Task 56 (Compiler-Runtime schlank)

```
Repo: scto/CodeForgeMobile. Lies agy-tasks/56-compiler-runtime-icons-extended-check.md.
Führe 56.1–56.2 aus: Messe (1) Anzahl genutzter Icons in einem Projektscan, (2) Größe der Compiler-Runtime mit material-icons-core statt material-icons-extended (≈ 35,7 von 53,8 MiB), (3) Download- und Installationszeit. Entscheide anhand der Zahlen; wenn core genügt, Umstellung mit Fallback-Hinweis für Projekte, die Extended-Icons nutzen (Erkennung beim Scan). Zahlen als Tabelle in docs/task-56-report.md. 56.3 (Gerätetest) als Checkliste für den Inhaber anlegen, nicht selbst als erledigt markieren.
```

## P8 – Task 99 (Dokumentation: Mermaid + Dokka)

```
Repo: scto/CodeForgeMobile. Lies agy-tasks/99-docs-mermaid-dokka.md und TASKS-STATUS.md (Abschnitt Task 99).
Setze um: Anwendungsdoku in docs/app/ mit Mermaid-Diagrammen (Modulabhängigkeiten, Datenfluss Editor↔LSP, Terminal/Bootstrap, APT-Repo-Pipeline), generierte Blöcke maschinell aus Gradle-Moduldaten erzeugen; API-Doku mit Dokka für :libs:plugin-api und die :core:domain-Schnittstellen; Gradle-Tasks `docsGenerate` und `docsCheck` (CI-tauglich, Version Catalog für Dokka). GitHub-Actions-Job mit actionlint prüfen. Keine erfundenen Diagramme: nur, was im Code belegbar ist.
```

## P9 – Task 17 (Hartcodierte Strings auslagern)

```
Repo: scto/CodeForgeMobile. Lies agy-tasks/17-externalize-hardcoded-strings.md und libs/hardcoded-strings.
Führe den Scanner über alle Module aus, lagere verbliebene hartcodierte UI-Strings nach core/resources (values/ und values-de/ synchron, gleiche Schlüssel, Platzhalter mit %1$s). Keine Strings in Logs/Tests/Dateipfaden ändern. Compose: stringResource/stringRes nutzen, nicht im Composable konkatenieren. Danach Scanner erneut laufen lassen; Rest im Bericht begründen.
```

## P10 – APT-Repo: Läufe anstoßen und prüfen (nur Inhaber/Workflow-Rechte)

```
Repo: scto/terminal-packages-codeforge (Workflow .github/workflows/build-bootstrap-repo.yml im App-Repo). Lies agy-tasks/49, 51, 53 und areas/apt-repo-Notizen in TASKS-STATUS.md.
Nicht erfinden: Du darfst nur Workflow-Läufe auslösen, wenn du Rechte hast; sonst Befehle für den Inhaber ausgeben.
1. Lauf mode=add, Stufe H4 (llvm), archs=aarch64, publish=true – stellt die libllvm-Manifest-Einträge wieder her.
2. Lauf Stufe H5-dart (nach dem dart-assemble-Fix).
3. Lauf Gruppe A (leicht-jvm) inkl. codeforge-env, jq muss vorher im Repo liegen (Gruppe B), openjdk-17/21 aus H1/H2.
Vor jedem publish: Schrumpfwächter beachten (FORCE_PUBLISH nicht setzen). Danach Prüfung per curl der Packages-Dateien: Paketzahl nicht kleiner als vorher, large-packages.txt unverändert oder größer, `apt-cache policy codeforge-env gradle kotlin` plausibel. Ergebnis in docs/task-60-p10-report.md, keine Tokens/Passphrasen ins Log.
```

## P11 – Gerät: apt-Listen und codeforge-env

```
Auf dem Gerät (Terminal der App) ausführen und Ausgabe protokollieren:
rm -rf "$PREFIX"/var/lib/apt/lists/*; apt update; apt-get check
apt-cache policy codeforge-env jq openjdk-21
codeforge-env doctor
codeforge-env setup --jdk 21 --ndk r29 --cmake 4.1.2
Erwartung: kein "Paket fehlt im APT-Repo (jq)", keine unerfüllten Abhängigkeiten von openjdk-21. Wenn die Meldung "CMake 4.1.2 nicht verfügbar – verwende das Termux-Paket" erscheint: Quelle der CMake-Versionsliste in libs/terminal-engine/src/main/assets/codeforge-env suchen, Ursache klären (fehlt 4.1.2 im Mirror codeforge-tools oder in der Manifestliste?) und beheben. Bericht mit den echten Ausgaben, nichts schönen.
```

## P12 – Optional: Tree-sitter Kotlin-Variablenscopes (LÄUFT BEREITS bei agy – nicht erneut starten)

```
Repo: scto/CodeForgeMobile, Modul feature/editor (assets/treesitter). Lies die vorhandenen Queries (highlights/locals).
Ergänze für Kotlin eine locals.scm bzw. Scope-Auflösung, damit lokale Variablen/Parameter anders eingefärbt werden als Properties. Nur wenn die verwendete sora-editor-Tree-sitter-Anbindung locals unterstützt (vorher per javap/Quelltext belegen); sonst Befund dokumentieren und abbrechen. Tests für die Query-Datei (Parsetest), Performance-Messung auf einer 5000-Zeilen-Datei.
```
