/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Sucht Layout-Dateien (XML-Dateien in `res/layout`, `res/layout-land` usw.) und `res`-Ordner im Projekt; reine JVM-Logik.
 */
package com.codeforge.feature.layoutdesigner.files

import java.io.File

object LayoutScanner {

    private val skipDirs = setOf("build", ".git", ".gradle", ".idea", "node_modules", "out")
    private const val MAX_DEPTH = 10
    val nameRegex = Regex("^[a-z][a-z0-9_]*$")

    /** Alle Ordner namens `res` unterhalb von `src/<variant>`; relative Pfade. */
    fun findResDirs(root: File): List<String> {
        val result = ArrayList<String>()
        walk(root, 0) { dir ->
            if (dir.name == "res" && dir.parentFile?.parentFile?.name == "src") {
                result += dir.relativeTo(root).path.replace(File.separatorChar, '/')
                false
            } else true
        }
        return result.sorted()
    }

    fun findLayouts(root: File): List<LayoutFileEntry> {
        val result = ArrayList<LayoutFileEntry>()
        walk(root, 0) { dir ->
            if (dir.name.startsWith("layout") && dir.parentFile?.name == "res") {
                dir.listFiles { f -> f.isFile && f.extension == "xml" }?.forEach {
                    result += LayoutFileEntry(it.path, it.relativeTo(root).path.replace(File.separatorChar, '/'))
                }
                false
            } else true
        }
        return result.sortedBy { it.relativePath }
    }

    private fun walk(dir: File, depth: Int, visit: (File) -> Boolean) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles { f -> f.isDirectory && f.name !in skipDirs && !f.name.startsWith('.') } ?: return
        for (child in children) {
            if (visit(child)) walk(child, depth + 1, visit)
        }
    }

    const val TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical" />
"""
}
