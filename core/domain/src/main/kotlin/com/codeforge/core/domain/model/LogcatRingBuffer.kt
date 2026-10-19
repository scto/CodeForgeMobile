/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

class LogcatRingBuffer(val capacity: Int = 50_000) {

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(minOf(capacity, 1024))

    fun add(entry: LogEntry) = synchronized(lock) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
    }

    fun addAll(entries: Collection<LogEntry>) = synchronized(lock) {
        for (entry in entries) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
        }
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
    }

    fun size(): Int = synchronized(lock) {
        buffer.size
    }

    fun snapshot(): List<LogEntry> = synchronized(lock) {
        ArrayList(buffer)
    }

    fun filteredSnapshot(filter: LogcatFilter): List<LogEntry> = synchronized(lock) {
        buffer.filter { filter.matches(it) }
    }
}
