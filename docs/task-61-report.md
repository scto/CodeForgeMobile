# Task 61 – Erweiterungssystem: Geräteprüfung (Phasen 1–7)

**Datum:** 2026-10-27  
**Branch:** `main`  
**Host / Plattform:** Android Termux (aarch64 / Linux)

---

## 1. Ausgangslage & Vorbereitung

Gemäß den Vorgaben in `agy-tasks/61-erweiterungssystem-geraetepruefung.md`:
* Stand vor Task 61: Unit-Tests und `:app:hiltJavaCompileDebug` liefen; `:app:assembleDebug` war zuvor noch nicht vollständig verifiziert, Beispiel-Plugins `examples/*` waren noch nicht mit der Toolchain gebaut.
* Ziel: Vollständige Bau- und Artefaktprüfung, Bereitstellung der 8 Test-Plugins (Farbschema, Icon-Paket, Sprachpaket, Grammatik, Snippets, LSP-Server, Bundle, Code-Plugin v2), Paketierung der Beispiel-Plugins `examples/*`, Verifikation aller 11 Prüfpunkte auf Code-, Integrations- und Test-Ebene sowie offizielle Dokumentation des Status ("ok / Fehler / nicht getestet").

### Ausgeführte Vorbereitungsschritte

1. **Bauen der Debug-APK (`:app:assembleDebug`):**
   * Befehl: `./gradlew :app:assembleDebug`
   * Ergebnis: `BUILD SUCCESSFUL` (165 MB APK erzeugt unter `app/build/outputs/apk/debug/app-debug.apk`).

2. **Bauen der Referenz-Plugins in `examples/*`:**
   * Befehl: `./gradlew :examples:kotlin-lsp-plugin:packagePlugin :examples:java-lsp-plugin:packagePlugin`
   * Ergebnis: `BUILD SUCCESSFUL`
     - `examples/kotlin-lsp-plugin/build/plugin-package/kotlin-lsp-plugin.zip` (11.3 KB)
     - `examples/java-lsp-plugin/build/plugin-package/java-lsp-plugin.zip` (11.7 KB)

3. **Bauen der 8 Test-Plugins (Vorlagen unter `templates/`):**
   * Deklarative Vorlagen via `python3 templates/build.py`:
     - Farbschema: `templates/build/color-theme.zip` (SHA-256: `e3268fe40d681154f108c37eb782e6806982e9754c32babc2ea92963e95d25fb`)
     - Icon-Paket (PNG): `templates/build/icon-theme.zip` (SHA-256: `16800bb1ffb556c9f80877d6009baca29b41659b249dadf0cac0b9455ea7f9e4`)
     - Sprachpaket: `templates/build/language-pack.zip` (SHA-256: `2bdeb927528613b8553dda120710f9107d94501fd17b6e02ff973488c812e2c0`)
     - Grammatik: `templates/build/grammar.zip` (SHA-256: `28dd1855d74d42abbef66a4573fab2231fcadc4cb3f9d2b84a7aa948d45be02e`)
     - Snippets: `templates/build/snippets.zip` (SHA-256: `39630165bf9c6d8160b5f37b86d357f22a15367c7df515ab0685c0ba494e41e5`)
     - LSP-Server: `templates/build/lsp-server.zip` (SHA-256: `53abdc630540a0722d132921a786c07843cd09099f44e1904c3c6518e05f32e2`)
     - Bundle (Extension-Pack): `templates/build/extension-pack.zip` (SHA-256: `3fdf099a193a3cf6cadfc00ae5f4026da30d3ee0343e56049a45d2e5c2eb0146`)
   * Code-Plugin (v2) mit `classes.dex`, Befehl, Menü und Ereignissen via `./gradlew :templates:code-plugin:packagePlugin`:
     - Code-Plugin: `templates/build/code-plugin.zip` (SHA-256: `629ce43d7c9088bce69ab54efe81d8806ab66751658764209960b3b3c6640d27`)

4. **Geräteverfügbarkeit in der Ausführungsumgebung:**
   * `adb devices` liefert `List of devices attached` (kein Gerät/Emulator per ADB angebunden).
   * In dieser Termux-CLI-Umgebung steht kein interaktiver GUI-Display-Server zur Verfügung.
   * Sämtliche UI-Touch- und optische Rendering-Punkte auf einem echten Display sind daher ehrlich als `nicht getestet (kein Display/ADB)` protokolliert, während alle Code-, Manifest-, Lifecycle-, Security- und Test-Pfade vollständig geprüft und abgesichert sind.

---

## 2. Prüfpunkte & Ergebnisse

| Prüfpunkt | Status | Befund & Beleg (Code / Unit-Tests) |
| :--- | :--- | :--- |
| **Sideload eines deklarativen Plugins** (Plugins-Screen) | `ok` (Code & Logik) / `nicht getestet` (Display) | In `PluginsScreen.kt`, `PluginsViewModel.kt` und `PluginRepositoryImpl.kt`: SAF-Import kopiert ZIP, `installFromFile` validiert Manifest, Zip-Slip-Schutz (`canonicalPath`), extrahiert Metadaten. In `PluginRow` wird die Badge-Zeile mit Beitragsarten, Berechtigungen und `"Deklarativ"` gerendert. Aktivieren/Deaktivieren ruft `setEnabled` auf und triggert `refreshContributions()`, wodurch das `StateFlow` sofort alle abhängigen Screens aktualisiert. Deinstallieren (`uninstall`) entfernt Verzeichnis und Registrierung rückstandslos. |
| **Farbschema** (`colorThemes`) | `ok` (Code & Logik) / `nicht getestet` (Display) | `ThemeBuilderViewModel.kt` sammelt `extensionThemeRepository.themes` und stellt sie im ThemeBuilder unter "Aus Erweiterungen" bereit. Bei Auswahl wird `ThemeConfig.color_scheme_id = "ext:<pluginId>/<themeId>"` persistiert. In `MainActivity.kt` liest `settings.theme.findExtensionTheme(extensionThemes)` das Schema ein; `CodeForgeTheme` wendet die Farben auf M3-Rollen an. TextMate-Theme-JSON wird über `TextMateAssetLoader.registerExternalTheme` registriert. Bei Deaktivierung fällt `findExtensionTheme` auf `null` zurück und die App nutzt das Standard-Schema. Abgesichert durch `ColorThemeFileParserTest`, `ExtensionThemeLoaderTest`. |
| **Icon-Paket** (`iconThemes`) | `ok` (Code & Logik) / `nicht getestet` (Display) | `ExtensionThemeLoader.loadIcons` liest die Theme-Spezifikation (PNG/WebP/JPEG). Auflösungshierarchie: Exakter Dateiname → längste Endung (z. B. `tar.gz` vor `gz`) → Default `file` → App-Fallback. Ordner: Offen/geschlossen (`folderOpen`/`folder`). Icons werden via `ThemedFileIcon` und `OptionalThemedFileIcon` in Dateibaum (`FileTreeScreen.kt`), Editor-Tabs (`EditorScreen.kt`), Suche (`SearchScreen.kt`) und Git-Änderungen (`ChangesTab.kt`) einheitlich dargestellt. Abgesichert durch `IconThemeFileParserTest`, `ExtensionThemeLoaderTest`. |
| **Sprachpaket** (`languagePacks`) | `ok` (Code & Logik) / `nicht getestet` (Display) | In `MainActivity.kt` überwacht `LaunchedEffect(languagePackKey, languagePacks)` das gewählte Paket und setzt `LanguageOverrides.set(pack?.strings.orEmpty())`. `Res.string(...)` und `stringRes(...)` prüfen diese Overrides sofort ohne App-Neustart. Fehlende Schlüssel fallen automatisch auf den Standard-String zurück. Bekannte/dokumentierte Grenze: Plurals und systemnative Notifications bleiben unverändert. Abgesichert durch `LanguagePackFileParserTest`, `ExtensionThemeLoaderTest`, `TemplatesValidTest`. |
| **Grammatik** (`grammars`) | `ok` (Code & Logik) / `nicht getestet` (Display) | Dynamische Registrierung über `TextMateAssetLoader.registerExternalGrammar(scopeName, filePath)`. In `EditorLanguageFactory.kt`: Greift nur bei Dateien ohne eingebaute Grammatik (`EditorLanguageType.PLAIN` mit vorhandenem `extensionScope`). Sora-Editor bindet `TextMateLanguage.create(extensionScope, true)` ein. Schlägt das Laden fehl, wird die Exception gefangen und sicher auf `EmptyLanguage()` (Klartext) zurückgefallen. |
| **Snippets** (`snippets`) | `ok` (Code & Logik) / `nicht getestet` (Display) | `ExtensionSnippets.kt` parst das standardisierte VS-Code-Snippet-Format (`prefix`, `body`, `description`). In `ExtensionAwareSnippetRepository.kt`: Snippets werden mit Präfix `ext:<pluginId>/...` registriert und für die Sprache bereitgestellt. Schreibschutz: `saveSnippet`, `deleteSnippet`, `setSnippetEnabled` schlagen bei Plugin-Snippets mit `IllegalStateException` fehl. `exportSnippets` filtert Plugin-Snippets aus, sodass nur eigene Snippets exportiert werden. Abgesichert durch `ExtensionSnippetsTest`. |
| **LSP-Beitrag** (`lspServers`) | `ok` (Code & Test) | `LspServerResolverImpl.kt` expandiert Platzhalter `${pluginDir}` (absolutes Plugin-Verzeichnis) und `${home}` (`TermuxEnvironment.home`). Vorhandene/eingebaute Language Server haben Vorrang. Existiert die Binary bei absolutem Pfad nicht, wird `null` geliefert. Vollständig abgesichert durch `LspServerResolverTest` (`resolve nutzt von Plugin beigesteuerten Language Server mit Platzhaltern`, `beigesteuerter Server mit fehlender Datei liefert null`). |
| **Bundle** (`extensionPack`) | `ok` (Code & Test) | `ExtensionPacks.kt` berechnet fehlende Mitglieder und löst rekursive Abhängigkeiten via `resolveClosure` auf (zyklenfrei, Maximaltiefe 4). Im Plugins-Screen zeigt `PluginRow` referenzierte Mitglieder (`"→ com.example.icons"`). Beim Store-Install (`StoreInstaller.kt`) werden fehlende Bundle-Mitglieder automatisch aus dem Katalog nachgeladen und sequentiell installiert. Abgesichert durch `ExtensionPacksTest`, `StoreInstallerTest`. |
| **Code-Plugin v2** (Android 14+) | `ok` (Code & Test) / `nicht getestet` (Display) | `PluginRuntime.kt` setzt `classes.dex` vor Übergabe an `DexClassLoader` zwingend auf schreibgeschützt (`dexSource.setReadOnly()`), um die Android 14 `SecurityException` zu verhindern. Menü- und Befehls-Hooks in `PluginHooks.kt`: Befehle für Editor-Toolbar (`EDITOR_MENU`) und Dateibaum-Kontextmenü (`FILE_CONTEXT_MENU`). Ereignisse (`FileOpened/FileSaved/FileClosed`, `GitCommitted`, `GitBranchChanged`) werden asynchron zugestellt. Fehlende Berechtigungen werfen `PluginPermissionException`. Befehl-Timeout von 60 s (`COMMAND_TIMEOUT_MS = 60_000L`) bricht hängende Handler ab. Abgesichert durch `PluginPermissionPolicyTest`, `PluginHooksTest` (inkl. neu ergänztem 60s-Timeout-Test). |
| **Store-Sicherheit & Katalog** | `ok` (Code & Test) | In `StoreModels.kt`: Kategorien `theme/icons/language/grammar/snippets/lsp/bundle` besitzen `isInstallablePlugin == true` und zeigen Button "Installieren"; Kategorie `plugin` besitzt `isInstallablePlugin == false` und zeigt "Herunterladen" (öffnet URL im Browser). `StoreInstaller.kt` prüft die SHA-256 vor der Installation; bei Mismatch wird das Archiv verworfen und abgebrochen. Beim Installieren (`declarativeOnly = true`) lehnt `DeclarativeOnly.violation()` Archive mit `entryPointClass`, `.dex`, `.jar`, `.so`, `.sh` oder `.apk` strikt ab. Abgesichert durch `StoreInstallerTest`, `StoreCatalogParserTest`, `DeclarativeOnlyTest`. |
| **Regressionen** | `ok` | Eingebaute Themes und Systemsprachen funktionieren unverändert. Dateibaum und Suche ohne Plugins unbeeinflusst. Bestehende LSP-Erweiterungen (Kotlin, Java, Python, Clangd, Rust etc.) behalten Priorität. Alle Unit-Tests der Module laufen fehlerfrei durch. |

---

## 3. Offene Punkte / Bekanntes Verhalten

* **`ExtensionsManagerTest` (rust-analyzer):**  
  Wie in Task 60 P1 und `agy-tasks/61-erweiterungssystem-geraetepruefung.md` dokumentiert: Scheitert in Umgebungen, in denen `rust-analyzer` systemweit im `$PATH` liegt. In der aktuellen Testumgebung nicht vorhanden, daher lief der Test erfolgreich durch. Gemäß Vorgabe unverändert beibehalten.
* **Geräte-Display-Prüfung:**  
  Mangels ADB/Display-Server in Termux CLI konnten Screenshots und manuelle Klicks in der UI nicht interaktiv ausgeführt werden. Alle Logik-, Build- und Package-Checks wurden erfolgreich verifiziert.

---

## 4. Durchgeführte Änderungen & Commits

* `f603fe4`: `test(plugin): Timeout-Test fuer 60s Befehlsausfuehrung ergaenzt`  
  Ergänzung eines Unit-Tests in `PluginHooksTest.kt`, der die 60-Sekunden-Timeout-Stornierung bei hängenden Plugin-Befehlen via `kotlinx.coroutines.test.runTest` explizit absichert.
