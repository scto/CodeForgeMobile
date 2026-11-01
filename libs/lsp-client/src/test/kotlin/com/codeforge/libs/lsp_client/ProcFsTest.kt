package com.codeforge.libs.lsp_client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProcFsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `vmrss is converted to bytes`() {
        assertEquals(2048L * 1024, ProcFs.parseVmRssBytes("Name:\tjava\nVmRSS:\t    2048 kB\nThreads:\t3"))
        assertNull(ProcFs.parseVmRssBytes("Name:\tkthreadd\n"))
    }

    @Test fun `ppid survives process names with spaces and parentheses`() {
        assertEquals(42L, ProcFs.parsePpid("123 (my (weird) name) S 42 123 123 0 -1"))
        assertNull(ProcFs.parsePpid("garbage"))
    }

    @Test fun `descendants follow the process tree`() {
        val tree = mapOf(2L to 1L, 3L to 2L, 4L to 2L, 9L to 8L)
        assertEquals(setOf(1L, 2L, 3L, 4L), ProcFs.descendants(1, tree))
    }

    @Test fun `tree rss sums parent and child`() {
        fun proc(pid: Long, ppid: Long, kb: Long) {
            File(tmp.root, "$pid").mkdirs()
            File(tmp.root, "$pid/stat").writeText("$pid (x) S $ppid 0 0")
            File(tmp.root, "$pid/status").writeText("VmRSS:\t$kb kB\n")
        }
        proc(10, 1, 100); proc(11, 10, 900); proc(12, 1, 5000)
        assertEquals(1000L * 1024, ProcFs.treeRssBytes(listOf(10), tmp.root))
        assertNull(ProcFs.treeRssBytes(listOf(99), tmp.root))
    }
}
