/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/**
 * Threadsicherer Ringpuffer für Logcat-Einträge; bei Überlauf werden die ältesten verworfen.
 *
 * @property capacity Höchstzahl gespeicherter Einträge.
 */
class LogcatRingBuffer(val capacity: Int = 50_000) {

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(minOf(capacity, 1024))

    /** Fügt einen Eintrag hinzu. */
    fun add(entry: LogEntry) = synchronized(lock) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
    }

    /** Fügt mehrere Einträge hinzu. */
    fun addAll(entries: Collection<LogEntry>) = synchronized(lock) {
        for (entry in entries) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
        }
    }

    /** Leert den Puffer. */
    fun clear() = synchronized(lock) {
        buffer.clear()
    }

    /** Aktuelle Anzahl der Einträge. */
    fun size(): Int = synchronized(lock) {
        buffer.size
    }

    /** Kopie aller Einträge (ältester zuerst). */
    fun snapshot(): List<LogEntry> = synchronized(lock) {
        ArrayList(buffer)
    }

    /** Kopie der Einträge, die [filter] erfüllen. */
    fun filteredSnapshot(filter: LogcatFilter): List<LogEntry> = synchronized(lock) {
        buffer.filter { filter.matches(it) }
    }
}
