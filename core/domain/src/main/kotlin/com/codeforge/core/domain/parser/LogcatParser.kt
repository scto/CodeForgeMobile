/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogLevel
import com.codeforge.core.domain.model.StackFrame
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogcatParser @Inject constructor() {

    private val idGenerator = AtomicLong(1L)

    private val headerRegex = Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s*([^:]*?)\s*:\s?(.*)$"""
    )

    private val stackFrameRegex = Regex(
        """^\s*at\s+([a-zA-Z0-9_$.]+)\.([a-zA-Z0-9_$<>-]+)\(([^:()]+):(\d+)\)"""
    )

    sealed interface ParseLineResult {
        data class NewEntry(val entry: LogEntry) : ParseLineResult
        data class Continuation(val text: String, val stackFrame: StackFrame?) : ParseLineResult
        data object Ignored : ParseLineResult
    }

    fun parseLine(rawLine: String): ParseLineResult {
        val line = rawLine.trimEnd('\r', '\n')
        if (line.isBlank() || line.startsWith("--------- beginning of")) {
            return ParseLineResult.Ignored
        }

        val match = headerRegex.find(line)
        if (match != null) {
            val (timestamp, pidStr, tidStr, levelStr, tag, message) = match.destructured
            val pid = pidStr.toIntOrNull() ?: 0
            val tid = tidStr.toIntOrNull() ?: 0
            val level = LogLevel.fromLetter(levelStr.firstOrNull() ?: 'D')
            val trimmedTag = tag.trim()

            val stackFrame = parseStackFrame(message)
            val isCrash = isCrashStart(trimmedTag, message, level)

            val entry = LogEntry(
                id = idGenerator.getAndIncrement(),
                timestamp = timestamp,
                pid = pid,
                tid = tid,
                level = level,
                tag = trimmedTag,
                message = message,
                isCrash = isCrash,
                stackFrames = if (stackFrame != null) listOf(stackFrame) else emptyList()
            )
            return ParseLineResult.NewEntry(entry)
        }

        // Fortsetzungszeile (z. B. Stacktrace ohne Header)
        val stackFrame = parseStackFrame(line)
        return ParseLineResult.Continuation(line, stackFrame)
    }

    fun parseLines(lines: List<String>): List<LogEntry> {
        val result = mutableListOf<LogEntry>()
        var currentEntry: LogEntry? = null

        for (line in lines) {
            when (val r = parseLine(line)) {
                is ParseLineResult.NewEntry -> {
                    // Prüfe, ob dies ein Fortsetzungs-Stacktrace-Log von AndroidRuntime ist
                    val prev = currentEntry
                    if (prev != null && prev.isCrash && r.entry.tag.equals("AndroidRuntime", ignoreCase = true) && prev.pid == r.entry.pid) {
                        val newFrames = if (r.entry.stackFrames.isNotEmpty()) {
                            prev.stackFrames + r.entry.stackFrames
                        } else {
                            prev.stackFrames
                        }
                        currentEntry = prev.copy(
                            message = "${prev.message}\n${r.entry.message}",
                            stackFrames = newFrames
                        )
                    } else {
                        if (currentEntry != null) {
                            result.add(currentEntry)
                        }
                        currentEntry = r.entry
                    }
                }
                is ParseLineResult.Continuation -> {
                    if (currentEntry != null) {
                        val newFrames = if (r.stackFrame != null) {
                            currentEntry.stackFrames + r.stackFrame
                        } else {
                            currentEntry.stackFrames
                        }
                        val isCrash = currentEntry.isCrash || r.text.contains("FATAL EXCEPTION", ignoreCase = true)
                        currentEntry = currentEntry.copy(
                            message = "${currentEntry.message}\n${r.text}",
                            isCrash = isCrash,
                            stackFrames = newFrames
                        )
                    }
                }
                is ParseLineResult.Ignored -> {}
            }
        }

        if (currentEntry != null) {
            result.add(currentEntry)
        }

        return result
    }

    fun parseStackFrame(line: String): StackFrame? {
        val match = stackFrameRegex.find(line) ?: return null
        val (className, methodName, fileName, lineStr) = match.destructured
        val lineNumber = lineStr.toIntOrNull() ?: return null
        return StackFrame(
            className = className,
            methodName = methodName,
            fileName = fileName,
            lineNumber = lineNumber,
            rawLine = line.trim()
        )
    }

    private fun isCrashStart(tag: String, message: String, level: LogLevel): Boolean {
        if (message.contains("FATAL EXCEPTION", ignoreCase = true)) return true
        if (tag.equals("AndroidRuntime", ignoreCase = true) && level >= LogLevel.ERROR) return true
        return false
    }
}
