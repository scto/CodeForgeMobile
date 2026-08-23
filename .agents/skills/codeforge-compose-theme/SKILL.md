---
name: codeforge-compose-theme
description: Richtlinien für UI-Entwicklung mit Jetpack Compose, Material 3 (inkl. Adaptive) und Theme-Persistenz.
---
# CodeForgeMobile Compose UI & Theme

## Wichtige Dateien
- `:core:designsystem/src/main/kotlin/com/codeforge/core/designsystem/CodeForgeTheme.kt`: Haupt-Theme mit Support für Dynamic Colors.
- `:core:datastore/src/main/proto/settings.proto`: Definition des Themes im Proto DataStore (`ThemeConfig`).
- `:feature:themebuilder`: UI zur Anpassung von Themes und Farben.

## UI-Prinzipien
- **Material 3**: Verwendung von M3-Komponenten (`androidx.compose.material3`).
- **Adaptive Layouts**: Nutzung von `androidx.compose.material3.adaptive` (z. B. `NavigableListDetailPaneScaffold` im `:feature:welcome` Modul) zur Unterstützung von Tablets und Foldables.
- **Theme-Persistenz**: Theme-Änderungen im `ThemeBuilderViewModel` werden als Protobuf geschrieben und global via `SettingsRepository` abonniert.

## Warnung
Keine harten Farben in Compose verwenden (kein `Color.Red`). Immer `MaterialTheme.colorScheme.*` nutzen.