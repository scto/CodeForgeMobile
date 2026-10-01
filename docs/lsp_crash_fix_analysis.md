# Fehleranalyse und Behebung: `java.io.IOException: Stream closed` beim LSP-Client

## 1. Fehlerbeschreibung (aus `codeforge_debug.log`)

Im Log `codeforge_debug.log` trat beim Aufruf von `EditorViewModel.openFile(...)` folgender unbehandelter Absturz (Fatal Exception) im Main-Thread auf:

```text
2026-09-21 18:39:04.799 ERROR/CRASH: FATAL EXCEPTION in thread main
java.io.IOException: Stream closed
	at java.lang.ProcessBuilder$NullOutputStream.write(ProcessBuilder.java:433)
	at java.io.OutputStream.write(OutputStream.java:162)
	at java.io.BufferedOutputStream.flushBuffer(BufferedOutputStream.java:81)
	at java.io.BufferedOutputStream.flush(BufferedOutputStream.java:142)
	at java.io.BufferedOutputStream.flush(BufferedOutputStream.java:143)
	at com.codeforge.libs.lsp_client.LspRpcConnection$Companion.writeMessage(LspRpcConnection.kt:151)
	at com.codeforge.libs.lsp_client.LspRpcConnection$Companion.access$writeMessage(LspRpcConnection.kt:106)
	at com.codeforge.libs.lsp_client.LspRpcConnection.sendNotification(LspRpcConnection.kt:95)
	at com.codeforge.libs.lsp_client.LspClientRepositoryImpl.didOpen(LspClientRepositoryImpl.kt:99)
	at com.codeforge.feature.editor.EditorViewModel$openFile$1.invokeSuspend(EditorViewModel.kt:197)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at android.os.Handler.handleCallback(Handler.java:1095)
	...
```

---

## 2. Ursachenanalyse

1. **Nicht abgefangene I/O-Exception bei geschlossenen Server-Streams:**
   - Wenn ein gestarteter Language-Server-Prozess (z. B. `kotlin-language-server`) beendet wird, noch nicht bereit ist oder den Eingabe-Stream (`stdin`) schließt, wirft der Aufruf von `OutputStream.flush()` bzw. `OutputStream.write()` in `LspRpcConnection.writeMessage(...)` eine `java.io.IOException: Stream closed`.
2. **Ausführung auf dem Main/UI-Thread:**
   - Die Methode `EditorViewModel.openFile(...)` rief `lspClient.didOpen(...)` direkt auf dem Haupt-Thread auf.
   - Da `writeMessage(...)` die `IOException` nicht abgefangen hat, wurde die Coroutine auf `Dispatchers.Main.immediate` abgebrochen und führte zum Absturz der Anwendung.

---

## 3. Durchgeführte Behebungen

### A. Schutz in `LspRpcConnection.kt` ([`LspRpcConnection.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/lsp-client/src/main/kotlin/com/codeforge/libs/lsp_client/LspRpcConnection.kt))
- Die Methode `writeMessage(...)` wurde in einen `runCatching`-Block gehüllt und fängt sämtliche `IOException`-Ausnahmen ab. Tritt ein Stream-Fehler auf, wird dieser sicher im `AppLogger` protokolliert, ohne dass ein Crash ausgelöst wird.
- `sendRequest(...)` stellt sicher, dass Netz-/Stream-I/O stets auf `Dispatchers.IO` ausgeführt wird und unerwartete Abbrüche sauber als `Result.failure` zurückgegeben werden.

### B. Absicherung in `LspClientRepositoryImpl.kt` ([`LspClientRepositoryImpl.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/lsp-client/src/main/kotlin/com/codeforge/libs/lsp_client/LspClientRepositoryImpl.kt))
- Sämtliche LSP-Client-Operationen (`didOpen`, `didChange`, `didClose`, `requestCompletion`, `requestHover`, `requestFormat`) wurden explizit auf `Dispatchers.IO` verlegt und mit `runCatching` abgesichert.
- Falls der LSP-Prozess unerwartet beendet wird, schlägt die Benachrichtigung fehl, aber die App bleibt stabil weiter nutzbar.

---

## 4. Verifikation & Status

Der Build wurde mit den Korrekturen neu kompiliert und erfolgreich verifiziert:
- **Build-Ergebnis:** `BUILD SUCCESSFUL`
- **Erzeugte APK:** `app/build/outputs/apk/debug/app-debug.apk`
