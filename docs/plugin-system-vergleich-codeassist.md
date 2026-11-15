# Plugin-System: CodeForgeMobile im Vergleich mit CodeAssist

Stand: 2026-11-15. CodeAssist: `scto/CodeAssist` @ `d155772` (2026-11-11), Plugin-API-Version 4, SPI 3.5.0.
CodeForgeMobile: `main` nach 3.0.8. Grundlage sind die Quellen und die Doku beider Projekte
(CodeAssist: `docs/plugin-system.md`, `writing-plugins.md`, `extension-points.md`, `ui-extensibility-and-plugin-api.md`;
CodeForge: `docs/dev/PluginDevelopment.md`, `docs/extensions.md`, `libs/plugin-api`, `core/domain/.../extension`).
Nichts davon ist auf einem Gerät nachgeprüft.

## 1. Kurzfazit

**Ja – als Programmier-Plattform ist das System von CodeAssist deutlich mächtiger.** Dort *ist* die IDE eine Menge von
Plugins: Sprach-Backends, Build-Systeme, Analyse, Git und der KI-Agent hängen an denselben 48 typisierten
Extension Points, die auch ein fremdes Plugin nutzt. Ein Plugin kann Sprachen, Build-Schritte, Tests, Run-Konfigurationen,
Einstellungsseiten, Werkzeugfenster und eigene Compose-Oberflächen beitragen, Dienste anbieten und nutzen und
IDE-Ereignisse abonnieren.

**CodeForge ist in einem anderen Bereich stärker:** bei **deklarativen Erweiterungen ohne Code** (Themes, Icons,
Grammatiken, Snippets, Sprachserver, Formatierer, Werkzeuge, Projektvorlagen, MCP-Server, Agent-Tools) inklusive
**Download und Ausführung von Programmen** und Verteilung über den **Store**. Dort hat CodeAssist auf Android fast
nichts Vergleichbares.

Kurz: CodeAssist ist **erweiterbar wie IntelliJ**, CodeForge ist **erweiterbar wie VS Code ohne Extension-Host**.
Die Lücke liegt fast vollständig beim **Code-Plugin-API** von CodeForge: Befehle, zwei Menüs, fünf Ereignisse und eine
Meldung, mehr nicht.

## 2. Architektur im Überblick

| | **CodeAssist** | **CodeForgeMobile** |
|---|---|---|
| Grundidee | Eine Erweiterungsschicht; die eingebauten Funktionen sind selbst Plugins (`BuiltInPlugins.kt`) | App mit festen Funktionen; Plugins ergänzen über festgelegte Beitragsarten |
| Kern | `ExtensionPoint<T>` + `ExtensionRegistry` (typisiert, `Disposable` je Beitrag, `unregisterAll(pluginId)`) | `PluginManifestParser` → `ActiveContributions`; Code über `CodeForgePlugin.onLoad` |
| Dienste | `ServiceContainer` mit 3 Ebenen (Application/Workspace/Module), lazy, pro Plugin registrierbar | keine Plugin-Dienste; Host gibt `PluginHostServices` (Shell, LSP-Client) |
| Registry-Hierarchie | Projekt-Registry erbt von App-Registry | eine globale Liste |
| Ereignisse | `MessageBus` mit Topics (Editor, Build, Run, Analyse, Index, Projekt, VFS, Settings), eigene Topics für Plugin↔Plugin | 5 Ereignisse: `FileOpened/Saved/Closed`, `GitCommitted`, `GitBranchChanged` |
| Abhängigkeiten | `dependsOn` mit Topo-Sortierung, Zyklus-/Fehlt-Erkennung, Deaktivieren nimmt Abhängige mit | keine (nur `extensionPack` als Paket-Liste) |
| Fehlertoleranz | `loadAll(onError)`: Plugin wird zurückgerollt, Abhängige übersprungen, Grund in der Liste | fehlschlagendes `onLoad` → Registrierungen entfernt, Plugin bleibt installiert |
| UI | Engine-Facette + UI-Facette (`UiPlugin`): Werkzeugfenster, Screens, Overlays, Vorschau-Panes, Editor-Dekorationen | keine eigene UI; nur Befehle, Menüeinträge, `showMessage` |
| Laden | installierte **APK** per PackageManager, `PathClassLoader` (Parent = IDE) | **ZIP** mit `classes.dex` per `DexClassLoader`; deklarative Teile ohne Code |
| Aktivieren | **Neustart nötig** (einmal laden, kein Hot-Swap) | **sofort** (Schalter ruft `onLoad`/`onUnload`) |
| Manifest | TOML in `res/raw` der Plugin-APK; Host ignoriert `essential`/`trusted` aus dem Manifest | `plugin.json` im ZIP, streng validiert |
| API-Version | `apiVersion` 4 (Gate), `minHostVersion`, SPI-Bytecode-Prüfung (`spi-compat`) | `apiVersion` 1/2 (Gate) |
| Verteilung | Plugin-App installieren (Sideload/Store der APK) | Store (deklarativ, SHA-256 + Freigabeliste) und Sideload (auch mit Code) |

## 3. Was ein Plugin beitragen kann

### 3.1 CodeAssist: 48 Extension Points (Auszug nach Bereich)

| Bereich | Extension Points | Bedeutung |
|---|---|---|
| Plattform | `service`, `settingsPage` | eigene Dienste; Einstellungsseite aus typisierten Controls (Text, Schalter, Auswahl, Farbe, Tastenkürzel) |
| Projektmodell | `moduleType`, `fileIcon`, `projectTemplate`, `facetCodec`, `projectImporter`, `importContributor`, `buildFileWriter` | neue Modularten, Projektimport fremder Build-Systeme, Rückschreiben in Build-Dateien |
| Sprache | `languageBackend`, `fileType`, `completionContributor`, `completionWeigher`, `postfixTemplate`, `syntheticClass`, `index` | ganze Sprache (Parser, Resolver, Compiler), Vervollständigung samt Ranking, persistente Indizes |
| Analyse | `analyzer`, `diagnosticProvider`, `quickFixProvider`, `actionProvider`, `declarationProvider`, `navigationProvider`, `referenceProvider`, `renameProvider` | Inspektionen, Quick-Fixes, Intentions, Navigation, Find Usages, Rename – auch ohne LSP |
| LSP | `languageServer` | Server-Definition; der Host-Client macht daraus alle Editor-Funktionen |
| Editor | `editorDecoration`, `keyBinding`, `uiAction`, `actionGroup`, Editor-Vorschau-Panes | Markierungen/Gutter/Inlays in jeder Datei, Tastenkürzel, Aktionen in Toolbar/Menü/Palette/Alt-Enter |
| Build | `buildSystem`, `buildPlugin`, `runTaskProvider`, `sourceGenerator`, `testProvider`, `kotlinCompilerPlugin` | eigenes Build-System, Tasks in jeden Build-Graph, Run-Einträge, Code-Generatoren, Test-Runner mit Gutter-Marken |
| Sonstiges | `vcsProvider`, `iconRepository`, `blockMapping`, Agent-API | andere Versionskontrollen, Icon-Bibliotheken, Block-Editor, Werkzeuge für den KI-Agenten |

Dazu **Dienste aus dem Host**, die ein Plugin auflösen darf: `BUILD_CONTROL`, `SYMBOL_SEARCH`, `MODULE_SOURCES`,
`MODULE_ANALYSIS`, `MODULE_RESOURCES`, `SETTINGS_ACCESS` und `CODE_INTERPRETER` – der führt Kotlin-Quelltext des Projekts
ohne Kompilieren aus, damit ein Plugin z. B. eine Vorschau für ein Framework bauen kann, das die IDE nicht kennt.

### 3.2 CodeForge: 13 Beitragsarten + kleines Code-API

| Deklarativ (`contributes`) | Code (`CodeForgePlugin`) |
|---|---|
| `colorThemes`, `iconThemes`, `languagePacks`, `grammars`, `snippets` | `registerCommand(id, title, handler)` |
| `lspServers` (mit `${deps}`, `${jvmArgs}`, Arch-Downloads) | `registerMenuItem` – Ziele `EDITOR_MENU`, `FILE_CONTEXT_MENU` |
| `formatters`, `tools` (Skripte im PT-Menü, mit Dialog) | `addEventListener` – 5 Ereignisse |
| `requires` (`pkg`/`npm`/`pip`/`download` mit SHA-256, Arch) | `showMessage` |
| `projectTemplates` (Schichten, Platzhalter) | `buildShellCommand`, `homeDir`, `jdkInstallPath` (Berechtigung `shell`) |
| `agentTools`, `mcpServers`, `extensionPack` | `lspClientRepository` (Berechtigung `lsp`) |

Ein Code-Plugin bekommt **keinen** Zugriff auf Editor-Inhalt, Cursor, Auswahl, Diagnosen, Projektmodell, Build,
Einstellungsseiten oder UI. `CommandArgs` enthält nur `activeFilePath`.

## 4. Sprachserver im Detail

Hier ist der Vergleich am interessantesten, weil beide Projekte LSP als Hauptweg für fremde Sprachen nutzen.

| | **CodeAssist** | **CodeForge** |
|---|---|---|
| Deklaration | Kotlin-Code: `LanguageServerDefinition` (id, Sprachen, `start`, `rootFor`, `initializationOptions`, `configuration`) | JSON: `lspServers` (Kommando, Sprachen, Env, JVM-Optionen) |
| Server-Programm | **muss in der Plugin-APK liegen** (`jniLibs`, `nativeLibrary("…")`) – Android ≥ 10 verbietet `exec()` aus App-Speicher (targetSdk 36) | wird über `requires` **heruntergeladen** oder per `pkg`/`npm`/`pip` installiert und ausgeführt (Termux-Modell, **targetSdk 28**) |
| Server vom Nutzer | nur Desktop (Settings → Language Servers); auf Android nicht möglich | ja, in den LSP-Einstellungen |
| Server je Sprache | **einer je (Definition, Workspace-Root)**; `rootFor` sucht z. B. die nächste `pubspec.yaml` → mehrere Server parallel möglich | einer je Sprache (erster Treffer gewinnt) |
| Budgets | feste Zeitbudgets je Anfrage (1,5 s Handshake, 2,5 s Diagnosen, 3 s Completion …), Abbruch-Token | **keine**: `LspRpcConnection.sendRequest` wartet unbegrenzt auf die Antwort, kein `$/cancelRequest` |
| Absturz | Neustart bei der nächsten Anfrage; nach >3 Abstürzen in 3 min aus, Nutzer wird einmal informiert | kein automatischer Neustart; Sitzung wird manuell bzw. beim nächsten Öffnen neu gestartet |
| Performance | Antworten pro Dokumentversion gecacht, gleiche Anfragen zusammengelegt, langsame/große Antworten geloggt | – |
| Zusammenspiel | eigenes `LanguageBackend` hat Vorrang; der Server bedient nur den Rest | Server ist die einzige Quelle |

**Ergebnis:** CodeAssists LSP-Integration ist robuster und lässt **mehrere Server pro Projekt** zu (das, was du für
kmp-lsp + Kotlin LS gefragt hattest). Beim *Bereitstellen* der Server ist CodeForge klar vorn: CodeAssist muss jeden
Server als Binary in eine Plugin-APK packen, CodeForge lädt ihn deklarativ herunter.

## 5. Laden, Verteilung, Sicherheit

**CodeAssist**
* Plugin = eigene Android-App mit Marker-Activity (`dev.ide.codeassist.action.PLUGIN`) und Manifest in `res/raw`.
  Die IDE liest beim Start nur die Manifeste. Nur aktivierte und vom Nutzer **bestätigte** Plugins bekommen einen
  Classloader. Code kommt unverändert aus der installierten APK: kein Dex-Schritt, das Verzeichnis ist schreibgeschützt.
* Der Parent-Classloader ist der der IDE. SPI, Kotlin-Stdlib und **Compose** kommen damit immer aus der IDE, eine
  falsche Version im Plugin kann nichts überschreiben.
* Installation, Update und Entfernen werden über `PluginPackageWatcher` erkannt. Wirksam wird die Änderung erst nach
  einem **Neustart**, den der Plugins-Screen anbietet.
* **Capabilities** (`fs.read`, `fs.write`, `net`, `ui.toolWindow`, `lang.server` …) werden angezeigt und bestätigt,
  aber **nicht durchgesetzt**. Ein `PluginPermissionBroker` ist entworfen, existiert aber nicht. Das Plugin läuft im
  IDE-Prozess mit allen Rechten der App. Die Signatur wird erfasst, aber nicht ausgewertet.

**CodeForge**
* ZIP mit Zip-Slip-Schutz, strenger Manifest-Prüfung und schreibgeschütztem Dex (Android 14). Wirkt **sofort**, ohne Neustart.
* **Berechtigungen werden am API durchgesetzt** (`PluginPermissionException`). Das schützt aber nur die Host-API: Ein
  Dex-Plugin kann über normale Java/Android-APIs trotzdem alles, was die App darf. Ehrlich gesagt sind beide Systeme
  also gleich offen, sobald Code geladen wird.
* **Store:** nur deklarative Plugins, SHA-256 und Freigabeliste. Dafür gibt es bei CodeAssist kein Gegenstück.
* Kommandos laufen ohne Shell, Eingaben sind einzelne Argumente.

## 6. Entwickeln eines Plugins

| | **CodeAssist** | **CodeForge** |
|---|---|---|
| SPI | als Maven-Artefakte veröffentlicht (`io.github.tyron12233:plugin-bom`, 11 Module), `compileOnly`, GPL + Classpath-Exception | `libs/plugin-api` im Repo; Build per Gradle + d8 → `classes.dex` |
| ABI-Stabilität | `spi-compat`: vergleicht Bytecode-Signaturen mit Baselines; entfernte Member brechen den Build | keine automatische Prüfung |
| Im Gerät entwickeln | Projektvorlage „CodeAssist Plugin“, Manifest-Prüfung und -Vervollständigung im Editor | Vorlagen unter `templates/` (für den PC), statische Tests |
| Ausprobieren | **Plugin-Sandbox**: zweite IDE-Instanz in eigenem Prozess, Hot-Swap beim erneuten Ausführen, Absturz/ANR mit Stacktrace in der Haupt-IDE | installieren und ausprobieren |
| Logs | `reg.logger(tag)` → im Log-Viewer nach Plugin filterbar | über die eigene App-Logik |
| Tests | `PluginManager`/`PluginCatalog` ohne App testbar | Manifest-Validierung (`ExamplePluginsValidTest` …) |

## 7. Wo CodeForge vorn liegt

1. **Ohne Code erweiterbar:** 13 Beitragsarten, für die man kein Android SDK braucht und die statisch prüfbar sind.
   Bei CodeAssist braucht selbst ein Sprachserver eine kompilierte Plugin-App.
2. **Programme herunterladen und ausführen** (targetSdk 28): Sprachserver, Formatierer, Werkzeuge, JDKs. Das ist der
   größte praktische Vorteil auf Android. Der Preis: kein Play Store.
3. **Store mit Prüfsummen und Freigabeliste.**
4. **Sofort wirksam**, kein Neustart.
5. **Durchgesetzte Berechtigungen** für die Host-API (wenn auch keine echte Sandbox).
6. **Agent-Tools und MCP-Server** als deklarative Beiträge.

## 8. Wo CodeAssist vorn liegt – und was sich davon lohnt

Sortiert nach Nutzen für CodeForge. Aufwand grob: S (klein), M (mittel), L (groß).

| # | Idee aus CodeAssist | Nutzen für CodeForge | Aufwand |
|---|---|---|---|
| 1 | **Typisierte Extension-Point-Registry** in `core:domain`: `ExtensionPoint<T>`, `register(...) : Disposable`, Beiträge an `pluginId` gebunden, `unregisterAll`. Eingebaute Funktionen nutzen sie selbst (Quick-Fix-Quellen, Palette-Befehle, Datei-Icons, Projektvorlagen, Formatierer). | Basis für alles Weitere; Plugin-Code und App-Code laufen über denselben Weg | M |
| 2 | **Editor-Kontext für Code-Plugins**: Text, Cursor, Auswahl, Sprache; Rückgabe von Effekten (`ApplyEdits`, `MoveCaret`, `CreateFile`, `RenameFile`) statt direkter Schreibzugriffe | Editor-Aktionen wie „Zeile sortieren“, „Surround with“, Generatoren – heute unmöglich | M |
| 3 | **Plugin-Befehle in Palette, Refactoring-Menü und Quick-Fix**: `PaletteAction.Plugin` existiert schon; ergänzen um `actionProvider` (Intentions am Cursor) und `quickFixProvider` | Plugins tauchen dort auf, wo der Nutzer sucht | S |
| 4 | **Mehr Ereignisse (Message Bus)**: Editor aktiv/Auswahl, Build/Run gestartet/beendet, Diagnosen, Projekt geöffnet/geschlossen, LSP-Server-Status | reaktive Plugins (Format-on-Save, Build-Benachrichtigung, Statistiken) | S–M |
| 5 | **Mehrere Sprachserver je Sprache** und `rootFor` (Workspace-Root je Datei, z. B. nächstes `build.gradle.kts`/`pubspec.yaml`) | kmp-lsp + Kotlin LS parallel; Monorepos | M–L |
| 6 | **LSP-Robustheit**: Zeitlimit + `$/cancelRequest` (fehlt heute ganz – eine hängende Anfrage blockiert ihren Aufrufer), Budgets je Methode, Neustart-Politik (max. 3 Abstürze/3 min), Antwort-Cache je Dokumentversion, Zusammenlegen gleicher Anfragen, Perf-Log | spürbar flüssiger Editor bei langsamen Servern | M |
| 7 | **Deklarative Einstellungsseite** (`settingsPage` mit typisierten Controls) – bei uns sogar deklarativ in `plugin.json` möglich | Plugins konfigurierbar ohne eigene UI | S–M |
| 8 | **`dependsOn` zwischen Plugins** mit Topo-Sortierung; Deaktivieren nimmt Abhängige mit | gemeinsame Basis-Plugins (z. B. JDK-Plugin für alle JVM-Server) | S |
| 9 | **Fortschritt, Benachrichtigung, Rückfrage** für Code-Plugins (fehlt auch CodeAssist noch) | lange Installationen sichtbar | S |
| 10 | **Plugin-Logger** mit Filter im Log-Viewer | Fehlersuche für Plugin-Autoren | S |
| 11 | **ABI-Prüfung** des `plugin-api` (Kotlin Binary Compatibility Validator, `apiDump`/`apiCheck`) | kein `NoSuchMethodError` bei alten Plugins | S |
| 12 | **Compose-UI-Facette** (Werkzeugfenster, Screen, Panel im Drawer). Geht auch mit `DexClassLoader`, solange Parent = App-Classloader, damit Compose aus der App kommt | eigene Oberflächen für Plugins | L |
| 13 | **Fremdsprachen ohne LSP**: `referenceProvider`/`renameProvider`/`navigationProvider`/`completionContributor` als Plugin-API | Sprachen per Kotlin-Code statt Server | L |
| 14 | **Test-Provider und Run-Einträge** (`testProvider`, `runTaskProvider`) | Gutter-Run-Marken, Tests-Tab für Python/Rust/Go | L |

**Nicht übernehmen:**
* **Plugin als installierte APK:** Das passt nicht zu Store und ZIP. Und bei targetSdk 28 brauchen wir die APK nicht,
  um Programme ausführen zu dürfen.
* **Neustart-Pflicht:** Unser sofortiges Aktivieren ist ein Vorteil. Mit einer Registry, in der jeder Beitrag ein
  `Disposable` liefert (Idee 1), bleibt das sauber.
* **„Die ganze IDE als Plugins“:** CodeAssist hat diesen Umbau als Grundsatzentscheidung gemacht. Für CodeForge reicht
  es, die *erweiterbaren* Stellen über die Registry zu führen, statt alles umzubauen.

## 9. Empfohlene Reihenfolge

1. **Registry + Disposable + Logger + `dependsOn`** (1, 8, 10, 11) – Fundament, kein sichtbares Feature.
2. **Editor-Kontext + Aktionen in Palette/Refactoring/Quick-Fix + mehr Ereignisse** (2, 3, 4) – macht Code-Plugins
   sofort deutlich nützlicher und baut auf der neuen Befehlspalette auf.
3. **LSP: mehrere Server, `rootFor`, Robustheit** (5, 6) – unabhängig vom Plugin-API, aber der größte Gewinn im Alltag.
4. **Deklarative Einstellungsseiten** (7).
5. **Compose-UI-Facette** (12) und danach **Sprach-/Test-/Run-APIs** (13, 14).

Punkt 1 und 2 zusammen bringen das Code-Plugin-API von CodeForge ungefähr auf den Stand der
„portablen Aktions-Ebene“ von CodeAssist. Unsere deklarativen Stärken bleiben dabei erhalten.
