# Projektvorlagen als Plugins (`contributes.projectTemplates`)

| Plugin | Vorlage | Sprachen / DSL |
|---|---|---|
| `com.codeforge.plugin.compose-clean.template` | Compose · Clean Architecture: `:app`, `:core:domain` (reines Kotlin), `:core:data`, `:feature:home`; MVI mit StateFlow, Hilt, Material 3, Unit-Test | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.compose-drawer.template` | Compose · Navigationsmenü: `ModalNavigationDrawer` mit Start (Übersicht), Notizen (Liste, Hinzufügen-Dialog, Löschen) und Einstellungen (Hell/Dunkel/System, dynamische Farben – wirken sofort); `:app`, `:core:domain`, `:core:data`, `:feature:home/notes/settings`; MVI, Hilt, typsichere Navigation, Unit-Tests | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.compose-bottomnav.template` | Compose · Untere Navigationsleiste: `NavigationBar` mit Heute (Fortschrittsring, Tagesziel), Aufgaben (Filter-Chips, Abhaken, Hinzufügen, Löschen) und Profil (Name, Tagesziel, Statistik); Abzeichen mit offenen Aufgaben; `:app`, `:core:domain`, `:core:data`, `:feature:today/tasks/profile`; MVI, Hilt, typsichere Navigation, Unit-Tests | Kotlin / KTS, minSdk ≥ 24 |
| `com.codeforge.plugin.jvm-cli.template` | Konsolenprogramm für die JVM (`./gradlew run`), JUnit-Test | Kotlin, Java / KTS, Groovy |

* Format und Platzhalter: `docs/extensions.md` → „Projektvorlagen“. Autoren-Vorlage: `templates/project-template`.
* Bauen: `python3 project-templates/generate.py [ausgabe]`; Store: über `store-site/publish.py` (Kategorie `template`).
* Vorschaubilder: `python3 project-templates/make_icons.py` (Pillow).
* Geprüft: `ProjectTemplatePluginsTest` (`:core:domain`) und `PluginTemplateGeneratorTest` (`:libs:template-engine`,
  mit `CF_TEMPLATE_OUT=<ordner>` bleiben die erzeugten Projekte zum Bauen liegen).
* Versionen (AGP, Kotlin, KSP, Hilt) stehen im `gradle/libs.versions.toml` der jeweiligen Vorlage; bei Updates dort anpassen
  und eine erzeugte Fassung bauen. Kein `jvmToolchain`: JDK 17 oder 21 auf dem Gerät reicht.
