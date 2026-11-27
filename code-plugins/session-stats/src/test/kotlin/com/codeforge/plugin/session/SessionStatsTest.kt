package com.codeforge.plugin.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {
    @Test
    fun countsEvents() {
        val s = SessionStats()
        assertTrue(s.onEvent("file.saved", mapOf("path" to "/p/A.kt")))
        s.onEvent("file.saved", mapOf("path" to "/p/A.kt"))
        s.onEvent("file.saved", mapOf("path" to "/p/B.kt"))
        s.onEvent("file.opened", mapOf("path" to "/p/A.kt"))
        s.onEvent("build.finished", mapOf("success" to "false"))
        s.onEvent("build.finished", mapOf("success" to "true"))
        s.onEvent("git.committed", emptyMap())
        assertFalse(s.onEvent("file.closed", emptyMap()))
        assertEquals(2, s.savesOf("/p/A.kt"))
        assertEquals("A.kt", s.topFile())
        assertEquals("💾 3  📂 1  🔨 1/2  ✔ 1", s.statusText(long = false))
        assertTrue(s.summary().endsWith("A.kt"))
        s.reset()
        assertEquals(0, s.saves)
        assertEquals(0, s.savesOf("/p/A.kt"))
    }

    @Test
    fun commentStyles() {
        assertEquals("# x", SessionStatsPlugin.commentFor("python", "x"))
        assertEquals("<!-- x -->", SessionStatsPlugin.commentFor("xml", "x"))
        assertEquals("// x", SessionStatsPlugin.commentFor("kotlin", "x"))
    }
}
