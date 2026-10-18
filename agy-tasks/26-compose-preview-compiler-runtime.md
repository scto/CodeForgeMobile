# Task 26 – Compose-Preview: Jar-Bundle für den On-Device-Compiler bereitstellen

**Lies zuerst:** `docs/compose-preview.md` (Pipeline, Jar-Liste, Risiken).
**Ausgangslage (belegt):** `ComposePreviewRendererImpl` liest `filesDir/compiler-runtime/*.jar` und `…/kotlin-compose-compiler-plugin-embeddable.jar`; **nichts** legt diese Dateien dort ab. Das Rendern scheitert immer an der fehlenden Jar-Datei. Die Fehlermeldung nennt fälschlich `assets/compiler-runtime/`.
**Regeln:** keine Versionen raten, alles aus `gradle/libs.versions.toml` (Compose-BOM, Kotlin) ableiten; nach jedem Schritt `./gradlew :feature:composepreview:assembleDebug`; Ergebnis in `docs/compose-preview-runtime-report.md` mit **getestet / nicht getestet**. Neue Texte nur in `:core:resources` (Englisch Standard, Deutsch in `values-de`).

## 26.1 Gradle-Aufgabe `packCompilerRuntime` (in `:feature:composepreview`)
1. Eigene (detached) Konfiguration, die dieselbe Compose-BOM wie die App benutzt (`platform(libs.androidx.compose.bom)`) und diese Artefakte auflöst: `runtime`, `runtime-saveable`, `ui`, `ui-graphics`, `ui-text`, `ui-unit`, `ui-geometry`, `ui-util`, `foundation`, `foundation-layout`, `animation`, `animation-core`, `material3`, `material-icons-core`, `ui-tooling-preview`, dazu `org.jetbrains.kotlin:kotlin-stdlib` und `kotlinx-coroutines-core-jvm` (Version aus dem Katalog bzw. der Auflösung). **Nicht** `material-icons-extended` (nur über Option `-PwithExtendedIcons`, Größe im Bericht nennen).
2. AARs in ihr `classes.jar` überführen (Artefaktansicht mit `artifactType = "android-classes-jar"`; falls das nicht greift, AAR selbst entpacken). Reine Jars unverändert übernehmen. Duplikate zusammenführen, transitive AndroidX-Abhängigkeiten (`annotation`, `collection`, `lifecycle-*`) nur soweit nötig.
3. Zusätzlich `org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:<kotlin>` laden und als `kotlin-compose-compiler-plugin-embeddable.jar` ablegen.
4. Ausgabe: `build/compiler-runtime/*.jar`, `manifest.json` und `compiler-runtime-<bom>-k<kotlin>.zip`. Manifest: `{ "bom": "…", "kotlin": "…", "files": [ { "name", "sha256", "size" } ] }`.
5. Prüfung im Task selbst: jede Jar-Datei öffnen (`unzip -t`), Gesamtgröße (ZIP und entpackt) im Bericht angeben. Entscheiden, ob der Download-Weg (26.2/26.3) wegen der Größe nötig ist, oder ob Assets genügen; Zahl begründen.

## 26.2 Bereitstellung (Release-Asset)
1. Workflow `.github/workflows/build-compiler-runtime.yml` (manuell, `workflow_dispatch`): führt `packCompilerRuntime` aus und lädt das ZIP samt `manifest.json` und `.sha256` an das Release `compiler-runtime-<bom>-k<kotlin>` im Repo `scto/codeforge-tools` (Secret wie in den anderen Workflows, nichts im Log ausgeben). `actionlint` muss sauber sein.
2. Die Release-URL-Konstruktion kommt aus **einer** Stelle (Konstante/BuildConfig), nicht an mehreren Orten.

## 26.3 App: `CompilerRuntimeProvider`
1. Schnittstelle in `:core:domain`, Implementierung in `:feature:composepreview`: `suspend fun ensureInstalled(onProgress: (Int) -> Unit): Result<File>` (Ordner `filesDir/compiler-runtime`).
2. Ablauf: `manifest.json` lokal prüfen (BOM/Kotlin müssen zu den Versionen der **laufenden App** passen, via `BuildConfig`-Felder, die aus dem Versionskatalog gesetzt werden); sonst ZIP laden, SHA-256 prüfen, in einen Temp-Ordner entpacken (Zip-Slip-Schutz: nur Dateinamen ohne `/` und `..`), jede Datei gegen das Manifest prüfen, dann atomar nach `compiler-runtime` verschieben. Bei Fehler Temp-Ordner löschen, alte Installation behalten.
3. Der Renderer ruft `ensureInstalled` vor dem Kompilieren auf; neuer UI-Zustand `PreparingRuntime(progress)` im `ComposePreviewViewModel` mit Fortschrittsanzeige und „Erneut versuchen“.
4. Für den Download den vorhandenen Downloader des Projekts wiederverwenden (z. B. aus dem SDK-Manager), keine neue HTTP-Bibliothek; nur `https://github.com/…`-URLs zulassen.

## 26.4 Klassenpfad und Plattform
1. Der Klassenpfad des Compilers = alle Jars aus `compiler-runtime` **plus** `android.jar` der höchsten installierten Plattform (`platforms;android-N`, Pfad über `SdkRepository` wie bei `resolveD8BinaryPath`). Fehlt sie, klare Meldung „Android-Plattform über den SDK-Manager installieren“ (neuer String).
2. `resolveRuntimeJars()` darf das Plugin-Jar nicht in den Klassenpfad aufnehmen (es gehört nur nach `pluginClasspaths`).

## 26.5 Texte korrigieren
`composepreview_compose_compiler_plugin_jar_fehlt` (en/de) so ändern, dass er das Laufzeitpaket meint („Compiler-Laufzeitpaket fehlt oder ist unvollständig – wird beim ersten Start geladen“), nicht `assets/…`. `python3 scripts/check_string_locales.py` muss grün sein.

## 26.6 Tests
1. JVM-Unit-Tests: Manifest-Parser, SHA-256-Prüfung (falsche Summe → Fehler, alte Installation bleibt), Zip-Slip, Versionsabgleich.
2. Gradle: Test, der prüft, dass das erzeugte Manifest zu jedem Jar im Ordner passt.
3. **Nicht Teil dieses Tasks und deshalb ausdrücklich nicht behaupten:** dass das Kompilieren auf dem Gerät funktioniert (siehe `docs/compose-preview.md`, Abschnitt 4: Compiler in ART, leeres Bitmap). Im Bericht auflisten.

## 26.7 Folge-Tasks (nur vorschlagen, nicht umsetzen)
* **27a** Compiler als Prozess in der Termux-JVM statt in ART (`java -cp … K2JVMCompiler`), sobald `openjdk-17` im APT-Repo liegt.
* **27b** Offscreen-Rendering reparieren (View an Fenster hängen, auf ersten Frame warten).

## Abnahme
Build grün, `./gradlew :feature:composepreview:packCompilerRuntime` liefert ZIP und Manifest, Unit-Tests grün, `actionlint` sauber, String-Check grün. `agy-tasks/TASKS-STATUS.md` und `README.md`: Task 26 eintragen, Verweis auf `docs/compose-preview.md`. Bericht mit Größen der Jars und Liste „nicht getestet“.
