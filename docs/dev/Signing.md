# Release-Signatur: Secrets für CI und lokale Builds

Die Release-APK wird mit einem eigenen Schlüssel signiert. `app/build.gradle.kts` liest ihn aus
`keystore.properties` (lokal) **oder** aus Umgebungsvariablen (CI). Fehlt beides, wird der Release-Build mit dem
Debug-Schlüssel signiert (installierbar, aber nicht für die Veröffentlichung).

| Wert | `keystore.properties` | Umgebungsvariable | GitHub-Secret |
|---|---|---|---|
| Keystore-Datei | `storeFile` | `CODEFORGE_STORE_FILE` | `CODEFORGE_KEYSTORE_BASE64` (Datei als Base64) |
| Keystore-Passwort | `storePassword` | `CODEFORGE_STORE_PASSWORD` | `CODEFORGE_STORE_PASSWORD` |
| Alias | `keyAlias` | `CODEFORGE_KEY_ALIAS` | `CODEFORGE_KEY_ALIAS` |
| Schlüssel-Passwort | `keyPassword` | `CODEFORGE_KEY_PASSWORD` | `CODEFORGE_KEY_PASSWORD` |

## 1. Schlüssel erzeugen (einmalig)

```bash
keytool -genkeypair -v \
  -keystore codeforge-release.jks -storetype PKCS12 \
  -alias codeforge -keyalg RSA -keysize 4096 -validity 10000
```

Passwörter wählen, Namen/Organisation eintragen. Bei PKCS12 sind Keystore- und Schlüssel-Passwort praktisch
identisch – trotzdem beide Secrets setzen.

> **Sichern!** Datei und Passwörter in einen Passwortmanager und ein zweites Offline-Backup. Geht der Schlüssel
> verloren, kann keine bestehende Installation mehr aktualisiert werden. Den Schlüssel **nie** ins Repository
> committen (`*.keystore`, `*.jks`-Dateien und `keystore.properties` gehören in `.gitignore`).

Prüfen: `keytool -list -v -keystore codeforge-release.jks` (zeigt Alias und SHA-256-Fingerabdruck).

## 2. CI-Builds (GitHub Actions)

Workflow: `.github/workflows/release-release-apk.yml`. Er liest vier Secrets, stellt die Keystore-Datei aus dem
Base64-Wert unter `$RUNNER_TEMP` wieder her, baut `:app:assembleRelease`, prüft mit `apksigner`, dass **nicht** der
Debug-Schlüssel benutzt wurde, und löscht die Datei danach.

**Base64 erzeugen** (eine Zeile, ohne Zeilenumbrüche):

```bash
base64 -w0 codeforge-release.jks > codeforge-release.jks.b64     # Linux / Termux
base64 -i codeforge-release.jks | tr -d '\n' > codeforge-release.jks.b64   # macOS
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("codeforge-release.jks")) | Set-Content -NoNewline codeforge-release.jks.b64   # Windows
```

**Secrets anlegen** – entweder im Browser (*Repository → Settings → Secrets and variables → Actions → New repository
secret*) oder mit der GitHub-CLI (`gh auth login` vorausgesetzt):

```bash
gh secret set CODEFORGE_KEYSTORE_BASE64 < codeforge-release.jks.b64
gh secret set CODEFORGE_STORE_PASSWORD   # fragt den Wert interaktiv ab
gh secret set CODEFORGE_KEY_ALIAS
gh secret set CODEFORGE_KEY_PASSWORD
```

Optional: Environment **release** (*Settings → Environments*) anlegen, die Secrets dort statt auf Repository-Ebene
hinterlegen und „Required reviewers“ aktivieren – dann braucht jeder Release eine Freigabe.

Danach ein Tag pushen, das zum `versionName` passt (`v3.0.1` ↔ `3.0.1`), oder den Workflow manuell starten
(*Actions → Release Release APK → Run workflow*). Secrets sind für Forks und Pull-Requests nicht verfügbar; der
Workflow läuft nur auf Tags und manuell.

## 3. Lokale Builds

**Variante A – `keystore.properties`** im Projektstamm (ist ignoriert):

```properties
storeFile=/home/ich/keys/codeforge-release.jks
storePassword=…
keyAlias=codeforge
keyPassword=…
```

Relative Pfade gelten ab dem Projektstamm. Dann:

```bash
./gradlew :app:assembleRelease
```

**Variante B – Umgebungsvariablen** (nichts auf der Platte im Projekt, z. B. auf dem Handy in Termux/CodeForge):

```bash
export CODEFORGE_STORE_FILE=$HOME/keys/codeforge-release.jks
export CODEFORGE_STORE_PASSWORD=…
export CODEFORGE_KEY_ALIAS=codeforge
export CODEFORGE_KEY_PASSWORD=…
./gradlew :app:assembleRelease
```

**Variante C – dieselbe Base64-Datei wie in CI:**

```bash
base64 -d codeforge-release.jks.b64 > "$HOME/keys/codeforge-release.jks"
chmod 600 "$HOME/keys/codeforge-release.jks"
```

**Ergebnis prüfen:**

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Der angezeigte SHA-256-Fingerabdruck muss zu dem aus `keytool -list` passen und darf nicht „Android Debug“ sein.

## 4. Vom Pre-release zum ersten öffentlichen Release

Technisch sind es zwei Schalter, inhaltlich eine Checkliste.

**Schalter**
- Manuell: *Run workflow* mit `prerelease = false`.
- Per Tag: Repository-Variable `RELEASE_IS_PUBLIC` = `true` setzen (*Settings → Secrets and variables → Actions →
  Variables*); Tag-Läufe erzeugen dann einen normalen Release und markieren ihn als „Latest“.
- Release Notes ohne Vorabhinweis: `python3 scripts/release/bump_version.py <Version> --public` (der Workflow
  entfernt den Hinweis `> ⚠️ …` im Release-Text bei öffentlichen Releases ohnehin).

**Checkliste vorher**
1. **Release-Schlüssel** erzeugt, gesichert und als Secrets hinterlegt (Abschnitte 1–2). Erst-Lauf als Pre-release
   testen: `apksigner` muss den Release-Fingerabdruck zeigen.
2. **Signaturwechsel:** Debug-Builds (Debug-Schlüssel) lassen sich nicht über die Release-APK aktualisieren –
   Android verlangt dieselbe Signatur. Testgeräte und frühe Nutzer müssen einmalig deinstallieren (Daten sichern;
   Projekte liegen in `CodeForgeMobileProjects`). Ab dem ersten Release-Schlüssel darf er nie mehr wechseln.
3. **Gerätetests** abarbeiten (`docs/device-test-checklist.md`: Bootstrap, W^X, Layouts, LSP, Debugger) und die
   Release-APK (R8!) auf einem echten Gerät starten – R8 kann Reflection-Pfade (Sora-Editor, Tree-sitter, Plugins)
   entfernen, die der Debug-Build noch hat.
4. **Version/Notes:** gewünschte Versionsnummer festlegen (z. B. `3.1.0`), `bump_version.py … --public`, englische
   Notes übersetzen, CHANGELOG prüfen; „Bekannte Einschränkungen“ ehrlich halten (Code Folding, targetSdk 28,
   SDK 37 nur aarch64).
5. **Dokumentation:** `README`/`README_DE` (Download-Link auf den neuesten Release, Badge), `docs/index.md`
   (Hinweis „Release-APK noch nicht auf Geräten geprüft“ entfernen), `docs/apk/APK-Dokumentation.md`.
6. **Recht & Vertrauen:** Lizenz GPLv3 und `docs/sub/NOTICE.md` aktuell, Datenschutz-Hinweis (KI-Anbieter-Schlüssel
   lokal verschlüsselt, Netzwerkzugriffe: GitHub, KI-APIs, Paket-Mirror), `SECURITY.md`/Issue-Vorlagen.
7. **Verteilung:** `targetSdk = 28` (nötig für ausführbare Termux-Binaries) schließt den Play Store aus. Bleiben:
   GitHub Releases (mit `SHA256SUMS.txt`/`SIGNER.txt`), optional eigenes F-Droid-Repo oder F-Droid-Hauptrepo
   (verlangt reproduzierbaren Bau aus dem Quelltext und Metadaten).
8. **Tag setzen** (`vX.Y.Z`, passt zu `versionName` – der Workflow bricht sonst ab) und Release-Lauf beobachten.
