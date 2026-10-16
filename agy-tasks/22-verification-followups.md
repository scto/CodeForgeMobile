# Task 22 – Folgepunkte aus den Verifikationsberichten

Sammelauftrag für Punkte, die die Berichte `docs/*-verification.md` (Task 08–15) **nur gemeldet, nicht umgesetzt** haben. Reihenfolge = Priorität; je Punkt ein Commit, jeweils `./gradlew assembleDebug testDebugUnitTest` grün. Arbeitsverzeichnis = Projekt-Root. Nach Task 18 ausführen (neue Texte zweisprachig).

1. **Speicherort per SAF im Projekt-Wizard** (`docs/project-wizard-template-engine-verification.md`, Schritt 5): Der Speicherort ist ein reines Textfeld. Ergänzen: Button „Ordner wählen" mit `ActivityResultContracts.OpenDocumentTree()`, Ergebnis in einen für das Termux-/App-Dateisystem nutzbaren Pfad umsetzen. **Zuerst klären**, ob ein SAF-Baum-URI mit der dateipfadbasierten Template-Engine und `codeforge-env` überhaupt verwendbar ist; wenn nicht, Auswahl auf Verzeichnisse unter `filesDir`/Termux-Home begrenzen (eigene Ordner-Auswahl in Compose) und die Entscheidung im Bericht begründen.
2. **Material 3 Expressive** (`docs/adaptive-expressive-verification.md`): `CodeForgeTheme.kt` nutzt das stabile `MaterialTheme`, weil BOM 2025.09.00 → material3 1.3.2. Prüfen, ob eine stabile material3-Version mit `MaterialExpressiveTheme`/`MotionScheme.expressive()` verfügbar ist und zur BOM/`compileSdk` passt; falls ja, anheben und Theme umstellen, sonst nur Ergebnis notieren (keine Alpha-Versionen einführen).
3. **Gradle-9-/AGP-Vorbereitung** (`docs/build-logic-verification.md`, Abschnitt „Verbleibende Warnungen"): `./gradlew help --warning-mode all` auswerten, nur Warnungen aus eigenem Code/build-logic beheben; Warnungen aus AGP-internen Tasks nur auflisten. Kein Upgrade von AGP/Gradle in diesem Task.
4. **ktlint:** `./gradlew ktlintCheck` ausführen und Befunde **nur berichten** (nicht automatisch formatieren), Ergebnis in `docs/` festhalten; Auto-Format nur nach Rückfrage.
5. **Gerätetests, die Berichte nicht abdecken:** Eine Checkliste `docs/device-test-checklist.md` anlegen (nicht ausführen): Bootstrap-Installation auf aarch64 und arm, `bash`/`java`/`git`/`aapt2` Start unter `targetSdk 28` (Android 10–15), `codeforge-env setup`, Adaptive-Layouts (Phone/Tablet/Faltbar/Split-Screen), Tastatur im Editor/Terminal, Dark/Light-Wechsel, Minimap/Rainbow Brackets/Pinch-Zoom, Dateibaum-Dialoge, Theme-Studio-Colorpicker. Je Zeile: erwartetes Ergebnis, Gerät, Status „ungeprüft".

## Grenzen

* Flutter-Wizard bleibt bewusst nicht portiert (siehe Bericht). `targetSdk = 28` nicht ändern (Voraussetzung für Ausführbarkeit der Termux-Binaries, Task 11); Hinweis: Play-Store-Veröffentlichung wäre damit nicht möglich – nur melden.
* Keine Logikänderungen außerhalb der genannten Punkte; keine harten Strings.

## Status: Abgeschlossen

- **Speicherort per SAF im Wizard:** SAF vs. Termux-Pfadmodell geklärt (POSIX-Syscalls und `noexec` verbieten direkte Arbeit auf externen SAF-URIs). Ordnerauswahl über Compose-Dialog `ProjectDirectoryPickerDialog` und trailing Icon in `ProjectWizardScreen` integriert (zweisprachig mit 7 neuen Strings).
- **Material 3 Expressive:** Geprüft. Da Material 3 Expressive erst in Alpha-Versionen (`1.4.0-alpha+`) existiert und gemäß Spezifikation keine Alpha-Versionen eingeführt werden dürfen, verbleibt das Theme auf dem stabilen `MaterialTheme` aus der Compose BOM 2025.09.00 (`material3 1.3.2`).
- **Gradle 9 / AGP-Warnungen:** Mit `./gradlew help --warning-mode all` ausgewertet. Keine Deprecation-Warnungen in eigenem Code/build-logic; nur AGP-internes AAPT2-Override.
- **ktlint:** `./gradlew ktlintCheck` ausgeführt (225 Tasks) und Befunde rein berichtend in `docs/verification-followups-report.md` dokumentiert.
- **Gerätetest-Checkliste:** Vollständige manuelle Test-Checkliste in `docs/device-test-checklist.md` mit 28 Szenarien in 6 Kategorien angelegt (alle initial „ungeprüft"). Play-Store-Einschränkung bei `targetSdk = 28` dokumentiert.

