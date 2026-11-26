/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Netzwerkwerkzeuge des Agenten (`web_fetch`, `http_request`) und die Abhängigkeitssuche (`search_dependency`,
 * `add_dependency`). Netzwerkzugriffe laufen als [ToolKind.EXECUTE] und brauchen daher eine Freigabe.
 * Vorbild: die Agenten-Werkzeuge von CodeAssist (GPL-3.0).
 */
package com.codeforge.libs.agent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** HTTP-Antwort. */
data class HttpResponse(val status: Int, val contentType: String?, val body: String)

/** Einfacher HTTP-Zugriff (in Tests durch einen Fake ersetzbar). */
fun interface HttpFetcher {
    /** Führt die Anfrage aus. */
    suspend fun fetch(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse
}

/** [HttpFetcher] über [HttpURLConnection]; liest höchstens [maxBytes]. */
class UrlConnectionFetcher(private val maxBytes: Int = 2_000_000, private val timeoutMs: Int = 20_000) : HttpFetcher {
    override suspend fun fetch(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "CodeForgeMobile-Agent")
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val bytes = stream?.use { s ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (out.size() < maxBytes) {
                        val n = s.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, minOf(n, maxBytes - out.size()))
                    }
                    out.toByteArray()
                } ?: ByteArray(0)
                HttpResponse(code, conn.contentType, String(bytes, Charsets.UTF_8))
            } finally {
                conn.disconnect()
            }
        }
}

/** Fabrik der Netzwerk- und Abhängigkeitswerkzeuge. */
object WebTools {
    private const val MAX_CHARS = 40_000

    /** Werkzeuge; [workspace] für `add_dependency`. */
    fun create(workspace: AgentWorkspace, http: HttpFetcher = UrlConnectionFetcher()): List<AgentTool> = listOf(
        WebFetch(http), HttpRequest(http), SearchDependency(MavenSearch(http)), AddDependency(workspace),
    )

    /** Prüft die URL: nur http(s). */
    internal fun checkUrl(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: throw ToolArgumentException("Ungültige URL: $raw")
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw ToolArgumentException("Nur http(s)-URLs sind erlaubt: $raw")
        }
        return uri.toString()
    }

    private class WebFetch(private val http: HttpFetcher) : AgentTool {
        override val name = "web_fetch"
        override val kind = ToolKind.EXECUTE
        override val description = "Downloads a web page (documentation, release notes, an issue) and returns its text."
        override val parameters = objectSchema(listOf("url"), "url" to stringProp("http(s) URL"))

        override fun describeCall(arguments: JsonObject) = "Webseite laden: ${arguments.optString("url")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val url = checkUrl(arguments.requireString("url"))
            val r = try {
                http.fetch("GET", url, mapOf("Accept" to "text/html,text/plain,application/json;q=0.9,*/*;q=0.5"), null)
            } catch (e: IOException) {
                return ToolResult.error("Laden fehlgeschlagen: ${e.message}")
            }
            if (r.status >= 400) return ToolResult.error("HTTP ${r.status} für $url")
            val text = if (r.contentType?.contains("html", ignoreCase = true) == true || r.body.trimStart().startsWith("<")) HtmlText.extract(r.body) else r.body
            return ToolResult(text.take(MAX_CHARS) + if (text.length > MAX_CHARS) "\n[gekürzt]" else "")
        }
    }

    private class HttpRequest(private val http: HttpFetcher) : AgentTool {
        override val name = "http_request"
        override val kind = ToolKind.EXECUTE
        override val description = "Sends an HTTP request (e.g. to test a REST API) and returns status, content type and body."
        override val parameters = objectSchema(
            listOf("method", "url"),
            "method" to stringProp("GET, POST, PUT, PATCH, DELETE or HEAD"),
            "url" to stringProp("http(s) URL"),
            "headers" to stringProp("Headers, one 'Name: value' per line"),
            "body" to stringProp("Request body"),
        )

        override fun describeCall(arguments: JsonObject) = "HTTP ${arguments.optString("method")?.uppercase()} ${arguments.optString("url")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val method = arguments.requireString("method").uppercase()
            if (method !in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")) throw ToolArgumentException("Methode nicht erlaubt: $method")
            val url = checkUrl(arguments.requireString("url"))
            val headers = arguments.optString("headers").orEmpty().lines().mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
            }.toMap()
            val r = try {
                http.fetch(method, url, headers, arguments.optString("body"))
            } catch (e: IOException) {
                return ToolResult.error("Anfrage fehlgeschlagen: ${e.message}")
            }
            val body = r.body.take(MAX_CHARS) + if (r.body.length > MAX_CHARS) "\n[gekürzt]" else ""
            return ToolResult("HTTP ${r.status}\nContent-Type: ${r.contentType ?: "-"}\n\n$body", isError = r.status >= 500)
        }
    }

    private class SearchDependency(private val search: MavenSearch) : AgentTool {
        override val name = "search_dependency"
        override val kind = ToolKind.EXECUTE
        override val description = "Searches Maven Central and Google's Maven repository for a library and its latest version. " +
            "Query either a name (e.g. 'room', 'retrofit') or group:artifact."
        override val parameters = objectSchema(listOf("query"), "query" to stringProp("Library name or group:artifact"))

        override fun describeCall(arguments: JsonObject) = "Abhängigkeit suchen: ${arguments.optString("query")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val query = arguments.requireString("query").trim()
            if (query.isEmpty()) throw ToolArgumentException("'query' ist leer")
            val hits = try {
                search.search(query)
            } catch (e: IOException) {
                return ToolResult.error("Suche fehlgeschlagen: ${e.message}")
            }
            if (hits.isEmpty()) return ToolResult("Nichts gefunden für '$query'")
            return ToolResult(hits.joinToString("\n") { "${it.group}:${it.artifact}:${it.latestVersion}  (${it.repository})" })
        }
    }

    private class AddDependency(private val ws: AgentWorkspace) : AgentTool {
        override val name = "add_dependency"
        override val kind = ToolKind.WRITE
        override val description = "Adds a library to a module: uses gradle/libs.versions.toml (version + library entry) when the " +
            "project has one, otherwise writes the coordinate into the module's build file."
        override val parameters = objectSchema(
            listOf("module", "coordinate"),
            "module" to stringProp("Gradle module path, e.g. :app"),
            "coordinate" to stringProp("group:artifact:version"),
            "configuration" to stringProp("implementation (default), api, ksp, testImplementation, …"),
            "alias" to stringProp("Catalog alias, e.g. androidx-room-runtime (default: derived from the artifact)"),
        )

        override fun describeCall(arguments: JsonObject) =
            "Abhängigkeit ${arguments.optString("coordinate")} zu ${arguments.optString("module")}"

        override suspend fun preview(arguments: JsonObject): String? = runCatching {
            DependencyAdder.plan(ws, arguments).joinToString("\n\n") { (path, before, after) -> "--- $path\n" + TextDiff.unified(before, after) }
        }.getOrNull()

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val plan = DependencyAdder.plan(ws, arguments)
            if (plan.isEmpty()) return ToolResult("Bereits vorhanden – nichts geändert")
            plan.forEach { (path, _, after) -> ws.writeFile(path, after) }
            return ToolResult("Hinzugefügt: ${plan.joinToString { it.first }}. Danach run_gradle zum Prüfen.")
        }
    }
}

/** Gefundene Bibliothek. */
data class ArtifactHit(val group: String, val artifact: String, val latestVersion: String, val repository: String)

/** Suche in Maven Central und Google Maven. */
class MavenSearch(private val http: HttpFetcher) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Bis zu 15 Treffer für [query] (Name oder `group:artifact`). */
    suspend fun search(query: String): List<ArtifactHit> {
        val parts = query.split(':')
        if (parts.size >= 2 && parts[0].contains('.')) {
            val (g, a) = parts
            return listOfNotNull(
                metadata(GOOGLE, g, a)?.let { ArtifactHit(g, a, it, "Google") },
                metadata(CENTRAL, g, a)?.let { ArtifactHit(g, a, it, "Maven Central") },
            )
        }
        return (google(query) + central(query)).distinctBy { it.group + ":" + it.artifact }.take(MAX_HITS)
    }

    private suspend fun metadata(base: String, group: String, artifact: String): String? {
        val r = http.fetch("GET", "$base/${group.replace('.', '/')}/$artifact/maven-metadata.xml", emptyMap(), null)
        if (r.status != 200) return null
        return MavenXml.latest(r.body)
    }

    private suspend fun central(query: String): List<ArtifactHit> {
        val q = URLEncoder.encode(query, "UTF-8")
        val r = http.fetch("GET", "https://search.maven.org/solrsearch/select?q=$q&rows=10&wt=json", emptyMap(), null)
        if (r.status != 200) return emptyList()
        return parseCentral(r.body)
    }

    /** Treffer aus der Antwort von `search.maven.org`. */
    internal fun parseCentral(body: String): List<ArtifactHit> {
        val docs = runCatching { json.parseToJsonElement(body).jsonObject["response"]?.jsonObject?.get("docs") as? JsonArray }.getOrNull() ?: return emptyList()
        return docs.mapNotNull { d ->
            val o = d as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            ArtifactHit(s("g") ?: return@mapNotNull null, s("a") ?: return@mapNotNull null, s("latestVersion") ?: s("v") ?: "?", "Maven Central")
        }
    }

    private suspend fun google(query: String): List<ArtifactHit> {
        val master = http.fetch("GET", "$GOOGLE/master-index.xml", emptyMap(), null).takeIf { it.status == 200 }?.body ?: return emptyList()
        val token = query.lowercase().replace(' ', '.')
        val groups = MavenXml.groups(master).filter { it.lowercase().contains(token) }.sortedBy { it.length }.take(3)
        return groups.flatMap { g ->
            val idx = http.fetch("GET", "$GOOGLE/${g.replace('.', '/')}/group-index.xml", emptyMap(), null).takeIf { it.status == 200 }?.body
            idx?.let { MavenXml.groupArtifacts(it) }.orEmpty().map { (a, v) -> ArtifactHit(g, a, v, "Google") }
        }.take(MAX_HITS)
    }

    private companion object {
        const val GOOGLE = "https://dl.google.com/android/maven2"
        const val CENTRAL = "https://repo1.maven.org/maven2"
        const val MAX_HITS = 15
    }
}

/** Kleine Leser für Maven-XML (ohne XML-Bibliothek). */
object MavenXml {
    /** Neueste stabile Version aus `maven-metadata.xml` (sonst `<release>`/`<latest>`). */
    fun latest(xml: String): String? {
        val versions = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1].trim() }.toList()
        val stable = versions.lastOrNull { !Regex("(?i)alpha|beta|rc|dev|snapshot|eap|preview|-m\\d").containsMatchIn(it) }
        return stable ?: Regex("<release>([^<]+)</release>").find(xml)?.groupValues?.get(1) ?: versions.lastOrNull()
    }

    /** Gruppen aus `master-index.xml`. */
    fun groups(xml: String): List<String> = Regex("<([a-zA-Z][\\w.-]*)\\s*/>").findAll(xml).map { it.groupValues[1] }.filter { it.contains('.') }.toList()

    /** Artefakte mit neuester stabiler Version aus `group-index.xml`. */
    fun groupArtifacts(xml: String): List<Pair<String, String>> =
        Regex("<([\\w.-]+)\\s+versions=\"([^\"]+)\"\\s*/>").findAll(xml).map { m ->
            val versions = m.groupValues[2].split(',')
            val stable = versions.lastOrNull { !Regex("(?i)alpha|beta|rc|dev").containsMatchIn(it) } ?: versions.last()
            m.groupValues[1] to stable
        }.toList()
}

/** Plant das Hinzufügen einer Abhängigkeit (Version Catalog oder direkt). */
object DependencyAdder {
    private val coordinateRegex = Regex("^([\\w.-]+):([\\w.-]+):([\\w.+-]+)$")

    /** Geplante Änderungen als (Pfad, vorher, nachher); leer, wenn schon vorhanden. */
    suspend fun plan(ws: AgentWorkspace, args: JsonObject): List<Triple<String, String, String>> {
        val coord = args.requireString("coordinate").trim()
        val m = coordinateRegex.matchEntire(coord) ?: throw ToolArgumentException("Koordinate muss group:artifact:version sein: $coord")
        val (group, artifact, version) = m.destructured
        val config = (args.optString("configuration") ?: "implementation").trim()
        if (!Regex("^[A-Za-z]+$").matches(config)) throw ToolArgumentException("Ungültige Konfiguration: $config")
        val module = args.requireString("module").trim().let { if (it.startsWith(":")) it else ":$it" }
        val dir = if (module == ":") "" else module.removePrefix(":").replace(':', '/')
        val buildPath = listOf("build.gradle.kts", "build.gradle").map { if (dir.isEmpty()) it else "$dir/$it" }
            .firstOrNull { ws.readFile(it) != null } ?: throw ToolArgumentException("Keine Build-Datei für Modul $module")
        val build = ws.readFile(buildPath)!!
        val kts = buildPath.endsWith(".kts")
        val toml = ws.readFile(CATALOG)
        val out = ArrayList<Triple<String, String, String>>()
        if (toml != null) {
            val existing = Regex("""(?m)^\s*([\w.-]+)\s*=\s*\{[^}\n]*(?:module\s*=\s*"$group:$artifact"|group\s*=\s*"$group"\s*,\s*name\s*=\s*"$artifact")""").find(toml)
            val alias = existing?.groupValues?.get(1) ?: (args.optString("alias")?.trim()?.ifEmpty { null } ?: defaultAlias(group, artifact))
            if (!Regex("^[a-z][\\w-]*$").matches(alias)) throw ToolArgumentException("Ungültiger Alias: $alias")
            val accessor = "libs." + alias.replace('-', '.').replace('_', '.')
            var newToml = toml
            if (existing == null) {
                val versionKey = alias
                newToml = addToSection(newToml, "versions", "$versionKey = \"$version\"")
                newToml = addToSection(newToml, "libraries", "$alias = { group = \"$group\", name = \"$artifact\", version.ref = \"$versionKey\" }")
                out += Triple(CATALOG, toml, newToml)
            }
            if (!build.contains(accessor + ")") && !Regex("\\b" + Regex.escape(accessor) + "\\b").containsMatchIn(build)) {
                val line = if (kts) "$config($accessor)" else "$config $accessor"
                out += Triple(buildPath, build, addDependencyLine(build, line))
            }
        } else if (!build.contains("$group:$artifact")) {
            val line = if (kts) "$config(\"$group:$artifact:$version\")" else "$config '$group:$artifact:$version'"
            out += Triple(buildPath, build, addDependencyLine(build, line))
        }
        return out
    }

    /** Alias wie in Android-Studio-Katalogen: `androidx.room` + `room-runtime` → `androidx-room-runtime`. */
    fun defaultAlias(group: String, artifact: String): String {
        val g = group.split('.').filterNot { it in setOf("com", "org", "io", "net", "dev") }
        val parts = (g + artifact.split('-', '.')).map { it.lowercase() }
        return parts.fold(mutableListOf<String>()) { acc, p -> acc.apply { if (lastOrNull() != p) add(p) } }.joinToString("-")
    }

    /** Hängt [entry] am Ende des Abschnitts `[name]` an (legt ihn an, wenn er fehlt). */
    fun addToSection(toml: String, name: String, entry: String): String {
        val header = Regex("(?m)^\\[$name]\\s*$").find(toml) ?: return toml.trimEnd() + "\n\n[$name]\n$entry\n"
        val next = Regex("(?m)^\\[").find(toml, header.range.last + 1)?.range?.first ?: toml.length
        val body = toml.substring(header.range.last + 1, next).trimEnd()
        return toml.substring(0, header.range.last + 1) + body + "\n" + entry + "\n" + (if (next < toml.length) "\n" else "") + toml.substring(next)
    }

    /** Fügt [line] am Ende des obersten `dependencies { }`-Blocks ein (legt ihn an, wenn er fehlt). */
    fun addDependencyLine(build: String, line: String): String {
        val start = Regex("(?m)^dependencies\\s*\\{").find(build) ?: return build.trimEnd() + "\n\ndependencies {\n    $line\n}\n"
        var depth = 0
        var i = start.range.last
        while (i < build.length) {
            when (build[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) break }
            }
            i++
        }
        if (i >= build.length) return build.trimEnd() + "\n\ndependencies {\n    $line\n}\n"
        val before = build.substring(0, i).trimEnd()
        return before + "\n    " + line + "\n" + build.substring(i)
    }

    private const val CATALOG = "gradle/libs.versions.toml"
}

/** Grober Text aus HTML: Skripte/Styles weg, Tags weg, Entities auflösen, Leerraum zusammenfassen. */
object HtmlText {
    /** Lesbarer Text aus [html]. */
    fun extract(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|tr|pre|section|article)>"), "\n")
        s = s.replace(Regex("(?i)<li[^>]*>"), "\n- ")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
        return s.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
