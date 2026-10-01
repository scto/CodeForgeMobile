# CodeForge Mobile – Custom Kotlin Editor Themes Refactoring Dokumentation

Diese Dokumentation beschreibt die Umsetzung der Anweisungen aus `Text.txt` zur Integration von 10 maßgeschneiderten Kotlin-Farbschemata im Sora Editor und der vollständigen Bereinigung veralteter Standard-Schemes.

---

## 1. Verschiebung & Paketierung der Kotlin-Theme-Dateien

Die 10 neuen Kotlin-Theme-Klassen wurden aus dem temporären Verzeichnis `assets/SoraEditor_Themes/` in das Quellpaket `:feature:editor` verschoben:

- **Zielpfad**: `feature/editor/src/main/kotlin/com/codeforge/feature/editor/theme/`
- **Paket-Name**: `package com.codeforge.feature.editor.theme`

### Liste der 10 Themes:
1. `DraculaTheme.kt` (`DraculaTheme`)
2. `MonokaiTheme.kt` (`MonokaiTheme`)
3. `NordDarkTheme.kt` (`NordDarkTheme`)
4. `MaterialPalenightTheme.kt` (`MaterialPalenightTheme`)
5. `TokyoNightTheme.kt` (`TokyoNightTheme`)
6. `GitHubLightTheme.kt` (`GitHubLightTheme`)
7. `SolarizedLightTheme.kt` (`SolarizedLightTheme`)
8. `OneLightTheme.kt` (`OneLightTheme`)
9. `RosePineDawnTheme.kt` (`RosePineDawnTheme`)
10. `MaterialLightTheme.kt` (`MaterialLightTheme`)

---

## 2. Bereinigung der Imports (`SoraLanguageProvider.kt`)

Alle Importe alter sora-editor Standard-Schemata (`SchemeDarcula`, `SchemeEclipse`, `SchemeGitHub`, `SchemeNotepadXX`, `SchemeVS2019`) wurden aus `SoraLanguageProvider.kt` entfernt. 

Stattdessen wurden die 10 neuen Theme-Klassen importiert:

```kotlin
import com.codeforge.feature.editor.theme.DraculaTheme
import com.codeforge.feature.editor.theme.MonokaiTheme
import com.codeforge.feature.editor.theme.NordDarkTheme
import com.codeforge.feature.editor.theme.MaterialPalenightTheme
import com.codeforge.feature.editor.theme.TokyoNightTheme
import com.codeforge.feature.editor.theme.GitHubLightTheme
import com.codeforge.feature.editor.theme.SolarizedLightTheme
import com.codeforge.feature.editor.theme.OneLightTheme
import com.codeforge.feature.editor.theme.RosePineDawnTheme
import com.codeforge.feature.editor.theme.MaterialLightTheme
```

---

## 3. Überarbeitung von `applySchemeByName` & Fallback-Logik

Die `applySchemeByName(editor: CodeEditor, schemeName: String)` Methode steuert die Farbschema-Zuweisung:

1. **Native Kotlin Themes**: Werden direkt per Name (case-insensitive) gemappt.
2. **TextMate JSON Fallback**: Wenn kein direktes Kotlin-Theme matched, wird die TextMate `ThemeRegistry` abgefragt.
3. **Sicherheits-Fallback**: Schlägt auch TextMate fehl, wird automatisch auf `DraculaTheme()` zurückgegriffen.
4. **Redraw**: `editor.invalidate()` wird garantiert am Ende ausgeführt.

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

## 4. Zusammenfassung

- **Eigene Kotlin-Theme-Architektur**: Bessere Performance und vollständige Kontrolle über Editor-Farben.
- **Saubere Codebasis**: Keine veralteten sora-editor standard schemes mehr im Projekt.
- **Robustes Fallback**: Reduziert Fehlermöglichkeiten und garantiert funktionierendes Highlighting.
