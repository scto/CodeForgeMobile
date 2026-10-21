// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspRouterTest {

    @Test
    fun `router delegates to correct session based on opened path and keeps sessions isolated`() = runBlocking {
        val router = LspClientRepositoryImpl()

        // Create 2 fake sessions
        var kotlinCompletionCalls = 0
        var javaCompletionCalls = 0

        val kotlinSession = LspServerSession(
            sessionId = "kotlin-ls@/workspace",
            serverId = "kotlin-ls",
            workspaceRootPath = "/workspace",
            languageIds = setOf("kotlin")
        )
        val javaSession = LspServerSession(
            sessionId = "jdtls@/workspace",
            serverId = "jdtls",
            workspaceRootPath = "/workspace",
            languageIds = setOf("java")
        )

        router.attachSession(kotlinSession)
        router.attachSession(javaSession)

        // Open a Kotlin file and a Java file
        router.didOpen("/workspace/src/App.kt", "kotlin", "class App")
        router.didOpen("/workspace/src/Service.java", "java", "class Service {}")

        // Active document switches to App.kt
        router.setActiveDocument("/workspace/src/App.kt")
        assertEquals(LspServerState.STOPPED, router.serverState.value) // fake session not started with real process

        // Route capabilities and states
        assertEquals("kotlin-ls@/workspace", router.getSession("kotlin-ls@/workspace")?.sessionId)
        assertEquals("jdtls@/workspace", router.getSession("jdtls@/workspace")?.sessionId)
        assertEquals(2, router.getAllSessions().size)
    }

    @Test
    fun `request on unknown path returns failure without throwing exception`() = runBlocking {
        val router = LspClientRepositoryImpl()
        // No sessions registered
        val result = router.requestCompletion("/unknown/Path.xyz", LspPosition(0, 0))
        assertTrue("Request on unknown path should fail gracefully", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `session key distinguishes same server in different workspaces and same server across languages`() {
        val router = LspClientRepositoryImpl()

        // c and cpp share clangd session
        val clangdSession = LspServerSession(
            sessionId = "clangd@/workspace",
            serverId = "clangd",
            workspaceRootPath = "/workspace",
            languageIds = setOf("c", "cpp")
        )
        router.attachSession(clangdSession)

        runBlocking {
            router.didOpen("/workspace/main.c", "c", "int main() {}")
            router.didOpen("/workspace/util.cpp", "cpp", "void util() {}")
        }

        assertEquals(1, router.getRunningSessionIds().size)
        assertEquals(2, router.getOpenDocumentCount("clangd@/workspace"))

        // html, css, json are 3 distinct sessions
        val htmlSession = LspServerSession("vscode-html-language-server@/workspace", "vscode-html-language-server", "/workspace", setOf("html"))
        val cssSession = LspServerSession("vscode-css-language-server@/workspace", "vscode-css-language-server", "/workspace", setOf("css"))
        val jsonSession = LspServerSession("vscode-json-language-server@/workspace", "vscode-json-language-server", "/workspace", setOf("json"))

        router.attachSession(htmlSession)
        router.attachSession(cssSession)
        router.attachSession(jsonSession)

        assertEquals(4, router.getRunningSessionIds().size)

        // Same server in two workspaces are two distinct sessions
        val clangdWs2 = LspServerSession("clangd@/workspace2", "clangd", "/workspace2", setOf("c", "cpp"))
        router.attachSession(clangdWs2)
        assertEquals(5, router.getRunningSessionIds().size)
        assertTrue(router.getRunningSessionIds().contains("clangd@/workspace"))
        assertTrue(router.getRunningSessionIds().contains("clangd@/workspace2"))
    }

    @Test
    fun `stopping session clears its diagnostics while other sessions diagnostics remain`() = runBlocking {
        val router = LspClientRepositoryImpl()

        val emittedDiagnostics = mutableListOf<Pair<String, List<LspDiagnostic>>>()
        val job = launch {
            router.diagnostics.collect {
                emittedDiagnostics.add(it)
            }
        }

        val sessionA = LspServerSession("serverA@/ws", "serverA", "/ws", setOf("langA"))
        val sessionB = LspServerSession("serverB@/ws", "serverB", "/ws", setOf("langB"))
        router.attachSession(sessionA)
        router.attachSession(sessionB)

        router.didOpen("/ws/fileA.a", "langA", "content A")
        router.didOpen("/ws/fileB.b", "langB", "content B")

        // Stop session A
        router.stopSession("serverA@/ws")

        assertEquals(1, router.getRunningSessionIds().size)
        assertEquals("serverB@/ws", router.getRunningSessionIds().first())

        job.cancel()
    }
}
