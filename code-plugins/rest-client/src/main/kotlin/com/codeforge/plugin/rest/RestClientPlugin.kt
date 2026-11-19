/**
 * Plugin: REST-Client
 *
 * Vorschau für `.http`/`.rest`: je Anfrage ein „Senden“-Knopf, darunter Status, Dauer, Header und Antwort.
 * Netz über `HttpURLConnection` (Zeitlimit 30 s, Antwort auf 1 MB begrenzt).
 */
package com.codeforge.plugin.rest

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_ui_api.PanelHost
import com.codeforge.libs.plugin_ui_api.PluginPreview
import com.codeforge.libs.plugin_ui_api.PluginUiExtensionPoints
import com.codeforge.libs.plugin_ui_api.PreviewFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RestClientPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginUiExtensionPoints.PREVIEW, HttpPreview)
    }

    override fun onUnload() = Unit
}

private data class HttpResponse(val status: Int, val message: String, val millis: Long, val headers: List<String>, val body: String, val error: String? = null)

private const val MAX_BODY = 1_000_000

private fun send(r: HttpRequest): HttpResponse {
    val start = System.nanoTime()
    return runCatching {
        require(r.url.startsWith("http://") || r.url.startsWith("https://")) { "Nur http(s)-Adressen: ${r.url}" }
        val c = URL(r.url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = r.method
            c.connectTimeout = 30_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            r.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            r.body?.let { b -> c.doOutput = true; c.outputStream.use { it.write(b.toByteArray()) } }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else c.inputStream
            val bytes = stream?.use { s -> s.readNBytesCompat(MAX_BODY) } ?: ByteArray(0)
            val headers = c.headerFields.filterKeys { it != null }.flatMap { (k, v) -> v.map { "$k: $it" } }
            HttpResponse(status, c.responseMessage.orEmpty(), (System.nanoTime() - start) / 1_000_000, headers, String(bytes))
        } finally {
            c.disconnect()
        }
    }.getOrElse { HttpResponse(0, "", (System.nanoTime() - start) / 1_000_000, emptyList(), "", it.message ?: it.toString()) }
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private object HttpPreview : PluginPreview {
    override val id = "http"
    override val title = "REST-Client"
    override val filePatterns = listOf("*.{http,rest}")

    @Composable
    override fun Content(file: PreviewFile, host: PanelHost, modifier: Modifier) {
        val text = file.text ?: remember(file.path) { runCatching { File(file.path).readText() }.getOrDefault("") }
        val requests = remember(text) { HttpFile.parse(text) }
        if (requests.isEmpty()) {
            Text("Keine Anfrage gefunden. Beispiel:\n\nGET https://httpbin.org/get\nAccept: application/json", modifier = modifier)
            return
        }
        Column(modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            requests.forEach { RequestCard(it) }
        }
    }
}

@Composable
private fun RequestCard(r: HttpRequest) {
    val scope = rememberCoroutineScope()
    var running by remember(r) { mutableStateOf(false) }
    var response by remember(r) { mutableStateOf<HttpResponse?>(null) }
    var showHeaders by remember(r) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Text("${r.method} ${r.url}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 2)
            }
            if (running) CircularProgressIndicator(Modifier.padding(8.dp))
            else FilledTonalButton(onClick = {
                running = true
                scope.launch { response = withContext(Dispatchers.IO) { send(r) }; running = false }
            }) { Text("Senden") }
        }
        response?.let { resp ->
            if (resp.error != null) {
                Text(resp.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("${resp.status} ${resp.message}") })
                    AssistChip(onClick = {}, label = { Text("${resp.millis} ms") })
                    AssistChip(onClick = { showHeaders = !showHeaders }, label = { Text("Header (${resp.headers.size})") })
                }
                if (showHeaders) Text(resp.headers.joinToString("\n"), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                val body = remember(resp) { HttpFile.prettyJson(resp.body) }
                Text(
                    body.take(50_000),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                )
            }
        }
    }
}
