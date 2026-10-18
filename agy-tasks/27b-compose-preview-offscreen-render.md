# Task 27b – Compose-Preview: Rendern so umbauen, dass das Bild nicht leer bleibt

**Lies zuerst:** `docs/compose-preview.md` (Abschnitt 4, Punkt 2). Dieser Task ist unabhängig von Task 26 und 27a und kann zuerst laufen.
**Problem (aus dem Code, nicht auf dem Gerät bestätigt):** `ComposeViewBitmapRenderer.renderToBitmap` erzeugt eine `ComposeView` ohne Fenster, ruft `setContent`, misst/zeichnet sofort und liest das Bitmap. Compose komponiert aber über den Recomposer mit dem Frame-Takt (Choreographer); nach einem synchronen `measure/layout/draw` ist der Inhalt vermutlich noch nicht komponiert, das Bild also leer. Außerdem fehlt ein Fenster für Eingabe- und Anzeige-Infrastruktur.
**Regeln:** Ergebnis in `docs/compose-preview-render-report.md` mit **getestet / nicht getestet**; Texte über `:core:resources` (en/de).

## 27b.0 Reproduzieren
Instrumentierter Test in `feature/composepreview/src/androidTest` (Compose-Plugin über `codeforge.android.library.compose` aktiv): eine einfache `@Composable` mit deutlich sichtbarer Fläche (z. B. `Box(Modifier.fillMaxSize().background(Color.Red))`) liegt **vorab kompiliert** im Test-Quellsatz; der Test lädt sie per Reflection (`SampleKt` → Methode) und rendert sie mit dem **bestehenden** `ComposeViewBitmapRenderer`. Erwartung vor dem Umbau: Test zeigt, ob das Bitmap leer ist (Anteil nicht transparenter Pixel im Bericht). Das macht den Fehler belegbar, unabhängig vom Compiler.

## 27b.1 Umbau (Empfehlung; Alternativen begründet verwerfen)
1. **Aufnahme innerhalb der Komposition:** Die Zielfunktion wird in einer echten Komposition (an Fenster angehängt, im Preview-Panel oder einem unsichtbaren Host) mit `rememberGraphicsLayer()` aufgenommen (`drawWithContent { layer.record { this@drawWithContent.drawContent() } … }`), nach dem ersten fertigen Frame wird `layer.toImageBitmap()` gelesen und als PNG kodiert. Das braucht kein Zeichnen einer losgelösten View.
2. **Warten auf den Frame:** erst `awaitFrame()`/`withFrameNanos` nach dem ersten Layout, dann lesen; Zeitlimit (z. B. 5 s) mit Fehlermeldung statt Endlosschleife.
3. **Fehlerisolation:** Wirft die Preview-Funktion eine Exception, darf die App nicht abstürzen. Reflection-Aufruf mit Fang von `InvocationTargetException`; Zustand `Failed(message)`. Prüfen und im Bericht festhalten, ob eine fehlerhafte Komposition die Haupt-Komposition beschädigt; falls ja, die Preview in eine eigene `ComposeView`/Subkomposition auslagern.
4. `ComposePreviewRenderer` (Domain) bleibt bei `PreviewRenderResult(functionName, imageBytes)`; `ComposePreviewViewModel` und `ComposePreviewScreen` ändern sich nur so weit nötig.
5. `ComposeViewBitmapRenderer` entfernen oder auf den neuen Weg umstellen, keinen toten Code lassen. `ReflectiveComposableHost` prüfen: Signatur `(Composer, Int)` für parameterlose Funktionen; Funktionen mit Parametern ausdrücklich ablehnen und mit klarer Meldung anzeigen (neuer String), nicht abstürzen.
6. Größe: statt festem 1080×1920 die tatsächliche Inhaltsgröße (Wrap) bzw. eine Standardgröße mit Seitenverhältnis des Gerätes verwenden; Wert und Begründung im Bericht.

## 27b.2 Tests
* Der Test aus 27b.0 muss nach dem Umbau ein Bitmap mit dem erwarteten roten Bereich liefern (Pixelprüfung, Anteil > 90 %).
* Zweiter Test: Funktion wirft Exception → `Failed`, kein Absturz.
* Dritter Test: Funktion mit Parametern → klare Fehlermeldung.
* Auf einem Gerät/Emulator ausführen. Wenn keiner verfügbar ist: ausdrücklich „nicht ausgeführt“ melden und den Test trotzdem so schreiben, dass er kompiliert (`./gradlew :feature:composepreview:assembleDebugAndroidTest`).

## Abnahme
Build grün (inkl. `assembleDebugAndroidTest`), String-Check grün, Bericht mit Vorher/Nachher der Pixelprüfung (falls ausgeführt), `TASKS-STATUS.md` und `README.md` um Task 27b ergänzt.
