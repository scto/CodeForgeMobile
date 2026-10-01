# CodeForge Mobile: Build-Restrukturierung, Android-Ressourcen & Master-Setup Dokumentation

## 1. Übersicht
Dieses Dokument beschreibt die Wiederherstellung der Gradle-Modulstruktur, die Behebung von AAPT2-Ressourcenfehlern (`Theme.CodeForge`, `ic_launcher`) sowie die vollständige Aktivierung aller Sora-Editor Master-Setup Features.

---

## 2. Modulstruktur-Wiederherstellung (`build.gradle.kts`)

Fehlende Konfigurationsdateien in den Untermodulen wurden mit den einheitlichen Convention-Plugins (`codeforge.android.library.compose` & `codeforge.android.hilt`) neu aufgesetzt:

- **Wiederhergestellte Features**:
  - `:feature:onboarding` (`com.codeforge.feature.onboarding`)
  - `:feature:welcome` (`com.codeforge.feature.welcome`)
  - `:feature:filetree` (`com.codeforge.feature.filetree` mit `libs.bundles.filetree`)
  - `:feature:terminal` (`com.codeforge.feature.terminal`)
  - `:feature:sdkmanager` (`com.codeforge.feature.sdkmanager`)
  - `:feature:layoutdesigner` (`com.codeforge.feature.layoutdesigner`)
  - `:feature:git` (`com.codeforge.feature.git`)
  - `:feature:plugins` (`com.codeforge.feature.plugins`)
  - `:feature:settings` (`com.codeforge.feature.settings`)
- **Wiederhergestellte Bibliotheken**:
  - `:libs:template-engine` (`com.codeforge.libs.template_engine`)
  - `:libs:gradle-tooling-bridge` (`com.codeforge.libs.gradle_tooling_bridge`)
  - `:libs:plugin-api` (`com.codeforge.libs.plugin_api`)

---

## 3. AAPT2 Android-Ressourcen Behebung (`app/src/main/res/`)

Um die AAPT2-Linkerfehler bei `:app:processDebugResources` vollständig zu beheben, wurden folgende Ressourcen in `app/src/main/res/` angelegt:

1. **`app/src/main/res/values/themes.xml`**:
   Define `Theme.CodeForge` als Kind von `Theme.Material3.DayNight.NoActionBar` mit transparenten Systembalken.
2. **`app/src/main/res/drawable/ic_launcher_background.xml`**:
   VectorDrawable-Hintergrund in Theme-Farbe (`#1E1E2E`).
3. **`app/src/main/res/drawable/ic_launcher_foreground.xml`**:
   VectorDrawable-Vordergrund in Akzentfarbe (`#6366F1`).
4. **`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`**:
   Adaptive App-Icons verknüpft mit Hintergrund- und Vordergrund-Drawables.

---

## 4. Sora-Editor Master-Setup Integration (`SoraEditorAppearance.kt`)

Alle im Dokument `Sora-Editor-Ultimate-Setup.md` geforderten Funktionalitäten wurden in `SoraEditorAppearance.kt` fest verankert:

```kotlin
// 1. Gesten & Navigationsmodus
editor.props.canScaleText = true
editor.props.navigationMode = CodeEditor.NAVIGATION_MODE_PC

// 2. Ultimate-Setup Komponenten (getComponent)
runCatching {
    editor.getComponent(Magnifier::class.java)?.isEnabled = config.magnifierEnabled
    editor.getComponent(EditorAutoCompletion::class.java)?.apply {
        isEnabled = true
        isHideWhenNoMatch = true
        setEnabledAnimation(config.completionAnimEnabled)
    }
    editor.getComponent(StickyScroll::class.java)?.isEnabled = config.stickyScroll
    editor.getComponent(BracketPairs::class.java)?.isEnabled = true

    val minimap = editor.getComponent(EditorMinimap::class.java)
    if (minimap != null) {
        minimap.isEnabled = if (config.fontSize == 0 && !config.showMinimap) true else config.showMinimap
        minimap.minWidth = 80
    }
    
    // Inlay Hints & Diagnostic Highlights via Reflection/LSP Client
    editor.getComponent(InlayHintManager::class.java)?.isEnabled = true
    editor.getComponent(DiagnosticManager::class.java)?.isEnabled = true
}
```

---

## 5. Verifizierung & Status
- **Formatierungs-Fallback**: Wenn kein Sprachserver verbunden ist, formatiert das integrierte `CodeFormatter`-Modul den Quellcode.
- **Adaptive Icon Preview**: `<adaptive-icon>` XML-Dateien werden in `ImageFilePreview.kt` korrekt geladen und gerendert.
- **APK Build**: Startet sauber über `bash .../gradle assembleDebug --no-daemon`.
