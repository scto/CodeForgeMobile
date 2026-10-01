# Comprehensive Fixes: Editor, FileTreeDrawer, FloatingWindow, and PRoot Environment

## Umgesetzte Anpassungen & Fehlerbehebungen

### 1. PRoot-Umgebung & `.bashrc` Exporte (`env`)
- **Problem**: Bei nicht-interaktiven Shells wurden Umgebungsvariablen wie `ANDROID_HOME`, `JAVA_HOME` und `PATH` in `env` nicht angezeigt, weil `.bashrc` durch den Guard `[ -z "$PS1" ] && return` in Zeile 6 die Ausführung abbrach. Zudem fehlten die Exporte am Anfang der `init-*.sh`-Skripte.
- **Lösung**:
  - `ANDROID_HOME`, `JAVA_HOME`, `ANDROID_NDK_HOME`, `CMAKE_HOME`, `GRADLE_HOME` und `PATH` am Anfang von [`assets/init-ubuntu.sh`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/init-ubuntu.sh) und [`assets/init-debian.sh`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/init-debian.sh) eingetragen.
  - Exporte werden in [`CommandlineSdkRepository.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/CommandlineSdkRepository.kt) zusätzlich nach `/etc/environment`, `/etc/profile.d/codeforge.sh` und an den **Anfang** von `/root/.bashrc` geschrieben.

### 2. Entfernung des `FloatingTerminalWindow`
- `FloatingTerminalWindow` wurde wie gewünscht vollständig aus [`SetupScreen.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/onboarding/src/main/kotlin/com/codeforge/feature/onboarding/SetupScreen.kt) entfernt.

### 3. FileTreeDrawer & Editor UI / Zusammenspiel
- **Top-Left Drawer Icon**: In [`EditorScreen.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt) ein Menü-Icon (`Icons.Default.Menu`) oben links in die `TopAppBar` eingebunden, um das `FileTreeDrawer` jederzeit öffnen und schließen zu können.
- **Swipe Gesten**: `gesturesEnabled = true` im `ModalNavigationDrawer` aktiviert.
- **Tab Close Buttons**: Schließen-Buttons `(X)` (`Icons.Default.Close`) zu den Datei-Tabs im Editor hinzugefügt.
- **Titelanzeige**: Der Name der aktuell geöffneten Datei wird in der TopAppBar angezeigt.

### 4. Behebung von Editor-Crashes & Tipp-Verzögerungen
- **IndexOutOfBoundsException Fix**: In [`EditorViewModel.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorViewModel.kt) für alle Methoden (`saveFile`, `openFile`, `closeTab`, `selectTab`, `updateBuffer`, `updateCursorPosition`, `selectCompletionItem`) strikte Bereichsprüfungen (`index in openFiles.indices`) hinzugefügt.
- **Keine doppelten Tabs**: Bereits geöffnete Dateien werden beim Anklicken im `FileTreeDrawer` ausgewählt, anstatt neue Duplikat-Tabs zu erzeugen.
- **Cursor & Text-Reset Fix**: In [`SoraCodeEditor.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/SoraCodeEditor.kt) wird `editor.setText(content)` während der Texteingabe nicht mehr ausgeführt, wenn der Editor den Fokus hat (`!editor.isFocused`), um Cursor-Sprünge nach Zeile 1 und Eingabe-Lags zu verhindern.
