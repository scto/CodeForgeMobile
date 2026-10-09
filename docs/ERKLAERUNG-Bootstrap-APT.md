# Bootstraps & APT-Repo für CodeForge: Warum der erste Ansatz scheiterte

Stand: 2026-10-09 · Projekt CodeForgeMobile · Prefix `/data/data/com.codeforge.app/files/usr`

**Wie belastbar ist das hier?** Jede Aussage ist so gekennzeichnet:
- **[geprüft]**: Ich habe es im Code, im Script oder im Actions-Log gesehen.
- **[Einschätzung]**: Meine technische Begründung, nicht selbst ausprobiert.
- **[offen]**: Noch nicht getestet. Ein vollständiger, erfolgreicher Durchlauf liegt bis jetzt nicht vor. Die letzte Prüfung war ein GitHub-Actions-Lauf, der nach rund 25 gebauten Paketen an einer fehlenden Ausführungsberechtigung in `termux-core` stoppte (Abschnitt 4).

---

## 1. Geht das direkt auf dem Gerät (Termux), ohne Docker?

**Kurzantwort:** Für den Teil „Pakete mit dem Prefix `com.codeforge.app` aus dem Quellcode bauen“ nein. Für aarch64 allein kommt man mit Umwegen weiter, für x86_64 und i686 praktisch nicht.

### 1.1 Was Gemini behauptet hat
Gemini sagte (so habe ich es von dir), das gehe sehr wohl unter Termux, Docker sei unnötig und Cross-Kompilierung sei auch möglich. Der Prompt in `build_codeforge_repo.md` verlangt: `generate-bootstraps.sh` statt `build-bootstraps.sh` verwenden, weil das Script „on an Android device inside PRoot/Termux“ laufe und das Kompilieren der Toolchain „not feasible“ sei [geprüft, steht so im Prompt].

### 1.2 Was daran stimmt
- Cross-Kompilierung **ist** möglich. Genau so bauen wir: Das Termux-Buildsystem kompiliert mit dem Android-NDK für alle vier Architekturen (aarch64, arm, i686, x86_64) von einem **x86_64-Linux-Rechner** aus. Das ist der Docker-Builder (`scripts/run-docker.sh`), der auch auf einem GitHub-Actions-Runner läuft [geprüft: `build-package.sh` bietet die Option `-a <arch>` nur an, wenn es **kein** On-Device-Build ist, Zeile 521].
- `generate-bootstraps.sh` läuft tatsächlich auf einem Gerät. Es kompiliert aber **nichts**, sondern lädt fertige `.deb`-Dateien herunter und packt sie zusammen. Wenn der Prefix `com.termux` bleibt, ist das der richtige und schnelle Weg (Minuten statt Stunden).

### 1.3 Was daran nicht stimmt (für unseren Fall)
1. **Die heruntergeladenen Pakete sind für `com.termux` gebaut.** Das Script lädt von `https://packages-cf.termux.dev/apt/termux-main` [geprüft, `scripts/generate-bootstraps.sh`]. In den Binaries steht der Prefix fest: im ELF-Header (RUNPATH), in Zeichenketten, in Pfaden, die dpkg und apt aus dem Quellcode kennen. Ein Textersatz im Rootfs kann das nicht reparieren (Details in Abschnitt 2).
2. **Auf dem Gerät kann man den Prefix nicht ändern.** Android trennt Apps durch Sandboxes: Termux (`com.termux`) darf in `/data/data/com.codeforge.app/` nichts schreiben [Einschätzung, Standard-Android-Verhalten]. Der Termux-Builder installiert aber nach `$TERMUX_PREFIX`, also genau dorthin. Die Toolchain (clang, make, …), die der On-Device-Build benutzt, liegt außerdem unter dem Prefix der laufenden Termux-Installation.
3. **On-Device-Builds bauen nur für die eigene Architektur.** Die Option `-a` fehlt dort [geprüft]. Ein aarch64-Handy baut aarch64. `arm`, `i686` und `x86_64` kommen so nicht heraus.
4. **Nicht jedes Paket unterstützt On-Device.** Pakete mit `TERMUX_PKG_ON_DEVICE_BUILD_NOT_SUPPORTED=true` brechen ab [geprüft, `termux_step_start_build.sh`, Zeile ~110].
5. **Speicher, Zeit, Strom:** `openjdk-17`, `rust`, `llvm` und Ähnliches sind auf dem Handy kaum zu bauen [Einschätzung].

### 1.4 Wann es später doch auf dem Gerät gehen kann [Einschätzung]
Sobald **ein** funktionierendes Bootstrap mit dem Prefix `com.codeforge.app` per Docker/Actions gebaut wurde und die CodeForge-App es installiert hat, läuft dort ein Termux-artiges System mit dem richtigen Prefix. Danach kann man in der App selbst für aarch64 on-device bauen oder Pakete aktualisieren. Das erste Bootstrap ist aber ein Henne-Ei-Problem und muss von außen kommen. Für x86_64/i686 (Emulatoren, Chromebooks) bleibt der Cross-Build auf einem x86_64-Rechner nötig.

**Fazit:** Gemini hat recht, dass Cross-Kompilierung existiert. Er hat unrecht damit, dass man das ohne x86_64-Linux-Host, Docker oder Runner direkt in Termux erledigt, und noch mehr damit, dass sich fremde Prefix-Binaries per Textersatz umbiegen lassen.

---

## 2. Was in `build_codeforge_repo.md` / `.sh` falsch war

Ich vergleiche mit dem Script `build_codeforge_repo.sh` von agy (Antigravity), das aus dem Prompt entstand. Fehler 1 und 2 stehen schon im Prompt, die übrigen kamen in der Umsetzung dazu.

| # | Fehler | Folge | Beleg |
|---|---|---|---|
| 1 | **Bootstrap aus offiziellen `.deb`-Dateien** (`generate-bootstraps.sh`) statt Pakete selbst zu bauen | Alle Binaries tragen den Prefix `com.termux` | [geprüft] Prompt Abschnitt 5, Script Schritt 5 |
| 2 | **Prefix-Wechsel durch `sed s/com.termux/com.codeforge.app/` über das Rootfs** | Bei Textdateien ok, bei ELF-Dateien Zerstörung (siehe unten) | [geprüft] Prompt Abschnitt 5.3, Script `find … sed -i` |
| 3 | **Der `sources.list` von `apt` wurde nie angepasst** | `apt` in der App würde das offizielle Termux-Repo mit `com.termux`-Paketen abfragen | [geprüft] Script enthält keinen Eingriff in `packages/apt/build.sh` |
| 4 | **Das APT-Repo enthielt praktisch nichts.** In den `pool/` kam nur ein einziges `codeforge-tools.deb` (`Architecture: all`), keine gebauten Pakete | Leeres Repo: `Packages` ist für alle Architekturen identisch und ohne die Pakete des Bootstraps | [geprüft] Script Schritt 7 |
| 5 | **Patches per Python ohne Fehler bei Nichttreffer** (`if old in content:` / `content.replace(...)`) | Passt das Muster nicht (andere Version des Scripts), passiert **nichts** und der Build läuft weiter. Auch der Patch in `build-package.sh` meldet nur „Notice: … not found“ | [geprüft] Script Schritt 4 und 5 |
| 6 | **Bei jedem Lauf wird ein neuer GPG-Schlüssel erzeugt** | Jedes Repo hätte eine andere Signatur. Alte Installationen vertrauen dem neuen Schlüssel nicht | [geprüft] Script Schritt 3 (`--gen-key`) |
| 7 | **Passphrase im Klartext in der Batch-Datei** (`Passphrase: ${KEY_PASS}`) | Liegt kurz im Dateisystem (im 700-Verzeichnis). Besser per `--passphrase-fd 0` | [geprüft] |
| 8 | **`cmd \| grep -q` unter `set -o pipefail`** | `grep -q` beendet die Pipe früh, der vorherige Befehl bekommt SIGPIPE, und das Ergebnis ist zufällig falsch (falsch-negativ). Betraf auch die ELF-Prüfung | [geprüft] Script Schritt 5; bei uns durch Variablen-Capture ersetzt |
| 9 | **`Release` wird mit `apt-ftparchive release .` direkt danach in dieselbe Datei geschrieben** | Die Datei kann ihren eigenen Hash enthalten und passt dann nie | [Einschätzung] Ich vermeide es in meiner Version |
| 10 | **`curl -sL` ohne `-f`** beim Laden von `codeforge-tools` | Eine 404-Seite wird als „Archiv“ gespeichert, der Fehler zeigt sich erst bei `tar` | [geprüft] |

### Warum Fehler 2 so schwer wiegt (ELF)
`com.termux` hat 10 Zeichen, `com.codeforge.app` hat 16. In einer Binärdatei sind Zeichenketten in Tabellen gepackt, deren Offsets und Längen sich nicht verschieben dürfen. Ein längenänderndes `sed` über ELF-Dateien **beschädigt** sie. Selbst ein Ersatz mit gleicher Länge wäre riskant, weil Pfade etwa im Lader (`PT_INTERP`, `RUNPATH`) und im dynamischen Abschnitt stehen. Der Prompt verbietet den Ersatz in Binaries zwar ausdrücklich, aber dann hätte er den Ansatz („offizielle Pakete nehmen“) gleich ganz verwerfen müssen. Beides zusammen ist ein Widerspruch: Die Prüfung am Ende („bricht ab, wenn ein ELF `com.termux` enthält“) schlägt bei offiziellen Paketen **immer** an.

---

## 3. Was war das mit `apt`, `sources.list`, leeren Bootstraps, `com.termux` und „ELF inkonsistent“?

Die Begriffe gehören zu einer Kette. Ich gehe sie in der Reihenfolge durch, in der sie bei uns auftraten.

### 3.1 `apt` muss selbst gebaut werden
`apt` und `dpkg` kennen ihre Pfade (`…/usr/etc/apt/`, `…/usr/var/lib/dpkg/`, `…/usr/lib/apt/`) **zur Compile-Zeit**. Ein offizielles `apt` sucht `/data/data/com.termux/files/usr/etc/apt/sources.list` und würde mit unserem Prefix nicht funktionieren. Deshalb baut das Script `apt` (und alles, wovon es abhängt) mit `TERMUX_APP__PACKAGE_NAME=com.codeforge.app` aus dem Quellcode. Daher stand im Log `Building 'apt'…`, und davor wurden etwa 25 Abhängigkeiten gebaut (zlib, openssl, libcurl, gnutls, …).

### 3.2 `sources.list`
`packages/apt/build.sh` erzeugt die Datei `etc/apt/sources.list` mit der URL des offiziellen Repos (`packages-cf.termux.dev`). Ohne Eingriff holt `apt update` in der App die Pakete von dort, also `com.termux`-Pakete, die im fremden Prefix nicht laufen. Das Script ersetzt deshalb den Block in `packages/apt/build.sh` per Regex durch unsere URL (`CODEFORGE_APT_URL`) und bricht ab, wenn das Muster nicht gefunden wird. Das ist der Unterschied zu Fehler 5 in der Tabelle.
Ebenso wird der Schlüsselbund (`termux-keyring`) um unseren öffentlichen Schlüssel erweitert, damit `apt` unsere Signatur akzeptiert.

### 3.3 „Bootstraps leer“, „APT-Repo leer“
- **Bootstrap leer:** `generate-bootstraps.sh` bekam in Schritt 5 seine Pakete aus dem offiziellen Repo. Die ZIPs konnten also nicht sauber sein. Die Prüfung schlug an oder (bei Fehlern in der Pipe) fälschlich nicht an. Mit Variablen-Capture statt `| grep -q` ist das Ergebnis eindeutig.
- **APT-Repo leer:** In den Pool kam nur `codeforge-tools.deb` (siehe Fehler 4). Die gebauten Pakete (`.deb`) wurden nie hineinkopiert.

### 3.4 „Pakete zeigen nach `com.termux`“
Zwei Ursachen:
1. **Offizielle Pakete** (Fehler 1) sind fest auf `com.termux` gebaut.
2. **`TERMUX_REPO_*` in `scripts/properties.sh`** beschreibt, aus welchem Repo `build-package.sh -i/-I` Abhängigkeiten **herunterlädt**. Solange `scripts/repo.json` auf das offizielle Repo zeigt, müssen diese Werte `com.termux` bleiben. Dann meldet das Script `Ignoring -i option to download dependencies since repo package name (com.termux) does not equal app package name (com.codeforge.app)` [geprüft im Log] und baut die Abhängigkeiten lokal. Wären die Werte auf `com.codeforge.app` gesetzt, würde es offizielle Pakete laden und als unsere ausgeben. Das Script prüft das jetzt in Schritt 2.

### 3.5 „ELF inkonsistent“
Gemeint ist der Zustand, in dem ein Binary teils den neuen und teils den alten Prefix enthält: Die Textteile wurden umgeschrieben, aber ELF-Header, Zeichenketten-Tabellen oder RUNPATH nicht oder falsch. Die Datei lässt sich dann nicht mehr laden (Fehler wie `CANNOT LINK EXECUTABLE`, `bad ELF magic` oder ein Absturz im Lader). Die Verifikation unseres Scripts (nach dem Build alle ELF-Dateien auf `com.termux` durchsuchen, Abbruch bei Treffern) soll genau diese Fälle abfangen, bevor die ZIP veröffentlicht wird.

### 3.6 Weitere Schäden im Fork, die der Lauf aufgedeckt hat
Der Fork `scto/terminal-packages-codeforge` (aus `Wadamzmail/terminal-packages-androidide`) war vor dem eigentlichen Build beschädigt. Das stammt aus früheren Kopier- und Ersetzungsläufen, nicht aus dem Original-Termux:

| Schaden | Ursache | Symptom im Build | Reparatur im Script |
|---|---|---|---|
| URLs `github.com/termux/<repo>` → `github.com.codeforge/<repo>` | Globales `sed s/com.termux/…/` (der Punkt trifft auch `/`) | `Could not resolve host: github.com.codeforge` | `repair_mangled_references` |
| Falscher glibc-Pfad | Gleiches `sed` | glibc-Paket wird nicht gefunden | gleiche Funktion (zurück auf `com.termux`, da Pfad **im** offiziellen glibc-Paket) |
| 196 Symlinks sind zu kleinen Textdateien geworden | Kopie ohne Symlink-Erhalt | `cp: cannot stat '…/procps/hsearch/*.h': Not a directory` | `restore_symlinks` |
| 25 Dateien haben das Ausführbar-Bit verloren (nach Vergleich mit dem offiziellen Repo) | gleiche Kopie | `Permission denied` / `make: Error 126` in `termux-core` | `restore_exec_bits` (neu) |

Der Vergleich mit dem Original: `termux/termux-packages` hat 205 Symlinks, der Ursprungsfork `Wadamzmail/terminal-packages-androidide` nur 5 [geprüft]. Ein neuer Fork von dort bringt deshalb nichts.

---

## 4. Wie der aktuelle Ansatz arbeitet und was noch offen ist

Ablauf von `build_codeforge_repo.sh` (Linux x86_64 + Docker, also lokal oder im Actions-Workflow):
1. Fork klonen und prüfen, dass der Prefix `com.codeforge.app` ist.
2. Beschädigte URLs, Symlinks und Ausführbar-Bits reparieren.
3. GPG-Schlüssel laden (einmal erzeugt, als Secret hinterlegt) und in `termux-keyring` einbauen.
4. `apt/sources.list` auf die eigene URL setzen.
5. Bootstraps **aus dem Quellcode** bauen (`build-bootstraps.sh` über `run-docker.sh`), je Architektur.
6. Prüfen: kein ELF enthält `com.termux`.
7. `sha256` je ZIP.
8. Optional weitere Pakete (`REPO_PACKAGES`) bauen, die nur ins APT-Repo kommen.
9. APT-Repo (`pool/`, `dists/`, `Packages`, `Release`, `InRelease`, `Release.gpg`) erzeugen und signieren.

**[offen] / nicht getestet:**
- Der Lauf ist noch nicht komplett durchgelaufen. Letzter bekannter Stand: Abbruch bei `termux-core` wegen des Ausführbar-Bits, jetzt mit `restore_exec_bits` behoben, aber noch nicht erneut gelaufen.
- Weitere Kopierschäden im Fork sind möglich.
- Die Optionen von `build-package.sh` und das Verhalten von `--add` in `build-bootstraps.sh` im Fork sind nicht verifiziert.
- `apt-ftparchive`/Repo-Erzeugung, Signaturprüfung durch `apt` und eine echte Installation in der App sind nicht getestet.
- Mit Android 10+ (`targetSdk ≥ 29`) dürfen Apps Dateien aus dem Datenverzeichnis nicht mehr ausführen (W^X). Ob das für das CodeForge-Terminal gelöst ist, ist eine eigene Frage.

---

## 5. Was Gemini dazu sagen könnte (und meine Antworten)

| Mögliches Argument | Antwort |
|---|---|
| „Der Prefix lässt sich per `sed` im Rootfs ändern.“ | Bei Textdateien ja. Bei ELF nein (Länge 10 vs. 16, Tabellen, RUNPATH). Auch der Prompt verbietet es für Binaries selbst. Wer das widerlegen will, soll ein Bootstrap damit bauen und `grep -rla com.termux` sowie `readelf -d` darauf laufen lassen. |
| „Man kann es doch direkt in Termux machen.“ | Nur im Prefix von Termux (`com.termux`) und nur für die eigene Architektur. In `/data/data/com.codeforge.app/` kann Termux nicht schreiben. |
| „Cross-Kompilierung geht auch.“ | Stimmt. Genau das macht der Docker-Builder mit dem NDK von x86_64-Linux aus. Es geht nicht innerhalb von Termux auf dem Handy. |
| „Docker ist unnötig.“ | Der Builder braucht die Termux-Toolchain und eine feste Umgebung. Docker ist der offiziell unterstützte Weg. Man kann es auch direkt auf einem Linux-x86_64-Rechner einrichten (`scripts/setup-ubuntu.sh`), aber nicht in Termux. |
| „`generate-bootstraps.sh` ist doch schneller.“ | Richtig, und das ist der Standardweg des Termux-Projekts, **solange der Prefix `com.termux` bleibt**. Für einen anderen Prefix taugt er nicht. |
| „Man kann den Prefix zur Laufzeit per `LD_PRELOAD`/`proot` umbiegen.“ | Möglich als Workaround (so arbeiten PRoot-Distros), aber es ist ein anderes Projekt: langsamer, anfälliger, und es macht die App-Prefix-Umstellung überflüssig. Damit wäre das Ziel „echter eigener Prefix“ aufgegeben. [Einschätzung] |

### So kannst du es selbst prüfen
```bash
# Bootstrap entpacken und nach dem alten Prefix suchen
mkdir v && cd v && unzip -q ../bootstrap-aarch64.zip
grep -rlaI 'com\.termux' . | head          # Textdateien
grep -rla  'com\.termux' . | head          # alles inkl. Binaries
# ELF-Eintrag ansehen (in Termux: pkg install binutils)
readelf -d usr/bin/apt | grep -iE 'runpath|rpath|needed' | head
strings usr/bin/dpkg | grep -m3 '/data/data/'
```
Das Bootstrap ist nur brauchbar, wenn die beiden `grep`-Aufrufe nichts ausgeben und `strings` ausschließlich `com.codeforge.app` zeigt.

---

## 6. Zusammenfassung in fünf Sätzen
1. Der erste Ansatz nahm die offiziellen, für `com.termux` gebauten Pakete und versuchte, den Prefix nachträglich per Textersatz zu ändern. Das funktioniert für Texte, nicht für Binaries.
2. Deshalb müssen alle Pakete (inklusive `apt`, `dpkg` und `termux-exec`) mit dem Prefix `com.codeforge.app` aus dem Quellcode gebaut werden.
3. Das geht nur mit dem Cross-Build auf x86_64-Linux (Docker oder GitHub-Actions-Runner), nicht in Termux auf dem Handy.
4. Das alte Script war außerdem unvollständig: leeres Repo, kein `sources.list`-Patch, stille Fehlschläge, jedes Mal ein neuer Schlüssel.
5. Der Fork war beschädigt (URLs, 196 Symlinks, 25 Ausführbar-Bits). Das Script repariert das jetzt selbst. Ob der Gesamtlauf damit durchgeht, zeigt erst der nächste Actions-Lauf.
