# Task 53 – APT-Pool unter dem Pages-Limit halten: Auslagerungsgrenze senken, Bestand umziehen, Größenwächter

**Ziel:** Das APT-Repo auf `gh-pages` bleibt dauerhaft unter dem Größenlimit von GitHub Pages (Quelle und Wert im Bericht belegen, nicht aus dem Gedächtnis; nach meinem Kenntnisstand etwa 1 GB je veröffentlichter Seite). Dazu (1) wird die Grenze, ab der ein Paket als Release-Asset statt im Pool liegt, von 95 MiB auf einen kleineren, begründeten Wert gesenkt, (2) werden die bereits im Pool liegenden Pakete über dieser Grenze einmalig ins Release `large-packages` umgezogen und (3) schlägt der Assemble-Lauf rechtzeitig Alarm, bevor das Limit erreicht ist.
**Warum jetzt:** Stand 2026-10-25, 13:28 UTC (frischer Klon von `gh-pages`): **Pool 941 MB**, 418 Pakete je Architektur (aarch64, arm). Fünf Dateien belegen zusammen **317 MiB**: `openjdk-17` aarch64 (91,6 MiB) und arm (88,1 MiB), `openjdk-17-source` (44,6 MiB), `openjdk-21-source` (46,9 MiB), `openjdk-25-source` (46,0 MiB). Alles andere ist kleiner als 15 MiB. Ausstehend sind noch Gruppe A (`gradle`, `kotlin`, `maven`, `ant`, `d8` …), D-Nachlauf, H4 (`libllvm`, `clang`), H5 (`dart`), H6 (`rust-analyzer`). Ohne Maßnahme wird das Limit mit Gruppe A erreicht.

**Gemeinsame Regeln:** wie Task 49/51 (Shell mit `set -euo pipefail`, shellcheck-sauber; Workflows mit `actionlint`; Python nur Standardbibliothek, Aufruf mit `python3 -I`). Nichts erfinden: Was nicht selbst ausgeführt wurde, steht im Bericht unter **nicht getestet**. Die Zahlen oben stammen aus einem Klon des Repos durch den Assistenten; messe selbst nach und korrigiere sie im Bericht. Geheimnisse (GPG-Passphrase, Tokens) nie ins Log, nie in Dateien, nie in Berichte. **Keine Veröffentlichung, kein Umzug im echten Repo ohne Auftrag des Inhabers.** Bericht in `docs/apt-pool-size-report.md`; `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash). Kein Umbau des Hostings (GitHub Pages bleibt).

## 53.1 Befund (erst selbst prüfen)

1. **Grenze an drei Stellen:** `.github/workflows/build-bootstrap-repo.yml` (Schritt „Große Pakete abtrennen (> 95 MiB)“, `find … -size +95M`), `build_codeforge_repo.sh` (Warnung `-size +95M`), Texte in `libs/terminal-engine/src/main/assets/codeforge-env` (Kommentar „> 95 MiB“), in `docs/` und im Index (`generate-apt-index.py`). Suche alle Fundstellen (`grep -rn "95"`), nenne sie im Bericht.
2. **Pool-Messung:** größte Dateien, Summe je Architektur und je Paket, Verhältnis zum Limit. Maß ist die **veröffentlichte Seite** (ohne `.git`); halte fest, was GitHub genau zählt (Quelle, Datum).
3. **Wie große Pakete installiert werden (Task 25):** `install_repo_pkg` in `codeforge-env` liest `large-packages.txt` (signiert), lädt das Asset vom Release und installiert es mit `dpkg`; nur wenn der Name **nicht** im Manifest steht, läuft `pkg install`. Große Pakete stehen **nicht** im APT-Index. Folge: Ein direktes `apt install openjdk-17` (ohne `codeforge-env`) findet sie nach dem Umzug nicht mehr; Pakete, die von ihnen abhängen (`openjdk-17-x`, `-source`), lassen sich per `apt` nur installieren, wenn das Hauptpaket schon installiert ist. Prüfe, wie `pkg_install` Abhängigkeiten auf ausgelagerte Pakete behandelt (nur Namen, die der Aufrufer nennt?), und ob die App überall `codeforge-env` verwendet. Halte die Folgen im Bericht fest.
4. **Verbraucher von `large-packages.txt`:** `build-bootstrap-repo.yml` (Manifest-Schritt), `check-apt-consistency.py` (Task 51), `generate-apt-index.py`, `codeforge-env`, `libs/terminal-engine/snippets/large-pkg-snippet.sh` (+ Test). Alle müssen nach dem Umzug weiter funktionieren.

## 53.2 Entscheidung: neue Grenze (Zahl begründen)

* Vorschlag: **30 MiB** (nur fünf bestehende Dateien liegen darüber; die nächstgrößte hat 14,7 MiB, `perl`). Prüfe Alternativen (z. B. 20 MiB, 50 MiB) gegen die erwartete Größe der noch ausstehenden Gruppen (`kotlin`, `maven`, `libllvm`, `clang`, `dart`, `rust-analyzer`: Größen aus den Bau-Artefakten bzw. Logs belegen, nicht schätzen) und gegen den Aufwand je Auslagerung (ein Download mehr, ein Eintrag mehr im Manifest).
* Die Grenze steht an **einer** Stelle (z. B. `apt-limits.conf` oder Workflow-Variable `LARGE_PKG_MIB`) und wird von Workflow, `build_codeforge_repo.sh` und Doku gelesen. Texte „> 95 MiB“ überall ersetzen.
* Beschreibe die Alternativen und warum sie nicht gewählt werden: (a) `*-source`-Pakete weglassen (sie dienen der JDK-Quellnavigation im Editor; Entscheidung beim Inhaber), (b) Pool auf eine zweite Pages-Seite oder anderes Hosting verteilen (eigener Entscheid, nicht Teil dieses Tasks).

## 53.3 Einmaliger Umzug des Bestands (nur auf Auftrag des Inhabers ausführen)

Ein Modus oder Workflow `rehome-large` (manuell startbar, Standard `dry_run=true`):
1. Liest `gh-pages` (Pool, `Packages`, `large-packages.txt`), listet alle `.deb` im Pool über der neuen Grenze (Name, Version, Architektur, Größe) und was der Umzug spart.
2. Mit `dry_run=false`: lädt jede dieser Dateien als Asset ins Release `large-packages` (Dateiname wie beim Abtrennen: `:` → `_`; **Upload prüfen**: Größe und SHA-256 des Assets gegen die Datei), ergänzt `large-packages.txt` (je Paket+Architektur gilt der neueste Eintrag, wie im bestehenden Schritt), **entfernt sie erst danach** aus dem Pool, erzeugt `Packages`/`Release` neu, signiert (`Release.gpg`/`InRelease`, `large-packages.txt.asc`), führt die Konsistenzprüfung (Task 51) aus und veröffentlicht nur bei Erfolg.
3. **Reihenfolge ist sicherheitskritisch:** erst hochladen und verifizieren, dann aus dem Pool entfernen, dann veröffentlichen. Bricht etwas ab, bleibt der alte Zustand unverändert (kein halber Umzug). Dokumentiere den Rückweg (Dateien liegen im Release und im Git-Verlauf des alten `gh-pages`-Commits).
4. Gleichzeitig laufende Veröffentlichungen müssen ausgeschlossen sein (Concurrency-Gruppe von `build-bootstrap-repo.yml` benutzen oder dieselbe Sperre).
5. Erwartung nach dem Umzug: Pool rund 620 MB (Messwert in den Bericht), Konsistenzprüfung grün, `large-packages.txt` mit zusätzlich `openjdk-17` (aarch64, arm) und den drei `-source`-Paketen.

## 53.4 Größenwächter im Zusammenbau

* Im Assemble-Job nach dem Zusammenbau: Größe des zu veröffentlichenden Repos (ohne `.git`) ausgeben und in `GITHUB_STEP_SUMMARY` schreiben (`Pool-Größe`, Anteil am Limit).
* **Warnschwelle** (Vorschlag 800 MB, `::warning::`) und **Abbruchschwelle** (Vorschlag 950 MB, Veröffentlichung wird verweigert, klare Meldung mit den fünf größten Paketen und dem Hinweis auf 53.3); beide konfigurierbar, mit `force_publish`-Eingabe nur für Ausnahmen (Standard aus, im Bericht begründen).
* Die bestehende Warnung „Pool > 900 MB“ in `build_codeforge_repo.sh` wird durch diese Logik ersetzt oder damit abgestimmt (keine zwei widersprüchlichen Schwellen).

## 53.5 Folgen für Gerät, Doku, Prüfungen

* `codeforge-env`: Kommentar und Fehlermeldungen auf die neue Grenze; prüfe, dass `pkg_install openjdk-17` und `pkg_install openjdk-21-source` nach dem Umzug den Manifestpfad nehmen (Test in `large-pkg-snippet.test.sh` mit Manifest-Zeile für ein kleineres Paket; vorhandene Tests grün).
* `generate-apt-index.py`: ausgelagerte Pakete erscheinen weiter in der Liste (Test).
* Doku: `docs/BOOTSTRAP-WORKFLOW.md` (neue Grenze, Wächter, `rehome-large`), Betriebshandbuch Task 99.8 (`08-fehlersuche.md`: Zeile „Pool zu groß / Veröffentlichung verweigert“), `README.md`/`STATUS.md`, soweit sie „95 MiB“ nennen.
* Hinweis für Benutzer ohne `codeforge-env`: ausgelagerte Pakete sind per `apt` nicht auffindbar; steht in der Hilfe der App bzw. im Bericht.

## 53.6 Verifikation (Inhaber führt aus, Agent bereitet vor)

1. `rehome-large` mit `dry_run=true`: Liste prüfen (genau die fünf erwarteten Dateien, Ersparnis ≈ 317 MiB).
2. `dry_run=false`: Pool-Größe vorher/nachher, Assets im Release `large-packages` (Name, Größe), neue Zeilen in `large-packages.txt`, Signatur (`gpg --verify`), `python3 -I scripts/check-apt-consistency.py repo <repo> --large-packages large-packages.txt` ohne Fehler.
3. Gerät (nur durch den Inhaber): `apt update`; `codeforge-env pkg openjdk-17` und `openjdk-21-source` laden vom Release; bestehende JDK-Installationen bleiben unverändert (`java -version`).
4. Gruppe A (`gradle`, `kotlin`, `maven` …) danach laufen lassen: Assemble zeigt `Pool-Größe` unterhalb der Warnschwelle.

## 53.7 Nicht Teil dieses Tasks

* Umzug des Hostings, zweites Repo, anderer Anbieter.
* Entfernen von Paketen aus dem Repo (außer es entscheidet der Inhaber ausdrücklich für die `*-source`-Pakete).
* Neuer Bootstrap (`mode=full`).

## Abnahme

* Messung des Ist-Zustands und Beleg des Limits mit Quelle und Datum im Bericht.
* Neue Grenze an einer Stelle definiert, begründet und überall übernommen; alle „95 MiB“-Texte aktualisiert.
* `rehome-large` zuerst nur als Trockenlauf, Umzug nur auf Auftrag; Upload wird verifiziert, bevor etwas aus dem Pool entfällt; kein halber Zustand bei Abbruch.
* Größenwächter mit Warn- und Abbruchschwelle im Zusammenbau; Ausnahme nur über `force_publish`.
* Konsistenzprüfung (Task 51), `codeforge-env`-Tests und Index-Test grün; `actionlint` und `shellcheck` ohne Fehler.
* Bericht mit **getestet / nicht getestet / offene Punkte**; Lauf auf GitHub und Gerätetest als offen vermerkt, bis der Inhaber sie bestätigt.

## Commit-Vorschlag
```
fix(apt-pool): lower large-package threshold, rehome existing pool debs, add pool size guard

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
