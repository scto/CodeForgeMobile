# Zentralisierte Strings (`:core:resources`)

Stand: 2026-10-07. **Nicht auf Gerät/Gradle gebaut** (Sandbox ohne Android-SDK); geprüft per Syntax-Parse aller 242 Kotlin-Dateien
mit `kotlinc` und per Stub-Lauf der Git-Tests (47/47) mit der echten `strings.xml`.

## Ergebnis

* Modul `:core:resources` (Namespace `com.codeforge.core.resources`), eingetragen in `settings.gradle.kts`.
* `values/strings.xml` mit 647 Einträgen (alle Module), `values-en/strings.xml` (Projekt-Assistent).
* 685 Literale automatisch ersetzt (`Res.string(R.string.…)` bzw. `stringRes(…)` im Composable), 34 Enum-/Listen-Labels von Hand auf
  `@StringRes Int` umgestellt, 17 Git-Statustexte (`op("Push…")` usw.) nachgezogen.
* `implementation(project(":core:resources"))` in 28 Modulen (alle mit Strings; `:core:testing` per `api`).
* `:feature:projectwizard`: eigene `strings.xml` entfernt, Texte jetzt zentral; Zugriff per `CoreR`-Alias (eigene `R` für Drawables).
* `TestRes` (`:core:testing`) für JVM-Tests; `GitRepositoryImplTest`, `GitAdvancedTest`, `GitSettingsRepositoryTest`, `GitPatchTest` installieren es.

Details zur Nutzung und zu den Konventionen: `core/resources/README.md`.

## Entscheidungen

| Thema | Entscheidung | Grund |
|---|---|---|
| Zugriff in Nicht-UI-Code | `Res.string` (Application-Context, Startup-Initializer) | ViewModels/Repositories bleiben ohne Context |
| Zugriff in Compose | `stringRes` nur bei direktem Argument von `Text/Icon/Image` | reagiert auf Konfigurationswechsel |
| Maschinen-Strings | nicht migriert (Patch-Header, Commit-Messages, Fehlermuster) | würden bei Sprachwechsel Logik brechen |
| Listen/Enums | `@StringRes Int` statt String | Auflösung erst im Composable |
| Tests | `TestRes` liest echte `strings.xml` per Reflection auf `R$string` | Tests prüfen denselben Text wie die App |

## Nebenbefunde

* **Behoben:** zwei Kotlin-Syntaxfehler (`/*` in KDoc öffnet verschachtelten Kommentar) in `ComposePreviewRendererImpl.kt` und
  `TextMateAssetLoader.kt` – hätten den Build gebrochen.
* **Behoben:** Intro-Seite „Echtes Linux-Terminal“ nannte noch PRoot/Alpine/Ubuntu; jetzt Termux-Umgebung.
* **Behoben:** SDK-Manager-Hinweistext enthielt `$PREFIX` als Kotlin-Template (unaufgelöste Referenz); steht jetzt wörtlich in der `strings.xml`.
* **Offen:** Platzhaltertext im Editor-Einstellungsfeld für den LSP-Pfad (`$PREFIX/bin/kotlin-language-server`) ist ein Vorschlag.
* **Offen/ungeprüft:** `values-en` fehlt für alle Texte außer dem Projekt-Assistenten; Plurals gibt es noch nicht
  (z. B. „Treffer in %1$s Datei(en)“ ist noch als Klammer-Plural formuliert).
* **Risiko:** Res-Initialisierung per `androidx.startup` – in Unit-Tests ohne `TestRes` liefert `Res.string` `@string/<id>`.

## Prüfen (Gerät/Gradle)

`./gradlew :core:resources:assembleDebug testDebugUnitTest`, dann App starten und die Bereiche Git, Editor, Einstellungen,
SDK-Manager, Onboarding, Projekt-Assistent durchklicken (alle Texte sichtbar, keine `@string/…`-Platzhalter).
Siehe `agy-tasks/13-verify-resources.md`.
