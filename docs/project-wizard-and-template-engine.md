# Project Wizard & Template Engine (Neuaufbau)

Stand: Oktober 2026. Der alte Wizard und die alte Template-Engine (Freemarker, `manifest.json`, `BuiltInTemplates`, die vier Altvorlagen `compose_empty_activity`, `java_console_app`, `kotlin_cli_app`, `multi_module_android`) sind **vollständig entfernt**. Beide Module wurden ausschließlich auf Basis des mitgelieferten NeonIDE-Wizards (ZIP: `projectwizard/`, `assets/`, `res/`) neu aufgebaut. Es gibt kein Freemarker mehr (Abhängigkeit und Katalogeintrag gelöscht).

> **Verifikation:** Gebaut wurde nichts mit Gradle/Android-SDK. Mit `kotlinc` kompiliert und getestet sind die reinen JVM-Teile (Domain-Modelle, Validator, alle Template-Funktionen, `ProjectGenerator`): 12 Tests grün. Compose-UI, Hilt/KSP, Proto-Codegen und der Gradle-Build der erzeugten Projekte sind **nicht** geprüft → `agy-tasks/09-verify-project-wizard-template-engine.md`.

## Architektur

```
:feature:projectwizard  (MVI: Contract / ViewModel / Route / Screen, M3)
        │  nutzt
        ├── :core:domain      ProjectRequest, ProjectInputValidator, TemplateEngineRepository, RecentProjectsRepository
        ├── :core:datastore   WizardConfig (letzter Ort, Sprache, minSdk, DSL)
        └── DI-Bindung → :libs:template-engine (TemplateEngineRepositoryImpl)
```

### `:libs:template-engine`
- `ProjectGenerator(assets: AssetSource).generate(request, projectDir)` – kein Android-Import, daher JVM-testbar.
- **Quelltexte/Konfiguration** kommen aus Kotlin-String-Funktionen unter `templates/**` (je Kotlin- und Java-Variante): Gradle (settings, root, app, properties), Manifest, Proguard, `.gitignore`, Activities, Fragmente, ViewModels, Navigation-/Strings-XML, Compose-Theme.
- **Binärdateien/Ressourcen** kommen aus `src/main/assets`: `gradle/` (Wrapper + IDE-Katalog), `gradlew/` (Skripte), `res/resources` (Launcher-Icons, Farben, Themes), `templates/{bottomNav,navDrawer,tabbed}/res` (Layouts).
- `AssetSource` (`list`, `open`) mit `AndroidAssetSource` (AssetManager) und `DirectoryAssetSource` (Tests).
- `TemplateEngineRepositoryImpl`: IO-Dispatcher, lehnt bestehendes `<Ort>/<Name>` ab, räumt bei Fehler auf; Standardort `filesDir/projects`. Hilt-Bindung in `TemplateEngineModule`.

### Vorlagen (9)
| id | Kind | Kotlin | Java |
|---|---|---|---|
| `no_activity` | NO_ACTIVITY | ✓ | ✓ |
| `empty_activity` | EMPTY_ACTIVITY | ✓ | ✓ |
| `cpp_activity` | CPP_ACTIVITY (JNI: `tomaslib.cpp`, `Android.mk`, `Application.mk`) | ✓ | ✓ |
| `basic_activity` | BASIC_ACTIVITY | ✓ | ✓ |
| `nav_drawer_activity` | NAV_DRAWER_ACTIVITY | ✓ | ✓ |
| `bottom_nav_activity` | BOTTOM_NAV_ACTIVITY | ✓ | ✓ |
| `tabbed_activity` | TABBED_ACTIVITY | ✓ | ✓ |
| `no_androidx_activity` | NO_ANDROIDX_ACTIVITY | ✓ | ✓ |
| `compose_activity` | COMPOSE_ACTIVITY | ✓ | – (nur Kotlin) |

Jeweils wählbar: Gradle Kotlin DSL (`.kts`) oder Groovy, minSdk (21/24/26/28/29/30/33).

### `:core:domain`
`ProjectTemplateKind`, `ProjectLanguage`, `ProjectTemplateDescriptor`, `ProjectRequest`, `ProjectHandle`, `ProjectInputValidator` (Name, Package, Java-Keywords, Zielordner existiert, Compose⇒Kotlin, Package-Vorschlag), `TemplateEngineRepository` (`listTemplates`, `defaultProjectsDirectory`, `generate`). Die untypisierten `Map<String,String>`-Parameter (`TemplateParam` & Co.) entfallen.

### `:core:datastore`
`settings.proto`: `AppSettings.wizard = 5` mit `WizardConfig { last_save_location, language, min_sdk, use_groovy_dsl }`; `SettingsRepository.updateWizard { }`.

### `:feature:projectwizard`
Zwei Schritte: Vorlagenraster (Vorschaubilder, de/en-Texte) → Formular (Projektname, Package mit Auto-Vorschlag solange nicht manuell bearbeitet, Speicherort, Sprache, minSdk, KTS-Schalter, Inline-Validierung). Nach Erfolg: Präferenzen speichern, Eintrag in `RecentProjectsRepository`, Navigation in den Editor. Route-Signatur unverändert: `ProjectWizardRoute(onNavigateToEditor, onCancel)`. Back im Formular führt zurück zur Vorlagenwahl. Drawables `pw_*` (mit Night-Varianten, wo im ZIP vorhanden), Strings `values/` (de) und `values-en/`.

## Korrekturen am ZIP-Code (beim Portieren gefunden)
- Vertauschte Strings-XMLs und falsche Activity bei Bottom-Navigation → korrigiert.
- Doppelte, ungeprefixte Template-Funktionen (Notif/Navigation/PagerAdapter/PageViewModel/PlaceholderFragment) → entfernt.
- `package="…"` im Manifest (von AGP 8 abgelehnt) und unnötige `FOREGROUND_SERVICE_DATA_SYNC`-Permission → entfernt.
- XML-Header mit escaped Quotes in `strings.xml` → korrigiert (Test dafür).
- Groovy-Root-Build ohne `kotlin.compose`-Alias → ergänzt.
- Tests prüfen für alle Kombinationen (Vorlage × Sprache × DSL): Kerndateien vorhanden, kein `package=`, `R.layout/menu/navigation`-Referenzen und `*Binding`-Klassen haben passende Layout-Dateien, `@string`/`R.string`-Referenzen sind definiert.

## Offene Punkte / Risiken
1. **Gradle 9.0.0 + Kotlin 2.1.0 + AGP 8.13.0** in erzeugten Projekten: Kompatibilität nicht geprüft; KGP 2.1.x ist für Gradle 9 möglicherweise nicht freigegeben. Zuerst auf einem Gerät testen (Task 09).
2. **Aufgeblähter Katalog:** `assets/gradle/libs.versions.toml` (227 Zeilen, IDE-intern) wird in jedes erzeugte Projekt kopiert und erzeugt im Dependency-Updater (siehe `docs/indexing-and-dependency-updater.md`) viele irrelevante Einträge. Empfehlung: schlanken Katalog je Vorlage.
3. Der **Flutter-Wizard** aus dem ZIP und die **SAF-Ordnerauswahl** sind nicht portiert (Speicherort = Textfeld).
4. Nicht verwendete Icons aus `res/` (Datei-/Aktionsicons) wurden nicht importiert, nur Vorlagenbilder.
5. `ProjectHandle.moduleCount` ist immer 1 (Einmodul-Projekte); der frühere Multi-Modul-Template entfällt bewusst.
