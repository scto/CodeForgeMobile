---
name: codeforge-architecture
description: CodeForgeMobile Projektarchitektur. Hilft bei Modulgrenzen, MVI-Pattern, Dependency-Regeln und Navigation.
---
# CodeForgeMobile Architektur & Navigation

## Wichtige Dateien
- `settings.gradle.kts`: Modulübersicht und Build-Struktur.
- `app/src/main/kotlin/com/codeforge/app/CodeForgeNavHost.kt`: Zentrale Jetpack Compose Navigation.
- `app/src/main/kotlin/com/codeforge/app/CodeForgeApplication.kt`: Hilt Application Entry Point.

## Modul-Struktur & Regeln
Das Projekt folgt einer strengen Multi-Modul Clean Architecture:
- `:app`: Application, NavHost, Hilt Dependency Root.
- `:core:*`: Fundament. `:core:domain` (UseCases, Models), `:core:data` (Repository-Impl), `:core:designsystem` (Theme), `:core:datastore` (Proto/Prefs).
- `:feature:*`: Isolierte UI-Module (MVI-Architektur). **Regel: Ein Feature darf niemals ein anderes Feature importieren!** Kommunikation erfolgt über Navigation-Routen oder Shared ViewModels.
- `:libs:*`: Gekapselte Kern-Engines (Terminal, LSP, Gradle Tooling, Templates).

## MVI Architektur in Features
Jedes Feature (z. B. `:feature:editor`) besteht aus:
1. `Contract.kt`: Definiert `@Immutable data class UiState`, `sealed interface UiEvent` und `UiEffect`.
2. `ViewModel.kt`: `@HiltViewModel` mit `StateFlow` für State und `SharedFlow` für Effects.
3. `Screen.kt`: Compose UI, die ausschließlich auf den State reagiert und Events an das ViewModel feuert.