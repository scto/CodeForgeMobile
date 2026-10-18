# Compose-Preview (`:feature:composepreview`)

**Stand:** Repo `ae1824f` · **Verifiziert:** nichts davon wurde auf einem Gerät ausgeführt. Der Code kompiliert (Build grün), die Pipeline lief aber nie.
Dieses Dokument beschreibt, was das Modul tut, was ihm fehlt (vor allem die Jar-Dateien) und in welcher Reihenfolge es sich verifizieren lässt. Zugehöriger Auftrag: `agy-tasks/26-compose-preview-compiler-runtime.md`.

## 1. Ziel
Im Editor öffnet der Nutzer eine Kotlin-Datei mit `@Composable`-Funktionen. Das Preview-Panel kompiliert die Datei **auf dem Gerät**, lädt das Ergebnis und zeigt die gewählte Funktion als Bild. Kein Gradle-Build nötig.

## 2. Pipeline (alle Dateien in `feature/composepreview/src/main/kotlin/…`)
| Schritt | Datei | Was passiert |
|---|---|---|
| Auslöser | `ComposePreviewViewModel` | Wartet auf `ActiveComposablePreviewBridge.activeFile`, wählt die Funktion (bevorzugt mit `@Preview`), ruft den Renderer auf. |
| Orchestrierung | `ComposePreviewRendererImpl` | Arbeitsordner unter `cacheDir/compose_preview/<zeit>`, ruft Schritte 1–5, räumt danach auf. |
| 1 Kompilieren | `PreviewCompiler` | `K2JVMCompiler` (kotlin-compiler-embeddable 2.1.21) mit dem Compose-Compiler-Plugin; schreibt `.class`-Dateien. |
| 2 Dexen | `PreviewDexer` | Startet `d8` aus den installierten Build-Tools per `ProcessBuilder`, erzeugt `classes.dex`. |
| 3 Laden | `ComposePreviewRendererImpl` | `DexClassLoader` mit dem App-ClassLoader als Parent; sucht `PreviewTargetKt` und die Methode. |
| 4 Einhängen | `ReflectiveComposableHost` | Ruft die Funktion reflektiv mit `(Composer, 1)` auf (nur parameterlose Funktionen). |
| 5 Rendern | `ComposeViewBitmapRenderer` | `ComposeView` ohne Activity, misst, zeichnet in ein 1080×1920-Bitmap, PNG. |

## 3. Was fehlt: die Jar-Dateien (Hauptproblem)
Der Kompiler braucht zum Typprüfen den **Klassenpfad** als `.jar`-Dateien. Die App enthält ihre Bibliotheken aber nur gedext in der APK.
`ComposePreviewRendererImpl` erwartet deshalb:

* `filesDir/compiler-runtime/*.jar` – Kotlin-Stdlib, Compose-Runtime/-UI usw.
* `filesDir/compiler-runtime/kotlin-compose-compiler-plugin-embeddable.jar` – genau dieser Dateiname.

**Nichts im Repo legt diese Dateien dort ab.** Eine Suche nach `compiler-runtime` findet nur den Lesezugriff im Renderer und die Fehlermeldung. Ohne die Jars schlägt jedes Rendern mit „Compose-Compiler-Plugin-Jar fehlt“ fehl. Die Meldung nennt dabei `assets/compiler-runtime/`, gelesen wird aber `filesDir/compiler-runtime/` – die Texte sind irreführend.

### Benötigte Jars (Kategorien; die genaue Liste erzeugt Gradle, siehe Task 26)
Alle Versionen müssen **exakt** zu denen der App passen (Compose-BOM `2025.09.00`, Kotlin `2.1.21` laut `gradle/libs.versions.toml`). Der `DexClassLoader` nimmt zur Laufzeit die Klassen der App; weichen die Jars beim Kompilieren ab, entstehen `NoSuchMethodError` oder falsche Composer-Signaturen.

| Gruppe | Artefakte (Auswahl) | Anmerkung |
|---|---|---|
| Compiler-Plugin | `org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.1.21` | normales Jar (Maven Central); Version = Kotlin-Version |
| Kotlin | `kotlin-stdlib` 2.1.21, `kotlinx-coroutines-core-jvm`/`-android` | Compose-Runtime referenziert Coroutines |
| Compose-Runtime | `androidx.compose.runtime:runtime`, `runtime-saveable` | **AAR**, `classes.jar` daraus |
| Compose-UI | `ui`, `ui-graphics`, `ui-text`, `ui-unit`, `ui-geometry`, `ui-util` | AAR |
| Foundation/Animation | `foundation`, `foundation-layout`, `animation`, `animation-core` | AAR |
| Material 3 | `material3`, optional `material-icons-core` | `material-icons-extended` ist sehr groß: nur auf Wunsch |
| Vorschau-Annotation | `ui-tooling-preview` | nötig, wenn die Datei `@Preview` importiert |
| AndroidX-Basis | `annotation`, `collection`, `lifecycle-*` (nur was transitiv gebraucht wird) | über die Auflösung von Gradle |
| Plattform | `android.jar` aus `platforms;android-N` | kommt **nicht** als Jar-Bundle, sondern aus dem installierten SDK (SDK-Manager) |

Compose-Bibliotheken liegen als **AAR** vor, der Compiler braucht das enthaltene `classes.jar`. Das Bundle wird deshalb nicht von Hand zusammengesucht, sondern von einer Gradle-Aufgabe aus derselben BOM erzeugt (Task 26).

### Auslieferung
* **Nicht in die APK:** Die Jars blähen die APK auf (Größe ungeprüft; Ziel: im Task messen und berichten). Der Compiler selbst (`kotlin-compiler-embeddable`, laut Kommentar im `build.gradle.kts` ~80 MB) liegt bereits als App-Abhängigkeit.
* **Empfohlen:** ein Bundle `compiler-runtime-<bom>-k<kotlin>.zip` plus `manifest.json` (Dateiname, SHA-256, Größe, BOM, Kotlin) als Release-Asset; die App lädt es beim ersten Gebrauch, prüft die SHA-256 und entpackt nach `filesDir/compiler-runtime/` – wie der SDK-Manager es für Android-Komponenten macht.

## 4. Weitere bekannte Risiken (nicht Teil von Task 26)
Der Quelltext sagt selbst, dass die Schritte 1, 4 und 5 „nach bestem Wissen“ geschrieben, aber nie ausgeführt wurden. Aus dem Lesen des Codes ergeben sich zwei Risiken, die erst ein Gerätetest klärt:

1. **Kotlin-Compiler in ART:** `kotlin-compiler-embeddable` ist für eine JVM gebaut. Ob er sich auf Android überhaupt laden und ausführen lässt, ist ungeprüft. Alternative: den Compiler als eigenen Prozess in der Termux-Umgebung starten (`java -cp … K2JVMCompiler` mit dem JDK und dem Paket `kotlin`, die wir im APT-Repo bauen). Das würde die 80 MB aus der APK nehmen und eine echte JVM nutzen. Es setzt ein fertig gebautes `openjdk-17` im Repo voraus (Gruppe H1).
2. **Offscreen-Rendering:** `ComposeViewBitmapRenderer` misst und zeichnet die `ComposeView` sofort nach `setContent`, ohne dass sie an ein Fenster gehängt ist und ohne auf einen Compose-Frame zu warten. Die Komposition läuft aber über den Recomposer/Choreographer. Es ist daher plausibel, dass das Bitmap leer bleibt. Mögliche Abhilfe: View an ein (unsichtbares) Fenster hängen, auf den ersten Frame warten, dann erst zeichnen.
3. **Einschränkung:** `ReflectiveComposableHost` ruft nur Funktionen ohne Parameter auf (`invoke(null, composer, 1)`).

## 5. Reihenfolge der Verifikation
1. Task 26: Bundle erzeugen, Download/Prüfsumme/Entpacken auf dem Gerät, Klassenpfad aus Jars + `android.jar`.
2. Schritt 1 isoliert testen: kompiliert `K2JVMCompiler` eine einfache `@Composable`-Datei auf dem Gerät (Ja/Nein, Fehlermeldung)? Bei „Nein“: Termux-JVM-Variante (Risiko 1).
3. Schritt 2–3: `d8` und `DexClassLoader` mit dem Ergebnis.
4. Schritt 4–5: Bitmap nicht leer? Bei „leer“: Risiko 2.
5. Erst danach Preview-Parameter/`@Preview`-Optionen.
