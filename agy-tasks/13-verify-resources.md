# Task 13 – Verifikation: `:core:resources` (zentrale Strings)

Kontext: `docs/resources-and-strings.md`, `core/resources/README.md`. Arbeitsverzeichnis = Projekt-Root `CodeForgeMobile/`.
Nichts davon wurde mit Gradle gebaut.

1. `./gradlew :core:resources:assembleDebug` – `strings.xml`/`values-en` müssen durch aapt2 gehen (Escapes, `%`, Anführungszeichen). Fehler dort beheben, nicht Texte entfernen.
2. `./gradlew assembleDebug` – Kompilierfehler aus der Migration beheben (z. B. `stringRes` außerhalb eines Composables → `Res.string`; fehlende `import com.codeforge.core.resources.R`; Module mit eigener `R` → `R as CoreR`).
3. `./gradlew testDebugUnitTest` – insbesondere `:core:data` (`Git*Test`) und `:core:domain` (`GitPatchTest`); `TestRes` nutzt Reflection auf `com.codeforge.core.resources.R$string` – falls AGP die Felder nicht als `public static int` liefert, `TestRes.idToName()` anpassen.
4. `grep -rn '"[A-ZÄÖÜ][a-zäöüß]' --include=*.kt feature app` – verbliebene sichtbare Literale in Compose-Code migrieren.
5. Gerät: App durchklicken (Git, Editor, Einstellungen, SDK-Manager, Onboarding, Projekt-Assistent, Drawer). Keine `@string/…`-Platzhalter. Sprache auf Englisch stellen: Projekt-Assistent englisch, Rest deutsch (Fallback).
6. Prüfen: läuft `ResInitializer` auch im Gradle-Bridge-Service-Prozess (Texte in `GradleBridgeService`)?
Akzeptanz: Build und Tests grün, keine harten UI-Strings mehr in `feature/` und `app/`.
