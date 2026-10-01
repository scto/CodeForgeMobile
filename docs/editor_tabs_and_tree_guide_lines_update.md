# Erweiterung der Editor-Tabs, Editor-Einstellungen und Dateibaum-Visuals

## Übersicht
Dieses Dokument beschreibt die neusten Erweiterungen und Anpassungen an den Editor-Tabs, den Editor-Einstellungen sowie der Baumstruktur im Modul `:feature:filetree`.

---

## 1. Editor Tab-Kontextmenü (Close, Close Others, Close All)

Jeder Tab in [EditorScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt) verfügt nun über ein Drei-Punkt-Menü `MoreVert` mit folgenden Schließen-Aktionen:

- **Schließen (`Close`)**: Schließt die aktuell ausgewählte Datei ([EditorContract.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorContract.kt)).
- **Andere schließen (`Close Others`)**: Schließt alle geöffneten Editor-Tabs mit Ausnahme des ausgewählten Tabs.
- **Alle schließen (`Close All`)**: Schließt sämtliche aktiven Editor-Sitzungen im Workspace.

Das ViewModel [EditorViewModel.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt) verarbeitet die Events `CloseOthersTab` und `CloseAllTabs` und informiert den Language-Server über `didClose`.

---

## 2. Editor-Einstellungen: Textgröße & Tab-Größe

In [EditorSettingsScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/src/main/kotlin/com/codeforge/feature/settings/editor/EditorSettingsScreen.kt) wurden unter dem Abschnitt **Display (Darstellung)** neue Interaktionselemente hinzugefügt:

- **Textgröße (`fontSize`)**: Interaktiver `Slider` von `8sp` bis `36sp` zur stufenlosen Anpassung der Schriftgröße mit visueller Echtzeit-Wertanzeige.
- **Tab-Größe (`tabSize`)**: Interaktiver `Slider` von `1` bis `8` Leerzeichen zur präzisen Einstellung der Einrückungstiefe.

---

## 3. Dateibaum: Aufgeklappte/Eingeklappte Ordner & Führungslinien

In [CompactFileSystemTree.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/CompactFileSystemTree.kt) und [FileTreeDrawer.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/filetree/src/main/kotlin/com/codeforge/feature/filetree/FileTreeDrawer.kt):

- **Ordner-Icons**:
  - Ordner mit Unterordnern/Inhalten zeigen `FolderOpen` + `KeyboardArrowDown` im aufgeklappten Zustand und `Folder` + `KeyboardArrowRight` im eingeklappten Zustand.
  - Leere Ordner verbergen den Erweiterungspfeil.
- **Tree-Führungslinien**:
  - Durch den `guideLineModifier` (mit `drawBehind`) werden dezente vertikale Führungslinien für jede Einrückungsebene gezeichnet.
