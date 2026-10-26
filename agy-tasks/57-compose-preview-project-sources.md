# Task 57 – Compose-Vorschau: Projektquellen und Projekt-Theme mitkompilieren

**Ziel:** Die Vorschau (`:feature:composepreview`) zeigt `@Preview`-Composables so, wie sie im Projekt aussehen: mit dem **echten Projekt-Theme** (`ui/theme/Theme.kt`, `Color.kt`, `Type.kt`) und mit Composables, Konstanten und Ressourcen aus **anderen Dateien** des Moduls. Heute wird nur die eine geöffnete Datei kompiliert; alles andere wird durch Ersatz ersetzt oder scheitert mit `unresolved reference`.

**Ausgangslage (Befund des Assistenten, selbst prüfen):**
* `ComposePreviewRendererImpl.render` schreibt den Quelltext der **einen** Datei als `PreviewTarget.kt` und kompiliert ihn mit `KotlincProcessCompiler` gegen die Runtime-JARs (`filesDir/compiler-runtime`, 31 Dateien) und `android.jar`. Weitere Projektdateien, `androidx.activity`, `androidx.core`, Hilt, Navigation, Coil usw. fehlen im Classpath.
* Commit `adeda2e` hat das mit `PreviewSourcePreparer` **notdürftig** abgefangen: `package` entfernt, Activity-Klassen und Imports aus `androidx.activity|core|dagger|javax.inject` gelöscht, Projekt-Themes (`import …ui.theme.XyzTheme`) durch einen Material3-Stub ersetzt. Folge: Die Vorschau zeigt das Standard-Material3-Theme, nicht die Projektfarben; Composables aus anderen Dateien lösen sich nicht auf; `R.string/drawable` fehlen.
* Dieser Task ersetzt den Stub durch echte Quellen, **behält den Stub aber als Rückfall**, wenn das Mitkompilieren scheitert (Fehlermeldung zeigt dann, was gefehlt hat).

**Gemeinsame Regeln:** Nichts ausführen, was nicht gemessen wurde; Bericht `docs/compose-preview-project-sources-report.md` mit **getestet / nicht getestet / offene Punkte**. Gerätetests (Pixel 9 Pro XL) führt der Inhaber aus, du lieferst Testprojekte und Anleitung. `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash). Bestehende Tests (`CompilerRuntimeProviderTest`, `PreviewSourcePreparerTest`) bleiben grün.

## 57.1 Abhängigkeitsanalyse der Zieldatei
1. Aus der Preview-Datei die benötigten Symbole bestimmen: Imports der eigenen Projektpackage-Wurzel (z. B. `com.compose.myapp.ui.theme.*`) und ungequalifizierte Aufrufe, die in Nachbardateien desselben Packages definiert sind.
2. Projektquellen finden: Modulverzeichnis aus dem Pfad der Datei ableiten (`…/app/src/main/(kotlin|java)/…`), alle `*.kt` desselben Moduls einlesen (Größenlimit, z. B. 400 Dateien / 4 MiB, sonst Hinweis).
3. Minimale Auswahl statt „alles“: Startdatei plus die **transitive Hülle** der Dateien, die benötigte Top-Level-Deklarationen enthalten (einfache Namensauflösung genügt: Funktions-, Klassen-, Objekt-, Property-Namen). Reine Activity-/Application-/ViewModel-/Hilt-Dateien werden nie aufgenommen (siehe 57.2). Auswahlfunktion mit Unit-Tests (Theme-Kette `Theme.kt → Color.kt/Type.kt`, Zyklus, fehlendes Symbol).

## 57.2 Quellen für die Isolation aufbereiten
1. `PreviewSourcePreparer` (existiert) zu einer mehrdateifähigen Variante erweitern: jede ausgewählte Datei bekommt dieselbe Behandlung (Activity-Klassen entfernen, nicht auflösbare Imports entfernen), das **`package` bleibt aber erhalten**, damit Verweise zwischen den Dateien stimmen. Die Startdatei bleibt der Ort der Preview-Funktion; der Klassenname für die Reflection (`<pkg>.<Dateiname>Kt`) wird aus Package und Dateiname gebildet (heute fest `PreviewTargetKt`: anpassen, `ComposePreviewRendererImpl` und Tests).
2. `Theme.kt` aus Standardvorlagen nutzt typischerweise `androidx.core.view.WindowCompat`, `LocalView`, `(view.context as Activity).window`, `dynamicColorScheme`, `SideEffect`. Entscheide mit Messung, wie das behandelt wird, und dokumentiere es:
   * **Weg 1:** Fehlende AndroidX-Klassen (nur `core`/`activity`) als zusätzliches **Mini-Classpath-JAR** liefern (Prüfung der Lizenz, SHA-256, in `manifest.json`/Release `compiler-runtime-*` aufnehmen; Größe messen), **oder**
   * **Weg 2:** In `Theme.kt` bekannte `SideEffect`-Blöcke mit `WindowCompat`/`Activity` per Regel entfernen (nachvollziehbar, nur diese Muster), Rest unverändert.
   Empfehlung: Weg 2 zuerst (kein größeres Bundle), Weg 1 nur, wenn die Messung zeigt, dass Weg 2 zu fragil ist.
3. `R.*`-Zugriffe (`R.string.x`, `painterResource(R.drawable.y)`): Erst messen, wie oft Previews sie brauchen (Vorlagen aus `libs/template-engine`, `examples/`). Mindestlösung: klare Fehlermeldung „Ressource `R.string.x` steht in der Vorschau nicht zur Verfügung“; optional Stufe 2: `strings.xml`-Werte als Konstanten ersetzen (nur wenn Messung es rechtfertigt, im Bericht begründen).

## 57.3 Kompilierung und Ausführung
1. `PreviewCompiling.compile` auf eine **Liste von Quelldateien** erweitern (Schnittstelle in `:feature:composepreview`; `KotlincProcessCompiler.buildCommandLine` entsprechend, Testabdeckung beibehalten). Arbeitsverzeichnis wie heute unter `cacheDir/compose_preview/<ts>/`.
2. Ausgabe der Compilerfehler **dateibezogen** anzeigen (`Theme.kt:12:5 …`), statt immer `PreviewTarget.kt`. Fehler in **Nachbardateien** sind ein eigener Hinweis („Projekt-Theme konnte nicht kompiliert werden; Vorschau mit Standard-Theme angezeigt“ → Rückfall auf den Stub und Neustart des Laufs, einmalig).
3. Kompilierzeit: messen (Kaltstart und Folgelauf, 3 Läufe, Median) mit nur Startdatei vs. mit Theme-Kette. Ein Cache pro (Datei-Hashes → Klassen/DEX) ist **optional**; nur umsetzen, wenn die Messung deutlich Zeit spart, sonst als Empfehlung im Bericht.

## 57.4 UI
* In der Vorschau kleiner Hinweis, **welche** Dateien zusätzlich kompiliert wurden und ob der Rückfall (Standard-Theme) aktiv ist (ein Chip oder eine Zeile, keine Dialoge). Strings in `values/` und `values-de/` (`python3 scripts/check_string_locales.py` ohne Fehler).

## 57.5 Tests
* Auswahl der Quelldateien (57.1), mehrdateiige Aufbereitung inkl. `package`-Erhalt und Klassenname-Bildung (57.2), Kommandozeile mit mehreren Quelldateien (57.3), Rückfall bei Fehler in einer Nachbardatei. Alle als JVM-Unit-Tests; was nur auf dem Gerät prüfbar ist (echte Kompilierung, DEX, Rendering), steht unter **nicht getestet**.
* Testprojekte für den Inhaber (Anleitung im Bericht): (a) Standardvorlage „Empty Activity“ mit `ui/theme`, (b) Projekt mit eigener Farbpalette, (c) Preview mit Composable aus einer zweiten Datei, (d) Preview mit `R.string`.

## Akzeptanz
* Vorlage (a): Die Vorschau zeigt das **Projekt-Theme** (nicht den Stub); `MainActivity`-Datei als Startdatei funktioniert weiterhin.
* Composables aus Nachbardateien desselben Moduls lösen sich auf; Fehler nennen die betroffene Datei.
* Scheitert das Mitkompilieren, erscheint die Vorschau mit Standard-Theme und einer erklärenden Zeile; kein Absturz.
* Zahlen (Kompilierzeit) und die Entscheidung Weg 1/Weg 2 stehen im Bericht; Tests grün; **nicht getestet** ehrlich aufgeführt.

## Commit-Vorschlag
```
feat(composepreview): compile project theme and sibling sources for previews

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
