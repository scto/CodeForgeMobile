// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import java.io.File

/** Liest Speicher- und Prozessbaum-Informationen aus /proc (Android: nur für Prozesse der eigenen UID lesbar). */
internal object ProcFs {

    /** `VmRSS:   12345 kB` → Bytes; `null`, wenn die Zeile fehlt (z. B. Kernel-Threads). */
    fun parseVmRssBytes(status: String): Long? {
        val line = status.lineSequence().firstOrNull { it.startsWith("VmRSS:") } ?: return null
        val kb = line.removePrefix("VmRSS:").trim().substringBefore(' ').toLongOrNull() ?: return null
        return kb * 1024
    }

    /** Eltern-PID aus `/proc/<pid>/stat`; der Prozessname in Klammern darf Leerzeichen und `)` enthalten. */
    fun parsePpid(stat: String): Long? {
        val rest = stat.substringAfterLast(')', "").trim().split(' ')
        return rest.getOrNull(1)?.toLongOrNull()
    }

    /** [root] samt allen Nachfahren aus der Eltern→Kinder-Zuordnung. */
    fun descendants(root: Long, parentOf: Map<Long, Long>): Set<Long> {
        val children = parentOf.entries.groupBy({ it.value }, { it.key })
        val result = linkedSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            for (child in children[queue.removeFirst()].orEmpty()) if (result.add(child)) queue.addLast(child)
        }
        return result
    }

    /** Summe der RSS-Bytes von [pids] samt Kindprozessen; `null`, wenn gar nichts lesbar war. */
    fun treeRssBytes(pids: List<Long>, procRoot: File = File("/proc")): Long? {
        val parentOf = HashMap<Long, Long>()
        procRoot.listFiles()?.forEach { dir ->
            val pid = dir.name.toLongOrNull() ?: return@forEach
            runCatching { File(dir, "stat").readText() }.getOrNull()?.let { parsePpid(it) }?.let { parentOf[pid] = it }
        }
        val all = pids.flatMapTo(linkedSetOf()) { descendants(it, parentOf) }
        var total = 0L
        var readable = false
        for (pid in all) {
            val rss = runCatching { File(procRoot, "$pid/status").readText() }.getOrNull()?.let { parseVmRssBytes(it) }
            if (rss != null) { total += rss; readable = true }
        }
        return if (readable) total else null
    }
}
