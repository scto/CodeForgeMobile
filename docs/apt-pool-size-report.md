# Bericht: Task 53 – APT-Pool unter dem Pages-Limit halten: Auslagerungsgrenze senken, Bestand umziehen, Größenwächter

**Datum:** 2026-10-25  
**Verfasser:** Antigravity (agy)  
**Status:** ✅ Vollständig implementiert, getestet & vorbereitet (Umzug auf GitHub wartet auf Inhaber-Auftrag)  

---

## 1. Befund (53.1)

### 1.1 Fundstellen der bisherigen 95-MiB-Grenze
Eine vollständige Suche (`grep -rnE "(95M|95 MiB|95 MB)"`) identifizierte folgende relevante Stellen im Codebase:
- `.github/workflows/build-bootstrap-repo.yml`: Schritt *Große Pakete abtrennen (> 95 MiB)* mit `find extra-debs -name '*.deb' -size +95M`
- `build_codeforge_repo.sh`: Warnung `find "${REPO_DIR}" -type f -size +95M` sowie Warnung `[ "${pool_mb}" -lt 900 ]`
- `libs/terminal-engine/src/main/assets/codeforge-env`: Kommentarzeilen und Hilfetext (`> 95 MiB`)
- `libs/terminal-engine/snippets/large-pkg-snippet.sh`: Kommentarzeilen
- `README.md` & `README_DE.md`: Abschnitt *Termux Bootstrap & Large Packages*
- `STATUS.md`: Tabellenzeile zu `codeforge-env`
- `docs/BOOTSTRAP-WORKFLOW.md`: Abschnitt *Große Pakete (> 100 MB)*

### 1.2 Offizielles Limit von GitHub Pages (Beleg & Zitat)
- **Quelle:** [GitHub Pages Documentation – About GitHub Pages / Usage Limits](https://docs.github.com/en/pages/getting-started-with-github-pages/about-github-pages#usage-limits) (abgerufen am 2026-10-25).
- **Zitate der Limits:**
  - *"Published GitHub Pages sites may be no larger than 1 GB."* (Die veröffentlichte Seite – ohne `.git` – darf maximal 1 GB groß sein).
  - *"GitHub Pages source repositories have a recommended limit of 1 GB."* (Empfohlenes Limit für das Quell-Repository).
  - *"GitHub Pages sites have a soft bandwidth limit of 100 GB per month."*
  - Zusätzlich gilt das allgemeine Git-Dateilimit: *"GitHub blocks pushes that contain files larger than 100 MB."* (`remote: error: GH001`).

### 1.3 Exakte Pool-Messung Stand 2026-10-25 (15:51 UTC)
Die Messung erfolgte durch Auswertung der aktuellen Indexdateien `Packages.gz` von `gh-pages` (`aarch64` und `arm`):
- **Gesamter APT-Pool:** **982.698.176 Bytes** = **937,17 MiB** = **982,70 MB**.
- **Auslastung des GitHub-Pages-Limits (1.000 MB):** **98,3 %** (bzw. 91,5 % bei 1.024 MiB).
- **Paketanzahl im Index:** 418 Pakete je Architektur (`aarch64`, `arm`), 809 distinkte `.deb`-Dateien im Pool.
- **Die 5 größten Dateien im Pool:**
  1. `pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb`: **96.092.408 Bytes** (91,64 MiB)
  2. `pool/main/o/openjdk-17/openjdk-17_17.0.20_arm.deb`: **92.374.432 Bytes** (88,10 MiB)
  3. `pool/main/o/openjdk-21-source/openjdk-21-source_21.0.12_all.deb`: **49.132.232 Bytes** (46,86 MiB)
  4. `pool/main/o/openjdk-25-source/openjdk-25-source_25.0.4_all.deb`: **48.242.584 Bytes** (46,01 MiB)
  5. `pool/main/o/openjdk-17-source/openjdk-17-source_17.0.20_all.deb`: **46.778.156 Bytes** (44,61 MiB)
- **Summe dieser 5 Dateien:** **332.619.812 Bytes** = **317,21 MiB** (332,62 MB).
- **Lücke im Datenbestand:** Zwischen 15 MiB und 30 MiB existiert **keine einzige Datei** im gesamten Pool. Die nächstgrößte Datei ist `perl_5.42.2_aarch64.deb` mit **14,74 MiB** (15.456.456 Bytes).
- **Erwartete Pool-Größe nach Umzug der 5 Dateien:** **619,96 MiB** (650,08 MB) – eine Einsparung von 34 % des aktuellen Bestands.

### 1.4 Installationsverhalten über `codeforge-env` & Folgen
- **Wie große Pakete geladen werden:** `codeforge-env pkg <paket>` (bzw. `install_repo_pkg`) lädt `<apt-url>/large-packages.txt` herunter, prüft die optionale GPG-Signatur (`large-packages.txt.asc`) via `gpgv`, findet das Paket nach Name und Architektur (`arch` oder `all`), lädt die `.deb`-Datei von der GitHub-Release-URL, verifiziert die SHA-256-Prüfsumme und installiert sie lokal via `apt install -y <deb-pfad>`.
- **Folgen für Direktaufrufe:**
  - Da ausgelagerte Pakete nicht in `dists/stable/main/binary-*/Packages` stehen, findet ein direktes `apt install openjdk-17` das Paket im Repository nicht.
  - Sobald `openjdk-17` über `codeforge-env pkg openjdk-17` (oder im Onboarding-Setup) installiert wurde, ist der dpkg-Status `installed`. Abhängige Pakete wie `openjdk-17-x` können danach problemlos per `apt install openjdk-17-x` installiert werden.
  - Die App CodeForge Mobile verwendet für alle JDK-Installationen und Paketinstallationen standardmäßig `codeforge-env` (`feature/onboarding`, `feature/sdkmanager`, `feature/settings`).

### 1.5 Verbraucher von `large-packages.txt`
Alle Leser des Manifests wurden geprüft:
1. `build-bootstrap-repo.yml`: Kombiniert bestehende `large-packages.txt` und `big-new.txt` im Assemble.
2. `scripts/check-apt-consistency.py`: Liest `--large-packages` (Task 51).
3. `.github/scripts/generate-apt-index.py`: Zeigt ausgelagerte Pakete mit Badge `groß` und Link auf GitHub Releases in `index.html`.
4. `codeforge-env` / `large-pkg-snippet.sh`: Lädt und installiert Release-Assets.
5. `large-pkg-snippet.test.sh`: Testet Download und Prüfung.

---

## 2. Entscheidung: Neue Auslagerungsgrenze (53.2)

- **Gewählte Grenze:** **30 MiB** (`LARGE_PKG_THRESHOLD_MIB=30`).
- **Begründung:**
  1. **Natürliche Lücke:** Zwischen `perl` (14,7 MiB) und `openjdk-17-source` (44,6 MiB) liegen 30 MiB Leerraum.
  2. **Maximale Entlastung bei minimalen Einträgen:** Mit genau 5 verschobenen Dateien werden sofort **317,21 MiB** eingespart. Der Pool schrumpft auf **~620 MiB**.
  3. **Ausstehende Gruppen geschützt:**
     - Gruppe A (`04-jvm-tools`): `gradle` (~142 MiB) ist bereits ausgelagert. `kotlin` (~70–75 MiB) wird mit der 30-MiB-Grenze sofort automatisch abgetrennt, statt den Pool wieder auf > 700 MB aufzublasen. `maven` (~12 MiB) und `ant` (~4 MiB) verbleiben regulär im Pool.
     - Gruppe H4 (`libllvm`, `clang`): `libllvm-static` (~100 MiB) ist bereits ausgelagert. `libllvm` und `clang` (je ca. 45–60 MiB) werden bei 30 MiB ebenfalls als Release-Asset abgetrennt.
- **Verworfene Alternativen:**
  - *50 MiB:* Würde die drei JDK-Source-Pakete (`openjdk-17/21/25-source`, je 44–47 MiB, zusammen ~138 MiB) im Pool belassen.
  - *20 MiB:* Bringt gegenüber 30 MiB keine zusätzliche Ersparnis, da keine Pakete zwischen 15 und 30 MiB existieren, könnte aber künftig kleinere Tools unnötig auslagern.
- **Single Source of Truth:**
  - Zentrale Konfigurationsdatei: [`apt-limits.conf`](file:///data/data/com.termux/files/home/CodeForgeMobile/apt-limits.conf) im Repository-Root:
    ```bash
    LARGE_PKG_THRESHOLD_MIB=30
    POOL_WARN_LIMIT_MB=800
    POOL_MAX_LIMIT_MB=950
    ```

---

## 3. Umsetzung des Umzugs (`rehome-large`, 53.3)

### 3.1 Skript `scripts/rehome-large-packages.sh`
Ein vollständiges Werkzeug für den sicheren Umzug:
- Standardmäßig **sicherer Trockenlauf** (`--dry-run`).
- Liest `apt-limits.conf`, scannt `pool/` nach Dateien `+${THRESHOLD_MIB}M`.
- Bei `--execute`:
  1. **Prüfen & Vorbereiten:** Release `large-packages` verifizieren.
  2. **Sicherheitskritische Reihenfolge:** Jedes Asset wird zuerst zu GitHub hochgeladen und über die API (`size` & Name) verifiziert. Erst wenn die Verifikation erfolgreich ist, wird die Datei aus dem Pool entfernt.
  3. **Manifest:** `large-packages.txt` wird aktualisiert (neuester Eintrag gewinnt, sortiert).
  4. **Metadaten neu bauen:** `Packages` und `Packages.gz` pro Architektur neu generiert.
  5. **Release & Signaturen:** `Release` neu generiert, `Release.gpg`, `InRelease` und `large-packages.txt.asc` mit GPG signiert und verifiziert.
  6. **Index & Konsistenz:** `generate-apt-index.py` und `check-apt-consistency.py repo` ausgeführt.
  7. **Rückgängig-Sicherheit:** Bricht ein Upload oder eine Signaturprüfung ab, bleibt der Pool konsistent; keine Dateien gehen verloren.

### 3.2 GitHub Actions Workflow `.github/workflows/rehome-large-packages.yml`
- Manuell triggerbar (`workflow_dispatch`).
- Parameter: `dry_run` (Standard: `true`), `threshold_mib` (Standard: `30`), `target_repo`.
- Hält dieselbe Concurrency-Gruppe `build-all-stages` (`cancel-in-progress: false`).

---

## 4. Größenwächter im Zusammenbau (53.4)

Im Workflow `.github/workflows/build-bootstrap-repo.yml` (sowohl `assemble` als auch `build_full`):
- **Messung:** Nach Erstellung des Repositories wird die Größe ohne `.git` berechnet und in `GITHUB_STEP_SUMMARY` ausgegeben.
- **Warnschwelle (`POOL_WARN_LIMIT_MB=800`):** Erzeugt eine GitHub-Actions-Warnung `::warning title=APT-Pool nähert sich dem GitHub Pages Limit::...`.
- **Abbruchschwelle (`POOL_MAX_LIMIT_MB=950`):** Bricht die Veröffentlichung mit `::error` ab, listet die 5 größten Dateien in der Step-Summary auf und verweist auf `rehome-large`.
- **Übersteuerung:** Neuer Workflow-Input `force_publish` (Standard: `false`), der auch von `build-all-stages.yml` durchgereicht wird.
- **Abstimmung mit `build_codeforge_repo.sh`:** Das Build-Skript liest `apt-limits.conf` ein und synchronisiert seine Warnungen mit denselben Schwellenwerten.

---

## 5. Verifikation & Testergebnisse (53.6)

### Getestet
- ✅ **Integrationstest `scripts/test_rehome_large_packages.sh`:**
  - Test 1 (Trockenlauf): Erkennt Dateien > 30 MiB korrekt, ändert keine Dateien im Pool, meldet Ersparnis.
  - Test 2 (Ausführung mit Mocks): Lädt Assets hoch, verifiziert Asset-Größe, aktualisiert `large-packages.txt`, entfernt Asset aus `pool/`, belässt kleinere Pakete (`jq`) im Pool, erzeugt `Packages`/`Release` und Signaturen, führt `check-apt-consistency.py` fehlerfrei aus.
- ✅ **Unit-Test `scripts/test_generate_apt_index.py`:**
  - Verifiziert, dass `generate-apt-index.py` ausgelagerte Pakete aus `large-packages.txt` darstellt, mit Tag `groß` markiert und Links auf das Release setzt.
- ✅ **Snippet-Test `libs/terminal-engine/snippets/large-pkg-snippet.test.sh`:**
  - Verifiziert, dass `openjdk-17` und `openjdk-21-source` nach Absenkung der Schwelle den Release-Asset-Pfad über `large-packages.txt` nehmen und SHA-256-geprüft installiert werden.
- ✅ **APT-Konsistenzprüfung `scripts/test_check_apt_consistency.py`:**
  - Alle 15 Tests erfolgreich (0.007s).
- ✅ **Linter & Shell-Validierung:**
  - `shellcheck build_codeforge_repo.sh scripts/rehome-large-packages.sh scripts/test_rehome_large_packages.sh` fehler- und warnungsfrei (Exit 0).
  - `actionlint .github/workflows/*.yml` fehler- und warnungsfrei (Exit 0).

### Nicht getestet
- Ausführung von `rehome-large-packages.sh --execute` mit echten Tokens auf GitHub (gemäß Task-Regel: **Keine Veröffentlichung, kein Umzug im echten Repo ohne Auftrag des Inhabers**).
- Installation der umgezogenen Pakete auf physischem Android-Gerät (durch Inhaber).

### Offene Punkte
- Start des Workflows `Rehome Large Packages (Task 53)` auf GitHub durch den Inhaber:
  1. Zuerst mit `dry_run: true` zur Kontrolle.
  2. Danach mit `dry_run: false` zur Ausführung des Umzugs (spart ~317 MiB).
- Nach erfolgreichem Umzug: Start der ausstehenden Gruppe `A-leicht-jvm` (`build-all-stages.yml`, `only=A-leicht-jvm`).
