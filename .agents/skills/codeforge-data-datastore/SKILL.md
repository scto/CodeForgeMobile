---
name: codeforge-data-datastore
description: CodeForgeMobile Datenhaltung. Proto DataStore für Settings, Preferences DataStore für Recents, sowie Coroutines/Flow.
---
# CodeForgeMobile Data & DataStore

## Wichtige Dateien
- `:core:datastore/src/main/proto/settings.proto`: Die Single-Source-of-Truth für App-Settings (Theme, Editor, Terminal).
- `:core:datastore/src/main/kotlin/com/codeforge/core/datastore/SettingsRepository.kt`: Kotlin-Wrapper um den Proto-DataStore.
- `:core:data/`: Implementierungen der Domain-Interfaces (z. B. FileSystem, Git, SdkManager).

## Speicher-Strategien
- **Proto DataStore**: Wird für strukturierte Daten verwendet (Editor-Konfigurationen, Theme). Änderungen erfolgen typ-sicher via `.toBuilder().set...build()`.
- **Preferences DataStore**: Wird für flache Listen verwendet, z. B. `RecentProjectsRepositoryImpl` (Serialisierung über Trennzeichen).
- **Dateisystem**: VFS-Abstraktion in `FileSystemRepositoryImpl`. Physische App-Dateien liegen in `context.filesDir` (Plugins, Terminal Rootfs).

Alle Repositories werden als `@Singleton` über Dagger Hilt in `:core:data` bereitgestellt.