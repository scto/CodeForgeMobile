# AGY Architecture & Refactoring Report: App-Wide Theming Consolidation

**Datum:** 2026-10-01  
**Komponenten:** `:core:designsystem`, `:feature:themebuilder`, `:core:datastore`  
**Autor:** Antigravity AI (AGY)  
**Status:** Behoben & Konsolidiert  

---

## 1. Übersicht & Fragestellung

Der Benutzer hat gefragt:  
> *"Wo werden die AppThemes generiert? Entferne alle AppThemes in Erscheiningsbild und Themes, lasse nur System, Hell, Dunkel, Dynamasche Farben und Amoled True Black. Nur der Editor behält die Schemes. Überarbeite das Theming Projektweit"*

---

## 2. Generierungsort der AppThemes

1. **Zentrale Theme-Instanziierung:**  
   Das globale App-Theme wird in [`core/designsystem/src/main/kotlin/com/codeforge/core/designsystem/Theme.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/core/designsystem/src/main/kotlin/com/codeforge/core/designsystem/Theme.kt) über den Composable-Provider `CodeForgeTheme` generiert.
2. **Einstellungen & Persistenz:**  
   Die Theme-Konfiguration wird im Proto-DataStore (`settings.proto` -> `ThemeConfig`) gespeichert und von `SettingsRepository` an die Anwendung übertragen.

---

## 3. Durchgeführte Refactoring-Maßnahmen

### 3.1 Bereinigung der App-Themes
- **Entfernte Preset-Paletten:** Alle vordefinierten Farbschemata (Dracula, Nord, Ayudark, Solarized, Cyberpunk, Forest etc.) sowie benutzerdefinierte Hex-ColorPicker wurden aus den globalen App-Einstellungen (`ThemeBuilderScreen.kt`) entfernt.
- **Konsolidierte App-Themes (Genau 5 Optionen):**
  1. **System:** Folgt der Systemeinstellung (`ThemeMode.SYSTEM`).
  2. **Hell:** Erzwingt helles Design (`ThemeMode.LIGHT`).
  3. **Dunkel:** Erzwingt dunkles Design (`ThemeMode.DARK`).
  4. **Dynamische Farben (Material You):** Verwendet Android 12+ Wallpaper-Farben (`useDynamicColor = true`).
  5. **AMOLED True Black:** Verwendet Reinschwarz (`#000000`) im Dunkelmodus (`useAmoled = true`).

### 3.2 Erhalt der Editor-Schemes ("Nur der Editor behält die Schemes")
- Alle TextMate- und Sora-Editor-Farbschemata (`Dracula`, `Monokai`, `Nord Dark`, `Material Palenight`, `Tokyo Night`, `GitHub Light`, `Solarized Light`, `One Light`, `Rose Pine Dawn`, `Material Light`, `CodeForge 2 Dark`, `CodeForge 2 Light`, `codeforge`, `quietlight`, etc.) bleiben unverändert im `:feature:editor` und `SoraLanguageProvider.kt` für den Code-Editor erhalten.

---

## 4. Technische Änderungen

1. **`settings.proto`:** `bool use_amoled = 5;` zu `ThemeConfig` hinzugefügt.
2. **`Theme.kt` (`CodeForgeTheme`):** Unterstützt `useDynamicColor` (via `dynamicDarkColorScheme`/`dynamicLightColorScheme`) und `useAmoled` (Schwarz-Superposition für AMOLED).
3. **`ThemeBuilderScreen.kt` & `ThemeBuilderViewModel.kt`:** Reduziert auf Vorschau-Karte, Segmented-Button für Modus-Auswahl (System/Hell/Dunkel), Dynamic Color Switch und AMOLED Switch.

---

## 5. Fazit

Das App-UI-Theming ist nun extrem schlank, modern und folgt Material 3 Standards (inkl. AMOLED True Black). Die Editor-Syntax-Farbschemata bleiben davon isoliert für den Code-Editor erhalten.
