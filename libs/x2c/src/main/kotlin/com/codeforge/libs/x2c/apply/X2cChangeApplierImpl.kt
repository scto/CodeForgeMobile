package com.codeforge.libs.x2c.apply

import com.codeforge.core.domain.model.ApplyResult
import com.codeforge.core.domain.model.JournalAction
import com.codeforge.core.domain.model.JournalEntry
import com.codeforge.core.domain.model.JournalStatus
import com.codeforge.core.domain.model.PlannedChange
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.UndoResult
import com.codeforge.core.domain.model.X2cJournal
import com.codeforge.core.domain.repository.ChangeRecorder
import com.codeforge.core.domain.repository.X2cChangeApplier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Task 52.9: Übernahme in einem Durchgang mit Journal.
 *
 * Reihenfolge: (1) Pfade und Voraussetzungen prüfen, noch keine Änderung; (2) Journal `Backup/x2c-journal-<ts>.json`
 * mit Status IN_PROGRESS schreiben; (3) Dateien einzeln atomar schreiben; (4) Folgeschritt; (5) Journal auf COMPLETE.
 * Jeder Fehler und jeder Abbruch nimmt alles anhand des Journals und des Backup-ZIPs zurück.
 */
class X2cChangeApplierImpl(private val clock: () -> Long = System::currentTimeMillis) : X2cChangeApplier {

    private class ApplyCancelled : Exception("Abgebrochen")

    private class Work(val change: PlannedChange, val file: File, val existed: Boolean, val inZip: Boolean)

    // ------------------------------------------------------------------ apply

    override suspend fun apply(
        projectPath: String,
        backupZipPath: String,
        changes: List<PlannedChange>,
        isCancelled: () -> Boolean,
        afterWrites: (suspend (ChangeRecorder) -> Unit)?
    ): Result<ApplyResult> {
        val project = File(projectPath).canonicalFile
        val zipFile = File(backupZipPath)
        val skipped = mutableListOf<String>()
        val entries = LinkedHashMap<String, JournalEntry>()
        val createdDirs = mutableListOf<String>()
        val registered = LinkedHashMap<String, Boolean>() // Pfad -> existierte im Backup unverändert
        var journalFile: File? = null
        var journalBase: X2cJournal? = null

        suspend fun step() {
            currentCoroutineContext().ensureActive()
            if (isCancelled()) throw ApplyCancelled()
        }

        try {
            require(project.isDirectory) { "Projektverzeichnis existiert nicht: $projectPath" }
            require(zipFile.isFile) { "Backup-ZIP fehlt: $backupZipPath (ohne Backup wird nichts geändert)" }
            val dup = changes.groupBy { it.relativePath.replace('\\', '/') }.filterValues { it.size > 1 }.keys
            require(dup.isEmpty()) { "Pfad mehrfach geplant: ${dup.joinToString()}" }

            val work = mutableListOf<Work>()
            ZipFile(zipFile).use { z ->
                for (c in changes) {
                    val rel = c.relativePath.replace('\\', '/')
                    val f = resolve(project, rel)
                    val existed = f.exists()
                    val zipHash = zipHash(z, rel)
                    when (c) {
                        is PlannedChange.Create -> {
                            if (existed && !c.overwrite) { skipped += rel; continue }
                            if (existed) requireUnchanged(rel, f, zipHash)
                        }
                        is PlannedChange.Replace -> {
                            require(existed) { "Datei fehlt: $rel" }
                            requireUnchanged(rel, f, zipHash)
                        }
                        is PlannedChange.Delete -> {
                            if (!existed) { skipped += rel; continue }
                            requireUnchanged(rel, f, zipHash)
                        }
                    }
                    work += Work(c, f, existed, zipHash != null)
                }
            }
            step()

            // Journal vorab (Absturz bleibt rücknehmbar)
            for (w in work) {
                val rel = w.change.relativePath.replace('\\', '/')
                entries[rel] = when (val c = w.change) {
                    is PlannedChange.Create -> if (w.existed) JournalEntry(JournalAction.MODIFIED, rel, sha(w.file.readBytes()), sha(c.content.toByteArray())) else JournalEntry(JournalAction.CREATED, rel, null, sha(c.content.toByteArray()))
                    is PlannedChange.Replace -> JournalEntry(JournalAction.MODIFIED, rel, sha(w.file.readBytes()), sha(c.content.toByteArray()))
                    is PlannedChange.Delete -> JournalEntry(JournalAction.DELETED, rel, sha(w.file.readBytes()), null)
                }
                if (w.change !is PlannedChange.Delete && !w.existed) {
                    var d = w.file.parentFile
                    val fresh = mutableListOf<File>()
                    while (d != null && !d.exists()) { fresh += d; d = d.parentFile }
                    fresh.forEach { createdDirs += it.relativeTo(project).path.replace('\\', '/') }
                }
            }
            val backupDir = File(project, "Backup").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(clock()))
            var jf = File(backupDir, "x2c-journal-$stamp.json")
            var n = 2
            while (jf.exists()) jf = File(backupDir, "x2c-journal-$stamp-${n++}.json")
            journalFile = jf
            val base = X2cJournal(1, clock(), zipFile.absolutePath, JournalStatus.IN_PROGRESS, entries.values.toList(), createdDirs.distinct().sortedBy { it.length })
            journalBase = base
            writeJournal(jf, base)

            // Schreiben
            for (w in work) {
                step()
                val rel = w.change.relativePath.replace('\\', '/')
                when (val c = w.change) {
                    is PlannedChange.Create -> writeAtomic(w.file, c.content.toByteArray())
                    is PlannedChange.Replace -> writeAtomic(w.file, c.content.toByteArray())
                    is PlannedChange.Delete -> Files.delete(w.file.toPath())
                }
            }

            // Folgeschritt (Build-Dateien, XML entfernen)
            if (afterWrites != null) {
                step()
                val recorder = object : ChangeRecorder {
                    override fun touch(relativePath: String, alreadyChanged: Boolean) {
                        val rel = relativePath.replace('\\', '/')
                        if (rel in entries || rel in registered) return
                        val f = resolve(project, rel)
                        ZipFile(zipFile).use { z ->
                            val zh = zipHash(z, rel)
                            if (!alreadyChanged) {
                                if (f.exists() && zh == null) throw IllegalStateException("$rel ist nicht im Backup und kann nicht zurückgenommen werden")
                                if (f.exists()) requireUnchanged(rel, f, zh)
                            }
                            registered[rel] = zh != null
                        }
                    }
                }
                afterWrites(recorder)
                step()
                ZipFile(zipFile).use { z ->
                    for (rel in registered.keys) {
                        val f = resolve(project, rel)
                        val zh = zipHash(z, rel)
                        val cur = if (f.isFile) sha(f.readBytes()) else null
                        when {
                            zh != null && cur != null && cur != zh -> entries[rel] = JournalEntry(JournalAction.MODIFIED, rel, zh, cur)
                            zh != null && cur == null -> entries[rel] = JournalEntry(JournalAction.DELETED, rel, zh, null)
                            zh == null && cur != null -> entries[rel] = JournalEntry(JournalAction.CREATED, rel, null, cur)
                        }
                    }
                }
            }

            val finalEntries = entries.values.map { e ->
                if (e.action == JournalAction.DELETED) e else e.copy(hashAfter = resolve(project, e.relativePath).takeIf { it.isFile }?.let { sha(it.readBytes()) })
            }
            val done = base.copy(status = JournalStatus.COMPLETE, entries = finalEntries)
            writeJournal(jf, done)
            return Result.success(ApplyResult(jf.absolutePath, done, skipped))
        } catch (t: Throwable) {
            val jf = journalFile
            if (jf != null && journalBase != null) {
                // Hook-Dateien, die nur registriert und evtl. verändert wurden, in die Rücknahme aufnehmen
                val all = LinkedHashMap(entries)
                for ((rel, inZip) in registered) if (rel !in all) {
                    all[rel] = JournalEntry(if (inZip) JournalAction.MODIFIED else JournalAction.CREATED, rel, null, null)
                }
                val failed = withContext(NonCancellable) {
                    rollbackNow(project, zipFile, all.values.toList(), journalBase!!.createdDirs)
                }
                withContext(NonCancellable) {
                    runCatching { writeJournal(jf, journalBase!!.copy(status = if (failed.isEmpty()) JournalStatus.ROLLED_BACK else JournalStatus.ROLLBACK_FAILED)) }
                }
                if (t is java.util.concurrent.CancellationException) throw t
                val msg = (t.message ?: t::class.simpleName ?: "Fehler") +
                    if (failed.isEmpty()) " (alle Änderungen wurden zurückgenommen)" else " (Rücknahme unvollständig: ${failed.joinToString()}; bitte Backup-ZIP verwenden)"
                return Result.failure(IllegalStateException(msg, t))
            }
            if (t is java.util.concurrent.CancellationException) throw t
            return Result.failure(t)
        }
    }

    /** Stellt alle Einträge wieder her; liefert die Pfade, bei denen es nicht ging. */
    private fun rollbackNow(project: File, zipFile: File, entries: List<JournalEntry>, createdDirs: List<String>): List<String> {
        val failures = mutableListOf<String>()
        val zip = runCatching { ZipFile(zipFile) }.getOrNull()
        try {
            for (e in entries.asReversed()) {
                runCatching {
                    val f = resolve(project, e.relativePath)
                    when (e.action) {
                        JournalAction.CREATED -> if (f.exists()) Files.delete(f.toPath())
                        JournalAction.MODIFIED, JournalAction.DELETED -> {
                            val z = zip ?: error("Backup-ZIP nicht lesbar")
                            restoreFromZip(z, e.relativePath, f)
                        }
                    }
                }.onFailure { failures += e.relativePath }
            }
        } finally {
            zip?.close()
        }
        removeEmptyDirs(project, createdDirs)
        return failures
    }

    // ------------------------------------------------------------------ undo

    override suspend fun undo(
        projectPath: String,
        journalPath: String,
        onConflict: suspend (String) -> UndoConflictPolicy
    ): Result<UndoResult> = try {
        val project = File(projectPath).canonicalFile
        val jf = File(journalPath)
        require(jf.isFile) { "Journal fehlt: $journalPath" }
        val journal = readJournal(jf)
        check(journal.status != JournalStatus.UNDONE) { "Lauf wurde bereits zurückgenommen" }
        check(journal.status != JournalStatus.ROLLED_BACK) { "Lauf wurde schon bei einem Fehler zurückgenommen, nichts zu tun" }
        val zipFile = File(journal.backupZipPath)
        if (journal.entries.any { it.action != JournalAction.CREATED } ) require(zipFile.isFile) { "Backup-ZIP fehlt: ${journal.backupZipPath}" }

        val restored = mutableListOf<String>(); val deleted = mutableListOf<String>()
        val kept = mutableListOf<String>(); val orig = mutableListOf<String>(); val already = mutableListOf<String>()
        val zip = if (zipFile.isFile) ZipFile(zipFile) else null
        try {
            for (e in journal.entries.asReversed()) {
                val f = resolve(project, e.relativePath)
                val cur = if (f.isFile) sha(f.readBytes()) else null
                val state = when (e.action) {
                    JournalAction.CREATED -> when (cur) { null -> 0; e.hashAfter -> 1; else -> 2 }
                    JournalAction.MODIFIED -> when (cur) { e.hashBefore -> 0; e.hashAfter -> 1; else -> 2 }
                    JournalAction.DELETED -> when (cur) { null -> 1; e.hashBefore -> 0; else -> 2 }
                } // 0 = schon Original, 1 = unverändert seit dem Lauf, 2 = Konflikt
                if (state == 0) { already += e.relativePath; continue }
                if (state == 2) {
                    when (onConflict(e.relativePath)) {
                        UndoConflictPolicy.KEEP -> { kept += e.relativePath; continue }
                        UndoConflictPolicy.SAVE_AS_ORIG -> {
                            if (f.exists()) {
                                var o = File(f.path + ".orig"); var i = 2
                                while (o.exists()) o = File(f.path + ".orig$i").also { i++ }
                                Files.move(f.toPath(), o.toPath())
                                orig += e.relativePath
                            }
                        }
                        UndoConflictPolicy.OVERWRITE -> Unit
                    }
                }
                when (e.action) {
                    JournalAction.CREATED -> { if (f.exists()) Files.delete(f.toPath()); deleted += e.relativePath }
                    else -> { restoreFromZip(requireNotNull(zip) { "Backup-ZIP fehlt" }, e.relativePath, f); restored += e.relativePath }
                }
            }
        } finally { zip?.close() }
        removeEmptyDirs(project, journal.createdDirs)
        if (kept.isEmpty()) writeJournal(jf, journal.copy(status = JournalStatus.UNDONE))
        Result.success(UndoResult(restored, deleted, kept, orig, already))
    } catch (c: java.util.concurrent.CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

    // ------------------------------------------------------------------ Helfer

    private fun resolve(project: File, rel: String): File {
        require(rel.isNotBlank() && !rel.startsWith("/") && !rel.contains(':')) { "Ungültiger Pfad: $rel" }
        val parts = rel.split('/')
        require(parts.none { it == ".." || it.isEmpty() }) { "Ungültiger Pfad: $rel" }
        require(parts.first() != ".git" && parts.first() != "Backup") { "Pfad nicht erlaubt: $rel" }
        val f = File(project, rel).canonicalFile
        if (!f.path.startsWith(project.path + File.separator)) throw SecurityException("Pfad liegt außerhalb des Projekts: $rel")
        return f
    }

    private fun requireUnchanged(rel: String, f: File, zipHash: String?) {
        check(zipHash != null) { "$rel ist nicht im Backup; Änderung würde sich nicht zurücknehmen lassen" }
        check(sha(f.readBytes()) == zipHash) { "$rel wurde seit dem Backup geändert; bitte Lauf neu starten" }
    }

    private fun zipHash(z: ZipFile, rel: String): String? =
        z.getEntry(rel)?.let { e -> z.getInputStream(e).use { sha(it.readBytes()) } }

    private fun restoreFromZip(z: ZipFile, rel: String, target: File) {
        val entry = z.getEntry(rel) ?: error("Nicht im Backup: $rel")
        writeAtomic(target, z.getInputStream(entry).use { it.readBytes() })
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.x2c-tmp")
        tmp.writeBytes(bytes)
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    }

    private fun removeEmptyDirs(project: File, dirs: List<String>) {
        for (d in dirs.sortedByDescending { it.length }) {
            val f = runCatching { resolve(project, d) }.getOrNull() ?: continue
            if (f.isDirectory && f.list().isNullOrEmpty()) f.delete()
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeJournal(file: File, j: X2cJournal) {
        val map = linkedMapOf<String, Any?>(
            "version" to j.version,
            "timestampEpochMillis" to j.timestampEpochMillis,
            "backupZipPath" to j.backupZipPath,
            "status" to j.status.name,
            "createdDirs" to j.createdDirs,
            "entries" to j.entries.map {
                linkedMapOf("action" to it.action.name, "relativePath" to it.relativePath, "hashBefore" to it.hashBefore, "hashAfter" to it.hashAfter)
            }
        )
        writeAtomic(file, MiniJson.write(map).toByteArray())
    }

    internal fun readJournal(file: File): X2cJournal {
        @Suppress("UNCHECKED_CAST")
        val m = MiniJson.parse(file.readText()) as? Map<String, Any?> ?: error("Journal ist kein Objekt")
        @Suppress("UNCHECKED_CAST")
        val entries = (m["entries"] as? List<Map<String, Any?>> ?: error("Journal ohne entries")).map {
            JournalEntry(
                JournalAction.valueOf(it["action"] as String),
                it["relativePath"] as String,
                it["hashBefore"] as String?,
                it["hashAfter"] as String?
            )
        }
        @Suppress("UNCHECKED_CAST")
        return X2cJournal(
            version = (m["version"] as Number).toInt(),
            timestampEpochMillis = (m["timestampEpochMillis"] as Number).toLong(),
            backupZipPath = m["backupZipPath"] as String,
            status = JournalStatus.valueOf(m["status"] as String),
            entries = entries,
            createdDirs = (m["createdDirs"] as? List<String>).orEmpty()
        )
    }
}
