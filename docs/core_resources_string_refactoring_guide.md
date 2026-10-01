# Project-Wide String Centralization & Refactoring Guide (`:core:resources`)

## Übersicht

Dieses Dokument beschreibt die vollständige Architektur und Durchführung des projektweiten Refactorings zur String-Zentralisierung im Android-Projekt **CodeForgeMobile**. Das Refactoring wurde in drei aufeinander aufbauenden Phasen durchgeführt:

1. **Phase 1 (`agy-Instruction.txt`)**: Initiales Aufsetzen der `ResGetter`-Infrastruktur im Modul `:core:resources`, Strukturierung der `strings.xml` mit Modul-Kommentaren und Refactoring primärer UI-Komponenten.
2. **Phase 2 (`agy-Instruction-2.txt`)**: Gezielter Sweep der Kern-Screens (`EditorScreen.kt`, `FileTreeDrawer.kt`, `FileTreeSettingsScreen.kt`, `EditorSettingsScreen.kt`) und Handhabung dynamischer String-Templates mit Variablen (`%1$s`, `%1$d`).
3. **Phase 3 (`agy-Instruction-3.txt`)**: Ultimativer projektweiter Sweep über alle 471 `.kt` / `.java` Dateien in allen Modulen (`:feature:*`, `:core:*`, `:app`), Erfassung sämtlicher verbliebener `contentDescription`, `label`, `placeholder`, Dialoge und Buttons sowie Sicherstellung des `ResGetter`-Imports in allen modifizierten Dateien.

---

## 1. Architektur & `ResGetter` Utility

Im Modul `:core:resources` wurde die Hilfsklasse `ResGetter` bereitgestellt, um in Jetpack Compose typsicher und performant auf String-Ressourcen zuzugreifen:

**Datei:** [Res.kt](file:///storage/emulated/0/Download/CodeForgeMobile/core/resources/src/main/java/com/codeforge/core/resources/Res.kt)

```kotlin
package com.codeforge.core.resources

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/**
 * Simple accessor for Compose string resources.
 *
 * Usage:
 *   ResGetter.get(R.string.someStr)
 *   ResGetter.get(R.string.someStrWithArgs, "value")
 *   @author nullij @ https://github.com/nullij
 */
object ResGetter {

    @Composable
    @ReadOnlyComposable
    fun get(@StringRes resId: Int): String {
        return stringResource(id = resId)
    }

    @Composable
    @ReadOnlyComposable
    fun get(@StringRes resId: Int, vararg formatArgs: Any): String {
        return stringResource(id = resId, formatArgs = formatArgs)
    }
}
```

---

## 2. Strukturierung & Namenskonvention in `strings.xml`

Alle extrahierten String-Ressourcen befinden sich in [strings.xml](file:///storage/emulated/0/Download/CodeForgeMobile/core/resources/src/main/res/values/strings.xml) und sind systematisch nach Herkunftsmodul gruppiert:

```xml
<!-- ================= :feature:editor ================= -->
<string name="feature_editor_title_file">Editor - %1$s</string>
<string name="feature_editor_title_default">CodeForge Editor</string>
<string name="feature_editor_diag_line_msg">[Zeile %1$d] %2$s</string>

<!-- ================= :feature:filetree ================= -->
<string name="feature_filetree_delete_confirm_msg">Möchtest du \'%1$s\' wirklich unwiderruflich löschen?</string>
<string name="feature_filetree_prop_name">Name: %1$s</string>
<string name="feature_filetree_prop_path">Pfad: %1$s</string>

<!-- ================= :feature:settings ================= -->
<string name="feature_settings_ext_title">Erweiterungen (Extensions)</string>
<string name="feature_settings_ext_installing">Installiere...</string>
```

---

## 3. Regeln für Code-Ersetzungen & Imports

Um Import-Konflikte mit lokalen R-Klassen in Multi-Modul-Architekturen auszuschließen, gelten folgende Pflichtregeln:

1. **Vollqualifizierter Aufruf**:
   ```kotlin
   ResGetter.get(com.codeforge.core.resources.R.string.id)
   ```
2. **Dynamische Argumente**:
   ```kotlin
   ResGetter.get(com.codeforge.core.resources.R.string.feature_projectwizard_api_level, sdk, getAndroidVersionName(sdk))
   ```
3. **Erforderlicher Import**:
   Jede modifizierte Datei enthält zu Beginn den Import:
   ```kotlin
   import com.codeforge.core.resources.ResGetter
   ```

---

## 4. Matrix aller bereinigten Module & Dateien

| Modul | Refaktorierte Dateien | Extrahierte UI-Elemente |
| :--- | :--- | :--- |
| `:app` | `CodeForgeNavHost.kt`, `EditorWithPreviewHost.kt`, `CodeForgeApplication.kt` | Navigations-Back-Button, Tab-Titel ("Editor", "Preview") |
| `:feature:editor` | `EditorScreen.kt` | Toolbar-Buttons, Dateititel, Dialoge, Suchen & Ersetzen Platzhalter, Context Descriptions ("Undo", "Redo", "Format", "Build", "Save") |
| `:feature:filetree` | `FileTreeDrawer.kt`, `FileTreeToolbar.kt`, `FileTreeContextMenu.kt`, `FileTreeHelpers.kt`, `FileTreeSettingsScreen.kt` | Ordner-/Dateidetails, Löschen-Dialog, Properties-Dialog, Inline-Aktionen, Filter- & Ansichtsmodi, Clipboard-Aktionen |
| `:feature:settings` | `EditorSettingsScreen.kt`, `TerminalSettingsScreen.kt`, `DebugSettingsScreen.kt`, `ExtensionsScreen.kt`, `SettingsHubScreen.kt` | Sektions-Header, Settings-Titel & Subtitles, Extension-Install-Buttons, Logging- & Trace-Labels |
| `:feature:terminal` | `TerminalScreen.kt`, `FloatingTerminalWindow.kt` | Session-Optionen, Umbenennen-Dialog, Schließen-Buttons, Empty-States |
| `:feature:onboarding` | `PermissionScreen.kt`, `SetupScreen.kt`, `IntroPagerScreen.kt` | Berechtigungs-Karten, Willkommens-Slides, Setup-Fortschritt & Retry-Buttons |
| `:feature:sdkmanager` | `SdkManagerScreen.kt` | Auto-Update Intervall, Filter-Placeholder, Diagnose-Status |
| `:feature:projectwizard` | `ProjectWizardRoute.kt` | Ordnerauswahl-Icon Description, API-Level Formatstring |
| `:feature:welcome` | `WelcomeRoute.kt` | Import-Button Label, DevKit, Plugins, Über-Icons |
| `:feature:composepreview` | `ComposePreviewScreen.kt` | Render-Status, Empty State Meldungen |
| `:feature:themebuilder` | `ThemeBuilderScreen.kt` | UI-Vorschau Buttons & Labels |

---

## 5. Ausschluss-Regel

Das Modul `:core:resources` (Pfad: `core/resources/...`) ist der zentrale Speicherort für String-Ressourcen und die `ResGetter`-Implementierung und bleibt von der Suche nach auszulagernden Strings ausnahmslos ausgeschlossen.
