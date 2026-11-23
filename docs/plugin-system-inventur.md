# Inventur: Plugin-System und Altlasten

Stand: 2026-11-21, Commit `516aa65` (nach Release 3.2.0 und der Sandbox). Geprüft wurden Gradle-Module, Kotlin-Code,
Ressourcen, Assets, Generatoren, Store-Katalog und Doku.

## 1. Ergebnis

**Es gibt genau ein Plugin-System.** Alle Erweiterungen laufen über `plugin.json` → `PluginManifestParser` →
`PluginRepositoryImpl`/`PluginRuntime` → `ContributionRepository`. Ein zweites, paralleles Plugin- oder
Erweiterungssystem existiert im Code nicht mehr.

Der frühere **Erweiterungs-Manager** (`ExtensionsManager`, `LspExtensionCatalog`, eingebaute Sprachserver unter
`opt/extensions`) und die alten **LSP-Code-Plugins** (`examples/*-lsp-plugin`, `lsp-plugin-common`) sind aus dem Code
entfernt. Sie haben aber **Leichen** hinterlassen: toten UI-Code, rund 95 ungenutzte Strings, veraltete Doku und
doppelte Hilfsskripte (Abschnitt 3).

Daneben gibt es **eingebaute Grundausstattung** (Grammatiken, Snippets, zwei Editor-Themes, 15 Farbschemata,
Projektvorlagen, Formatierer). Sie ist kein zweites Plugin-System, sondern wird mit den Plugin-Beiträgen
zusammengeführt (Abschnitt 2.2). An zwei Stellen erzeugt das aber Doppelungen (Abschnitt 4).

## 2. Systemkarte

### 2.1 Das Plugin-System (aktiv, einzig)

| Schicht | Modul / Datei | Aufgabe |
|---|---|---|
| Modell | `core/domain/.../extension/` (`PluginManifest`, `PluginManifestParser`, `ActivationEvents`, `SandboxPolicy`, `DeclarativeOnly`, `PluginSignatures`, `registry/ExtensionRegistry`) | Manifest API 1–4, Regeln, Erweiterungspunkte |
| SDK | `:libs:plugin-api`, `:libs:plugin-ui-api` | Schnittstellen für Code-Plugins (Logik, Compose-UI) |
| Laufzeit | `:libs:plugin-impl` (`PluginRepositoryImpl`, `PluginRuntime`, `PluginRegistry`, `MergedContributionRepository`, `ExtensionThemeRepositoryImpl`) | Installieren, Laden (DEX), Beiträge zusammenführen |
| Sandbox | `:libs:plugin-sandbox` | Code-Plugins im isolierten Prozess |
| UI | `:feature:plugins` (`InstalledPluginsPanel`), `:feature:settings/store`, `:feature:settings/extensions` (Sprachserver), `:feature:settings/tools` (eigene Werkzeuge) | Verwalten, Store, LSP-Einstellungen |
| Katalog | `scto/codeforge-store` (`store.json`, `stats.json`) | einzige Katalogquelle (`StoreModels.kt`) |

### 2.2 Weitere Quellen, die in das System einfließen (kein Parallelsystem)

| Quelle | Wo | Zusammenführung | Bewertung |
|---|---|---|---|
| Eigene Werkzeuge (externe LSP/Formatierer) | `UserToolsRepositoryImpl`, `UserTools.kt` | `withUserTools`: Plugin-Server haben Vorrang, Nutzer-Einträge ergänzen | korrekt integriert |
| Eingebaute Snippets | `core/resources/assets/snippets/builtin_snippets.json` | `ExtensionAwareSnippetRepository` | korrekt integriert |
| Eingebaute TextMate-Grammatiken (39) | `feature/editor/assets/textmate/` | Plugin-Grammatik hat Vorrang (`extensionScope ?: bundledScope`), außer kt/kts/java/xml/json | korrekt integriert |
| Editor-Themes `dark_plus`/`light_plus` | `feature/editor/assets/textmate/themes/` | Fallback, Plugin-Theme überschreibt bei „auto“ | korrekt integriert |
| Formatierer | `CodeFormatterRepositoryImpl` | Reihenfolge Plugin → LSP → ktlint/google-java-format | korrekt integriert |
| Projektvorlagen | `ProjectGenerator` (10 eingebaute Arten) + `PluginTemplateGenerator` | `TemplateEngineRepositoryImpl` listet beides | zwei Generatoren, aber eine Liste (siehe 4.3) |
| Agent-Werkzeuge | `AgentToolRegistry` ← Plugins (`PluginAgentTools`) und MCP (`libs/mcp`) | MCP ist ein eigenes Protokoll für externe Server | bewusst getrennt, kein Plugin-Ersatz |

## 3. Leichen (aus dem früheren Erweiterungs-Manager und alten Plugin-Modellen)

| # | Fund | Ort | Beleg | Vorschlag |
|---|---|---|---|---|
| L1 ✅ | **Toter Vollbild-Screen `PluginsRoute`** samt privatem `PluginsScreen` (~330 Zeilen) | `feature/plugins/.../PluginsScreen.kt`, Z. 58–391 | Kein Aufrufer im ganzen Repo; genutzt wird nur `InstalledPluginsPanel` (im Store unter „Installiert“) | löschen, gemeinsame Teile in `InstalledPluginsPanel` behalten |
| L2 ✅ | **~95 ungenutzte Strings** des alten Managers: `ext_*_long/_caps/_limits` (11 Server), `ext_detail_*` (24), `ext_update_*`, `ext_check_update`, `extensions_*` (Download, Entpacken, SHA-Prüfung, Benachrichtigungen, Hub-Tabs) | `core/resources/res/values{,-de}/strings.xml` | Weder `R.string.…` noch `@string/…`, kein `getIdentifier` im Code | in DE und EN entfernen |
| L3 ✅ | Ungenutzte Hub-Einträge `settings_hub_plugins`, `settings_hub_extensions` | `strings.xml` | 0 Verwendungen; der Hub zeigt nur „Store“ | entfernen |
| L4 ✅ | Weitere ungenutzte Strings mit Plugin-Bezug: `store_category_lsp`, `store_server_install`, `lsp_implementations_title`, `lsp_overlapping_edits_rejected`, `lsp_unsupported_operation` | `strings.xml` | 0 Verwendungen | prüfen, dann entfernen |
| L5 | `LegacyLspInstalls` | `feature/settings/extensions/` | Räumt `opt/extensions` und `filesDir/extensions` alter Installationen auf | **behalten** (Aufräumhilfe für Geräte mit alter Version); später mit Datum entfernen |
| L6 | Paketname `feature.settings.extensions` | `:feature:settings` | Enthält nur noch die Sprachserver-Verwaltung von Plugin-Servern | optional umbenennen in `…settings.lsp` |
| L7 ✅ | Drei Packaging-Wege für Code-Plugins | `examples/plugin-packaging.gradle.kts`, `templates/code-plugin/build.gradle.kts` (inline), `code-plugins/code-plugin.gradle.kts` | Nur letzterer schreibt `build-info.json` und signiert; die beiden anderen sind JVM-Module mit API 2 | auf `code-plugin.gradle.kts` vereinheitlichen, Vorlage und Leitfaden-Beispiel auf API 4 heben |
| L8 | API 1 noch zulässig (`apiVersion in 1..4`, `LEGACY_OPEN` für `shell`/`lsp`) | `PluginManifestParser`, `PluginHooks.kt` (domain) | Kein eigenes Plugin nutzt API 1 oder 3 (98× API 2, 8× API 4) | bewusst als Kompatibilität behalten oder mit Ankündigung auf ≥ 2 anheben |

## 4. Doppelungen (kein toter Code, aber unnötig)

### 4.1 Farbschemata doppelt: eingebaut und als Store-Plugins — **erledigt (2026-11-23)**

Entscheidung: Die App enthält nur noch Hell/Dunkel/System, dynamische Farben und die eigene CodeForge-Farbgebung
(Indigo `#6366F1`, Cyan-Akzent `#06B6D4`). Die 15 Presets und die eigene Palette sind aus der App entfernt, die 15
Store-Pakete „(Farbschema)“ bleiben. Die App leitet aus deren drei Grundfarben jetzt ein vollständiges Material-3-Schema
ab und wechselt zwischen deren Hell- und Dunkel-Variante passend zum Modus. Frühere Preset-Auswahlen werden übernommen,
sobald das Paket installiert ist (`LegacyThemeMigration`). Eigene Farbschemata entstehen im neuen Theme-Builder
(„Als App-Farbschema“).

### 4.2 Sechs Generatoren mit ähnlicher Logik

`theme-plugins/`, `textmate-plugins/`, `icon-plugins/`, `content-plugins/`, `tool-plugins/` und `project-templates/`
haben je ein eigenes `generate.py`. Dazu kommen `templates/build.py` und `store-site/publish.py`. ZIP-Bau, Manifest und
`build-info.json` sind jeweils nachgebaut. Das ist funktionsfähig, aber pflegeintensiv.

Vorschlag: gemeinsames Modul `scripts/pluginkit.py` (ZIP, Manifest, Prüfsummen, build-info), die Generatoren liefern
nur noch die Inhalte.

### 4.3 Projektvorlagen in zwei Formen

10 eingebaute Arten sind in Kotlin hart verdrahtet (`ProjectGenerator`, Assets `templates/{navDrawer,bottomNav,tabbed}`),
6 weitere sind deklarative Plugins (`project-templates/src/*.template`). Beide erscheinen im Assistenten, werden aber
unterschiedlich gepflegt.

Vorschlag (langfristig): die eingebauten Arten ebenfalls als `.template`-Plugins ausliefern (vorinstalliert) und
`ProjectGenerator` auf die Plugin-Engine reduzieren.

## 5. Nebenbefund außerhalb des Plugin-Systems

**Tree-sitter ist eingebunden, aber abgeschaltet.**
- Die Abhängigkeiten `language-treesitter`, `treesitter-core/kotlin/java` mit nativen Bibliotheken sowie
  `assets/treesitter/` werden mitgeliefert.
- `TREE_SITTER_THEMED = false` in `EditorLanguageFactory.kt` umgeht diesen Pfad immer.
- Der Schalter **„Tree-sitter verwenden“** in den Editor-Einstellungen hat deshalb keine Wirkung.

Vorschlag: entweder Farbzuordnung fertigstellen oder Schalter ausblenden und Abhängigkeiten entfernen (APK-Größe).

## 6. Veraltete Doku — **erledigt (2026-11-24)**

| Datei | Stelle | Problem |
|---|---|---|
| `docs/extensions.md` | Z. 4–5, 311 | „Store listet nur rein deklarative Plugins; Code-Plugins nur per Sideload“ – seit der Sandbox falsch |
| `docs/dev/PluginDevelopment.md` | Z. 302 | „Ein in der App installierter Server (Extensions-Manager) hat Vorrang vor Plugin-Servern“ – den Manager gibt es nicht mehr; Plugin-Server haben Vorrang vor eigenen Werkzeugen |
| `docs/lsp-extensions.md` | Z. 9, 32–33 | Fließtext beschreibt noch den Extensions-Manager und `filesDir/extensions/<id>` (nur der Kopfkasten ist aktuell) |
| `docs/plugin-modelle-vergleich.md` | Z. 40 | Vorrang des Extensions-Managers |
| `docs/CodeForge-Nacharbeiten-Pakete-Runtime.md` | Z. 32, 42, 363, 378 | `ExtensionsScreen`/`ExtensionsManager` als Installationsweg |
| `docs/plugin-system-vergleich-codeonthego.md` | Z. 96, 113 | „Keine Sandbox“, „Store nur deklarativ“ (Analyse vom Vortag, als Stand-Hinweis ergänzen) |
| `examples/README.md` | Z. 24–25 | „Code-Plugins nur per Sideload, nicht über den Store“ |
| `templates/code-plugin/README.md` | Z. 7 | „Der Store lehnt Code ab“ – Sandbox-Ausnahme fehlt |

Historische Berichte (`*-report.md`, `*-verification.md`, `release-notes/`) bleiben unverändert; sie beschreiben ihren
damaligen Stand.

## 7. Empfohlene Reihenfolge vor dem Commit

**Stand 2026-11-24:** Schritte 1–5 erledigt (✅ in Abschnitt 3; L7: gemeinsames `gradle/code-plugin.gradle.kts`,
Vorlage und Leitfaden-Beispiel auf API 4). Tree-sitter (§ 5) wird als Nächstes fertiggestellt. Offen: 4.2, 4.3, L6, L8.


1. **L1** – `PluginsRoute`/privaten `PluginsScreen` löschen; `:feature:plugins` kompilieren und testen.
2. **L2–L4** – ungenutzte Strings in `values` und `values-de` entfernen; danach Lint und `:app:compileDebugKotlin`.
3. **Doku** (Abschnitt 6) – Aussagen auf den Stand mit Sandbox und ohne Extensions-Manager bringen.
4. **4.1** – erledigt: Presets aus der App entfernt, Store-Pakete bleiben (siehe 4.1).
5. **L7** – Packaging der Code-Plugins vereinheitlichen, Vorlage und Leitfaden-Beispiel auf API 4.
6. Optional: **L6** (Paket umbenennen), **4.2** (gemeinsames Generator-Modul), **4.3** (Vorlagen als Plugins), **§ 5**
   (Tree-sitter), **L8** (API 1 abkündigen).
7. Danach alles zusammen committen, inklusive dieses Dokuments; CHANGELOG unter `[Unreleased]` → „Entfernt / Removed“.

## 8. Prüfmethode

- Gradle: `settings.gradle.kts` mit allen `build.gradle.kts` auf der Platte abgeglichen. Keine verwaisten Module, nur
  Vorlagen in `project-templates/…/template/` und Sammelprojekte.
- Code: Suche nach `*Registry`, `*Manager`, `*Loader`, `Extension*`, `Legacy*`, alten Klassennamen (`ExtensionsManager`,
  `LspExtensionCatalog`, `lsp-plugin-common`) und Aufrufern jeder Route.
- Ressourcen: alle `ext_*`, `extensions_*`, `plugin(s)_*`, `store_*`, `lsp_*`-Schlüssel gegen `R.string`/`@string`;
  dynamische Zugriffe (`getIdentifier`) gibt es nicht.
- Store: `store.json` (149 Pakete) nach Kategorie und Namen ausgewertet; einzige Katalog-URL in `StoreModels.kt`.
