# Codevervollständigung, Inlay Hints, Compose Preview Button & FileTree Composable Icons

## Übersicht
Dieses Dokument beschreibt die neuen Implementierungen bezüglich Codevervollständigung (Completion), Inlay Hints, des Compose Preview-Buttons sowie der Composable-Icon-Darstellung im Dateibaum.

---

## 1. Codevervollständigung (Completion) & Inlay Hints

### Universal Language Wrapper (`CodeForgeLanguage.kt`)
- **Datei**: [CodeForgeLanguage.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/lang/CodeForgeLanguage.kt)
- Implementiert ein Delegations-Language-Wrapper um Syntax-Highlighting-Engines (TextMate, Monarch, TreeSitter).
- Nutzt `IdentifierAutoComplete` von Sora Editor, um Dokument-Variablen, Funktionen und Schlüsselwörter (`@Composable`, `fun`, `val`, `var`, `class`, `import`, `Modifier`, `Column`, `Row`, `Text` etc.) beim Tippen automatisch vorzuschlagen.
- Eingebunden in [SoraLanguageProvider.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraLanguageProvider.kt).

### Inlay Hints
- In [SoraCodeEditor.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraCodeEditor.kt) wird `registerInlayHintProvider` verwendet.
- Blendet Parameter-Hinweise (z.B. `text: `, `all: `) dynamisch direkt im Code-Editor ein, wenn `inlayHintsEnabled = true` in den Editor-Einstellungen gewählt ist.

---

## 2. Compose Preview Button im Editor

- In [EditorScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt) wurde in den TopAppBar-Aktionen sowie im Kontextmenü ("Mehr Optionen") ein eigener Button **Compose Preview (Vorschau)** mit `AutoAwesome`-Icon in Indigo-Farbe (`#6366F1`) hinzugefügt.
- Beim Klicken auf das Icon schaltet der Editor direkt in die Compose Preview um.
- Event-Behandlung in [EditorContract.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorContract.kt) und [EditorViewModel.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt).

---

## 3. Dateibaum: Composable-Icons für `@Composable`-Dateien

## 4. Automatisches Herunterladen & Kopieren von Compose-Preview JARs (inkl. d8)

- In [ComposePreviewRendererImpl.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/composepreview/src/main/kotlin/com/codeforge/feature/composepreview/ComposePreviewRendererImpl.kt) und [PreviewDexer.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/composepreview/src/main/kotlin/com/codeforge/feature/composepreview/PreviewDexer.kt) wurde eine automatische Wiederherstellungs- und Download-Logik für die benötigten Compiler-, Runtime- und Dexer-Dateien implementiert (`resolveRuntimeJars()`, `resolveComposeCompilerPluginJar()` & `resolveD8BinaryPath()`):
  - **Zielordner**: `<app_files_dir>/compiler-runtime/` (wird automatisch angelegt).
  - **Asset-Kopie**: Falls die JAR-Dateien in den Assets der App vorhanden sind (`assets/compiler-runtime/*.jar` inkl. `d8.jar`), werden sie beim Starten der Preview direkt in den Zielordner kopiert.
  - **Automatischer Download**: Sollten die JAR-Dateien oder das `d8`-Dexing-Tool (`d8.jar` / `r8.jar`) weder in den SDK-Build-Tools noch lokal oder in den Assets vorhanden sein, werden sie beim Starten der Preview automatisch über Maven Central heruntergeladen (`kotlin-stdlib`, `compose-runtime`, `compose-ui`, `kotlinx-coroutines-core`, `kotlin-compose-compiler-plugin-embeddable` sowie `d8.jar`).
  - **Ausführung**: `PreviewDexer` verarbeitet sowohl direkt installierte `d8`-Executables als auch heruntergeladene `d8.jar`-Archive per `java -jar`.
