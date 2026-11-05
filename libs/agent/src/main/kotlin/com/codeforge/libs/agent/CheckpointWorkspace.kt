/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Merkt sich, was jeder Agenten-Zug an Dateien geändert hat, damit die Änderungen als Diff gezeigt und zurückgenommen
 * werden können.
 */
package com.codeforge.libs.agent

/** Änderung einer Datei innerhalb eines Zugs; `null` = Datei existierte nicht (vorher) bzw. existiert nicht (nachher). */
data class FileChange(val path: String, val before: String?, val after: String?)

/** Ergebnis von [CheckpointWorkspace.revert]. */
data class RevertResult(val restored: List<String>, val failed: List<String>)

/**
 * [AgentWorkspace], der vor jeder Änderung den Ausgangszustand je Datei festhält. Pro Zug (eine Nutzernachricht samt
 * allem, was der Agent daraufhin tut) zählt der erste gesicherte Stand einer Datei – das ist der Zustand, zu dem
 * man zurückkehrt, egal wie oft der Zug die Datei danach ändert.
 *
 * Erfasst werden Schreiben, Anlegen und Löschen (bei Ordnern bis [MAX_SNAPSHOT_FILES] Dateien).
 */
class CheckpointWorkspace(private val delegate: AgentWorkspace) : AgentWorkspace by delegate {

    private class Turn(val id: Long) {
        val originals = LinkedHashMap<String, String?>()
        var truncated = false
    }

    private val turns = ArrayList<Turn>()
    private var current: Turn? = null
    private var nextId = 1L

    /** Beginnt einen neuen Zug und liefert seine Nummer. Ohne Aufruf werden Änderungen nicht festgehalten. */
    @Synchronized
    fun beginTurn(): Long = Turn(nextId++).also { turns += it; current = it }.id

    /** Nummern der bisher aufgezeichneten Züge (älteste zuerst). */
    @Synchronized
    fun turnIds(): List<Long> = turns.map { it.id }

    @Synchronized
    private fun turn(id: Long): Turn? = turns.firstOrNull { it.id == id }

    /** Aktuelle Änderungen des Zugs [turnId] gegenüber dem Ausgangszustand (unveränderte Dateien fehlen). */
    suspend fun changes(turnId: Long): List<FileChange> {
        val t = turn(turnId) ?: return emptyList()
        val snapshot = synchronized(this) { t.originals.entries.map { it.key to it.value } }
        return snapshot.mapNotNull { (path, before) ->
            val after = delegate.readFile(path)
            if (after == before) null else FileChange(path, before, after)
        }
    }

    /** Macht den Zug [turnId] rückgängig: stellt Inhalte wieder her und löscht neu angelegte Dateien. */
    suspend fun revert(turnId: Long): RevertResult {
        val t = turn(turnId) ?: return RevertResult(emptyList(), emptyList())
        val snapshot = synchronized(this) { t.originals.entries.map { it.key to it.value } }
        val restored = ArrayList<String>()
        val failed = ArrayList<String>()
        for ((path, before) in snapshot.asReversed()) {
            try {
                if (before == null) delegate.deletePath(path) else delegate.writeFile(path, before)
                restored += path
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += path
            }
        }
        if (t.truncated) failed += "(große Ordnerlöschung nur teilweise gesichert)"
        synchronized(this) { turns.remove(t); if (current === t) current = null }
        return RevertResult(restored, failed)
    }

    override suspend fun writeFile(path: String, content: String) {
        snapshot(path)
        delegate.writeFile(path, content)
    }

    override suspend fun deletePath(path: String): Boolean {
        snapshotTree(path)
        return delegate.deletePath(path)
    }

    private suspend fun snapshot(path: String) {
        val t = synchronized(this) { current } ?: return
        if (synchronized(this) { path in t.originals }) return
        val content = delegate.readFile(path)
        synchronized(this) { t.originals.putIfAbsent(path, content) }
    }

    private suspend fun snapshotTree(path: String) {
        val t = synchronized(this) { current } ?: return
        val children = delegate.listDir(path)
        if (children == null) {
            snapshot(path)
            return
        }
        val queue = ArrayDeque(listOf(path))
        var files = 0
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            for (e in delegate.listDir(dir).orEmpty()) {
                val child = if (dir.isEmpty()) e.name else "$dir/${e.name}"
                if (e.isDirectory) queue += child
                else if (files++ < MAX_SNAPSHOT_FILES) snapshot(child)
                else synchronized(this) { t.truncated = true }
            }
        }
    }

    private companion object {
        const val MAX_SNAPSHOT_FILES = 200
    }
}
