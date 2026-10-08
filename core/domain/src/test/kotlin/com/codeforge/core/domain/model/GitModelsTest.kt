package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitModelsTest {

    private fun c(h: String, vararg p: String) = GitCommitInfo(h, h, "a", "m $h", 0, p.toList())

    @Test fun urlHosts() {
        assertEquals("github.com", GitUrl.httpHost("https://github.com/x/y.git"))
        assertEquals("gitlab.example.org", GitUrl.httpHost("https://user:pw@GitLab.Example.org:8443/a/b"))
        assertNull(GitUrl.httpHost("git@github.com:x/y.git"))
        assertNull(GitUrl.httpHost("/sdcard/repo"))
        assertEquals("github.com", GitUrl.normalizeHostInput(" https://github.com/foo "))
        assertEquals("github.com", GitUrl.normalizeHostInput("GitHub.com"))
        assertTrue(GitUrl.isHttp("HTTP://x"))
    }

    @Test fun linearGraphUsesOneLane() {
        val rows = GitGraphLayout.layout(listOf(c("c3", "c2"), c("c2", "c1"), c("c1")))
        assertEquals(listOf(0, 0, 0), rows.map { it.lane })
        assertTrue(rows.all { it.laneCount == 1 })
        assertEquals(listOf(GraphEdge.Kind.OUT), rows[0].edges.map { it.kind })
        assertEquals(listOf(GraphEdge.Kind.IN), rows[2].edges.map { it.kind })
    }

    @Test fun branchAndMerge() {
        // m merges feature(f2→f1) into main(a2→a1); beide gehen auf base
        val rows = GitGraphLayout.layout(listOf(
            c("m", "a2", "f2"), c("f2", "f1"), c("a2", "a1"), c("f1", "base"), c("a1", "base"), c("base"),
        ))
        val byHash = rows.associateBy { it.commit.hash }
        assertEquals(0, byHash.getValue("m").lane)
        assertEquals(0, byHash.getValue("a2").lane)
        assertEquals(1, byHash.getValue("f2").lane)
        assertEquals(1, byHash.getValue("f1").lane)
        assertEquals(0, byHash.getValue("a1").lane)
        // a1 läuft in die bereits erwartete Lane 1 hinein (Zusammenlauf), base sitzt dort
        assertEquals(1, byHash.getValue("a1").edges.single { it.kind == GraphEdge.Kind.OUT }.toLane)
        val base = byHash.getValue("base")
        assertEquals(1, base.lane)
        assertEquals(1, base.edges.count { it.kind == GraphEdge.Kind.IN })
        assertEquals(2, rows.maxOf { it.laneCount })
        // m hat zwei OUT-Kanten (Lane 0 und 1)
        assertEquals(setOf(0, 1), byHash.getValue("m").edges.filter { it.kind == GraphEdge.Kind.OUT }.map { it.toLane }.toSet())
    }

    @Test fun twoTipsGetSeparateLanes() {
        val rows = GitGraphLayout.layout(listOf(c("x", "base"), c("y", "base"), c("base")))
        assertEquals(listOf(0, 1, 0), rows.map { it.lane })
        assertEquals(1, rows[1].edges.count { it.kind == GraphEdge.Kind.THROUGH })
    }

    @Test fun parsesUnifiedDiff() {
        val d = """diff --git a/src/A.kt b/src/A.kt
index 111..222 100644
--- a/src/A.kt
+++ b/src/A.kt
@@ -1,3 +1,4 @@ class A
 line1
-old
+new
+extra
 line3
\ No newline at end of file
diff --git a/img.png b/img.png
index 1..2 100644
Binary files a/img.png and b/img.png differ
diff --git a/New.kt b/New.kt
new file mode 100644
--- /dev/null
+++ b/New.kt
@@ -0,0 +1,1 @@
+hello
"""
        val files = UnifiedDiffParser.parse(d)
        assertEquals(3, files.size)
        val a = files[0]
        assertEquals("src/A.kt", a.displayPath)
        assertEquals(2, a.added); assertEquals(1, a.removed)
        val lines = a.hunks.single().lines
        assertEquals(listOf(DiffLineType.CONTEXT, DiffLineType.REMOVED, DiffLineType.ADDED, DiffLineType.ADDED, DiffLineType.CONTEXT, DiffLineType.NO_NEWLINE), lines.map { it.type })
        assertEquals(2, lines[1].oldNumber); assertNull(lines[1].newNumber)
        assertEquals(2, lines[2].newNumber); assertEquals(3, lines[3].newNumber)
        assertEquals(3, lines[4].oldNumber); assertEquals(4, lines[4].newNumber)
        assertTrue(files[1].isBinary)
        assertEquals("New.kt", files[2].displayPath)
        assertEquals(1, files[2].added)
    }

    @Test fun statusViews() {
        val s = GitRepoStatus(
            "main", entries = listOf(
                GitStatusEntry("a", GitFileStatus.ADDED, true), GitStatusEntry("a", GitFileStatus.MODIFIED, false),
                GitStatusEntry("b", GitFileStatus.UNTRACKED), GitStatusEntry("c", GitFileStatus.CONFLICTING),
            )
        )
        assertEquals(1, s.staged.size); assertEquals(2, s.unstaged.size); assertEquals(1, s.conflicts.size)
        assertFalse(GitIdentity("x", " ").isComplete); assertTrue(GitIdentity("x", "y@z").isComplete)
    }
}
