# Projektvorlagen als Plugins (`contributes.projectTemplates`)

| Plugin | Vorlage | Sprachen / DSL |
|---|---|---|
| `com.codeforge.plugin.compose-clean.template` | Compose · Clean Architecture: `:app`, `:core:domain` (reines Kotlin), `:core:data`, `:core:resources`, `:feature:home`; MVI mit StateFlow, Hilt, Material 3, Unit-Test | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.compose-drawer.template` | Compose · Navigationsmenü: `ModalNavigationDrawer` mit Start (Übersicht), Notizen (Liste, Hinzufügen-Dialog, Löschen) und Einstellungen (Hell/Dunkel/System, dynamische Farben – wirken sofort); `:app`, `:core:domain`, `:core:data`, `:core:resources`, `:feature:home/notes/settings`; MVI, Hilt, typsichere Navigation, Unit-Tests | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.compose-bottomnav.template` | Compose · Untere Navigationsleiste: `NavigationBar` mit Heute (Fortschrittsring, Tagesziel), Aufgaben (Filter-Chips, Abhaken, Hinzufügen, Löschen) und Profil (Name, Tagesziel, Statistik); Abzeichen mit offenen Aufgaben; `:app`, `:core:domain`, `:core:data`, `:core:resources`, `:feature:today/tasks/profile`; MVI, Hilt, typsichere Navigation, Unit-Tests | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.compose-notes.template` | Compose · Notizen-App mit eigenem Designsystem (`:core:designsystem`: Theme hell/dunkel, Akzentfarben, Formen, Verläufe, Komponenten): Übersicht mit Suche und Raster, Editor mit Notizfarben und Anheften, Einstellungen mit Theme-Vorschau; `:app`, `:core:domain/data/resources/designsystem`, `:feature:notes/editor/settings`; MVI, Hilt, typsichere Navigation, Unit-Tests | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.compose-podcast.template` | Compose · Podcast-App mit demselben Designsystem: Entdecken (Kategorien, Empfehlung, Trends), Bibliothek, Podcast-Seite mit Abo, Vollbild-Player mit animiertem Cover, Mini-Player (simulierte Wiedergabe), Einstellungen; `:app`, `:core:domain/data/resources/designsystem`, `:feature:discover/library/player/settings`; MVI, Hilt, typsichere Navigation, Unit-Tests | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.jvm-cli.template` | Konsolenprogramm für die JVM (`./gradlew run`), JUnit-Test; `build-logic` mit Convention-Plugin, bei Kotlin detekt und ktlint | Kotlin, Java / KTS, Groovy |

* Format und Platzhalter: `docs/extensions.md` → „Projektvorlagen“. Autoren-Vorlage: `templates/project-template`.
* Bauen: `python3 project-templates/generate.py [ausgabe]`; Store: über `store-site/publish.py` (Kategorie `template`).
* Vorschaubilder: `python3 project-templates/make_icons.py` (Pillow).
* Geprüft: `ProjectTemplatePluginsTest` (`:core:domain`) und `PluginTemplateGeneratorTest` (`:libs:template-engine`,
  mit `CF_TEMPLATE_OUT=<ordner>` bleiben die erzeugten Projekte zum Bauen liegen).
* Versionen (AGP, Kotlin, KSP, Hilt) stehen im `gradle/libs.versions.toml` der jeweiligen Vorlage; bei Updates dort anpassen
  und eine erzeugte Fassung bauen. Kein `jvmToolchain`: JDK 17 oder 21 auf dem Gerät reicht.

Alle Compose-Vorlagen enthalten `build-logic` mit Convention-Plugins (`app.android.application`, `app.android.library`,
`app.android.library.compose`, `app.android.feature`, `app.android.hilt`, `app.jvm.library`, `app.quality`), Hilt über
KSP, detekt und ktlint (`./gradlew detekt ktlintCheck`), zentrale Ressourcen in `:core:resources` und `compileSdk` 37.
