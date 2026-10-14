# 📋 Architekturentscheidungen: Hintergrundbefehle & Fluent 2 (Task 16 Punkte 5 & 6)

## 1. Hintergrundbefehle (`RunCommandWorker` vs. `TermuxService`)

### Fragestellung (Task 16 Punkt 5)
Prüfen, ob `RunCommandWorker` (WorkManager) oder ein separater Foreground-Service für lange Terminal-/Build-Läufe erforderlich ist, oder ob diese Funktionalität bereits durch `TermuxService` (`:libs:termux-app`) abgedeckt wird.

### Analyse & Befund
1. **Bestehende Implementierung in `:libs:termux-app`**:
   - `com.codeforge.app.TermuxService` ist ein vollständiger Android `ForegroundService` (`Service.startForeground(TERMUX_APP_NOTIFICATION_ID, notification)`).
   - Er hält während aktiver Prozesse zuverlässig `PowerManager.PARTIAL_WAKE_LOCK` und `WifiLock`, um ein Einschlafen von CPU und Netzwerkverbindung auf Mobilgeräten zu verhindern.
   - Er verwaltet sowohl interaktive Termux-Sessions (`TermuxSession`) als auch asynchrone Hintergrundbefehle (`AppShell` / `ExecutionCommand`).
   - Im Android-Manifest (`libs/termux-app/src/main/AndroidManifest.xml`) ist der Service mit `FOREGROUND_SERVICE` und `WAKE_LOCK` Berechtigungen registriert.
2. **WorkManager (`RunCommandWorker`) Bewertung**:
   - WorkManager ist konzipiert für aufschiebbare, batteriefreundliche Hintergrundjobs (z. B. periodische Synchronisation), nicht für Terminal-/PTY-gebundene Compilerläufe oder interaktive Shells.
   - Ein zusätzlicher `RunCommandWorker` würde zu Konflikten mit der PTY-/JNI-Prozessverwaltung von Termux führen und unnötige Bibliotheksabhängigkeiten erzeugen.

### Entscheidung
Die Hintergrundausführung langer Terminal- und Build-Läufe wird **vollständig über `TermuxService`** abgewickelt. Ein separater `RunCommandWorker` oder doppelter `TerminalForegroundService` entfällt als redundant.

---

## 2. Microsoft Fluent 2 Design Tokens (`FluentTokens`)

### Fragestellung (Task 16 Punkt 6)
Entscheiden, ob Fluent-Tokens zusätzlich zu Material 3 Expressive eingeführt werden, und falls ja, als eigenes Token-Set in `:core:designsystem`.

### Analyse & Befund
- Das Projekt verwendet primär **Material 3 Expressive** als stabiles UI-Designsystem (`CodeForgeTheme.kt`).
- Das offizielle Microsoft Fluent UI Android SDK befindet sich im `0.0.1-alpha`-Status und bindet veraltete Compose-Bibliotheken ein.
- Entwickler und Themes profitieren jedoch von standardisierten Fluent 2 Gestaltungselementen (Farben, Radien, Elevationen, Konturen) nach Microsoft-Spezifikation (siehe `FLUENT2_JETPACK_COMPOSE_GUIDE.md`).

### Entscheidung & Umsetzung
Fluent 2 wird als **leichtgewichtiges, reines Kotlin-Token-Set** in `:core:designsystem` (`FluentTokens.kt`) bereitgestellt:
- `FluentTokens.Brand`: Offizielle Fluent 2 Brand-Palette (Primary `#0078D4`, Tints und Shades).
- `FluentTokens.Neutral`: Spezifizierte Graustufen für Light- und Dark-Mode.
- `FluentTokens.CornerRadius`: Fluent 2 Radien-Skala (None, Small 2.dp, Medium 4.dp, Large 8.dp, XLarge 12.dp, Circle).
- `FluentTokens.Elevation`: Schatten- & Tiefenstufen (Level 0 bis 4).
- `FluentTokens.Stroke`: Standardisierte Rahmenstärken (None, Thin 1.dp, Thick 2.dp, Thicker 3.dp).
- `FluentColorScheme`: Typisierter Container für Light- und Dark-Modi.
- Verifiziert durch Unit-Tests in `FluentTokensTest`.
