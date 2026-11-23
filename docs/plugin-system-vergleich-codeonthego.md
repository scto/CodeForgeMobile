# Plugin-System: Code on the Go vs. CodeForge Mobile

> **Stand-Hinweis (2026-11-21):** Seit dieser Analyse gibt es in CFM eine Prozess-Sandbox (`:libs:plugin-sandbox`,
> `isolatedProcess`) und Signaturen (`signature.json`). Sandbox-Code-Plugins sind aus dem Store installierbar; die
> Zeilen „Sandbox“, „Verteilung“ und „Updates/Vertrauen“ unten beschreiben den Stand davor.

Stand: 2026-11-18. Quellen: `scto/CodeOnTheGo` (Branch `stage`: `plugin-api/`, `plugin-manager/`,
`docs/plugin-api.md`, `docs/PLUGIN_AUTHORING.md`), `appdevforall/plugin-examples` (HEAD `83c8f60`: `libs/`,
`plugins/`, `scripts/`), CodeForge Mobile `main` (`libs/plugin-api`, `libs/plugin-ui-api`, `libs/plugin-impl`,
`core/domain/extension`, `templates/`, `docs/extensions.md`).

## 1. Code on the Go (CoGo)

### Aufbau

| Teil | Wo | Inhalt |
|---|---|---|
| API | `CodeOnTheGo/plugin-api` (Paket `com.itsaky.androidide.plugins.*`) | `IPlugin`, `PluginContext`, Erweiterungs-Interfaces, `Ide*Service`, Datenklassen, Hilfsklassen (`KeystoreSecretStore`, KI-Prompt-Engine, `ServerUrl`) |
| Builder | `CodeOnTheGo/plugin-api/plugin-builder` → `gradle-plugin.jar` | Gradle-Plugin `com.itsaky.androidide.plugins.build`: Aufgaben `assemblePlugin`/`assemblePluginDebug`, Versionsstempel, Herkunft (`cgp-build.properties`) |
| Laufzeit | `CodeOnTheGo/plugin-manager` | `PluginManager` (~2000 Zeilen), `PluginLoader`, `PluginContextImpl`, 20 Service-Implementierungen, Sicherheit, Absturz-Zähler, Doku/Tooltips |
| Beispiele + Galerie | `appdevforall/plugin-examples` | 30 Plugins, `libs/` (5 Jars), Skripte, Veröffentlichung nach Cloudflare R2 |

### Plugin-Format und Build

- **`.cgp` = umbenannte, signierte APK.** `PluginBuilder` hängt an `assemble<Variant>` und kopiert
  `outputs/apk/<variant>/*.apk` nach `build/plugin/<name>.cgp`.
- Ein Plugin ist ein **Android-*Application*-Modul** mit vollständigem AGP-Build (Kotlin, Ressourcen, Manifest).
  Metadaten stehen als `<meta-data>` im `AndroidManifest.xml`: `plugin.id`, `plugin.name`, `plugin.version`,
  `plugin.main_class`, `plugin.min_ide_version`/`plugin.max_ide_version`, `plugin.permissions`, Icons und Herkunft.
- Die API wird `compileOnly(files("../../libs/plugin-api.jar"))` eingebunden. Die Jars kommen per Skript
  (`update-libs.sh`) oder per GitHub Action aus CoGo `stage`. Veröffentlicht wird gegen das Release `plugin-api-latest`.
- **Herkunft:** Jede `.cgp` enthält `assets/cgp-build.properties` (Commit, `+dirty`, Zeitstempel, `libs_revision`).
  `verify-provenance.sh` prüft das nach jedem Build.
- **Ressourcen:** Plugins bringen eigene `res/` mit (Layouts, Drawables, Strings, Themes). Bibliotheken wie Compose
  stecken ebenfalls in der APK, weil der Host Compose nicht mehr bereitstellt.

### Laden

- `PluginLoader` liest das Manifest über `PackageManager.getPackageArchiveInfo` und lädt die Ressourcen über
  `getResourcesForApplication`, bei Fehlern über `AssetManager.addAssetPath`.
- Der Code läuft in einem `DexClassLoader` mit **dem Klassenlader des Hosts als Eltern**. Ein Plugin sieht damit
  alle Host-Klassen.
- Native Bibliotheken (`lib/<abi>`) gibt es nur mit der Berechtigung `native.code`.
- Ablauf: `initialize(PluginContext) → activate() → deactivate() → dispose()`.
- Updates werden nur angenommen, wenn die Signatur übereinstimmt (`haveMatchingSignatures`, aufgerufen aus
  `PluginRepositoryImpl` und `PluginManagerViewModel`).

### Schnittstellen

- **Erweiterungen, die das Plugin implementiert:**
  - `UIExtension`: Hauptmenü, Kontextmenü, Editor-Tabs, Seitenleiste, Toolbar, FAB, Host-Aktionen ausblenden
  - `EditorExtension`: Vervollständigung, Code-Aktionen, Hover, Syntaxhervorhebung
  - `EditorTabExtension`, `DocumentationExtension` (Tooltips in drei Stufen, Offline-HTML), `BuildActionExtension`
  - `SnippetExtension`, `ProjectExtension` (Vorlagen), `FileOpenExtension`, `SettingsExtension`
  - `LanguageExtension`: Tree-sitter-Grammatiken als `.so` und Language-Server-Definitionen
- **Host-Dienste (`ServiceRegistry.get(...)`):**
  - Projekt: `IdeProjectService` (Projekte, `getModuleContext`), `IdeProjectManipulationService`
  - Editor und Dateien: `IdeEditorService` (Text lesen und ändern, Cursor, Peer-Cursor, Inline-Vorschläge,
    Listener), `IdeEditorTabService`, `IdeFileService` (mit Pfad-Allowlist), `IdeArchiveService`
  - Build: `IdeBuildService` (Gradle-Aufgaben ausführen, Status-Listener, Sync, App starten, QuickBuild)
  - Werkzeuge: `IdeCommandService`, `IdeTerminalService`, `IdeEnvironmentService`, `IdeLogService`
  - Oberfläche: `IdeUIService`, `IdeThemeService`, `IdeTooltipService`, `IdeSidebarService`
  - Sonstiges: `IdeTemplateService`, `IdeSnippetService`, `IdeFeatureFlagService`
- **Dienste zwischen Plugins (`SharedServices`):** `LlmInferenceService` (in AI-Core implementiert, von allen
  KI-Plugins genutzt) und `ToolSourceRegistry` (Werkzeuge für den Agenten).
- **Berechtigungen:** `filesystem.read`, `filesystem.write`, `network.access`, `system.commands`, `ide.settings`,
  `project.structure`, `native.code`, `ide.environment.write`.
- **API-Stabilität:** Ausdrücklich *nicht eingefroren*. Seit 26.28 gibt es ein ABI-Dump (Kotlin
  binary-compatibility-validator) als CI-Prüfung und ein Changelog „ab welcher Version gibt es was“.

### Sicherheit (Befund)

- Plugins laufen **im App-Prozess mit derselben UID**. Berechtigungen sperren nur die Host-Dienste
  (`IdeFileService`, `IdeArchiveService`, Befehle …). Direktes `java.io.File` oder `java.net` verhindert das nicht.
- `PluginSecurityManager.checkClassAccess` (Sperrliste für `Runtime`, `ProcessBuilder`, Reflection …): In
  `plugin-manager` und in den Plugin-Dateien von `app` gibt es keinen Aufruf.
- `validatePlugin` prüft nur, ob die Datei lesbar ist, ob das Manifest vollständig ist und ob die Berechtigungen gültig sind.
- Da der Host-Klassenlader der Eltern-Lader ist, erreichen Plugins auch interne Host-Klassen
  (z. B. `PluginFragmentHelper` mit `@InternalPluginApi`).
- **Fazit:** Das Vertrauen hängt an der Herkunft (Galerie, Signatur), nicht an einer technischen Sandbox.

### Strategie

Fast jede größere Funktion ist ausgelagert: Compose-Vorschau, Layout-Editor, APK-Analyzer, KI (Core plus
Backends), JVM-Tools, NDK-Installer, Keystore-Generator, Markdown-Vorschau. Vorlagen (`.cgt`) und Snippets
(`.cgs`) sind eigene, deklarative Formate neben `.cgp`. ADR 0018/0019: Plugins für Android Studio und VS Code
werden *portiert*, nicht ausgeführt.

## 2. CodeForge Mobile (CFM)

| Teil | Inhalt |
|---|---|
| Format | ZIP mit `plugin.json` (Manifest v1–v3, `apiVersion` aktuell 3), optional `classes.dex`/Jar und `entryPointClass` |
| Deklarativ (`contributes`) | `colorThemes`, `iconThemes`, `languagePacks`, `grammars` (TextMate), `snippets` (VS-Code-Format), `lspServers`, `formatters`, `tools`, `requires` (pkg/npm/pip/download), `agentTools`, `mcpServers`, `projectTemplates`, `settings`, `extensionPack` |
| Code-API (`:libs:plugin-api`) | `CodeForgePlugin` (`onLoad`/`onUnload`/`onUninstall`), `PluginContext` (Logger, `register(ExtensionPoint)`, Settings, UI-Rückmeldung), `PluginHostServices` (Shell, LSP-Client, Befehle, Menüs, Ereignisse) |
| Erweiterungspunkte | Editor-Aktionen, Quick-Fixes, Vervollständigung, Hover, Definition, Referenzen, Umbenennen, Tests, Run-Tasks, `PluginPanel` (Compose, `:libs:plugin-ui-api`) |
| Ereignisse | Datei geöffnet/gespeichert/geschlossen, Git-Commit/Branch, Editor aktiv, Diagnosen, Build gestartet/beendet, Projekt geöffnet/geschlossen |
| Berechtigungen | `commands`, `menus`, `file-events`, `git-events`, `shell`, `lsp`, `agent`, `editor`, `build-events`, `project-events`, `ui`, `run`, `notifications`; durchgesetzt in `PermissionedHostServices` (`PluginPermissionException`) |
| Laufzeit | `PluginRuntime`: `DexClassLoader` (Dex schreibgeschützt, Eltern = App-Lader), Registrierungen werden bei Fehlern und beim Entladen entfernt |
| Verteilung | Store `scto/codeforge-store` (`store.json`, SHA-256): **nur deklarative Plugins**. Code-Plugins nur per Sideload |
| Build | Deklarativ: `templates/build.py` (ZIP + SHA-256, kein SDK nötig). Code: `:templates:code-plugin:packagePlugin` |
| Prüfung | `TemplatesValidTest`, `ExamplePluginsValidTest` (echte Parser plus Store-Regel) |

## 3. Gegenüberstellung

| Thema | CoGo | CFM | Bewertung |
|---|---|---|---|
| Grundmodell | Alles ist Code (APK) | Zuerst deklarativ, Code optional (Hybrid) | CFM: weniger Aufwand und Risiko für Themes, LSP, Werkzeuge |
| Paket | `.cgp` = signierte APK | ZIP + `plugin.json` | CoGo kann Ressourcen; CFM ist leichter und statisch prüfbar |
| Ressourcen im Plugin | Ja (`res/`, eigener `Resources`-Kontext) | Nein (nur Dateien/Assets im Plugin-Ordner) | **Lücke in CFM** für Code-Plugins mit eigener UI |
| UI-Beiträge | Menü, Kontextmenü, Toolbar, Seitenleiste, FAB, Editor-Tabs, Fragmente, Einstellungsseiten | Befehle, Menüs (Editor/Datei), Compose-`PluginPanel`, deklarative Einstellungen | CoGo hat mehr Ankerpunkte |
| Editor | Lesen und ändern (Zeilen, Bereiche), Inline-Vorschläge, Peer-Cursor, Listener | Aktionen/Quick-Fixes (`EditorEffect`), Completion, Hover, Navigation, Umbenennen | Gleichwertig, verschieden geschnitten; CFM ist LSP-artiger |
| Build und Projekt | `IdeBuildService` (Gradle-Aufgaben, Sync, App starten), `getModuleContext` (Classpath, Build-Ergebnisse, Ressourcen-APK) | Nur Build-Ereignisse | **Lücke in CFM** |
| Sprachen | Tree-sitter (`.so`) + LSP | TextMate + LSP (deklarativ, Abhängigkeiten über `requires`) | CFM deklarativ einfacher; CoGo genauer, aber nativer Code |
| Zwischen Plugins | `SharedServices` (KI-Backends, Werkzeugregister) | `extensionPack` (nur Bündel), kein Dienstaustausch | Lücke in CFM, falls KI- oder Werkzeug-Plugins entstehen |
| Berechtigungen | 8, grob (Dateisystem/Netz) | 13, an Funktionen gebunden, an der API-Fassade durchgesetzt | CFM genauer; bei beiden keine Prozess-Sandbox |
| Sandbox | Keine (gleiche UID, Host-Lader als Eltern; Klassen-Sperrliste ohne Aufrufer) | Keine für Code; Store nur deklarativ | CFM ist durch „Code nur per Sideload“ konsequenter |
| Updates/Vertrauen | APK-Signatur muss passen | SHA-256 im Katalog, keine Signatur | Signaturprüfung fehlt in CFM |
| Versionierung | `min/max_ide_version`, nicht eingefroren, ABI-Dump in CI | `apiVersion` (neuer wird abgelehnt) | ABI-Dump fehlt in CFM |
| Bauen | AGP-Projekt + Builder-Plugin + 5 Jars aus CoGo; Herkunft im Paket | Python-Skript (deklarativ) bzw. Gradle-Aufgabe (Code) | CoGo besser nachverfolgbar; CFM einfacher |
| Größe | Plugins bündeln Compose (MB je Plugin) | Compose kommt vom Host | CFM schlanker |
| Doku/Hilfe | Tooltips in drei Stufen + Offline-HTML je Plugin, Pflicht | Keine Integration | Nett, aber kein Muss |

## 4. Empfehlungen für CFM

**Übernehmen (Priorität):**

1. **Build- und Projekt-Dienst** (`build-events` erweitern bzw. neue Berechtigung `build`):
   - Gradle-Aufgaben ausführen und Ergebnis abwarten, Sync auslösen
   - `moduleContext(file)` mit Classpath, Variante und Build-Ergebnissen. `PreviewGradleOutputs` aus
     `:feature:composepreview` liefert das schon und gehört dafür nach `:core`.
2. **Signaturen für Code-Plugins:**
   - Signatur (oder Ed25519 im ZIP) beim ersten Installieren merken
   - Updates nur mit gleicher Signatur annehmen; Grundlage, falls Code-Plugins je in den Store sollen
3. **ABI-Prüfung für `:libs:plugin-api`** mit binary-compatibility-validator (`apiDump`/`apiCheck` in CI). Die
   Kotlin-Fallen aus CoGos `plugin-api.md` gelten genauso:
   - Datenklassen-Parameter
   - Interface-Methoden in Erweiterungs-Interfaces
   - Enums
4. **Herkunft im Paket:** `build-info.properties` (Commit, Zeitstempel, API-Version) von `build.py` und
   `packagePlugin` schreiben lassen und in der Plugin-Ansicht zeigen.

**Prüfen (je nach Bedarf):**

5. **Mehr UI-Ankerpunkte** (Toolbar, Seitenleiste, Editor-Tab) als Erweiterungspunkte für `PluginPanel` statt
   eigener Fragment-Welt.
6. **Ressourcen für Code-Plugins:** optional `resources.ap_`/APK im ZIP, geladen wie in
   `PreviewProjectResources` (`AssetManager.addAssetPath`).
7. **Dienste zwischen Plugins** (`registerService`/`service(type)`), sobald es einen Anbieter gibt (z. B. KI-Backends).

**Nicht übernehmen:**

- „Alles als APK“: Für Themes, LSP, Werkzeuge und Vorlagen ist das deklarative Modell klar überlegen
  (Store-fähig, ohne SDK, statisch prüfbar).
- Kernfunktionen (Compose-Vorschau, Layout-Editor) in Plugins auslagern. Das bringt bei CoGo Compose in jede `.cgp`,
  koppelt sie an fünf Jars und macht API-Brüche teuer. CFM hat diese Funktionen als Module mit direkter Integration.
- Eine Klassen-Sperrliste als Scheinsicherheit. Ohne getrennten Prozess schützt nur die Herkunft (Store/Signatur).
