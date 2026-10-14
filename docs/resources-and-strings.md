# Zentralisierte Strings (`:core:resources`)

Stand: 2026-10-14. **Vollständig zweisprachig (EN Standard / DE Übersetzung) auf Gerät/Gradle gebaut und verifiziert** (Task 18: `agy-tasks/18-translate-strings-en-de.md`).

## Ergebnis

* Modul `:core:resources` (Namespace `com.codeforge.core.resources`), eingetragen in `settings.gradle.kts`.
* `values/strings.xml` mit 816 Einträgen (Standardsprache Englisch), `values-de/strings.xml` mit 816 Einträgen (vollständige deutsche Übersetzung). Redundantes `values-en/strings.xml` entfernt.
* Alle sichtbaren UI-Literale in `feature/` und `app/` migriert (`find_hardcoded_strings.py --check` liefert Exit 0; `check_string_locales.py` liefert Exit 0).
* `implementation(project(":core:resources"))` in allen benötigten Modulen.
* `:feature:projectwizard`: eigene `strings.xml` entfernt, Texte jetzt zentral; Zugriff per `CoreR`-Alias.
* `TestRes` (`:core:testing`) für JVM-Tests; liest standardmäßig die englische `values/strings.xml` (optional `TestRes.install("de")` für Deutsch); `GitRepositoryImplTest`, `GitAdvancedTest`, `GitSettingsRepositoryTest`, `GitPatchTest`, `FileTreeFormattersTest` installieren es.
* `GradleBridgeService` initialisiert `Res` im isolierten `:gradletooling`-Prozess in `onCreate()`.

Details zur Nutzung und zu den Konventionen: `core/resources/README.md`.

## Entscheidungen

| Thema | Entscheidung | Grund |
|---|---|---|
| Standardsprache | Englisch in `values/strings.xml` | Internationale Standard-Konvention auf Android, Fallback für alle nicht übersetzten Locales |
| Deutsche Übersetzung | `values-de/strings.xml` | Muttersprachliche Übersetzung mit 100% Schlüssel- und Platzhalter-Parität |
| Prüfskript | `scripts/check_string_locales.py` | Garantiert identische Schlüsselmenge, Escapes (`\n`, `\'`, `\"`) und Platzhalter (`%1$s`) |
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
* **Behoben:** Test-Erwartungen in `GitRepositoryImplTest` auf englische Texte umgestellt.
* **Offen:** Platzhaltertext im Editor-Einstellungsfeld für den LSP-Pfad (`$PREFIX/bin/kotlin-language-server`) ist ein Vorschlag.
* **Risiko:** Res-Initialisierung per `androidx.startup` – in Unit-Tests ohne `TestRes` liefert `Res.string` `@string/<id>`.

## Prüfen (Gerät/Gradle)

`./gradlew :core:resources:assembleDebug testDebugUnitTest`, dann App starten und die Bereiche Git, Editor, Einstellungen,
SDK-Manager, Onboarding, Projekt-Assistent durchklicken (alle Texte sichtbar, keine `@string/…`-Platzhalter).
Validierungsskripte:
* `python3 scripts/find_hardcoded_strings.py --check`
* `python3 scripts/check_string_locales.py`
