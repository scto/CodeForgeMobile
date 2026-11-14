# Projektvorlage (`contributes.projectTemplates`)

Der Ordner `template/` enthält Ebenen, die nacheinander ins neue Projekt kopiert werden:

| Ebene | wann |
|---|---|
| `common/` | immer |
| `kotlin/` / `java/` | gewählte Sprache |
| `kts/` / `groovy/` | gewählte Gradle-DSL |
| `kotlin-kts/`, `java-groovy/` … | Kombination (z. B. unterschiedliche `build.gradle`) |

Platzhalter in Datei-/Ordnernamen und Textdateien: `{{projectName}}`, `{{packageName}}`, `{{packagePath}}` (`com/example/app`),
`{{minSdk}}`. Binärdateien (z. B. PNG) werden unverändert kopiert. Fehlen `gradlew` oder `gradle/libs.versions.toml`,
ergänzt die App ihre eigenen (Gradle-Wrapper bitte nicht selbst mitliefern: `.jar` ist im Store verboten).

Felder: `languages` (`kotlin`, `java`), `gradleDsl` (`kts`, `groovy`), `android` (`false` = kein Mindest-SDK),
`minSdk` (kleinstes wählbares), `icon` (PNG, etwa 320×200). Größere Beispiele: `project-templates/src/`.
