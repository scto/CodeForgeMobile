# Task 15 – Layout-Designer verifizieren

Kontext: `docs/layout-designer.md`, Modul `feature/layoutdesigner`.

1. `./gradlew :feature:layoutdesigner:testDebugUnitTest` – `LayoutModelTest` (11) und `LayoutScannerTest` (3) müssen grün sein (auf der JVM bereits geprüft).
2. `./gradlew :app:assembleDebug` – Compose-/Hilt-Kompilierung der UI prüfen (bisher nur Syntax geprüft). Typische Kandidaten: Icons (`SpaceBar`, `TouchApp`, `HourglassEmpty`, `AutoMirrored.*`), `ModalBottomSheet`/`VerticalDivider` (material3-Version), `CircularProgressIndicator(progress = { })`.
3. Gerät: Drawer → Layouts → neue Datei anlegen → Designer öffnet; Widgets hinzufügen, Eigenschaften ändern (Cursor springt nicht?), Undo/Redo, XML-Tab bearbeiten + übernehmen, Speichern, „Im Editor öffnen“ zeigt die gespeicherte Datei.
4. Eine echte Projekt-Layoutdatei mit ConstraintLayout öffnen: Hinweis „Näherungsdarstellung“ sichtbar, nichts crasht, Speichern erhält alle Attribute.
5. Tablet/Querformat: Seitenpanel ab 600 dp; Systemleisten verdecken nichts (Edge-to-Edge).
6. Optional: ViewModel-Tests (Undo-Coalescing, Draft/Apply, Save-Fehler) ergänzen.
