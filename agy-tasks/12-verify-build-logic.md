# Auftrag: build-logic und Modul-Umstellung auf Convention-Plugins verifizieren

Kontext: `docs/build-logic.md`, `build-logic/README.md`. Dieser Stand wurde nie mit Gradle gebaut.

1. `./gradlew help` im Projekt-Root. Compile-Fehler in `build-logic/convention` beheben (kleinstmögliche Änderung,
   Absicht der Plugins erhalten; Hot-Spots: `consumerProguardFiles`, `getByName("release")`, `KtlintExtension`).
2. `./gradlew projects` und `./gradlew :app:assembleDebug -PcodeforgeBootstrapSkip=true`. Fehler pro Modul
   beheben; NICHT einfach `compileSdk`/Plugin-Versionen herabsetzen, ohne Grund zu dokumentieren.
3. `./gradlew test` (JVM-Tests) und `./gradlew ktlintCheck` (nur berichten, nicht auto-formatieren).
4. Ergebnis (Fehler, Fixes, Warnungen) am Ende von `docs/build-logic.md` unter „Verifikation“ eintragen.
