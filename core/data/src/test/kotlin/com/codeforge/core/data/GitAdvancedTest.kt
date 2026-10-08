package com.codeforge.core.data

import com.codeforge.core.data.repository.GitRepositoryImpl
import com.codeforge.core.domain.model.GitChangeKind
import com.codeforge.core.domain.model.GitConflictSide
import com.codeforge.core.domain.model.GitCredential
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitHunkAction
import com.codeforge.core.domain.model.GitIdentity
import com.codeforge.core.domain.model.GitMergeStatus
import com.codeforge.core.domain.model.GitRebaseOperation
import com.codeforge.core.domain.model.GitRepoState
import com.codeforge.core.domain.model.GitResetMode
import com.codeforge.core.domain.model.UnifiedDiffParser
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.testing.TestRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Erweiterte Git-Funktionen (Stash, Tags, Rebase, Cherry-Pick, Hunks, Konflikte …) gegen echte JGit-Repos. */
class GitAdvancedTest {

    init { TestRes.install() }

    private class FakeSettings : GitSettingsRepository {
        val id = GitIdentity("Tester", "t@example.org")
        override val identity: Flow<GitIdentity> = MutableStateFlow(id)
        override val credentials: Flow<List<GitCredentialInfo>> = MutableStateFlow(emptyList())
        override suspend fun currentIdentity() = id
        override suspend fun setIdentity(identity: GitIdentity) {}
        override suspend fun saveCredential(host: String, username: String, token: String) {}
        override suspend fun removeCredential(host: String) {}
        override suspend fun credentialFor(remoteUrl: String): GitCredential? = null
    }

    private val repo = GitRepositoryImpl(FakeSettings())

    private fun tmp(): File = File.createTempFile("cfgit2", "").apply { delete(); mkdirs() }
    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }
    private fun File.write(name: String, text: String) = File(this, name).apply { parentFile.mkdirs(); writeText(text) }
    private fun File.read(name: String) = File(this, name).readText()

    private fun initRepo(): File = tmp().also { d -> run { repo.init(d.path, null).getOrThrow() } }
    private fun commitAll(d: File, msg: String): String = run {
        repo.stageAll(d.path).getOrThrow()
        repo.commit(d.path, msg).getOrThrow()
    }
    private fun status(d: File) = run { repo.status(d.path).getOrThrow() }

    // ------------------------------------------------------------------ Stash

    @Test fun stashSaveListApplyPopDrop() {
        val d = initRepo(); d.write("a.txt", "one\n"); commitAll(d, "base")
        assertFalse(run { repo.stashSave(d.path, null).getOrThrow() }) // nichts zu stashen

        d.write("a.txt", "changed\n"); d.write("new.txt", "untracked\n")
        assertTrue(run { repo.stashSave(d.path, "mein Stash").getOrThrow() })
        assertEquals("one\n", d.read("a.txt"))
        assertFalse(File(d, "new.txt").exists())
        var list = run { repo.stashes(d.path).getOrThrow() }
        assertEquals(1, list.size)
        assertTrue(list[0].message, list[0].message.contains("mein Stash"))
        assertEquals("stash@{0}", list[0].ref)

        run { repo.stashApply(d.path, 0, drop = false).getOrThrow() }
        assertEquals("changed\n", d.read("a.txt"))
        assertEquals("untracked\n", d.read("new.txt"))
        assertEquals(1, run { repo.stashes(d.path).getOrThrow() }.size)

        run { repo.discard(d.path, listOf("a.txt", "new.txt")).getOrThrow() }
        run { repo.stashApply(d.path, 0, drop = true).getOrThrow() } // pop
        assertEquals("changed\n", d.read("a.txt"))
        assertTrue(run { repo.stashes(d.path).getOrThrow() }.isEmpty())

        run { repo.stashSave(d.path, null, includeUntracked = false).getOrThrow() }
        d.write("b.txt", "x\n"); run { repo.stashSave(d.path, "zweiter").getOrThrow() }
        list = run { repo.stashes(d.path).getOrThrow() }
        assertEquals(2, list.size)
        assertTrue(list[0].message.contains("zweiter"))
        run { repo.stashDrop(d.path, 0).getOrThrow() }
        assertEquals(1, run { repo.stashes(d.path).getOrThrow() }.size)
    }

    @Test fun stashApplyConflictIsReportedFriendly() {
        val d = initRepo(); d.write("a.txt", "one\n"); commitAll(d, "base")
        d.write("a.txt", "stashed\n"); run { repo.stashSave(d.path, null).getOrThrow() }
        d.write("a.txt", "local\n"); commitAll(d, "local")
        val r = run { repo.stashApply(d.path, 0, drop = false) }
        // Konflikt → entweder Fehler oder Konfliktmarker; in beiden Fällen darf nichts stillschweigend verloren gehen
        assertTrue(r.isFailure || d.read("a.txt").contains("<<<<<<<") || d.read("a.txt").contains("stashed"))
        assertEquals(1, run { repo.stashes(d.path).getOrThrow() }.size)
    }

    // ------------------------------------------------------------------ Tags

    @Test fun tagsCreateListDelete() {
        val d = initRepo(); d.write("a", "1\n"); val first = commitAll(d, "c1")
        d.write("a", "2\n"); commitAll(d, "c2")
        run { repo.createTag(d.path, "v1", null, first).getOrThrow() }
        run { repo.createTag(d.path, "v2", "Release 2").getOrThrow() }
        val tags = run { repo.tags(d.path).getOrThrow() }
        assertEquals(listOf("v2", "v1"), tags.map { it.name })      // neueste Commits zuerst
        assertFalse(tags.single { it.name == "v1" }.annotated)
        assertEquals(first, tags.single { it.name == "v1" }.commitHash)
        val v2 = tags.single { it.name == "v2" }
        assertTrue(v2.annotated); assertEquals("Release 2", v2.message)
        assertTrue(run { repo.log(d.path).getOrThrow() }.first().refs.contains("tag: v2"))
        assertTrue(run { repo.createTag(d.path, "v1", null) }.isFailure)
        assertTrue(run { repo.createTag(d.path, "bad name", null) }.isFailure)
        run { repo.deleteTag(d.path, "v1").getOrThrow() }
        assertEquals(listOf("v2"), run { repo.tags(d.path).getOrThrow() }.map { it.name })
    }

    // ------------------------------------------------------------------ Cherry-Pick / Revert / Reset

    private fun twoBranches(): Pair<File, String> {
        val d = initRepo(); d.write("a.txt", "base\n"); commitAll(d, "base")
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        d.write("f.txt", "feature file\n"); val pick = commitAll(d, "add f")
        run { repo.checkout(d.path, "main").getOrThrow() }
        return d to pick
    }

    @Test fun cherryPickClean() {
        val (d, pick) = twoBranches()
        val r = run { repo.cherryPick(d.path, pick).getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, r.status)
        assertEquals("feature file\n", d.read("f.txt"))
        val log = run { repo.log(d.path).getOrThrow() }
        assertEquals("add f", log.first { it.refs.any { r -> r.startsWith("HEAD") } }.message)
        assertEquals("t@example.org", log.first().authorEmail)
    }

    @Test fun cherryPickConflictThenCommit() {
        val d = initRepo(); d.write("a.txt", "base\n"); commitAll(d, "base")
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        d.write("a.txt", "feature\n"); val pick = commitAll(d, "feature change")
        run { repo.checkout(d.path, "main").getOrThrow() }
        d.write("a.txt", "main\n"); commitAll(d, "main change")

        val r = run { repo.cherryPick(d.path, pick).getOrThrow() }
        assertEquals(GitMergeStatus.CONFLICTING, r.status)
        assertEquals(listOf("a.txt"), r.conflicts)
        var st = status(d)
        assertEquals(GitRepoState.CHERRY_PICKING, st.state)
        assertNotNull(st.pendingMessage)
        assertTrue(st.pendingMessage!!.contains("feature change"))

        run { repo.resolveConflict(d.path, "a.txt", GitConflictSide.THEIRS).getOrThrow() }
        assertEquals("feature\n", d.read("a.txt"))
        run { repo.commit(d.path, st.pendingMessage!!).getOrThrow() }
        st = status(d)
        assertEquals(GitRepoState.NORMAL, st.state)
        assertTrue(st.entries.isEmpty())
    }

    @Test fun cherryPickConflictAbort() {
        val d = initRepo(); d.write("a.txt", "base\n"); commitAll(d, "base")
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        d.write("a.txt", "feature\n"); val pick = commitAll(d, "feature change")
        run { repo.checkout(d.path, "main").getOrThrow() }
        d.write("a.txt", "main\n"); commitAll(d, "main change")
        run { repo.cherryPick(d.path, pick).getOrThrow() }
        run { repo.abortMerge(d.path).getOrThrow() }
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertEquals("main\n", d.read("a.txt"))
    }

    @Test fun revertCommitAndConflict() {
        val d = initRepo(); d.write("a.txt", "one\n"); commitAll(d, "base")
        d.write("a.txt", "two\n"); val second = commitAll(d, "second")
        val r = run { repo.revert(d.path, second).getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, r.status)
        assertEquals("one\n", d.read("a.txt"))
        assertTrue(run { repo.log(d.path).getOrThrow() }.first().message.startsWith("Revert"))

        // Konflikt: zuerst „three“, dann zweiten Commit zurücknehmen
        d.write("a.txt", "three\n"); commitAll(d, "third")
        val c = run { repo.revert(d.path, second).getOrThrow() }
        assertEquals(GitMergeStatus.CONFLICTING, c.status)
        assertEquals(GitRepoState.REVERTING, status(d).state)
        run { repo.abortMerge(d.path).getOrThrow() }
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertEquals("three\n", d.read("a.txt"))
    }

    @Test fun resetModes() {
        val d = initRepo(); d.write("a.txt", "1\n"); val c1 = commitAll(d, "c1")
        d.write("a.txt", "2\n"); commitAll(d, "c2")
        run { repo.reset(d.path, c1, GitResetMode.SOFT).getOrThrow() }
        assertEquals("2\n", d.read("a.txt"))
        assertEquals(listOf("a.txt"), status(d).staged.map { it.path })
        run { repo.commit(d.path, "c2 again").getOrThrow() }
        run { repo.reset(d.path, c1, GitResetMode.MIXED).getOrThrow() }
        assertEquals("2\n", d.read("a.txt"))
        assertTrue(status(d).staged.isEmpty())
        assertEquals(listOf("a.txt"), status(d).unstaged.map { it.path })
        run { repo.reset(d.path, "HEAD", GitResetMode.HARD).getOrThrow() }
        assertEquals("1\n", d.read("a.txt"))
        assertEquals(1, run { repo.log(d.path).getOrThrow() }.size)
    }

    // ------------------------------------------------------------------ Rebase

    @Test fun rebaseClean() {
        val (d, _) = twoBranches()
        d.write("m.txt", "main\n"); commitAll(d, "main work")
        run { repo.checkout(d.path, "feature").getOrThrow() }
        val r = run { repo.rebase(d.path, "main").getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, r.status)
        val log = run { repo.log(d.path).getOrThrow() }
        assertEquals(listOf("add f", "main work", "base"), log.filter { it.refs.isNotEmpty() || true }.take(3).map { it.message }.let { l ->
            // lineare Historie: feature steht oben
            run { repo.log(d.path, 10).getOrThrow() }.map { it.message }.filter { it in setOf("add f", "main work", "base") }
        })
        assertTrue(File(d, "m.txt").exists() && File(d, "f.txt").exists())
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertEquals(GitMergeStatus.ALREADY_UP_TO_DATE, run { repo.rebase(d.path, "main").getOrThrow() }.status)
    }

    private fun conflictingFeature(): File {
        val d = initRepo(); d.write("a.txt", "base\n"); commitAll(d, "base")
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        d.write("a.txt", "feature\n"); commitAll(d, "feature change")
        run { repo.checkout(d.path, "main").getOrThrow() }
        d.write("a.txt", "main\n"); commitAll(d, "main change")
        run { repo.checkout(d.path, "feature").getOrThrow() }
        return d
    }

    @Test fun rebaseConflictResolveContinue() {
        val d = conflictingFeature()
        val r = run { repo.rebase(d.path, "main").getOrThrow() }
        assertEquals(GitMergeStatus.CONFLICTING, r.status)
        assertEquals(GitRepoState.REBASING, status(d).state)
        assertEquals(listOf("a.txt"), status(d).conflicts.map { it.path })

        run { repo.resolveConflict(d.path, "a.txt", GitConflictSide.THEIRS).getOrThrow() } // THEIRS = der eigene Commit beim Rebase
        val c = run { repo.rebaseControl(d.path, GitRebaseOperation.CONTINUE).getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, c.status)
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertEquals("feature\n", d.read("a.txt"))
        assertEquals("feature change", run { repo.log(d.path).getOrThrow() }.first().message)
    }

    @Test fun rebaseConflictAbortAndSkip() {
        var d = conflictingFeature()
        run { repo.rebase(d.path, "main").getOrThrow() }
        val a = run { repo.rebaseControl(d.path, GitRebaseOperation.ABORT).getOrThrow() }
        assertEquals(GitMergeStatus.ABORTED, a.status)
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertEquals("feature\n", d.read("a.txt"))

        d = conflictingFeature()
        run { repo.rebase(d.path, "main").getOrThrow() }
        val s = run { repo.rebaseControl(d.path, GitRebaseOperation.SKIP).getOrThrow() }
        assertTrue(s.status == GitMergeStatus.MERGED || s.status == GitMergeStatus.FAST_FORWARD || s.status == GitMergeStatus.ALREADY_UP_TO_DATE)
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertEquals("main\n", d.read("a.txt"))
    }

    // ------------------------------------------------------------------ Amend, Commit-Details, Verlauf, Blame

    @Test fun amendReplacesLastCommit() {
        val d = initRepo(); d.write("a", "1\n"); commitAll(d, "first")
        d.write("a", "2\n"); commitAll(d, "second wrong")
        assertEquals("second wrong", run { repo.lastCommitMessage(d.path).getOrThrow() })
        d.write("b", "b\n"); run { repo.stageAll(d.path).getOrThrow() }
        run { repo.commit(d.path, "second right", amend = true).getOrThrow() }
        val log = run { repo.log(d.path).getOrThrow() }
        assertEquals(listOf("second right", "first"), log.map { it.message })
        assertEquals(listOf("a", "b"), run { repo.commitDetail(d.path, log[0].hash).getOrThrow() }.files.map { it.path })
        // Nur Nachricht ändern ohne neue Änderungen
        run { repo.commit(d.path, "reworded", amend = true).getOrThrow() }
        assertEquals("reworded", run { repo.log(d.path).getOrThrow() }.first().message)
        assertEquals(2, run { repo.log(d.path).getOrThrow() }.size)
        assertEquals(null, run { repo.lastCommitMessage(initRepo().path).getOrThrow() })
        assertTrue(run { repo.commit(initRepo().path, "x", amend = true) }.isFailure)
    }

    @Test fun commitDetailAndDiff() {
        val d = initRepo(); d.write("a.txt", "one\ntwo\n"); d.write("old.txt", "x\n"); val root = commitAll(d, "root")
        d.write("a.txt", "one\nTWO\n"); File(d, "old.txt").delete(); d.write("new.txt", "n\n")
        val second = commitAll(d, "second\n\nlonger body")

        val rootDetail = run { repo.commitDetail(d.path, root).getOrThrow() }
        assertEquals(listOf("a.txt", "old.txt"), rootDetail.files.map { it.path })
        assertTrue(rootDetail.files.all { it.kind == GitChangeKind.ADDED })

        val detail = run { repo.commitDetail(d.path, second).getOrThrow() }
        assertEquals("second\n\nlonger body", detail.fullMessage)
        assertEquals("Tester", detail.committerName)
        assertFalse(detail.isMerge)
        assertEquals(mapOf("a.txt" to GitChangeKind.MODIFIED, "new.txt" to GitChangeKind.ADDED, "old.txt" to GitChangeKind.DELETED),
            detail.files.associate { it.path to it.kind })

        val diff = run { repo.commitDiff(d.path, second, "a.txt").getOrThrow() }
        val files = UnifiedDiffParser.parse(diff)
        assertEquals(1, files.size)
        assertEquals(1, files[0].added); assertEquals(1, files[0].removed)
        val all = UnifiedDiffParser.parse(run { repo.commitDiff(d.path, second).getOrThrow() })
        assertEquals(3, all.size)
        assertTrue(run { repo.commitDiff(d.path, "deadbeef") }.isFailure)
    }

    @Test fun historyAndBlame() {
        val d = initRepo(); d.write("a.txt", "l1\nl2\n"); val c1 = commitAll(d, "c1")
        d.write("b.txt", "other\n"); commitAll(d, "unrelated")
        d.write("a.txt", "l1\nl2 changed\nl3\n"); val c3 = commitAll(d, "c3")
        val hist = run { repo.fileHistory(d.path, "a.txt").getOrThrow() }
        assertEquals(listOf("c3", "c1"), hist.map { it.message })
        val blame = run { repo.blame(d.path, "a.txt").getOrThrow() }
        assertEquals(listOf("l1", "l2 changed", "l3"), blame.map { it.text })
        assertEquals(listOf(c1, c3, c3), blame.map { it.commitHash })
        assertEquals("Tester", blame[0].author)
        assertEquals(1, blame[0].lineNumber)
        assertTrue(run { repo.blame(d.path, "nope.txt") }.isFailure)
    }

    // ------------------------------------------------------------------ Hunks

    private fun numbered(change: Set<Int> = emptySet()) =
        (1..30).joinToString("\n", postfix = "\n") { if (it in change) "line$it CHANGED" else "line$it" }

    @Test fun stageUnstageDiscardHunks() {
        val d = initRepo(); d.write("f.txt", numbered()); commitAll(d, "base")
        d.write("f.txt", numbered(setOf(3, 27)))
        val hunks = { staged: Boolean -> UnifiedDiffParser.parse(run { repo.diff(d.path, "f.txt", staged).getOrThrow() }).flatMap { it.hunks } }
        assertEquals(2, hunks(false).size)

        // Zweiten Hunk vormerken
        run { repo.applyHunks(d.path, "f.txt", listOf(1), GitHunkAction.STAGE).getOrThrow() }
        assertEquals(1, hunks(true).size)
        assertTrue(hunks(true)[0].lines.any { it.text == "line27 CHANGED" })
        assertEquals(1, hunks(false).size)
        assertTrue(hunks(false)[0].lines.any { it.text == "line3 CHANGED" })
        assertEquals(numbered(setOf(3, 27)), d.read("f.txt")) // Arbeitskopie unberührt
        val st = status(d)
        assertTrue(st.entries.any { it.path == "f.txt" && it.staged } && st.entries.any { it.path == "f.txt" && !it.staged })

        // Ersten Hunk der Arbeitskopie verwerfen
        run { repo.applyHunks(d.path, "f.txt", listOf(0), GitHunkAction.DISCARD).getOrThrow() }
        assertEquals(numbered(setOf(27)), d.read("f.txt"))
        assertTrue(hunks(false).isEmpty())

        // Vorgemerkten Hunk wieder zurücknehmen
        run { repo.applyHunks(d.path, "f.txt", listOf(0), GitHunkAction.UNSTAGE).getOrThrow() }
        assertTrue(hunks(true).isEmpty())
        assertEquals(1, hunks(false).size)
        assertEquals(numbered(setOf(27)), d.read("f.txt"))

        // Commit enthält nur den vorgemerkten Teil
        run { repo.applyHunks(d.path, "f.txt", listOf(0), GitHunkAction.STAGE).getOrThrow() }
        commitAllStaged(d, "only 27")
        assertTrue(status(d).entries.isEmpty())
        assertEquals(numbered(setOf(27)), run { repo.commitDiff(d.path, "HEAD") }.let { d.read("f.txt") })
    }

    private fun commitAllStaged(d: File, msg: String) { run { repo.commit(d.path, msg).getOrThrow() } }

    @Test fun hunksOnNewAndDeletedFiles() {
        val d = initRepo(); d.write("keep.txt", "k\n"); d.write("gone.txt", "g1\ng2\n"); commitAll(d, "base")
        // Untracked → ganze Datei
        d.write("new.txt", "n1\nn2\n")
        run { repo.applyHunks(d.path, "new.txt", listOf(0), GitHunkAction.STAGE).getOrThrow() }
        assertEquals(listOf("new.txt"), status(d).staged.map { it.path })
        run { repo.applyHunks(d.path, "new.txt", listOf(0), GitHunkAction.UNSTAGE).getOrThrow() }
        assertTrue(status(d).staged.isEmpty())
        run { repo.applyHunks(d.path, "new.txt", listOf(0), GitHunkAction.DISCARD).getOrThrow() }
        assertFalse(File(d, "new.txt").exists())

        // Gelöschte Datei hunk-weise vormerken → Index-Eintrag verschwindet
        File(d, "gone.txt").delete()
        run { repo.applyHunks(d.path, "gone.txt", listOf(0), GitHunkAction.STAGE).getOrThrow() }
        assertEquals(listOf("gone.txt"), status(d).staged.map { it.path })
        assertEquals(com.codeforge.core.domain.model.GitFileStatus.DELETED, status(d).staged.single().status)
        // … und wieder zurücknehmen
        run { repo.applyHunks(d.path, "gone.txt", listOf(0), GitHunkAction.UNSTAGE).getOrThrow() }
        assertTrue(status(d).staged.isEmpty())
        assertEquals(com.codeforge.core.domain.model.GitFileStatus.DELETED, status(d).unstaged.single().status)
        run { repo.applyHunks(d.path, "gone.txt", listOf(0), GitHunkAction.DISCARD).getOrThrow() }
        assertEquals("g1\ng2\n", d.read("gone.txt"))
    }

    @Test fun hunkErrors() {
        val d = initRepo(); d.write("f.txt", numbered()); commitAll(d, "base")
        assertTrue(run { repo.applyHunks(d.path, "f.txt", listOf(0), GitHunkAction.STAGE) }.isFailure)    // keine Änderungen
        assertTrue(run { repo.applyHunks(d.path, "f.txt", emptyList(), GitHunkAction.STAGE) }.isFailure)
        d.write("f.txt", numbered(setOf(3)))
        assertTrue(run { repo.applyHunks(d.path, "f.txt", listOf(5), GitHunkAction.STAGE) }.isFailure)    // Hunk existiert nicht
    }

    // ------------------------------------------------------------------ Konflikte

    @Test fun resolveMergeConflictWithOneSide() {
        val d = initRepo(); d.write("a.txt", "base\n"); d.write("b.txt", "b\n"); commitAll(d, "base")
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        d.write("a.txt", "feature\n"); d.write("b.txt", "b feature\n"); commitAll(d, "feature")
        run { repo.checkout(d.path, "main").getOrThrow() }
        d.write("a.txt", "main\n"); d.write("b.txt", "b main\n"); commitAll(d, "main")
        assertEquals(GitMergeStatus.CONFLICTING, run { repo.merge(d.path, "feature").getOrThrow() }.status)
        assertTrue(d.read("a.txt").contains("<<<<<<<"))

        run { repo.resolveConflict(d.path, "a.txt", GitConflictSide.OURS).getOrThrow() }
        run { repo.resolveConflict(d.path, "b.txt", GitConflictSide.THEIRS).getOrThrow() }
        assertEquals("main\n", d.read("a.txt")); assertEquals("b feature\n", d.read("b.txt"))
        assertTrue(status(d).conflicts.isEmpty())
        run { repo.commit(d.path, "merged").getOrThrow() }
        assertEquals(GitRepoState.NORMAL, status(d).state)
        assertTrue(run { repo.resolveConflict(d.path, "a.txt", GitConflictSide.OURS) }.isFailure) // nicht (mehr) im Konflikt
    }

    @Test fun resolveDeleteModifyConflict() {
        val d = initRepo(); d.write("a.txt", "base\n"); d.write("x", "x\n"); commitAll(d, "base")
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        File(d, "a.txt").delete(); commitAll(d, "delete a")
        run { repo.checkout(d.path, "main").getOrThrow() }
        d.write("a.txt", "modified\n"); commitAll(d, "modify a")
        assertEquals(GitMergeStatus.CONFLICTING, run { repo.merge(d.path, "feature").getOrThrow() }.status)
        run { repo.resolveConflict(d.path, "a.txt", GitConflictSide.THEIRS).getOrThrow() } // feature hat gelöscht
        assertFalse(File(d, "a.txt").exists())
        assertTrue(status(d).conflicts.isEmpty())
    }

    // ------------------------------------------------------------------ Sonstiges

    @Test fun gitignoreAppendsOnce() {
        val d = initRepo()
        run { repo.addToGitignore(d.path, "/build/").getOrThrow() }
        run { repo.addToGitignore(d.path, "/build/").getOrThrow() }
        run { repo.addToGitignore(d.path, "*.log").getOrThrow() }
        assertEquals("/build/\n*.log\n", d.read(".gitignore"))
        d.write(".gitignore", "no-newline")
        run { repo.addToGitignore(d.path, "x") }
        assertEquals("no-newline\nx\n", d.read(".gitignore"))
        d.write("build/out.txt", "o"); d.write("a.log", "l")
        run { repo.addToGitignore(d.path, "/build/") }
        assertFalse(status(d).entries.any { it.path.startsWith("build") })
    }

    @Test fun renameBranchAndCheckoutCommitAndBranchAtCommit() {
        val d = initRepo(); d.write("a", "1\n"); val c1 = commitAll(d, "c1")
        d.write("a", "2\n"); commitAll(d, "c2")
        run { repo.createBranch(d.path, "old", checkout = false).getOrThrow() }
        run { repo.renameBranch(d.path, "old", "newname").getOrThrow() }
        assertTrue(run { repo.branches(d.path).getOrThrow() }.any { it.name == "newname" })
        assertTrue(run { repo.renameBranch(d.path, "newname", "main") }.isFailure)
        assertTrue(run { repo.renameBranch(d.path, "newname", "bad name") }.isFailure)

        run { repo.createBranch(d.path, "at-c1", checkout = true, startPoint = c1).getOrThrow() }
        assertEquals("1\n", d.read("a"))
        run { repo.checkout(d.path, "main").getOrThrow() }
        run { repo.checkoutCommit(d.path, c1).getOrThrow() }
        val st = status(d)
        assertTrue(st.detachedHead); assertEquals(null, st.branch)
    }

    // ------------------------------------------------------------------ Remote (lokales Bare-Repo)

    private fun withRemote(): Pair<File, File> {
        val bare = tmp()
        org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        val d = initRepo(); d.write("a", "1\n"); commitAll(d, "c1")
        run { repo.setRemote(d.path, "origin", bare.toURI().toString()).getOrThrow() }
        run { repo.push(d.path).getOrThrow() }
        return d to bare
    }

    @Test fun pushTagAndDeleteRemoteBranch() {
        val (d, bare) = withRemote()
        run { repo.createTag(d.path, "v1", null).getOrThrow() }
        run { repo.pushTag(d.path, "v1").getOrThrow() }
        org.eclipse.jgit.api.Git.open(bare).use { assertNotNull(it.repository.findRef("refs/tags/v1")) }

        run { repo.createBranch(d.path, "topic").getOrThrow() }
        d.write("t", "t\n"); commitAll(d, "topic work")
        run { repo.push(d.path).getOrThrow() }
        org.eclipse.jgit.api.Git.open(bare).use { assertNotNull(it.repository.findRef("refs/heads/topic")) }
        run { repo.fetch(d.path).getOrThrow() }
        assertTrue(run { repo.branches(d.path).getOrThrow() }.any { it.name == "origin/topic" })

        run { repo.deleteRemoteBranch(d.path, "origin/topic").getOrThrow() }
        org.eclipse.jgit.api.Git.open(bare).use { assertEquals(null, it.repository.findRef("refs/heads/topic")) }
        assertFalse(run { repo.branches(d.path).getOrThrow() }.any { it.name == "origin/topic" })
        assertTrue(run { repo.deleteRemoteBranch(d.path, "nowhere/x") }.isFailure)

        run { repo.removeRemote(d.path, "origin").getOrThrow() }
        assertTrue(run { repo.remotes(d.path).getOrThrow() }.isEmpty())
    }

    @Test fun pullWithRebase() {
        val (d, bare) = withRemote()
        // Zweiter Klon pusht einen Commit
        val other = tmp()
        org.eclipse.jgit.api.Git.cloneRepository().setURI(bare.toURI().toString()).setDirectory(other).call().use { g ->
            File(other, "remote.txt").writeText("r\n")
            g.add().addFilepattern(".").call()
            g.commit().setMessage("remote commit").setAuthor("O", "o@x").setCommitter("O", "o@x").setSign(false).call()
            g.push().call()
        }
        d.write("local.txt", "l\n"); commitAll(d, "local commit")
        val r = run { repo.pull(d.path, rebase = true).getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, r.status)
        val msgs = run { repo.log(d.path).getOrThrow() }.map { it.message }
        assertEquals(listOf("local commit", "remote commit", "c1"), msgs.filter { it != "Merge branch" })
        assertFalse(run { repo.log(d.path).getOrThrow() }.any { it.parents.size > 1 })   // keine Merge-Commits
        assertTrue(File(d, "remote.txt").exists())
    }
}
