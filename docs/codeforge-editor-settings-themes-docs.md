# CodeForge Mobile - Editor Theme & Settings Overhaul Documentation

## 1. Übersicht

Dieses Dokument beschreibt die Überarbeitung der Editor-Einstellungen (Editor Theme Overhaul), die Einführung der visuellen Live-Vorschau-Karten für alle 21 Kotlin- und TextMate-Farbschemata sowie die Implementierung des neuen Onboarding- und Debug-Bereichs in den App-Einstellungen.

---

## 2. Editor Theme Overhaul (`EditorSettingsScreen.kt`)

### 2.1 Themenkatalog & Live-Vorschau
In `EditorSettingsScreen.kt` wurden alle 21 verfügbaren Themes (sowohl native Kotlin-Themes als auch TextMate-Farbschemata) vollständig registriert und visuell aufbereitet. Jedes Theme bietet eine interaktive Vorschau-Karte mit exakten Farbwerten für Syntax-Highlighting und Hintergrundfarbe.

#### Registrierte Themes (`EDITOR_THEME_OPTIONS`):
1. **CodeForge 2 Dark** (Modern Dark IDE Standard, Hex `#1E1E2E`)
2. **CodeForge 2 Light** (Clean & Vibrant Light IDE Standard, Hex `#FAFAFA`)
3. **Dracula** (Vampire Dark Palette, Hex `#282A36`)
4. **One Dark Pro** (Atom Classic Dark, Hex `#282C34`)
5. **Monokai Pro** (High Contrast Professional Dark, Hex `#2D2A2E`)
6. **GitHub Dark** (GitHub Native Dark Theme, Hex `#0D1117`)
7. **GitHub Light** (GitHub Native Light Theme, Hex `#FFFFFF`)
8. **Solarized Dark** (Precision Colors Dark, Hex `#002B36`)
9. **Solarized Light** (Precision Colors Light, Hex `#FDF6E3`)
10. **Nord** (Arctic Ice Blue Palette, Hex `#2E3440`)
11. **Tokyo Night** (Neon Tokyo Night Vibe, Hex `#1A1B26`)
12. **Catppuccin Mocha** (Soothing Pastel Dark, Hex `#1E1E2E`)
13. **Catppuccin Latte** (Soothing Pastel Light, Hex `#EFF1F5`)
14. **Gruvbox Dark** (Retro Groove Dark, Hex `#282828`)
15. **Gruvbox Light** (Retro Groove Light, Hex `#FBF1C7`)
16. **Material Ocean** (Deep Ocean Blue, Hex `#0F111A`)
17. **SynthWave '84** (Retro Cyberpunk Neon, Hex `#262335`)
18. **Night Owl** (Optimized for Late Night Coding, Hex `#011627`)
19. **Ayumirage** (Elegant Orange Accent Dark, Hex `#212733`)
20. **Rose Pine** (Soho Vibes Pastel Dark, Hex `#191724`)
21. **Cyberpunk 2077** (High Contrast Yellow/Cyan Neon, Hex `#000B1E`)

### 2.2 Re-Location der Core-Themes
- `CodeForge2DarkTheme.kt` und `CodeForge2LightTheme.kt` wurden erfolgreich in das Paket `com.codeforge.feature.editor.theme` unter `feature/editor/src/main/kotlin/com/codeforge/feature/editor/theme/` verschoben.
- Theme-Schlüssel (Keys) werden jetzt im `EditorSettingsScreen.kt` Case-Insensitive über `isSelected = currentThemeKey.equals(theme.key, ignoreCase = true)` matchend verglichen.

---

## 3. Onboarding & Debug Settings (`feature/settings/debug/`)

### 3.1 Speicherort & Navigation
Die Steuerung des Onboarding-Flows sowie erweiterte App-Debugging-Optionen befinden sich in den Einstellungen im Abschnitt **Debug / Onboarding** ([DebugSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/debug/DebugSettingsScreen.kt)).

### 3.2 Funktionalitäten
- **Onboarding Status Toggle**: Manuelles Aktivieren / Deaktivieren des Onboarding-Status.
- **Reset Onboarding Button**: Setzt `onboardingCompleted` zurück, sodass der geführte Willkommens-Flow beim nächsten App-Start erneut ausgelöst wird.
- **Terminal Storage Wipe**: Ermöglicht das Bereinigen des Terminal-Caches mit visueller Statusanzeige.
- **State Flow Management**: `DebugSettingsViewModel.kt` nutzt `stateIn` für reaktive UI-Updates via `DebugSettingsUiState` und `DebugSettingsUiEvent`.

---

## 4. Dateien & Architektur

| Modul | Datei | Beschreibung |
|---|---|---|
| `:feature:editor` | [EditorSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/settings/EditorSettingsScreen.kt) | Theme-Auswahl mit Vorschau-Karten & Live-Preview |
| `:feature:editor` | [CodeForge2DarkTheme.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/theme/CodeForge2DarkTheme.kt) | Nativ abgeleitetes Dark-Theme |
| `:feature:editor` | [CodeForge2LightTheme.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/theme/CodeForge2LightTheme.kt) | Nativ abgeleitetes Light-Theme |
| `:feature:settings` | [DebugSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/debug/DebugSettingsScreen.kt) | UI für Debug & Onboarding Reset |
| `:feature:settings` | [DebugSettingsViewModel.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/debug/DebugSettingsViewModel.kt) | ViewModel mit `stateIn` Flow Management |
| `:feature:settings` | [DebugSettingsContract.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/debug/DebugSettingsContract.kt) | Event- und State-Definitionen für Debug/Onboarding |
