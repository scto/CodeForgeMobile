# CodeForge Mobile – CodeForge2 Custom Themes Integration Dokumentation

Diese Dokumentation beschreibt die Umsetzung der Anweisungen aus `Text-2.txt` zur Integration von zwei weiteren benutzerdefinierten Kotlin-Farbschemata (`CodeForge2DarkTheme` & `CodeForge2LightTheme`) in den Sora Editor.

---

## 1. Verschiebung & Paketierung der CodeForge2 Themes

Die beiden Theme-Dateien wurden aus dem Root-Asset-Verzeichnis in das Paket `:feature:editor` verschoben:

- **Quelldateien**: `assets/CodeForge2DarkTheme.kt` und `assets/CodeForge2LightTheme.kt`
- **Zielverzeichnis**: `feature/editor/src/main/kotlin/com/codeforge/feature/editor/theme/`
- **Paket-Name**: `package com.codeforge.feature.editor.theme`

---

## 2. Imports in `SoraLanguageProvider.kt`

In `SoraLanguageProvider.kt` wurden die Importe für die zwei neuen Themes ergänzt:

```kotlin
import com.codeforge.feature.editor.theme.CodeForge2DarkTheme
import com.codeforge.feature.editor.theme.CodeForge2LightTheme
```

---

## 3. Erweiterung von `applySchemeByName`

In der Methode `applySchemeByName(editor: CodeEditor, schemeName: String)` wurden zwei neue Zweige im `when`-Block eingefügt, um die Themes direkt per Namen (case-insensitive) anzuwenden:

```kotlin
fun applySchemeByName(editor: CodeEditor, schemeName: String) {
    val cleanName = schemeName.trim()
    when {
        cleanName.equals("Dracula", ignoreCase = true) -> editor.colorScheme = DraculaTheme()
        cleanName.equals("Monokai", ignoreCase = true) -> editor.colorScheme = MonokaiTheme()
        cleanName.equals("Nord Dark", ignoreCase = true) || cleanName.equals("NordDark", ignoreCase = true) -> editor.colorScheme = NordDarkTheme()
        cleanName.equals("Material Palenight", ignoreCase = true) || cleanName.equals("MaterialPalenight", ignoreCase = true) -> editor.colorScheme = MaterialPalenightTheme()
        cleanName.equals("Tokyo Night", ignoreCase = true) || cleanName.equals("TokyoNight", ignoreCase = true) -> editor.colorScheme = TokyoNightTheme()
        cleanName.equals("GitHub Light", ignoreCase = true) || cleanName.equals("GitHubLight", ignoreCase = true) -> editor.colorScheme = GitHubLightTheme()
        cleanName.equals("Solarized Light", ignoreCase = true) || cleanName.equals("SolarizedLight", ignoreCase = true) -> editor.colorScheme = SolarizedLightTheme()
        cleanName.equals("One Light", ignoreCase = true) || cleanName.equals("OneLight", ignoreCase = true) -> editor.colorScheme = OneLightTheme()
        cleanName.equals("Rose Pine Dawn", ignoreCase = true) || cleanName.equals("RosePineDawn", ignoreCase = true) -> editor.colorScheme = RosePineDawnTheme()
        cleanName.equals("Material Light", ignoreCase = true) || cleanName.equals("MaterialLight", ignoreCase = true) -> editor.colorScheme = MaterialLightTheme()
        
        // CodeForge 2 Themes
        cleanName.equals("CodeForge 2 Dark", ignoreCase = true) || cleanName.equals("CodeForge2Dark", ignoreCase = true) -> editor.colorScheme = CodeForge2DarkTheme()
        cleanName.equals("CodeForge 2 Light", ignoreCase = true) || cleanName.equals("CodeForge2Light", ignoreCase = true) -> editor.colorScheme = CodeForge2LightTheme()
        
        else -> {
            ensureTextMateInitialized()
            val tmKey = when (cleanName.lowercase()) {
                "codeforge", "codeforge dark" -> "codeforge"
                "quietlight for tm", "quietlight", "quiet_light" -> "quietlight"
                "ayu dark for tm", "ayu-dark", "ayu_dark" -> "ayu_dark"
                "ayu light for tm", "ayu-light", "ayu_light" -> "ayu_light"
                "ayu mirage for tm", "ayu-mirage", "ayu_mirage" -> "ayu_mirage"
                "eclipse dark", "eclipse_dark" -> "eclipse_dark"
                "eclipse light", "eclipse_light" -> "eclipse_light"
                "onedark", "one dark" -> "onedark"
                "solarized dark for tm", "solarized_dark", "solarized-dark" -> "solarized_dark"
                else -> if (cleanName.isNotBlank()) cleanName else "codeforge"
            }
            val applied = runCatching {
                ThemeRegistry.getInstance().setTheme(tmKey)
                editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                true
            }.getOrDefault(false)

            if (!applied) {
                editor.colorScheme = DraculaTheme()
            }
        }
    }
    editor.invalidate()
}
```

---

## 4. Übersicht der verfügbaren Native Kotlin Themes

| Theme Name | Klasse | Mode | Background Hex |
| :--- | :--- | :--- | :--- |
| **Dracula** | `DraculaTheme` | Dark | `#282A36` |
| **Monokai** | `MonokaiTheme` | Dark | `#272822` |
| **Nord Dark** | `NordDarkTheme` | Dark | `#2E3440` |
| **Material Palenight** | `MaterialPalenightTheme` | Dark | `#292D3E` |
| **Tokyo Night** | `TokyoNightTheme` | Dark | `#1A1B26` |
| **CodeForge 2 Dark** | `CodeForge2DarkTheme` | Dark | `#121318` |
| **GitHub Light** | `GitHubLightTheme` | Light | `#FFFFFF` |
| **Solarized Light** | `SolarizedLightTheme` | Light | `#FDF6E3` |
| **One Light** | `OneLightTheme` | Light | `#FAFAFA` |
| **Rose Pine Dawn** | `RosePineDawnTheme` | Light | `#FAF4ED` |
| **Material Light** | `MaterialLightTheme` | Light | `#FAFAFA` |
| **CodeForge 2 Light** | `CodeForge2LightTheme` | Light | `#FAFAFA` |
