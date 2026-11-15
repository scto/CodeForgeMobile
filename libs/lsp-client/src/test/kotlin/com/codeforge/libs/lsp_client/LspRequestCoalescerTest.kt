// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LspRequestCoalescerTest {

    @Test
    fun concurrentCallsShareOneRequest() = runTest {
        val coalescer = LspRequestCoalescer(scope = backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val block: suspend () -> Result<List<String>> = { calls++; gate.await(); Result.success(listOf("a")) }
        val first = async { coalescer.run("k", block = block) }
        val second = async { coalescer.run("k", block = block) }
        runCurrent()
        gate.complete(Unit)
        assertEquals(listOf("a"), first.await().getOrThrow())
        assertEquals(listOf("a"), second.await().getOrThrow())
        assertEquals(1, calls)
        assertEquals(1, coalescer.sharedFlights)
    }

    @Test
    fun cachesUntilInvalidated() = runTest {
        val coalescer = LspRequestCoalescer(scope = backgroundScope)
        var calls = 0
        val block: suspend () -> Result<String?> = { calls++; Result.success("doc") }
        coalescer.run("k", block = block)
        coalescer.run("k", block = block)
        assertEquals(1, calls)
        assertEquals(1, coalescer.cacheHits)
        coalescer.invalidate()
        coalescer.run("k", block = block)
        assertEquals(2, calls)
    }

    @Test
    fun emptyAndFailuresNotCached() = runTest {
        val coalescer = LspRequestCoalescer(scope = backgroundScope)
        var calls = 0
        repeat(2) { coalescer.run("empty") { calls++; Result.success(emptyList<String>()) } }
        repeat(2) { coalescer.run<String?>("null") { calls++; Result.success(null) } }
        repeat(2) { coalescer.run<String>("fail") { calls++; Result.failure(IllegalStateException("x")) } }
        repeat(2) { coalescer.run("nocache", cache = false) { calls++; Result.success("v") } }
        assertEquals(8, calls)
    }

    @Test
    fun entriesExpire() = runTest {
        var time = 0L
        val coalescer = LspRequestCoalescer(scope = backgroundScope, ttlMs = 100, now = { time })
        var calls = 0
        val block: suspend () -> Result<String> = { calls++; Result.success("v") }
        coalescer.run("k", block = block)
        time = 50
        coalescer.run("k", block = block)
        time = 200
        coalescer.run("k", block = block)
        assertEquals(2, calls)
    }

    @Test
    fun staleResultIsDropped() = runTest {
        val coalescer = LspRequestCoalescer(scope = backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val stale = async { coalescer.run("k") { calls++; gate.await(); Result.success("alt") } }
        runCurrent()
        coalescer.invalidate()
        gate.complete(Unit)
        assertEquals("alt", stale.await().getOrThrow())
        assertEquals("neu", coalescer.run("k") { calls++; Result.success("neu") }.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun cancelledWhenAllWaitersLeave() = runTest {
        val coalescer = LspRequestCoalescer(scope = backgroundScope)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val block: suspend () -> Result<String> = {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                Result.success("nie")
            } finally {
                cancelled.complete(Unit)
            }
        }
        val first = async { coalescer.run("k", block = block) }
        val second = async { coalescer.run("k", block = block) }
        started.await()
        first.cancel()
        runCurrent()
        assertTrue(!cancelled.isCompleted)
        second.cancel()
        cancelled.await()
    }
}
