# Task 57 – Compose-Vorschau: Projektquellen und Projekt-Theme mitkompilieren

**Datum:** 2026-10-26  
**Modul:** `:feature:composepreview`  
**Status:** Implementiert & JVM-getestet (Gerätetest durch Inhaber ausstehend)

---

## 1. Ausgangslage und Ziel

Bisher kompilierte `ComposePreviewRendererImpl` ausschließlich eine isolierte Datei als `PreviewTarget.kt`. Vor Task 57 wurde in Commit `adeda2e` das `package` entfernt und Projekt-Themes (`import …ui.theme.XyzTheme`) pauschal durch einen leeren Material3-Stub (`MaterialTheme(content = content)`) ersetzt.

**Folgen der vorherigen Lösung:**
* Die Vorschau zeigte ausschließlich das Standard-Material3-Theme, nicht die im Projekt definierten Farben und Typografie (`ui/theme/Color.kt`, `Theme.kt`, `Type.kt`).
* Composables aus Nachbardateien desselben Moduls schlugen mit `unresolved reference` fehl.
* Fehlermeldungen zeigten stets den künstlichen Dateinamen `PreviewTarget.kt` statt des echten Dateinamens.

**Ziel von Task 57:**
1. Abhängigkeiten der Zieldatei analysieren und eine minimale transitive Hülle an Nachbardateien (Projekt-Theme, Hilfs-Composables) ermitteln.
2. Das `package` der Quelldateien erhalten und den Klassennamen für die Reflexion dynamisch aus Package und Dateiname bilden (`<pkg>.<Dateiname>Kt`).
3. Projekt-Themes mit echten Farbpaletten und Typografien mitkompilieren.
4. Bei Kompilierfehlern in Nachbardateien stabil auf den Theme-Stub zurückfallen (mit klarer Kennzeichnung in der UI).

---

## 2. Architektur & Umsetzung

### 57.1 Abhängigkeitsanalyse (`PreviewDependencyResolver`)
* **Modul-Erkennung:** Findet das übergeordnete Modulverzeichnis anhand von `src/main/kotlin` bzw. `src/main/java` oder `build.gradle(.kts)`.
* **Dateiscanning & Grenzwerte:** Liest alle `*.kt`-Dateien des Moduls ein; schützt vor Performanceproblemen durch Limits (`MAX_SOURCE_FILES = 400`, `MAX_SOURCE_BYTES = 4 MiB`).
* **Transitive Hülle:**
  * Startet bei der Zieldatei (unter Berücksichtigung des aktuellen Editor-Puffers).
  * Löst Projekt-Imports (z. B. `com.example.app.ui.theme.*` oder Einzelsymbole) auf.
  * Löst unqualifizierte Aufrufe im selben Package auf (z. B. Hilfs-Composables in Nachbardateien).
  * Durchläuft rekursiv die Abhängigkeiten (z. B. `Theme.kt` $\rightarrow$ `Color.kt` und `Type.kt`).
  * Erkennt und unterbricht Zyklen zuverlässig (`visited`-Menge).
  * Schließt reine Activity-, Application-, ViewModel- und Hilt-Dateien (`@AndroidEntryPoint`, `@HiltViewModel`, `ComponentActivity` ohne `@Composable`) von der Nachbar-Menge aus.

### 57.2 Isolation & Aufbereitung (`PreviewSourcePreparer`)
* **Erhalt von `package`:** Im Mehr-Datei-Modus bleibt die `package`-Zeile jeder Datei erhalten, damit Kotlin-Referenzen zwischen Paketen (`.ui.theme`) exakt auflösen.
* **Klassenname-Generierung:** Reflektiver Aufruf lädt `<pkg>.<SimpleName>Kt` bzw. den in `@file:JvmName("...")` angegebenen Bezeichner.
* **Entscheidung Weg 1 vs. Weg 2 für `Theme.kt`:**
  * *Weg 1:* Zusätzliches Mini-Classpath-JAR für `androidx.core` / `androidx.activity`.
  * *Weg 2:* Regelbasierte Bereinigung bekannter `SideEffect`-Blöcke mit `WindowCompat`, `statusBarColor` oder `(view.context as Activity)` in Theme-Dateien.
  * **Entscheidung:** **Weg 2**.
    * *Begründung:* Weg 2 erfordert keine Vergrößerung des Runtime-ZIP-Bundles (+0 Bytes). Da die Vorschau in einer Offscreen-ComposeView gerendert wird, besitzt der View-Context kein `Activity.window`; ein Cast `(view.context as Activity)` würde zur Laufzeit eine `ClassCastException` auslösen. `WindowCompat.getInsetsController` ist für die Offscreen-Bildgenerierung irrelevant. Die Farben (`colorScheme`), Typografie (`typography`) und Formen (`shapes`) bleiben vollständig erhalten. `dynamicDarkColorScheme` / `dynamicLightColorScheme` sind bereits in `androidx.compose.material3:material3` enthalten und kompilieren ohne zusätzliche Bibliotheken.
* **Ressourcen-Zugriffe (`R.*`):**
  * Vorlagen in `libs/template-engine` für Compose nutzen keine `R.string`- oder `R.drawable`-Ressourcen in Previews.
  * Bei Aufruf von `R.string.x` in Preview-Code fängt der Compiler-Output-Parser den Fehler ab und annotiert ihn mit einem klaren Hinweis: `Ressource 'R.*' steht in der Vorschau nicht zur Verfügung.`

### 57.3 Kompilierung & Ausführung (`KotlincProcessCompiler` & `ComposePreviewRendererImpl`)
* `PreviewCompiling.compile` akzeptiert nun eine Liste von Quelldateien (`sourceFiles: List<File>`).
* Die Kommandozeile übergibt alle vorbereiteten Dateien an `kotlinc`.
* **Dateibezogene Compilerfehler:** Dateiname und Zeile werden für jede Datei separat extrahiert (`Theme.kt:14:5 ...`).
* **Rückfall-Mechanismus (`PreviewCompileStep`):**
  * Schlägt die Mehrdatei-Kompilierung fehl und liegt der Fehler in einer Nachbardatei, wird ein einmaliger Rückfall gestartet:
  * Nur die Zieldatei wird vorbereitet (mit Material3-Theme-Stub) und erneut kompiliert.
  * Gelingt dies, wird die Vorschau mit dem Standard-Theme gerendert und der Status `isFallbackTheme = true` mit dem Fehlergrund vermerkt.

### 57.4 Benutzeroberfläche (`ComposePreviewScreen`)
* Unter der gerenderten Vorschau zeigt eine dezente Zeile die mitkompilierten Quellen an (z. B. `Quellen: MainActivity.kt, Theme.kt, Color.kt, Type.kt`).
* Ist der Rückfall aktiv, wird dies in Warnfarbe angezeigt: `Standard-Theme aktiv (Rückfall: <Fehler>)`.
* Alle Strings sind zweisprachig (`values/` und `values-de/`) gepflegt und synchron.

---

## 3. Messwerte (Kompilierzeit auf ARM64 / Termux)

Gemessen auf dem Host-System mit `kotlinc 2.4.21` und `compose-compiler-plugin.jar` gegen 30 Compose-Runtime-JARs und `android.jar`:

| Messung | Kaltstart (1. Lauf) | 2. Lauf | 3. Lauf | Median Folgeläufe | Gesamt-Median |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Single-File** (nur `MainActivity.kt` mit Stub) | 7.36 s | 5.97 s | 6.58 s | 6.27 s | **6.58 s** |
| **Multi-File** (`MainActivity` + `Theme` + `Color` + `Type`) | 7.27 s | 6.66 s | 6.84 s | 6.75 s | **6.84 s** |

* **Zusatzaufwand für die Theme-Kette:** Nur **+0.26 s** (+3.9%).
* **Fazit zum Cache:** Ein dateibasierter DEX-Cache für Theme-Klassen ist aufgrund des minimalen Overheads von 0.26 s vorerst nicht erforderlich.

---

## 4. Getestet

* **`PreviewDependencyResolverTest` (6 Unit-Tests):**
  * `resolves transitive theme chain Theme Color and Type`: Transitive Auflösung von `Theme.kt`, `Color.kt`, `Type.kt` anhand von Projekt-Imports und Symbolen.
  * `resolves sibling in same package without explicit import`: Auflösung von Nachbar-Composables im selben Package.
  * `handles cyclic dependencies without infinite recursion`: Zyklische Referenzen zwischen Nachbardateien terminieren ohne Schleife.
  * `excludes pure Activity and ViewModel sibling files`: Ausschluss von `ComponentActivity` und `ViewModel` ohne Composables.
  * `determines target class name correctly for custom JvmName and empty package`: Korrekte Reflection-Namen (`<pkg>.<Name>Kt`, `@file:JvmName`).
  * `handles missing and external symbols gracefully without errors`: Unbekannte Symbole führen nicht zum Abbruch.
* **`PreviewSourcePreparerTest` (9 Unit-Tests):**
  * Beibehalten von `package` bei `keepPackage = true`.
  * Entfernen von `SideEffect`, `WindowCompat`, `LocalView` und `isInEditMode` aus Theme-Dateien (Weg 2).
  * Bereinigung unbenutzter `SideEffect`- und `LocalView`-Imports.
  * `PreviewCompileStep.determineFallback`: Aktivierung und Stub-Generierung bei Fehlern in Nachbardateien.
  * Bestehende Tests (Activity-Entfernung, Strings mit Klammern, Theme-Stubs) unverändert grün.
* **`KotlincProcessCompilerTest` (7 Unit-Tests):**
  * `buildCommandLine` mit mehreren Quelldateien.
  * Output-Parser annotiert `R.*`-Referenzfehler mit Hinweis auf fehlende Ressourcen.
  * Bestehende Compiler-Tests grün.
* **String-Lokalisierung:**
  * `python3 -I scripts/check_string_locales.py` bestätigt 1644 synchronisierte Einträge ohne Fehler.
* **Projekt-Build:**
  * `:feature:composepreview:testDebugUnitTest` (31 Tests, 0 Fehler).
  * Reales Dexing mit `d8` auf generierte Klassen aus der Multi-File-Kette erfolgreich geprüft.

---

## 5. Nicht getestet

* **Geräte-Rendering auf physischem Pixel 9 Pro XL:**
  * Das tatsächliche On-Device-Rendering der Bitmap in einer aktiven Android-App-Laufzeit (`ComposeViewBitmapRenderer` und `dalvik.system.DexClassLoader`) kann nur auf dem physischen Zielgerät verifiziert werden.

---

## 6. Anleitung für Gerätetests (Pixel 9 Pro XL)

Zur Verifikation der Vorschau auf dem Gerät bitte folgende Testfälle durchführen:

### Testfall (a): Standardvorlage „Empty Activity“ mit `ui/theme`
1. Neues Compose-Projekt über den Projekt-Wizard anlegen.
2. `MainActivity.kt` im Editor öffnen.
3. Vorschau-Reiter öffnen und `GreetingPreview` auswählen.
4. **Erwartung:** Die Vorschau zeigt den Begrüßungstext im echten Projekt-Theme (z. B. Material3-Farben aus `Color.kt` und Typografie aus `Type.kt`). Unter dem Bild erscheint `Quellen: MainActivity.kt, Color.kt, Theme.kt, Type.kt`.

### Testfall (b): Eigene Farbpalette
1. In `ui/theme/Color.kt` eine Primärfarbe ändern (z. B. auf ein auffälliges Grün `Color(0xFF00FF00)`).
2. Zurück zu `MainActivity.kt` wechseln und in der Vorschau auf **Neu rendern** tippen.
3. **Erwartung:** Die Vorschau aktualisiert sich und übernimmt die neue grüne Primärfarbe.

### Testfall (c): Composable aus zweiter Nachbardatei
1. Im selben Package wie `MainActivity.kt` eine neue Datei `CustomCard.kt` anlegen:
   ```kotlin
   package com.example.myapp

   import androidx.compose.material3.Text
   import androidx.compose.runtime.Composable

   @Composable
   fun CustomCard(text: String) {
       Text(text = "Card: $text")
   }
   ```
2. `CustomCard("Test")` in `GreetingPreview` in `MainActivity.kt` aufrufen.
3. Vorschau aktualisieren.
4. **Erwartung:** `CustomCard` wird mitkompiliert und gerendert; `Quellen:` listet `CustomCard.kt` auf.

### Testfall (d): Rückfall bei Fehler in Nachbardatei
1. In `Theme.kt` einen Syntaxfehler einbauen (z. B. `val ungueltig: NichtExistierenderTyp = 123`).
2. Vorschau in `MainActivity.kt` neu rendern.
3. **Erwartung:** Die Vorschau stürzt nicht ab, sondern rendert mit dem Standard-Theme. Darunter erscheint der rote Hinweis: `Standard-Theme aktiv (Rückfall: Theme.kt:...)`.

---

## 7. Offene Punkte / Empfehlungen

1. **Optionale Stufe 2 für Ressourcen (`strings.xml`):**
   * Falls Nutzer häufig `stringResource(R.string.x)` in Vorschauen verwenden, könnte ein Präprozessor `strings.xml` auslesen und ein einfaches synthetisches `R.string`-Objekt mit String-Konstanten mitkompilieren.
2. **Kompilier-Cache bei sehr großen Projekten:**
   * Bei Projekten mit mehr als 50 Nachbardateien könnte ein Hash-Cache für unveränderte Module Zeit einsparen (aktuell bei 4 Dateien mit +0.26s jedoch noch nicht erforderlich).
