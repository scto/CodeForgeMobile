# {{projectName}}

Konsolenprogramm für die JVM.

```bash
./gradlew run      # starten
./gradlew test     # Tests
./gradlew check    # Tests und – bei Kotlin – detekt und ktlint
./gradlew ktlintFormat   # Kotlin-Code formatieren
./gradlew installDist && build/install/{{projectName}}/bin/{{projectName}}
```

Die Build-Konfiguration steckt in Convention-Plugins unter `build-logic/` (`app.kotlin.application` bzw.
`app.java.application`); Versionen stehen in `gradle/libs.versions.toml`.
