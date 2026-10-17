# Task 24 – Onboarding-Farben, Rechte, Launcher-Icon, volle Termux-UI, sichtbare Setup-Fehler

**Anlass:** Gerätetest (Screenshots vom 2026-10-17, Stand `baa1e07`). Alle fünf Punkte sind im Code belegt.
Regeln wie in allen Tasks: nichts raten, nach jedem Punkt `./gradlew :app:assembleDebug`, am Ende `./gradlew ktlintCheck` (falls vorhanden),
Ergebnis in `docs/task-24-report.md` mit Abschnitt **getestet / nicht getestet**. Neue UI-Texte nur über `:core:resources` (`values/` Englisch, `values-de/` Deutsch).

---
## 24.1 Onboarding: schwarzer Text auf dunklem Hintergrund
**Ursache:** `OnboardingRoute` rendert in einer `Box` ohne `Surface`/`Scaffold`. Ohne Surface gibt es keine `LocalContentColor` aus dem Theme, `Text` fällt auf Schwarz zurück.
**Auftrag:**
1. In `feature/onboarding/.../OnboardingRoute.kt` die `Box` in `Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)` einbetten (`safeDrawingPadding` bleibt innen).
2. `Icon` in `IntroPagerScreen`/`PermissionRow` ohne `tint` erbt dann ebenfalls `onBackground`; das Intro-Icon (`Code`, `Terminal` …) auf `MaterialTheme.colorScheme.primary` setzen und auf 64.dp vergrößern.
3. Alle anderen Screens, die ohne `Scaffold`/`Surface` direkt `Text` setzen, per Suche finden (`grep -rn "setContent\|composable(" app feature | …`) und prüfen; Funde im Bericht listen und beheben.
4. Compose-`@Preview` für Hell und Dunkel der drei Onboarding-Schritte ergänzen.

## 24.2 Rechte im Onboarding
**Ist:** Angefragt wird `READ_EXTERNAL_STORAGE`, im Manifest nur mit `maxSdkVersion="32"`; der Text verspricht `MANAGE_EXTERNAL_STORAGE`. Auf Android 13+ erscheint kein Dialog. Rechte zum Installieren/Löschen von Paketen fehlen.
**Auftrag:**
1. **Dateizugriff (alle Dateien):** Manifest `MANAGE_EXTERNAL_STORAGE` ergänzen. Anfrage über `Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))` (API 30+), darunter `READ/WRITE_EXTERNAL_STORAGE` per `RequestPermission` (nur API 26–29, dort `maxSdkVersion` setzen). Status mit `Environment.isExternalStorageManager()` bzw. `checkSelfPermission` lesen und **bei `ON_RESUME` neu auswerten** (Rückkehr aus den Einstellungen), nicht nur aus dem Launcher-Ergebnis.
2. **Apps installieren:** `REQUEST_INSTALL_PACKAGES` ins Manifest; Status `packageManager.canRequestPackageInstalls()`, Anfrage über `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` mit `package:`-URI.
3. **Apps löschen:** `REQUEST_DELETE_PACKAGES` ins Manifest. Das ist eine normale Berechtigung ohne Laufzeitdialog; im Onboarding nur als Info-Zeile „wird beim Installieren erteilt“ mit Häkchen zeigen, nicht als anfragbare Zeile. Die eigentliche Deinstallation läuft später über `Intent.ACTION_DELETE`.
4. Notifications bleiben (`POST_NOTIFICATIONS`, API 33+; darunter automatisch „erteilt“).
5. `OnboardingUiState` um `installPackagesGranted`, `deletePackagesInfo` erweitern; „Weiter“ nur freigeben, wenn Dateizugriff erteilt ist (Installieren und Benachrichtigungen optional, aber sichtbar).
6. Zeilen klickbar machen (ganze Karte, nicht nur das Kreis-Icon), bereits erteilte Rechte nicht erneut anfragen.
7. Texte (`onboarding_*`) in beiden Sprachen anpassen. targetSdk bleibt 28 (siehe BOOTSTRAP.md); prüfen und im Bericht festhalten, ob `MANAGE_EXTERNAL_STORAGE` mit targetSdk 28 auf einem Gerät mit Android 11+ wirklich benötigt wird oder Legacy-Storage genügt. Entscheidung begründen.

## 24.3 Launcher-Icon wiederherstellen
Das eigene Icon (Deep Indigo `#111318`, Grid, Code-Klammern/Konsole in Neon Cyan, `@author Thomas Schmid`) wurde in `4bdb999` durch einen „Eigenentwurf“ ersetzt. Das Original liegt in `de2caf0`.
**Auftrag:**
1. Aus `de2caf0` wiederherstellen (liegt diesem Paket bei): `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`; `mipmap-anydpi-v26/ic_launcher.xml` und `ic_launcher_round.xml` zeigen auf diese Drawables, `ic_launcher_monochrome.xml` bleibt für Themed Icons erhalten.
2. Die Farben `codeforge_icon_background/foreground/accent` aus `values/colors.xml` entfernen, wenn nichts mehr darauf verweist (`grep`).
3. Das große Vektor-Icon `icon.xml` aus `de2caf0` (`core/designsystem/.../drawable/icon.xml`, `feature/onboarding/.../drawable/icon.xml`) war im Onboarding/Designsystem im Einsatz und fehlt jetzt. Prüfen, wo es gebraucht wurde (Willkommensseite, Intro), und als Logo wieder einsetzen, falls dort eine Lücke ist.
4. Auf einem Gerät/Emulator prüfen: Launcher (rund/eckig), Themed Icon. Nicht Getestetes ausweisen.

## 24.4 Task 06 umsetzen: volle Termux-UI
Siehe `06-optional-full-termux-ui.md`; ab jetzt **Pflicht**, nicht mehr optional. Aktueller Stand: `:libs:termux-app` (`TermuxActivity`, `TermuxService`) ist in `settings.gradle.kts`, aber `:app` hängt nicht davon ab; das Terminal ist nur der schlanke Compose-Screen (`:feature:terminal`, 132 Zeilen, keine Sessions, keine Extra-Keys).
**Auftrag (Ergänzungen zu Task 06):**
1. `implementation(project(":libs:termux-app"))` in `app/build.gradle.kts`, gemergtes Manifest auf doppelte Komponenten prüfen.
2. `Routes.TERMINAL` und der Onboarding-Abschluss (`Routes.terminalWith(command)`) starten `TermuxActivity` per Intent. Das Setup-Kommando als `RUN_COMMAND`/Extra an die Activity geben, sodass es nach dem Start der ersten Session ausgeführt wird (nicht per `ACTION_VIEW`).
3. Der schlanke `:feature:terminal`-Screen bleibt als Fallback (Einstellung „Terminal: Termux-UI / Kompakt“, Standard Termux-UI).
4. Theme-Attribute (`termuxActivityDrawerBackground`, `extraKeys…`) sind schon in `Theme.CodeForge`; prüfen, dass Drawer, Extra-Keys, Style/Font-Auswahl, Mehrfach-Sessions, lange Druck-Menüs (Kopieren/Einfügen) funktionieren.
5. `TermuxApplication.onCreate()`-Logik gemäß Task 06 Schritt 4 in `CodeForgeApplication` übernehmen.
6. Zurücknavigation von `TermuxActivity` zur App (Task-Affinität) testen.

## 24.5 `codeforge-env`: Fehler sichtbar machen
**Ist:** In `libs/terminal-engine/src/main/assets/codeforge-env` (u. a. Zeilen ~197, 223, 333, 428, 475, 516) wird `pkg install … >/dev/null 2>&1` ausgeführt. Der Gerätetest zeigt nur „pkg install openjdk-17 fehlgeschlagen“, nie den Grund.
**Auftrag:**
1. Hilfsfunktion `pkg_install()`: gibt die Ausgabe in `$HOME/.codeforge/logs/setup-<datum>.log` und bei Fehler die letzten 20 Zeilen auf der Konsole aus; erkennt „Unable to locate package“ und meldet dann konkret: „Paket fehlt im APT-Repo (Etappe bauen) – URL: …“ (aus `sources.list`).
2. Vor dem ersten Paket `pkg update`/`apt-get update` ausführen und Fehler (Netz, Signatur, 404) anzeigen; `apt-cache policy <pkg>` im Fehlerfall ausgeben.
3. Anzeige „(1/7) jdk;17“: Trennzeichen `;` ist der interne Schlüssel – lesbar als „JDK 17“ darstellen.
4. Fehlerexit setzt den Code ungleich 0; das Setup-Terminal zeigt am Ende „Erneut versuchen: codeforge-env setup …“.

## 24.6 Bootstrap-Skript Zeile 290
Im Gerätelog: `termux-bootstrap-second-stage.sh: line 290: [: =: unary operator expected`, danach „completed successfully“; zuvor „Starting fallback run of termux bootstrap second stage“.
**Auftrag:** Das Skript liegt im gebauten Bootstrap (`build_codeforge_repo.sh` / vendort in `libs/termux-app` bzw. `termux-shared`; per `grep -rn "second-stage" .` ermitteln). Zeile 290 finden, die unquotierte Variable in `[ … ]` quoten (`[ "$var" = … ]`), die Ursache der leeren Variable klären (Prefix/Home?) und im Bericht dokumentieren. Der „fallback run“ deutet darauf hin, dass der reguläre Aufruf nach dem Entpacken nicht griff; klären und beheben. Änderung gehört ins Repo (Patch/Script), nicht nur ins Release; danach neuen Bootstrap bauen lassen (nur vorbereiten, nicht ausführen).

## 24.7 Offener Punkt außerhalb des Codes (nicht von agy lösbar)
`pkg install openjdk-17` schlägt wahrscheinlich fehl, weil Etappe `01-jdk17` noch nie ins APT-Repo gebaut wurde. Im Bericht festhalten: Prüfen mit `curl -s https://scto.github.io/terminal-packages-codeforge/dists/stable/main/binary-aarch64/Packages | grep -c '^Package: openjdk-17'`. Der Lauf wird vom Nutzer über `build-all-stages.yml` (`only=H1-jdk17`) gestartet.

## Abnahme
- Build grün; Onboarding in Hell/Dunkel lesbar (Preview-Screenshots im Bericht).
- Rechte: Dateizugriff, Installieren, Löschen-Info sichtbar, Zustand nach Rückkehr aus den Einstellungen korrekt.
- Icon = Original aus `de2caf0`.
- Terminal öffnet `TermuxActivity` mit Extra-Keys und mehreren Sessions; Setup-Kommando läuft.
- `codeforge-env` zeigt die echte `pkg`-Fehlermeldung.
- `TASKS-STATUS.md`: Task 24 eintragen, Task 06 auf „⚠️ ersetzt durch 24.4“ setzen. README der agy-tasks aktualisieren.
