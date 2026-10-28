/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import java.io.File
import java.util.Locale

/** Reine Sortier-Logik des Dateibaums (ohne Compose, gut testbar). */
object FileTreeSorter {

    /** Zwischengespeicherte Dateiinfo, damit pro Vergleich keine Syscalls anfallen. */
    private class Item(val file: File) {
        val name: String = file.name
        val isDir: Boolean = file.isDirectory
        val hidden: Boolean = name.startsWith(".")
        val size: Long = if (isDir) 0L else file.length()
        val modified: Long = file.lastModified()
        val ext: String = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun groupRank(item: Item, mode: FileTreeSortMode): Int = when (mode) {
        FileTreeSortMode.MIXED -> 0
        FileTreeSortMode.GROUPED -> when {
            item.isDir && item.hidden -> 0
            item.isDir -> 1
            item.hidden -> 2
            else -> 3
        }
    }

    /** Natürlicher, groß/klein-unabhängiger Namensvergleich ("file2" < "file10"). */
    internal fun compareNames(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length.compareTo(nb.length)
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val rest = (a.length - i).compareTo(b.length - j)
        return if (rest != 0) rest else a.compareTo(b)
    }

    fun sort(files: List<File>, options: FileTreeOptions): List<File> {
        val items = files.map(::Item)
        val dir = if (options.descending) -1 else 1
        val comparator = Comparator<Item> { a, b ->
            val g = groupRank(a, options.sortMode).compareTo(groupRank(b, options.sortMode))
            if (g != 0) return@Comparator g
            val primary = when (options.sortBy) {
                FileTreeSortBy.NAME -> 0
                FileTreeSortBy.TYPE -> compareNames(a.ext, b.ext)
                FileTreeSortBy.SIZE -> a.size.compareTo(b.size)
                FileTreeSortBy.MODIFIED -> a.modified.compareTo(b.modified)
            }
            if (primary != 0) primary * dir else compareNames(a.name, b.name) * dir
        }
        return items.sortedWith(comparator).map { it.file }
    }
}
