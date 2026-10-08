# :core:resources

Zentrale Texte der App. **Alle** sichtbaren Strings (Text, Titel, `contentDescription`, Fehlermeldungen für Nutzer) liegen in
`src/main/res/values/strings.xml`; Zugriff über `Res.kt` / `ResCompose.kt`.

## Nutzung

```kotlin
import com.codeforge.core.resources.R          // R dieses Moduls (nicht transitiv!)
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

// Composable: direkt als Argument von Text/Icon/Image
Text(stringRes(R.string.git_conflict_n, index + 1))

// ViewModel / Repository / UseCase
snack(Res.string(R.string.common_token_fehlt))

// Listen/Enums: Resource-ID speichern, im Composable auflösen
enum class DrawerSection(@StringRes val labelRes: Int, …)
Text(stringRes(section.labelRes))
```

| API | Wo | Hinweis |
|---|---|---|
| `Res.string(id, vararg args)` | überall | löst gegen den Application-Context zum Aufrufzeitpunkt auf |
| `stringRes(id, vararg args)` | `@Composable` | reagiert auf Sprach-/Konfigurationswechsel |
| `pluralRes` / `Res.plural` | | für `<plurals>` (bisher keine Einträge) |
| `UiText` / `asString()` | State | Text im UI-State halten, erst im Composable auflösen |
| `TestRes.install()` | JVM-Unit-Tests | `:core:testing`, liest die echte `strings.xml` |

Initialisierung: `ResInitializer` (androidx.startup) – läuft in jedem App-Prozess (auch im Gradle-Bridge-Service).

## Konventionen

* Schlüssel: `<modul>_<kurzform>` (`git_`, `editor_`, `settings_`, …); Texte, die in ≥ 2 Modulen vorkommen, `common_…`.
* Argumente **positional**: `%1$s`, `%2$s` (auch für Zahlen). Literales `%` bei Argumenten als `%%`, ohne Argumente `formatted="false"`.
* Android-Escapes: `\'`, `\"`, `\n`, `\@`; führende/endende Leerzeichen → Wert in Anführungszeichen.
* `R` ist **nicht transitiv** (`android.nonTransitiveRClass=true`): jedes Modul importiert `com.codeforge.core.resources.R`.
  Module mit eigener `R` (z. B. `:feature:projectwizard` mit Drawables) importieren `… R as CoreR`.
* Sprachen: Standard ist Deutsch (`values/`). Englisch: `values-en/strings.xml` (bisher nur Projekt-Assistent `pw_*`).
* Neue Texte **nie** als Literal im Code; Enum-/Listen-Labels als `@StringRes Int`.

## Bewusst nicht migriert

* Technische Strings: Log-Tags, Dateinamen, Regex, JSON-Schlüssel, Shell-Kommandos, Git-Patch-Header (`diff --git …`),
  Commit-Nachrichten (`Merge branch '…'`), Fehler-Erkennungsmuster (`"not authorized"`), Annotationsnamen (`"Composable"`).
* `libs/code-tools` (reines JVM-Modul ohne Android) und `libs/template-engine` (Generator-Templates, `assets/`).
* Termux-Module (`libs/termux-*`) behalten ihre eigenen XML-Ressourcen.
* Test-Quellen.
