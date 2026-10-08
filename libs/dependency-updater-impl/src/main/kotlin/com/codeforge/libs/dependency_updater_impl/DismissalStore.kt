/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import java.io.File

/** Persistente „Dismiss“-Markierungen pro Projekt (Schlüssel = [com.codeforge.libs.dependency_updater_api.DependencyUpdate.key]). */
interface DismissalStore {
    fun dismissed(rootPath: String): Set<String>
    fun add(rootPath: String, key: String)
}

/** Einfaches Textformat: eine Zeile je Eintrag `projektpfad<TAB>schlüssel`. */
class FileDismissalStore(private val file: File) : DismissalStore {

    private val lock = Any()

    override fun dismissed(rootPath: String): Set<String> = synchronized(lock) {
        readAll().filter { it.first == clean(rootPath) }.map { it.second }.toSet()
    }

    override fun add(rootPath: String, key: String) {
        synchronized(lock) {
            val entries = readAll().toMutableList()
            val entry = clean(rootPath) to clean(key)
            if (entry in entries) return
            entries += entry
            file.parentFile?.mkdirs()
            AtomicFiles.write(file, entries.joinToString("\n") { "${it.first}\t${it.second}" } + "\n")
        }
    }

    private fun readAll(): List<Pair<String, String>> {
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines() }.getOrDefault(emptyList())
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
            }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
