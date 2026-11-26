package com.codeforge.libs.agent

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAgentToolsTest {

    @Test
    fun textEditsApply() {
        val text = "fun a() {\n    foo()\n}\n"
        val out = TextEdits.apply(
            text,
            listOf(TextEditSpec(1, 4, 1, 7, "bar"), TextEditSpec(0, 4, 0, 5, "b")),
        )
        assertEquals("fun b() {\n    bar()\n}\n", out)
    }

    @Test
    fun symbolDeclarations() {
        val r = SymbolDeclarations.regex("UserRepo")
        assertTrue(r.containsMatchIn("class UserRepo @Inject constructor("))
        assertTrue(r.containsMatchIn("interface UserRepo {"))
        assertTrue(r.containsMatchIn("public final class UserRepo implements Repo {"))
        assertFalse(r.containsMatchIn("val repo = UserRepo()"))
        assertTrue(SymbolDeclarations.regex("load").containsMatchIn("suspend fun load(id: Int)"))
        assertTrue(SymbolDeclarations.regex("btnOk").containsMatchIn("android:id=\"@+id/btnOk\""))
    }

    @Test
    fun findSymbolAndPosition() = runTest {
        val ws = FakeWorkspace(mapOf("a/Repo.kt" to "package a\n\nclass Repo\n", "b/Use.kt" to "val r = Repo()\n"))
        val tool = CodeIntelTools.create(ws, null).single { it.name == "find_symbol" }
        val res = tool.execute(args("name" to "Repo"))
        assertEquals("a/Repo.kt:3  class Repo", res.content)
        val pos = CodeIntelTools.position(ws, buildJsonObject { put("path", "b/Use.kt"); put("line", 1); put("symbol", "Repo") })
        assertEquals(CodePosition("b/Use.kt", 1, 9), pos)
    }

    @Test
    fun renameUsesIntelEdits() = runTest {
        val ws = FakeWorkspace(mapOf("A.kt" to "class Foo\n", "B.kt" to "val f = Foo()\n"))
        val intel = object : AgentCodeIntel {
            override suspend fun definition(position: CodePosition) = emptyList<CodePosition>()
            override suspend fun references(position: CodePosition) = listOf(CodePosition("B.kt", 1, 9))
            override suspend fun rename(position: CodePosition, newName: String) = mapOf(
                "A.kt" to listOf(TextEditSpec(0, 6, 0, 9, newName)),
                "B.kt" to listOf(TextEditSpec(0, 8, 0, 11, newName)),
            )
            override suspend fun codeActions(path: String, line: Int) = listOf(
                CodeActionInfo("Import x", "quickfix", mapOf("A.kt" to listOf(TextEditSpec(0, 0, 0, 0, "import x\n")))),
                CodeActionInfo("Run cmd", null, null),
            )
            override suspend fun format(path: String, content: String) = content.trim() + "\n"
            override suspend fun organizeImports(path: String) = null
        }
        val tools = CodeIntelTools.create(ws, intel).associateBy { it.name }
        tools.getValue("rename_symbol").execute(buildJsonObject { put("path", "A.kt"); put("line", 1); put("symbol", "Foo"); put("new_name", "Bar") })
        assertEquals("class Bar\n", ws.files["A.kt"])
        assertEquals("val f = Bar()\n", ws.files["B.kt"])
        val refs = tools.getValue("find_references").execute(buildJsonObject { put("path", "A.kt"); put("line", 1); put("column", 7) })
        assertEquals("B.kt:1:9  val f = Bar()", refs.content)
        val list = tools.getValue("list_quick_fixes").execute(buildJsonObject { put("path", "A.kt"); put("line", 1) })
        assertTrue(list.content.contains("2. Run cmd (nicht anwendbar"))
        assertTrue(tools.getValue("apply_quick_fix").execute(buildJsonObject { put("number", 2) }).isError)
        tools.getValue("apply_quick_fix").execute(buildJsonObject { put("number", 1) })
        assertEquals("import x\nclass Bar\n", ws.files["A.kt"])
        assertTrue(tools.getValue("organize_imports").execute(args("path" to "A.kt")).isError)
    }

    @Test
    fun movePathKeepsContentAndRejectsBinary() = runTest {
        val ws = FakeWorkspace(mapOf("src/a/X.kt" to "x", "src/a/Y.kt" to "y", "img.png" to "\u0000PNG"))
        val tools = ProjectTools.create(ws).associateBy { it.name }
        tools.getValue("move_path").execute(args("from" to "src/a", "to" to "src/b"))
        assertEquals(mapOf("src/b/X.kt" to "x", "src/b/Y.kt" to "y"), ws.files.filterKeys { it.startsWith("src/") })
        tools.getValue("rename_path").execute(args("path" to "src/b/X.kt", "new_name" to "Z.kt"))
        assertEquals("x", ws.files["src/b/Z.kt"])
        assertThrows(ToolArgumentException::class.java) { kotlinx.coroutines.runBlocking { PathMover.move(ws, "img.png", "a.png") } }
        assertEquals("\u0000PNG", ws.files["img.png"])
    }

    @Test
    fun projectOverviewAndTasks() = runTest {
        val ws = FakeWorkspace(
            mapOf(
                "settings.gradle.kts" to "rootProject.name = \"Demo\"\ninclude(\":app\", \":core:data\")\n",
                "app/build.gradle.kts" to "plugins {\n    alias(libs.plugins.android.application)\n}\nandroid {\n    namespace = \"com.x\"\n    compileSdk = 36\n    defaultConfig { minSdk = 26 }\n}\ntasks.register(\"hello\") {}\n",
                "core/data/build.gradle.kts" to "plugins {\n    id(\"com.android.library\")\n}\n",
                "gradle/libs.versions.toml" to "[versions]\nagp = \"9.0.1\"\n",
            ),
        )
        val info = GradleProjectInfo.read(ws)
        assertEquals(listOf(":", ":app", ":core:data"), info.modules.map { it.path })
        val app = info.modules.single { it.path == ":app" }
        assertEquals("com.x", app.namespace)
        assertEquals("36", app.compileSdk)
        assertEquals("26", app.minSdk)
        assertEquals(listOf("hello"), app.customTasks)
        assertTrue(info.render().contains("agp=9.0.1"))
        val tasks = ProjectTools.create(ws).single { it.name == "list_tasks" }.execute(buildJsonObject { })
        assertTrue(tasks.content.contains(":app:assembleDebug"))
        assertTrue(tasks.content.contains(":app:hello"))
        assertTrue(tasks.content.contains(":core:data:testDebugUnitTest"))
    }

    @Test
    fun addDependencyWithCatalog() = runTest {
        val ws = FakeWorkspace(
            mapOf(
                "app/build.gradle.kts" to "plugins {\n}\n\ndependencies {\n    implementation(libs.androidx.core)\n}\n",
                "gradle/libs.versions.toml" to "[versions]\ncore = \"1.0\"\n\n[libraries]\nandroidx-core = { group = \"androidx.core\", name = \"core-ktx\", version.ref = \"core\" }\n\n[plugins]\n",
            ),
        )
        val tool = WebTools.create(ws).single { it.name == "add_dependency" }
        tool.execute(args("module" to ":app", "coordinate" to "androidx.room:room-runtime:2.7.0"))
        val toml = ws.files.getValue("gradle/libs.versions.toml")
        assertTrue(toml.contains("androidx-room-runtime = \"2.7.0\""))
        assertTrue(toml.contains("androidx-room-runtime = { group = \"androidx.room\", name = \"room-runtime\", version.ref = \"androidx-room-runtime\" }\n\n[plugins]"))
        assertTrue(ws.files.getValue("app/build.gradle.kts").contains("    implementation(libs.androidx.core)\n    implementation(libs.androidx.room.runtime)\n}"))
        // Zweites Mal: nichts zu tun.
        assertEquals("Bereits vorhanden – nichts geändert", tool.execute(args("module" to "app", "coordinate" to "androidx.room:room-runtime:2.7.0")).content)
        assertEquals("androidx-room-runtime", DependencyAdder.defaultAlias("androidx.room", "room-runtime"))
        assertEquals("squareup-retrofit2-retrofit", DependencyAdder.defaultAlias("com.squareup.retrofit2", "retrofit"))
    }

    @Test
    fun addDependencyWithoutCatalogGroovy() = runTest {
        val ws = FakeWorkspace(mapOf("build.gradle" to "apply plugin: 'java'\n"))
        WebTools.create(ws).single { it.name == "add_dependency" }
            .execute(args("module" to ":", "coordinate" to "junit:junit:4.13.2", "configuration" to "testImplementation"))
        assertEquals("apply plugin: 'java'\n\ndependencies {\n    testImplementation 'junit:junit:4.13.2'\n}\n", ws.files["build.gradle"])
    }

    @Test
    fun mavenParsing() {
        assertEquals("2.7.0", MavenXml.latest("<metadata><versioning><versions><version>2.6.1</version><version>2.7.0</version><version>2.8.0-alpha01</version></versions></versioning></metadata>"))
        assertEquals(listOf("androidx.room", "com.google.android.material"), MavenXml.groups("<metadata><androidx.room/><com.google.android.material/><x/></metadata>"))
        assertEquals(listOf("room-runtime" to "2.7.0"), MavenXml.groupArtifacts("<androidx.room><room-runtime versions=\"2.6.1,2.7.0,2.8.0-beta01\"/></androidx.room>"))
        val hits = MavenSearch { _, _, _, _ -> HttpResponse(404, null, "") }
            .parseCentral("""{"response":{"docs":[{"g":"com.squareup.retrofit2","a":"retrofit","latestVersion":"3.0.0"}]}}""")
        assertEquals(listOf(ArtifactHit("com.squareup.retrofit2", "retrofit", "3.0.0", "Maven Central")), hits)
    }

    @Test
    fun webFetchExtractsText() = runTest {
        val http = HttpFetcher { _, url, _, _ -> HttpResponse(200, "text/html", "<html><head><title>x</title></head><body><script>bad()</script><h1>Hi &amp; bye</h1><p>A<br>B</p></body></html>") }
        val tool = WebTools.create(FakeWorkspace(), http).single { it.name == "web_fetch" }
        assertEquals("Hi & bye\nA\nB", tool.execute(args("url" to "https://example.com")).content)
        assertThrows(ToolArgumentException::class.java) { kotlinx.coroutines.runBlocking { tool.execute(args("url" to "file:///etc/passwd")) } }
        assertEquals(ToolKind.EXECUTE, tool.kind)
    }

    @Test
    fun askUserMemoryAndImages() = runTest {
        var asked: AgentQuestion? = null
        val memory = object : AgentMemory {
            var text = ""
            override suspend fun read() = text
            override suspend fun write(content: String) { text = content }
        }
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        val tools = InteractionTools.create(
            questioner = { q -> asked = q; "B" },
            memory = memory,
            images = { path -> if (path == "a.png") ImageData("image/png", "AAA") else null },
            preview = { _, _ -> Result.success(png) },
        ).associateBy { it.name }
        val answer = tools.getValue("ask_user").execute(buildJsonObject {
            put("question", "Welche?")
            put("options", JsonArray(listOf(JsonPrimitive("A"), JsonPrimitive("B"))))
        })
        assertEquals("Antwort des Nutzers: B", answer.content)
        assertEquals(listOf("A", "B"), asked?.options)
        tools.getValue("write_memory").execute(args("content" to "Build: ./gradlew :app:assembleDebug"))
        tools.getValue("write_memory").execute(args("content" to "Kotlin 2.2"))
        assertEquals("Build: ./gradlew :app:assembleDebug\nKotlin 2.2", tools.getValue("read_memory").execute(buildJsonObject { }).content)
        assertEquals(1, tools.getValue("view_image").execute(args("path" to "a.png")).images.size)
        assertTrue(tools.getValue("view_image").execute(args("path" to "b.txt")).isError)
        val shot = tools.getValue("screenshot_preview").execute(args("path" to "Ui.kt", "function" to "PreviewMain"))
        assertEquals("image/png", shot.images.single().mediaType)
        assertEquals("image/png", ImageTypes.detect(png))
        assertNull(ImageTypes.detect(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun onlyLatestMessageKeepsImages() {
        val img = listOf(ImageData("image/png", "x"))
        val msgs = listOf(
            AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("1", "a", images = img))),
            AgentMessage(AgentRole.ASSISTANT, listOf(ContentPart.Text("ok"))),
            AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("2", "b", images = img))),
        )
        val out = onlyLatestImages(msgs)
        assertTrue((out[0].parts[0] as ContentPart.ToolResultPart).images.isEmpty())
        assertEquals(1, (out[2].parts[0] as ContentPart.ToolResultPart).images.size)
    }

    @Test
    fun keyFailoverRotatesBeforeOutput() = runTest {
        var now = 0L
        val pool = ApiKeyPool(listOf("k1", "k2", " k2 "), clock = { now })
        assertEquals(2, pool.size)
        val used = mutableListOf<String>()
        val client = KeyFailoverClient(pool) { key ->
            LlmClient {
                flow {
                    used += key
                    if (key == "k1") emit(LlmStreamEvent.Failed("429", LlmFailureKind.RATE_LIMIT, 5_000)) else { emit(LlmStreamEvent.TextDelta("hi")); emit(endTurn) }
                }
            }
        }
        val events = client.stream(LlmRequest("", emptyList(), emptyList())).toList()
        assertEquals(listOf("k1", "k2"), used)
        assertEquals(LlmStreamEvent.TextDelta("hi"), events.first())
        // k1 pausiert: nächste Anfrage direkt mit k2.
        used.clear()
        client.stream(LlmRequest("", emptyList(), emptyList())).toList()
        assertEquals(listOf("k2"), used)
        now = 6_000
        assertEquals(0, pool.next())
    }

    @Test
    fun keyFailoverDoesNotRotateOnOtherErrors() = runTest {
        val pool = ApiKeyPool(listOf("a", "b"))
        val client = KeyFailoverClient(pool) { LlmClient { flow { emit(LlmStreamEvent.Failed("bad request", LlmFailureKind.OTHER)) } } }
        val events = client.stream(LlmRequest("", emptyList(), emptyList())).toList()
        assertEquals("bad request", (events.single() as LlmStreamEvent.Failed).message)
    }

    @Test
    fun requestPacerWaitsAndLearns() {
        var now = 0L
        val pacer = RequestPacer(configuredRpm = 2, clock = { now })
        assertEquals(0, pacer.delayMs()); pacer.record()
        now = 1_000; assertEquals(0, pacer.delayMs()); pacer.record()
        now = 2_000; assertEquals(58_000, pacer.delayMs())
        val learning = RequestPacer(clock = { now })
        repeat(5) { learning.record() }
        assertNull(learning.rpm)
        learning.learn()
        assertEquals(4, learning.rpm)
    }

    @Test
    fun loopbackQuery() {
        assertEquals(mapOf("code" to "a b", "state" to "x"), LoopbackRedirect.parseQuery("code=a%20b&state=x"))
        assertEquals(43, LoopbackRedirect.challenge("verifier").length)
    }
}
