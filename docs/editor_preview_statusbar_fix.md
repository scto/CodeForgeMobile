# Reparatur der Statusleisten-Überlappung (Editor & Preview)

## Problembeschreibung
Aufgrund von `enableEdgeToEdge()` in `MainActivity.kt` dehnte sich das Anwendungsfenster unter die System-Statusleiste (Symbolleiste des Smartphones) aus. Wenn im Editor eine Datei mit `@Composable`-Funktionen geöffnet wurde, zeichnete `EditorWithPreviewHost` das Tab-Row ("Editor" / "Preview") direkt an Position y=0. Dadurch verdeckte die Vorschau-/Editor-Leiste die Statusleiste / Symbolleiste des Smartphones.

---

## Durchgeführte Änderungen

### 1. `EditorWithPreviewHost.kt`
- **Datei**: [EditorWithPreviewHost.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/src/main/kotlin/com/codeforge/app/EditorWithPreviewHost.kt)
- **Anpassung**: Hinzufügen von `.statusBarsPadding()` zum Haupt-`Column`. Dadurch rückt die Tab-Leiste ("Editor" / "Preview") und der gesamte nachfolgende Inhalt unter die System-Statusleiste des Smartphones ein.

### 2. `WorkspaceScreen.kt`
- **Datei**: [WorkspaceScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/src/main/kotlin/com/codeforge/app/workspace/WorkspaceScreen.kt)
- **Anpassung**: Hinzufügen von `.statusBarsPadding()` zur `ModalNavigationDrawer`-Komponente, um sicherzustellen, dass der Workspace-Header und der Drawer einen sauberen Abstand zur System-Symbolleiste einhalten.

---

## Ergebnis
- Die Statusleiste des Smartphones (Uhrzeit, Akku, Benachrichtigungssymbole) bleibt stets vollständig sichtbar und bedienbar.
- Die Editor- und Preview-Tabs werden unterhalb der Statusleiste positioniert.
