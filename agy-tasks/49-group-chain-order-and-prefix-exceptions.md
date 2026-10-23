# Task 49 – Gruppenkette: Reihenfolge, Abbruch bei fehlgeschlagenem Zusammenbau, Ausnahmen für eingebaute Pfade

**Ziel:** Ein Lauf der leichten Gruppen (A bis F) liefert ein vollständiges, konsistentes Repo oder bricht früh und deutlich ab. Verworfene Pakete sind im Job-Status sichtbar. Bekannte, begründete Pfad-Treffer in Binärdateien (taplo, shellcheck) blockieren das Paket nicht mehr, alle anderen Treffer bleiben streng.
**Warum jetzt:** Lauf 108064644844 (2026-10-22/23, `preset=light`, `mode=add`, `archs=aarch64,arm`, `publish=true`). Gruppe A scheiterte im Zusammenbau, B bis F liefen trotzdem weiter und wurden veröffentlicht. Ergebnis: gh-pages enthält B bis F (679 Pakete im Pool), aber **nicht** die 12 neuen Pakete aus A (gradle, kotlin, maven, libjansi, ant, apksigner, d8, dx, codeforge-env, yarn, autoconf, automake; Liste im Log prüfen).

**Gemeinsame Regeln:** wie Task 46/48 (Shell mit `set -euo pipefail`, shellcheck-sauber; GitHub-Actions-Dateien mit `actionlint`; Clean-Architecture-Regeln entfallen). Nichts erfinden: Was nicht selbst ausgeführt wurde, steht im Bericht unter **nicht getestet**. Geheimnisse (GPG-Passphrase, Tokens, Schlüsselmaterial) nie ins Log, nie in Dateien, nie in Berichte. Keine Pushes ohne Auftrag des Inhabers. Bericht in `docs/group-chain-report.md` (getestet / nicht getestet / offene Punkte); `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash). Die Betriebshandbuch-Zeile „Paket fehlt im Repo trotz grünem Lauf“ (Task 99.8, `08-fehlersuche.md`) und die Reihenfolge-Regel unter 49.1 werden dort nachgetragen, sobald 99.8 existiert.

## 49.1 Befund (erst selbst prüfen)

Aus den Logs des Laufs (Dateien `26_A-leicht-jvm _ assemble.txt`, `27_A-leicht-jvm _ build_add (aarch64).txt`, `30_Gruppen planen.txt`, `0_Zusammenfassung.txt`):

* **Gruppe A, Zusammenbau, 19 Fehler der Konsistenzprüfung** (`consistency_check: fail`):
  * `openjdk-17` und `openjdk-21` nicht erfüllbar (ant, apksigner, d8, kotlin, maven, codeforge-env): Die Heavy-Gruppen H1-jdk17 und H2-jdk21 sind noch nie gelaufen.
  * `m4` nicht erfüllbar (autoconf): `m4` stand in keiner Stage und keiner Gruppe; autoconf wurde mit `SKIP_DEPS_PACKAGES` (`-s`) ohne Abhängigkeiten gebaut. **Der Inhaber hat `m4` inzwischen in `apt-groups.json` bei A eingetragen** (prüfen, an welcher Stelle der Liste; `m4` muss vor `autoconf` stehen).
  * `jq` (codeforge-env) und `nodejs`/`nodejs-lts` (yarn): kommen erst in Gruppe B bzw. E, die nach A laufen. Sie liegen inzwischen im Repo.
  * `libjansi (>= 2.4.0-1)` nur für `arm` (maven): A baut im Lauf nur `aarch64`. Prüfen, ob das gewollt ist (Stage-Konfiguration, Matrix) und ob die Pakete `all` oder `aarch64` sind.
* **Kette läuft nach fehlgeschlagenem Zusammenbau weiter:** B (Basis 420 Pakete, gleicher Stand wie vor A) bis F wurden zusammengebaut und mit `git push --force origin gh-pages` veröffentlicht; erst der Job „Zusammenfassung“ meldet `failure`. Prüfe in `build-all-stages.yml`, warum (`stop_on_error: false`? `if: always()`?), und ob eine veröffentlichte Zwischenstufe nach einem Fehler gewollt ist.
* **Verworfene Pakete, grüne Jobs** (`keep_going`): openssh (B, aarch64+arm, Task 48), taplo (D, aarch64+arm), shellcheck (D aarch64: `com.termux`; D arm: Cabal-Download `splitmix` vom Hackage-Mirror mit HTTP 403, vermutlich vorübergehend).
* **Treffer in taplo/shellcheck:** nur in ELF-Dateien, nicht in Text. taplo: Liste von Zertifikatspfaden (`…/data/data/com.termux/files/usr/etc/tls` zwischen weiteren Pfaden). shellcheck: eingebaute Rückfallwerte des GHC-Laufzeitsystems (`/data/data/com.termux/files/home`, `…/usr/bin/bash`, `…/usr/bin/login`). Ersetzen im fertigen Binary geht nicht (`com.codeforge.app` ist länger als `com.termux`).

Prüfe diese Aussagen vor dem Start (Logs und Fork) und korrigiere sie im Bericht.

## 49.2 Reihenfolge und Abbruch

1. **Reihenfolge:** Die Abhängigkeiten von Gruppe A (JDK, m4, jq, nodejs) müssen vor A im Repo liegen. Lege eine **belegte Reihenfolge** fest und dokumentiere sie in `docs/BOOTSTRAP-WORKFLOW.md` (Gruppentabelle) und `apt-groups.json`:
   * Entscheidungsvorschlag (der Inhaber entscheidet): Heavy-Gruppen H1-jdk17 und H2-jdk21 zuerst, danach die leichten Gruppen; Gruppe A rückt hinter B und E (jq, nodejs). Alternative: A bleibt vorn, `consistency_check=warn` nur für diesen Lauf. Beschreibe beide mit Folgen (A ohne JDK ist bis zu H1/H2 nicht installierbar) und setze die gewählte um.
   * Ein kleines Skript (z. B. `scripts/check-group-order.py`, nur Standardbibliothek, mit `python3 -I`) liest die `Depends` aller Pakete der Gruppen (aus `apt-stages.conf`/`apt-groups.json` und den Fork-Rezepten, soweit lesbar) und meldet, wenn eine Abhängigkeit in einer **späteren** Gruppe liegt oder in keiner. Es ändert nichts.
2. **Abbruch:** Schlägt der Zusammenbau (Konsistenzprüfung) einer Gruppe fehl, laufen die folgenden Gruppen **nicht** weiter, und es wird **nichts** veröffentlicht, solange nicht `stop_on_error: false` ausdrücklich gesetzt ist. Mit `stop_on_error: false` weiterlaufen ist erlaubt, aber der Job „Zusammenfassung“ nennt dann die betroffenen Gruppen und die Pakete, die im veröffentlichten Repo **fehlen**.
3. **Nur Fehlendes neu bauen:** Beschreibe im Bericht und im Workflow-Kommentar, wie ein einzelner Gruppenlauf nachgeholt wird (`only=A-leicht-jvm`, `mode=add`), und prüfe, dass dieser Lauf die bereits veröffentlichten Pakete von B bis F nicht verändert (Pool, `Packages`, Signatur).

## 49.3 Ausnahmeliste für eingebaute Pfade in Binärdateien

1. Neue Datei `prefix-exceptions.conf` (neben `apt-stages.conf`): Zeilen `paket|Dateiglob|Begründung`. Startinhalt: `taplo|usr/bin/taplo|…` und `shellcheck|usr/bin/shellcheck|…` mit der Begründung aus 49.1 (im Bericht belegen).
2. `build_codeforge_repo.sh` (Prüfung ~Zeile 705): Ein Treffer gilt nur dann als Ausnahme, wenn (a) das Paket in der Liste steht, (b) die Fundstelle in einer **ELF-Datei** liegt und (c) der Dateipfad auf das Glob passt. Alle anderen Treffer (Textdateien, andere Pakete, andere Dateien im selben Paket) bleiben Fehler. Bei einer Ausnahme: Zeile `AUSNAHME: <Paket>: <Datei> enthält com.termux (siehe prefix-exceptions.conf)` ins Log und ins `GITHUB_STEP_SUMMARY`; das Paket wird aufgenommen.
3. Prüfe, ob eine Ausnahme vermeidbar ist (kurz, ohne Aufwand-Spirale): taplo (Rust-Crate mit festen Zertifikatspfaden; Rezept im Fork), shellcheck (GHC-Laufzeitsystem). Wenn eine Änderung am Quelltext klein und sicher ist, schlage sie im Bericht vor, setze sie aber nur um, wenn der Inhaber zustimmt.
4. **Wirkung prüfen (nicht raten):** Was passiert zur Laufzeit mit dem eingebauten Pfad? Für taplo: Zertifikatssuche nur bei Schema-Downloads; welcher Umgebungswert (`SSL_CERT_FILE`) hilft? Für shellcheck: welche Funktion nutzt die Pfade? Beides als Absicht nur im Bericht, Geräteversuch durch den Inhaber (**nicht getestet**, bis er ihn gemacht hat).
5. Test: Fixture-Pakete (Text mit `com.termux`, ELF mit `com.termux` in der Liste, ELF nicht in der Liste); erwartetes Ergebnis je Fall. Die Prüfung für die übrigen Pakete (z. B. openssh, Task 48) bleibt unverändert streng.

## 49.4 Sichtbarkeit verworfener Pakete

Task 48.3 (Warnung, `failed-packages.txt`, `fail_on_skipped`) ist Voraussetzung. Im Zusammenbau-Job gibt es bereits einen Schritt, der `extra-debs/*/failed-packages-*.txt` in die Zusammenfassung schreibt („Nicht gebaut …“). Prüfe, **warum die Zusammenfassung dieses Laufs die verworfenen Pakete von B und D nicht zeigte** (Dateiname, Pfad, Artefaktname, nur bei `keep_going`?), und behebe es. Ziel: Pro Gruppe steht in der Zusammenfassung, welche Pakete in welcher Architektur fehlen und warum (Kurzgrund aus dem Log).

## 49.5 shellcheck arm und andere Netzwerkfehler beim Bauen

* shellcheck arm: `Unexpected response 403` vom Hackage-Mirror (`objects-us-east-1.dream.io`) für `splitmix`. Ein erneuter Lauf kann helfen; eine Wiederholung mit Wartezeit (zwei bis drei Versuche) in `build_codeforge_repo.sh` für den Cabal-Schritt ist erlaubt, wenn sie sich sauber einhängen lässt. Grund und Ergebnis im Bericht; kein stilles Überspringen.
* Prüfe, ob andere Schritte ähnlich anfällig sind (Downloads ohne Wiederholung) und liste sie auf, ohne alles umzubauen.

## 49.6 Verifikation (Inhaber führt aus, Agent bereitet vor)

1. Nach dem Merge: H1-jdk17 und H2-jdk21 (je nach Entscheidung 49.2) laufen lassen, dann `only=A-leicht-jvm`, `mode=add`, `archs` wie im Lauf. Erwartet: Konsistenzprüfung grün, 12 Pakete aus A im Repo.
2. `scripts/check-apt-consistency.py` (Task 45) gegen das veröffentlichte Repo: fehlen nur noch bewusst verworfene Pakete?
3. Gerät (nur durch den Inhaber): `apt update`, `apt-cache policy codeforge-env gradle kotlin autoconf m4`.
4. Die Doku (Task 99.8, `06-pflege-nach-ausloeser.md`) um die Reihenfolge und den Nachhol-Lauf ergänzen.

## 49.7 Commit-Vorschlag
```
fix(apt-chain): stop after failed assemble, order groups by dependency, allow documented ELF exceptions

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
