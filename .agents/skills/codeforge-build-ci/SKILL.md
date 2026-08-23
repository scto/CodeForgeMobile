---
name: codeforge-build-ci
description: CodeForgeMobile Build-Prozess, Version Catalog, CI/CD Pipelines und Kotlin-Formatierung (ktfmt).
---
# CodeForgeMobile Build & CI

## Wichtige Dateien
- `gradle/libs.versions.toml`: Zentrale Abhängigkeits- und Versionsverwaltung.
- `.github/workflows/`: GitHub Actions Pipelines (z. B. `main-build-test.yml`, `ktfmt-check.yml`).
- `build.gradle.kts`: Root-Build-Script.

## Best Practices & Fakten
- **Abhängigkeiten**: Neue Libraries MÜSSEN in die `libs.versions.toml` eingetragen und via `alias(libs.x.y)` referenziert werden.
- **Code-Formatierung**: Das Projekt nutzt `ktfmt`. Vor Commits sollte `./gradlew ktfmtFormat` ausgeführt werden. Die CI prüft dies (`ktfmt-check.yml`).
- **Build-Befehle**:
 - Debug APK: `./gradlew assembleDebug`
 - Release APK: `./gradlew assembleRelease`
 - Tests ausführen: `./gradlew testDebugUnitTest`