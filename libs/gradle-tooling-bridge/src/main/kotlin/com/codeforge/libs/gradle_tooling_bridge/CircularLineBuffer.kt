/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-sicherer Ringpuffer für Build-Ausgabezeilen.
 * Wenn [maxLines] erreicht ist, werden die ältesten Zeilen verworfen.
 */
class CircularLineBuffer(val maxLines: Int = 20_000) {

    init {
        require(maxLines > 0) { "maxLines must be > 0" }
    }

    private val lock = ReentrantLock()
    private val buffer = ArrayList<String>(minOf(maxLines, 1024))
    private var head = 0
    private var count = 0

    /** Fügt eine Zeile hinzu; bei vollem Puffer wird die älteste überschrieben. */
    fun add(line: String) = lock.withLock {
        if (count < maxLines) {
            buffer.add(line)
            count++
        } else {
            buffer[head] = line
            head = (head + 1) % maxLines
        }
    }

    /** Fügt mehrere Zeilen hinzu. */
    fun addAll(lines: Collection<String>) = lock.withLock {
        for (line in lines) {
            if (count < maxLines) {
                buffer.add(line)
                count++
            } else {
                buffer[head] = line
                head = (head + 1) % maxLines
            }
        }
    }

    /** Anzahl der gespeicherten Zeilen. */
    val lineCount: Int get() = lock.withLock { count }

    /** Leert den Puffer. */
    fun clear() = lock.withLock {
        buffer.clear()
        head = 0
        count = 0
    }

    /** Alle Zeilen, älteste zuerst. */
    fun toList(): List<String> = lock.withLock {
        if (count < maxLines) {
            ArrayList(buffer)
        } else {
            val result = ArrayList<String>(count)
            for (i in 0 until count) {
                result.add(buffer[(head + i) % maxLines])
            }
            result
        }
    }

    /** Alle Zeilen als ein Text mit Zeilenumbrüchen. */
    fun getAllText(): String = toList().joinToString("\n")
}
