package com.codeforge.core.data

import com.codeforge.core.data.repository.GitRepositoryImpl
import com.codeforge.core.domain.model.GitCredential
import com.codeforge.core.domain.model.GitCredentialInfo
import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitIdentity
import com.codeforge.core.domain.model.GitMergeStatus
import com.codeforge.core.domain.model.GitRepoState
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.testing.TestRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Läuft gegen echte JGit-Repositories in Temp-Ordnern; „Remote“ ist ein lokales Bare-Repo. */
class GitRepositoryImplTest {

    init { TestRes.install() }

    private class FakeSettings(var id: GitIdentity = GitIdentity("Tester", "t@example.org")) : GitSettingsRepository {
        override val identity: Flow<GitIdentity> = MutableStateFlow(id)
        override val credentials: Flow<List<GitCredentialInfo>> = MutableStateFlow(emptyList())
        override suspend fun currentIdentity() = id
        override suspend fun setIdentity(identity: GitIdentity) { id = identity }
        override suspend fun saveCredential(host: String, username: String, token: String) {}
        override suspend fun removeCredential(host: String) {}
        override suspend fun credentialFor(remoteUrl: String): GitCredential? = null
    }

    private val settings = FakeSettings()
    private val repo = GitRepositoryImpl(settings)

    private fun tmp(): File = File.createTempFile("cfgit", "").apply { delete(); mkdirs() }
    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }
    private fun File.write(name: String, text: String) = File(this, name).apply { parentFile.mkdirs(); writeText(text) }

    private fun initRepo(): File {
        val d = tmp()
        run { repo.init(d.path, null).getOrThrow() }
        return d
    }

    @Test fun initWithInitialCommit() {
        val d = tmp(); d.write("a.txt", "hi\n")
        val r = run { repo.init(d.path, "Initial commit").getOrThrow() }
        assertTrue(r.initialCommitCreated)
        val log = run { repo.log(d.path).getOrThrow() }
        assertEquals(1, log.size)
        assertEquals("Initial commit", log[0].message)
        assertEquals("t@example.org", log[0].authorEmail)
        assertEquals(listOf("HEAD -> main"), log[0].refs)
        assertEquals("main", run { repo.status(d.path).getOrThrow() }.branch)
        assertTrue(run { repo.isRepository(d.path) })
    }

    @Test fun initWithoutIdentitySkipsCommit() {
        settings.id = GitIdentity()
        val d = tmp(); d.write("a.txt", "hi\n")
        val r = run { repo.init(d.path, "Initial commit").getOrThrow() }
        assertFalse(r.initialCommitCreated)
        assertTrue(r.note!!.contains("Git-Einstellungen"))
        assertFalse(run { repo.status(d.path).getOrThrow() }.hasCommits)
    }

    @Test fun stageUnstageCommitAndStatus() {
        val d = initRepo()
        d.write("a.txt", "one\n"); d.write("dir/b.txt", "two\n")
        var st = run { repo.status(d.path).getOrThrow() }
        assertEquals(setOf("a.txt", "dir/b.txt"), st.unstaged.map { it.path }.toSet())
        assertTrue(st.unstaged.all { it.status == GitFileStatus.UNTRACKED })

        run { repo.stage(d.path, listOf("a.txt")).getOrThrow() }
        st = run { repo.status(d.path).getOrThrow() }
        assertEquals(listOf("a.txt"), st.staged.map { it.path })
        run { repo.unstage(d.path, listOf("a.txt")).getOrThrow() }           // ohne HEAD: rm --cached
        assertTrue(run { repo.status(d.path).getOrThrow() }.staged.isEmpty())

        run { repo.stageAll(d.path).getOrThrow() }
        assertTrue(run { repo.commit(d.path, "first").isSuccess })
        assertTrue(run { repo.status(d.path).getOrThrow() }.entries.isEmpty())

        d.write("a.txt", "one changed\n"); File(d, "dir/b.txt").delete()
        st = run { repo.status(d.path).getOrThrow() }
        assertEquals(GitFileStatus.MODIFIED, st.unstaged.single { it.path == "a.txt" }.status)
        assertEquals(GitFileStatus.DELETED, st.unstaged.single { it.path == "dir/b.txt" }.status)
        run { repo.stage(d.path, listOf("dir/b.txt", "a.txt")).getOrThrow() }  // inkl. Löschung
        st = run { repo.status(d.path).getOrThrow() }
        assertEquals(2, st.staged.size)
        assertEquals(GitFileStatus.DELETED, st.staged.single { it.path == "dir/b.txt" }.status)
        run { repo.unstage(d.path, listOf("a.txt")).getOrThrow() }            // mit HEAD: reset
        assertEquals(listOf("dir/b.txt"), run { repo.status(d.path).getOrThrow() }.staged.map { it.path })
    }

    @Test fun commitRequiresIdentityAndMessageAndChanges() {
        val d = initRepo(); d.write("a", "x")
        run { repo.stageAll(d.path) }
        assertTrue(run { repo.commit(d.path, "  ").isFailure })
        settings.id = GitIdentity()
        assertTrue(run { repo.commit(d.path, "m") }.exceptionOrNull()!!.message!!.contains("Git-Einstellungen"))
        settings.id = GitIdentity("T", "t@x")
        assertTrue(run { repo.commit(d.path, "m").isSuccess })
        val again = run { repo.commit(d.path, "again") }
        assertTrue(again.exceptionOrNull()!!.message!!.contains("Nichts zu committen"))
    }

    @Test fun discardRestoresAndDeletesUntracked() {
        val d = initRepo(); d.write("a.txt", "orig\n")
        run { repo.stageAll(d.path); repo.commit(d.path, "c") }
        d.write("a.txt", "changed\n"); d.write("new.txt", "n\n")
        run { repo.discard(d.path, listOf("a.txt", "new.txt")).getOrThrow() }
        assertEquals("orig\n", File(d, "a.txt").readText())
        assertFalse(File(d, "new.txt").exists())
    }

    @Test fun diffForModifiedStagedUntrackedAndNoHead() {
        val d = initRepo(); d.write("a.txt", "l1\nl2\n")
        run { repo.stage(d.path, listOf("a.txt")) }
        // Staged ohne HEAD → synthetischer Diff
        assertTrue(run { repo.diff(d.path, "a.txt", staged = true).getOrThrow() }.contains("+l1"))
        run { repo.commit(d.path, "c") }
        d.write("a.txt", "l1\nl2 changed\n"); d.write("u.txt", "untracked\n")
        val unstaged = run { repo.diff(d.path, "a.txt", staged = false).getOrThrow() }
        assertTrue(unstaged.contains("-l2") && unstaged.contains("+l2 changed"))
        val untracked = run { repo.diff(d.path, "u.txt", staged = false).getOrThrow() }
        assertTrue(untracked.contains("new file mode") && untracked.contains("+untracked"))
        run { repo.stage(d.path, listOf("a.txt")) }
        assertTrue(run { repo.diff(d.path, "a.txt", staged = true).getOrThrow() }.contains("+l2 changed"))
        assertEquals("", run { repo.diff(d.path, "a.txt", staged = false).getOrThrow() })
    }

    @Test fun branchesCheckoutDeleteAndGraph() {
        val d = initRepo(); d.write("a", "1\n")
        run { repo.stageAll(d.path); repo.commit(d.path, "base") }
        run { repo.createBranch(d.path, "feature").getOrThrow() }
        d.write("f", "f\n"); run { repo.stageAll(d.path); repo.commit(d.path, "on feature") }
        run { repo.checkout(d.path, "main").getOrThrow() }
        d.write("m", "m\n"); run { repo.stageAll(d.path); repo.commit(d.path, "on main") }

        val branches = run { repo.branches(d.path).getOrThrow() }
        assertEquals(listOf("main", "feature"), branches.map { it.name })
        assertTrue(branches[0].isCurrent)

        val merge = run { repo.merge(d.path, "feature").getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, merge.status)
        val log = run { repo.log(d.path).getOrThrow() }
        assertEquals(4, log.size)
        assertEquals(2, log[0].parents.size)                          // Merge-Commit
        assertTrue(log[0].message.startsWith("Merge branch 'feature'"))
        assertTrue(log[0].refs.contains("HEAD -> main"))
        assertTrue(log.first { it.message == "on feature" }.refs.contains("feature"))
        val rows = com.codeforge.core.domain.model.GitGraphLayout.layout(log)
        assertEquals(2, rows.maxOf { it.laneCount })

        assertTrue(run { repo.deleteBranch(d.path, "main").isFailure })     // aktueller Branch
        assertTrue(run { repo.deleteBranch(d.path, "feature").isSuccess })
        assertTrue(run { repo.createBranch(d.path, "bad name").isFailure })
        assertTrue(run { repo.createBranch(d.path, "main").isFailure })
    }

    @Test fun mergeConflictAndAbort() {
        val d = initRepo(); d.write("a.txt", "base\n")
        run { repo.stageAll(d.path); repo.commit(d.path, "base") }
        run { repo.createBranch(d.path, "other") }
        d.write("a.txt", "other\n"); run { repo.stageAll(d.path); repo.commit(d.path, "other change") }
        run { repo.checkout(d.path, "main") }
        d.write("a.txt", "main\n"); run { repo.stageAll(d.path); repo.commit(d.path, "main change") }

        val res = run { repo.merge(d.path, "other").getOrThrow() }
        assertEquals(GitMergeStatus.CONFLICTING, res.status)
        assertEquals(listOf("a.txt"), res.conflicts)
        val st = run { repo.status(d.path).getOrThrow() }
        assertEquals(GitRepoState.MERGING, st.state)
        assertEquals(listOf("a.txt"), st.conflicts.map { it.path })
        assertTrue(File(d, "a.txt").readText().contains("<<<<<<<"))

        run { repo.abortMerge(d.path).getOrThrow() }
        val after = run { repo.status(d.path).getOrThrow() }
        assertEquals(GitRepoState.NORMAL, after.state)
        assertEquals("main\n", File(d, "a.txt").readText())
    }

    @Test fun resolveConflictAndCommitCompletesMerge() {
        val d = initRepo(); d.write("a.txt", "base\n")
        run { repo.stageAll(d.path); repo.commit(d.path, "base") }
        run { repo.createBranch(d.path, "other") }
        d.write("a.txt", "other\n"); run { repo.stageAll(d.path); repo.commit(d.path, "o") }
        run { repo.checkout(d.path, "main") }
        d.write("a.txt", "main\n"); run { repo.stageAll(d.path); repo.commit(d.path, "m") }
        run { repo.merge(d.path, "other") }
        d.write("a.txt", "resolved\n")
        run { repo.stage(d.path, listOf("a.txt")).getOrThrow() }
        assertTrue(run { repo.commit(d.path, "Merge other").isSuccess })
        assertEquals(2, run { repo.log(d.path).getOrThrow() }[0].parents.size)
        assertEquals(GitRepoState.NORMAL, run { repo.status(d.path).getOrThrow() }.state)
    }

    @Test fun pushPullFetchAgainstBareRemote() {
        val bare = tmp()
        org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        val a = initRepo(); a.write("a.txt", "1\n")
        run { repo.stageAll(a.path); repo.commit(a.path, "c1") }
        assertTrue(run { repo.push(a.path) }.exceptionOrNull()!!.message!!.contains("Kein Remote"))
        run { repo.setRemote(a.path, "origin", bare.toURI().toString()).getOrThrow() }
        assertEquals("origin", run { repo.remotes(a.path).getOrThrow() }.single().name)
        run { repo.push(a.path).getOrThrow() }
        var st = run { repo.status(a.path).getOrThrow() }
        assertEquals("origin/main", st.upstream); assertEquals(0, st.ahead)

        // zweiter Klon pusht eine Änderung
        val bDir = tmp()
        org.eclipse.jgit.api.Git.cloneRepository().setURI(bare.toURI().toString()).setDirectory(bDir).call().close()
        run { repo.stageAll(bDir.path) }
        File(bDir, "b.txt").writeText("from b\n")
        run { repo.stageAll(bDir.path); repo.commit(bDir.path, "c2 from b"); repo.push(bDir.path).getOrThrow() }

        run { repo.fetch(a.path).getOrThrow() }
        st = run { repo.status(a.path).getOrThrow() }
        assertEquals(1, st.behind)
        val pull = run { repo.pull(a.path).getOrThrow() }
        assertEquals(GitMergeStatus.FAST_FORWARD, pull.status)
        assertTrue(File(a, "b.txt").exists())
        assertEquals(0, run { repo.status(a.path).getOrThrow() }.behind)

        // lokaler Commit ahead + Push abgelehnt, wenn Remote weiter ist
        a.write("a.txt", "2\n"); run { repo.stageAll(a.path); repo.commit(a.path, "c3 local") }
        File(bDir, "c.txt").writeText("c\n")
        run { repo.stageAll(bDir.path); repo.commit(bDir.path, "c4 from b"); repo.push(bDir.path).getOrThrow() }
        val rejected = run { repo.push(a.path) }
        assertTrue(rejected.isFailure)
        val merged = run { repo.pull(a.path).getOrThrow() }
        assertEquals(GitMergeStatus.MERGED, merged.status)
        run { repo.push(a.path).getOrThrow() }
    }

    @Test fun cloneFlowWithCredentialLookupForLocalRepo() {
        val bare = tmp()
        org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(bare).call().close()
        val target = File(tmp(), "clone")
        val events = run {
            val out = ArrayList<com.codeforge.core.domain.model.GitCloneProgress>()
            repo.clone(bare.toURI().toString(), target.path).collect { out += it }
            out
        }
        assertTrue(events.last() is com.codeforge.core.domain.model.GitCloneProgress.Completed)
        assertTrue(File(target, ".git").exists())
    }

    @Test fun notARepositoryGivesFailure() {
        assertTrue(run { repo.status(tmp().path) }.isFailure)
    }
}
