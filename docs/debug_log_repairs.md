# Fehleranalyse und Reparaturen (codeforge_debug.log)

## Übersicht
Dieses Dokument beschreibt die Analyse der Fehler im Protokoll `codeforge_debug.log` sowie die durchgeführten Reparaturen im Codebase.

---

## Fehler 1: `IndexOutOfBoundsException` beim Schließen von Tabs

### Fehlerbeschreibung
```
java.lang.IndexOutOfBoundsException: Index 1 out of bounds for length 1
	at java.util.ArrayList.get(ArrayList.java:435)
	at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(TabRow.kt:502)
```

### Ursachenanalyse
Beim Schließen eines Tabs im Editor verringert sich die Größe der Liste `openFiles` (z. B. von 2 auf 1). Jetpack Compose Material3 `ScrollableTabRow` führt während des Re-Layouts eine Indikator-Messung durch. Wurde `selectedTabIndex` im vorherigen Render-Pass auf `1` gesetzt, griff der Indikator-Messcode kurzzeitig vor Abschluss der Rekomposition auf den Index `1` der aktualisierten Tab-Positions-Liste der Länge `1` zu.

### Behebung
In [EditorScreen.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorScreen.kt) wurde die `ScrollableTabRow` in ein `androidx.compose.runtime.key(uiState.openFiles.size)` eingewickelt. Dadurch wird `ScrollableTabRow` bei einer Änderung der Anzahl geöffneter Dateien vollständig und ohne Rassebedingungen neu initialisiert.

```kotlin
if (uiState.openFiles.isNotEmpty()) {
    val safeActiveIndex = uiState.activeFileIndex.coerceIn(0, uiState.openFiles.size - 1)
    androidx.compose.runtime.key(uiState.openFiles.size) {
        ScrollableTabRow(
            selectedTabIndex = safeActiveIndex
        ) {
            // Tab-Renderings...
        }
    }
}
```

---

## Fehler 2: `IOException: Stream closed` in `LspRpcConnection`

### Fehlerbeschreibung
```
ERROR/LspRpcConnection: Failed to write RPC message
java.io.IOException: Stream closed
	at java.lang.ProcessBuilder$NullOutputStream.write(ProcessBuilder.java:433)
	at com.codeforge.libs.lsp_client.LspRpcConnection$Companion.writeMessage(LspRpcConnection.kt:154)
```

### Ursachenanalyse
Wenn der Language Server Prozess heruntergefahren wird oder beendet ist, während ausstehende Schreib- oder Benachrichtigungsaufrufe an den Prozess gerichtet werden, schlägt der Output-Stream fehl. Bisher wurde dieser normale Beendigungsfall als kritischer Systemfehler (`AppLogger.e`) mit vollem Stacktrace geloggt.

### Behebung
In [LspRpcConnection.kt](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/lsp-client/src/main/kotlin/com/codeforge/libs/lsp_client/LspRpcConnection.kt) wird ein Schreibfehler bei geschlossenem Stream (`IOException`) nun abgefangen und als behandelte Warnung geloggt.

```kotlin
}.getOrElse { e ->
    if (e is java.io.IOException) {
        AppLogger.w("LspRpcConnection", "RPC stream closed: ${e.message}")
    } else {
        AppLogger.e("LspRpcConnection", "Failed to write RPC message", e)
    }
    false
}
```

---

## Verifizierung
Der Build wurde mit `gradlew assembleDebug` fehlerfrei durchgeführt (**BUILD SUCCESSFUL**).
