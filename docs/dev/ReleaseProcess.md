# Release-Ablauf: Version und Release Notes automatisch

`scripts/release/bump_version.py` zählt die Version hoch und erzeugt die Release Notes. Es nutzt nur die
Python-Standardbibliothek.

## Versionsschema

`versionName` = `MAJOR.MINOR.PATCH`, `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH`
(3.0.1 → `30001`). Beide stehen in `app/build.gradle.kts`; MINOR und PATCH dürfen höchstens 99 sein.

## Ablauf

1. Während der Arbeit Änderungen unter `## [Unreleased]` in `CHANGELOG.md` sammeln
   (`### Hinzugefügt | Geändert | Behoben (Bereich)`, je Eintrag ein Aufzählungspunkt).
2. Release vorbereiten:

   ```bash
   python3 scripts/release/bump_version.py patch              # 3.0.1 -> 3.0.2
   python3 scripts/release/bump_version.py minor --dry-run    # nur anzeigen
   python3 scripts/release/bump_version.py 3.1.0 --date 2026-12-01
   ```

3. Notes prüfen: `docs/release-notes/X.Y.Z.md` (Deutsch) und `X.Y.Z.en.md` (Englisch). Der Generator übernimmt
   die CHANGELOG-Einträge (erster Satz je Eintrag). Die englische Datei enthält die deutschen Texte und einen
   `TODO`-Kommentar – vor dem Release übersetzen. Highlights schreibt man von Hand.
4. Committen, taggen, pushen:

   ```bash
   git add -A && git commit -m "chore(release): X.Y.Z" && git push
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```

## Was das Skript ändert

| Datei | Änderung |
|---|---|
| `app/build.gradle.kts` | `versionName`, `versionCode` |
| `CHANGELOG.md` | `[Unreleased]` → `[X.Y.Z] - Datum`, neuer leerer `[Unreleased]` |
| `docs/release-notes/X.Y.Z[.en].md` | neu (Installation, Einschränkungen und Lizenz aus den vorherigen Notes übernommen) |
| `mkdocs.yml` | Navigation |
| `README.md`, `README_DE.md`, `STATUS.md`, `docs/apk/…`, `docs/index.md`, `docs/overview.md` | Versionsangaben |

Ist `[Unreleased]` leer, erzeugt das Skript die Einträge aus den Conventional Commits seit dem letzten
`v*`-Tag (`feat` → Hinzugefügt, `fix` → Behoben, sonst Geändert); `--from-git` ergänzt sie auch bei gefülltem
Abschnitt. `--notes-only X.Y.Z` erzeugt die Notes einer bestehenden CHANGELOG-Version neu.

## Per GitHub Actions

Workflow **Bump Version** (*Actions → Bump Version → Run workflow*): Art wählen, das Ergebnis wird als Commit
gepusht. Mit *build* lässt sich danach direkt bauen: `debug` startet **Release Debug APK** (Debug-Schlüssel), `release`
startet **Release Release APK** (Tag `v<Version>`, Release-Schlüssel aus den Secrets, Schalter *prerelease*,
siehe [Release-Signatur](Signing.md)). Beide übernehmen die Release Notes der Version
(`docs/release-notes/<Version>.md`) als Release-Text.

> Das Skript ändert bewusst keine Dateien unter `.github/workflows/`: der `GITHUB_TOKEN` darf sie nicht pushen
> ("refusing to allow a GitHub App to create or update workflow … without `workflows` permission").
