package com.codeforge.libs.x2c.apply

import com.codeforge.core.domain.model.GitBranchInfo
import com.codeforge.core.domain.repository.GitBranchCommitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Git über das `git`-Programm (Termux/PRoot/Desktop). Kein Push; Prozesse ohne Rückfragen und mit Zeitlimit. */
class ProcessGitBranchCommitter(
    private val gitCommand: String = "git",
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutSeconds: Long = 60
) : GitBranchCommitter {

    private class Out(val code: Int, val text: String)

    private fun git(dir: File, vararg args: String, identity: Boolean = false): Out {
        val cmd = mutableListOf(gitCommand)
        if (identity) cmd += listOf("-c", "user.name=CodeForge", "-c", "user.email=codeforge@localhost")
        cmd += args
        val pb = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true)
        pb.environment()["GIT_TERMINAL_PROMPT"] = "0"
        val p = pb.start()
        val text = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { p.destroyForcibly(); error("git ${args.firstOrNull()} Zeitüberschreitung") }
        return Out(p.exitValue(), text.trim())
    }

    override suspend fun prepare(projectPath: String): Result<GitBranchInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(projectPath)
            check(File(dir, ".git").exists()) { "Kein Git-Projekt" }
            val inside = git(dir, "rev-parse", "--is-inside-work-tree")
            check(inside.code == 0 && inside.text == "true") { "Kein Git-Arbeitsverzeichnis" }
            val status = git(dir, "status", "--porcelain")
            check(status.code == 0) { "git status fehlgeschlagen: ${status.text.take(200)}" }
            val dirty = status.text.lines().filter { it.isNotBlank() && !it.drop(3).startsWith("Backup/") }
            check(dirty.isEmpty()) { "Arbeitsverzeichnis ist nicht sauber (nicht committete Änderungen); Zweig wird nicht angelegt" }
            val prev = git(dir, "rev-parse", "--abbrev-ref", "HEAD").takeIf { it.code == 0 }?.text
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(clock()))
            var name = "x2c/$day"
            var n = 2
            while (git(dir, "rev-parse", "--verify", "--quiet", "refs/heads/$name").code == 0) name = "x2c/$day-${n++}"
            val r = git(dir, "checkout", "-b", name)
            check(r.code == 0) { "Zweig konnte nicht angelegt werden: ${r.text.take(200)}" }
            GitBranchInfo(name, prev)
        }
    }

    override suspend fun commit(projectPath: String, relativePaths: List<String>, message: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(projectPath)
                require(relativePaths.isNotEmpty()) { "Nichts zu committen" }
                require(relativePaths.none { it.startsWith("-") || it.contains("..") }) { "Ungültiger Pfad" }
                val add = git(dir, "add", "-A", "--", *relativePaths.toTypedArray())
                check(add.code == 0) { "git add fehlgeschlagen: ${add.text.take(200)}" }
                val hasIdentity = git(dir, "config", "user.email").let { it.code == 0 && it.text.isNotBlank() }
                val c = git(dir, "commit", "-m", message, identity = !hasIdentity)
                check(c.code == 0) { "git commit fehlgeschlagen: ${c.text.take(200)}" }
                git(dir, "rev-parse", "HEAD").text
            }
        }
}
