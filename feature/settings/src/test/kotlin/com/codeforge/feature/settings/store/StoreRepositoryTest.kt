package com.codeforge.feature.settings.store

import com.codeforge.feature.settings.lsp.ConditionalText
import com.codeforge.feature.settings.lsp.HttpTextFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun catalog(updated: String) =
        """{"schema":1,"updated":"$updated","categories":[],"items":[{"id":"a","category":"tool","name":"A"}]}"""

    private class FakeFetcher(var reply: Result<ConditionalText>) : HttpTextFetcher {
        val etags = mutableListOf<String?>()
        override suspend fun fetch(url: String): Result<String> = Result.failure(IllegalStateException("offline"))
        override suspend fun fetchIfChanged(url: String, etag: String?): Result<ConditionalText> {
            etags += etag
            return reply
        }
    }

    @Test
    fun bundledWhenNothingCached() = runTest {
        val repo = StoreRepository(FakeFetcher(Result.failure(Exception())), File(tmp.root, "c.json"), bundled = { catalog("b") })
        val cached = repo.cached("de")
        assertTrue(cached!!.bundled)
        assertEquals("b", cached.catalog.updated)
        assertTrue(repo.load("de") is StoreLoadResult.Cached)
    }

    @Test
    fun etagAndNotModified() = runTest {
        val fetcher = FakeFetcher(Result.success(ConditionalText.Fresh(catalog("1"), "\"e1\"")))
        var now = 0L
        val repo = StoreRepository(fetcher, File(tmp.root, "c.json"), clock = { now })
        assertEquals("1", (repo.refresh("de") as StoreLoadResult.Online).catalog.updated)
        assertNull(fetcher.etags.last())

        now += StoreRepository.MEMO_MS - 1
        repo.refresh("de")
        assertEquals(1, fetcher.etags.size)

        fetcher.reply = Result.success(ConditionalText.NotModified)
        val again = repo.refresh("de", force = true)
        assertEquals("\"e1\"", fetcher.etags.last())
        assertEquals("1", (again as StoreLoadResult.Online).catalog.updated)
        assertEquals("1", repo.cached("de")!!.catalog.updated)
    }

    @Test
    fun invalidKeepsLastGood() = runTest {
        val fetcher = FakeFetcher(Result.success(ConditionalText.Fresh(catalog("1"), null)))
        val repo = StoreRepository(fetcher, File(tmp.root, "c.json"))
        repo.refresh("de")
        fetcher.reply = Result.success(ConditionalText.Fresh("kaputt", null))
        assertTrue(repo.refresh("de", force = true) is StoreLoadResult.Failed)
        assertEquals("1", (repo.load("de", force = true) as StoreLoadResult.Cached).catalog.updated)
    }
}
