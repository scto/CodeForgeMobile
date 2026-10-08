> **ERLEDIGT / ÜBERHOLT (2026-10-06):** Die Wiring-Aufgabe ist umgesetzt: `OnboardingViewModel` löst `RunTermuxBootstrapSetup` aus, `OnboardingRoute` ruft `TermuxInstaller.setupBootstrapIfNeeded` auf, danach öffnet das Terminal und führt `codeforge-env setup` aus (siehe `libs/terminal-engine/BOOTSTRAP.md`). Nur noch prüfen (Build/Gerät): `agy-tasks/11-verify-termux-sdk-script.md`.

# Auftrag: Termux-Bootstrap-Installation in den Onboarding-Flow verdrahten

## Kontext

Repo: CodeForgeMobile (Android, Kotlin, MVI/Hilt/Compose, Clean Architecture).
Arbeitsverzeichnis: Projekt-Root (`CodeForgeMobile/`).

Lies zuerst `docs/sub/TERMUX-PORTING.md`, Abschnitt "🔧 1.
Bootstrap-Installation anstoßen" — das ist der vollständige Hintergrund dieses
Auftrags. Kurzfassung: `TerminalSessionRepositoryImpl.start()`
(`libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/TerminalSessionRepositoryImpl.kt`)
scheitert zur Laufzeit, solange der Termux-Bootstrap nicht nach
`TermuxConstants.TERMUX_PREFIX_DIR_PATH` entpackt wurde. Das Entpacken übernimmt
`TermuxInstaller.setupBootstrapIfNeeded(Activity activity, Runnable whenDone)`
in `libs/termux-app/src/main/java/com/codeforge/app/TermuxInstaller.java` —
diese Methode ist bereits `public`, wird aber aktuell von niemandem aufgerufen.

Relevante existierende Dateien (lies sie, bevor du etwas änderst):
- `feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingContract.kt`
  (definiert `OnboardingUiState`, `OnboardingUiEvent`, `OnboardingUiEffect`)
- `feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingViewModel.kt`
  (Hilt-`ViewModel`, aktuell nur `distroBootstrapRepository` injiziert, kein
  `Activity`-Zugriff — genau wie bei `RequestStoragePermission` wird eine
  `Activity`-abhängige Operation über einen `OnboardingUiEffect` an die UI-Schicht
  delegiert, nicht direkt im ViewModel ausgeführt)
- `feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/OnboardingRoute.kt`
  (sammelt vermutlich die `effect`-Flow und reagiert z. B. auf
  `RequestStoragePermission` — das ist dein Vorbild für das neue Effect-Handling)
- `feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/SetupScreen.kt`

**Wichtige Randbedingung:** `:feature:onboarding` hat aktuell keine Gradle-
Dependency auf `:libs:termux-app`. Du musst diese Dependency ergänzen (siehe
Schritt 3).

## Aufgabe

1. **Contract erweitern** (`OnboardingContract.kt`):
   - Neuer `OnboardingUiEffect`: `data object RunTermuxBootstrapSetup`
   - Neuer `OnboardingUiEvent`: `data object TermuxBootstrapSetupCompleted` (wird
     von der UI-Schicht gefeuert, nachdem `TermuxInstaller.setupBootstrapIfNeeded`
     seinen `whenDone`-Callback aufgerufen hat)
   - `SetupPhase` ggf. um eine Phase für den Termux-Bootstrap-Schritt erweitern,
     falls du ihn als sichtbaren Fortschrittsschritt zusätzlich zum bestehenden
     PRoot-Rootfs-Bootstrap anzeigen willst (optional — minimal auch ohne eigene
     Phase lösbar, dann einfach als Teil von `FINALIZING` behandeln).

2. **ViewModel anpassen** (`OnboardingViewModel.kt`):
   - In `reduceBootstrapProgress`, Fall `BootstrapProgress.Completed`: statt
     direkt `completeOnboarding()` aufzurufen, zuerst
     `_effect.emit(OnboardingUiEffect.RunTermuxBootstrapSetup)` senden.
   - Neuen Fall in `onEvent()` für `OnboardingUiEvent.TermuxBootstrapSetupCompleted`
     ergänzen, der dann `completeOnboarding()` aufruft (bzw. bei Fehler analog zu
     `BootstrapProgress.Failed` einen Fehlerzustand setzt — definiere dafür bei
     Bedarf ein Error-Signal im neuen Event, z. B.
     `data class TermuxBootstrapSetupCompleted(val success: Boolean, val errorMessage: String? = null)`).

3. **Gradle-Dependency ergänzen** (`feature/onboarding/build.gradle.kts`):
   - `implementation(project(":libs:termux-app"))` hinzufügen (für
     `TermuxInstaller`). Prüfe vorher die bestehenden Dependencies in dieser
     Datei und halte dich an den gleichen Stil/gleiche Gruppierung.

4. **UI-Schicht verdrahten** (vermutlich `OnboardingRoute.kt`, dort wo bereits
   auf `effect`-Werte reagiert wird, analog zum bestehenden
   `RequestStoragePermission`-Handling):
   - Bei `OnboardingUiEffect.RunTermuxBootstrapSetup`:
     `TermuxInstaller.setupBootstrapIfNeeded(activity) { /* whenDone */
     viewModel.onEvent(OnboardingUiEvent.TermuxBootstrapSetupCompleted(success = true)) }`
     aufrufen. Die benötigte `Activity`-Referenz holst du dir wie an anderer
     Stelle in diesem Composable/dieser Route bereits üblich (z. B.
     `LocalContext.current as Activity` oder eine bereits vorhandene
     Activity-Referenz aus dem Compose-Baum — orientiere dich am bestehenden
     Code-Stil in `OnboardingRoute.kt`/`PermissionScreen.kt`, wo vermutlich
     bereits mit `Activity`/`ActivityResultContracts` gearbeitet wird).
   - Fehlerfall: Falls `setupBootstrapIfNeeded` eine Exception werfen kann, fange
     sie ab und feuere `TermuxBootstrapSetupCompleted(success = false, errorMessage = ...)`.

## Akzeptanzkriterien

- `./gradlew :feature:onboarding:assembleDebug` baut erfolgreich.
- Der bestehende PRoot-Rootfs-Bootstrap-Flow (`distroBootstrapRepository.bootstrap`)
  bleibt unverändert funktionsfähig — du fügst den Termux-Bootstrap-Schritt
  **zusätzlich** ein, ersetzt nichts.
- Nach `BootstrapProgress.Completed` läuft jetzt zusätzlich
  `TermuxInstaller.setupBootstrapIfNeeded` durch, bevor `completeOnboarding()`
  aufgerufen wird.
- Kein Breaking Change an `OnboardingUiState`-Feldern, die von `SetupScreen.kt`
  bereits gelesen werden (nur additive Änderungen an `OnboardingContract.kt`).
- Manuelle/instrumentierte Prüfung (falls Testgerät/Emulator verfügbar): nach
  vollständigem Onboarding-Durchlauf existiert
  `/data/data/com.codeforge.app/files/usr` (bzw. ist zumindest teilweise befüllt
  — der eigentliche Bootstrap-Inhalt hängt von Auftrag 04 ab, hier geht es nur
  um den Aufruf-Pfad).
