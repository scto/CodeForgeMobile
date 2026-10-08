# Modul-Pflichtdateien und `:app`-Ressourcen

Stand: 2026-10-07. Nicht gebaut (kein Gradle/Android-SDK); XML wohlgeformt (xmllint), Manifest-Verweise gegen vorhandene Ressourcen geprüft.

## Pflichtdateien pro Modul

Alle 43 Module aus `settings.gradle.kts` haben jetzt `build.gradle.kts` und `.gitignore` (`/build`).
Android-Module (App + Bibliotheken) zusätzlich `src/main/AndroidManifest.xml`, `consumer-rules.pro`, `proguard-rules.pro`.
Reine JVM-Module (`:libs:*-api`, `:libs:code-tools`, `:examples:*`) brauchen kein Manifest und keine Proguard-Dateien.

* Die Convention-Plugins verdrahten `consumer-rules.pro` immer (`consumerProguardFiles`), `proguard-rules.pro` nur, wenn die Datei existiert.
* Neu angelegte Manifeste (leer, Namespace steht in Gradle): `:feature:dependencyupdates`, `:libs:indexing-impl`, `:libs:dependency-updater-impl`, `:libs:termux-emulator`.
* Inhaltliche Consumer-Regeln (nur wo R8 sonst Funktionen zerstören würde):

| Modul | Regel | Grund |
|---|---|---|
| `:libs:plugin-api` | `keep` der Plugin-Schnittstellen | Plugins werden per DexClassLoader gegen sie gebunden |
| `:libs:termux-emulator` / `-view` / `-shared` / `-app` | `keep` JNI-Klassen, native Methoden | C-Code ruft per Name |
| `:libs:gradle-tooling-bridge` | `keep` Bridge-Paket | Binder-Callbacks im eigenen Prozess |
| `:libs:lsp-client` | `keepclassmembers` Felder | JSON-Reflection |
| `:core:datastore` | `keep` `GeneratedMessageLite` | Proto-DataStore |
| `:core:data` | `keep` JGit, `dontwarn` | Reflection/Resource-Bundles |
| übrige | leer, mit Kommentar | — |

R8 ist in keinem Build-Typ aktiviert (`isMinifyEnabled` wird pro Modul gesteuert). Die Regeln sind **ungetestet**; vor dem Aktivieren
von Minify einen Release-Build samt Smoke-Test durchführen.

## `:app`-Ressourcen (vorher: keine)

Das Manifest verwies auf `@style/Theme.CodeForge` und `@mipmap/ic_launcher`, die es nicht gab (Build-Abbruch). Neu:

* `values/themes.xml` (+ `values-v28`, `values-v29`): `Theme.CodeForge` auf Basis `Theme.Material3.DayNight.NoActionBar`
  (dafür `implementation(libs.google.material)`), transparente Systemleisten, `windowLayoutInDisplayCutoutMode=shortEdges`,
  Kontrast-Scrims aus (API 29+). Eigentliche UI bleibt Compose.
* `values/colors.xml`, `values-night/colors.xml`: Fensterhintergrund (kein Weißblitz im Dunkelmodus), Icon-Farben.
* `mipmap-anydpi-v26/ic_launcher(.xml|_round.xml)` + `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (Themed Icons). Eigenentwurf; bei minSdk 26 sind keine PNG-Fallbacks nötig.
* `xml/backup_rules.xml`, `xml/data_extraction_rules.xml`: schließen Termux-Prefix/Home (`files/usr`, `files/home`) und die verschlüsselten Git-Zugangsdaten (`codeforge_git.xml`, Keystore-gebunden) aus dem Backup aus.
* Manifest: Label `@string/app_name` (steht zentral in `:core:resources`), `roundIcon`, `dataExtractionRules`, `fullBackupContent`, `supportsRtl`, `enableOnBackInvokedCallback` (Predictive Back),
  `MainActivity` mit `configChanges` (Drehen/Falten/Größenänderung ohne Activity-Neustart – wichtig für Terminal und Editor) und `windowSoftInputMode=adjustResize`.

## Weitere Prüfungen

* `file_provider_paths`: vorhanden (`:feature:editor/res/xml/editor_file_provider_paths.xml`, im Modul-Manifest referenziert). Der Veraltet-Kommentar („Rootfs“) ist korrigiert. Hinweis: `files-path path="."` gibt das gesamte `filesDir` frei (inkl. Termux-Prefix) – nur für Dateien nutzen, die der Editor bewusst teilt.
* Offene Punkte: `compileSdk 36` braucht die SDK-Plattform 36 auf dem Build-Rechner; `:feature:layoutdesigner` ist inzwischen umgesetzt (siehe `docs/layout-designer.md`).
* Edge-to-Edge im Code (`enableEdgeToEdge()`, Insets) und Adaptive Layouts sind der nächste Schritt (Task 19) und noch **nicht** umgesetzt.
