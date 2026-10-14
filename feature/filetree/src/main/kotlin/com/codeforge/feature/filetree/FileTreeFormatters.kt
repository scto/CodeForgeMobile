/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FilePropertiesData(
    val name: String,
    val path: String,
    val type: String,
    val sizeFormatted: String,
    val modifiedFormatted: String,
    val permissions: String
)

object FileTreeFormatters {

    fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.1f GB", gb)
    }

    fun formatFileDate(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return "-"
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
        return sdf.format(Date(timestamp))
    }

    fun formatChildCount(count: Int): String {
        return when {
            count <= 0 -> Res.string(R.string.filetree_keine_eintraege)
            count == 1 -> Res.string(R.string.filetree_ein_eintrag)
            else -> Res.string(R.string.filetree_eintraege, count)
        }
    }

    fun formatFilePermissions(file: File): String {
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "$r$w$x"
    }

    fun extractFileProperties(file: File, isDirectory: Boolean): FilePropertiesData {
        val name = file.name.ifBlank { file.absolutePath }
        val path = file.absolutePath
        val type = if (isDirectory) {
            Res.string(R.string.common_ordner)
        } else {
            val ext = file.extension.uppercase(Locale.ROOT)
            if (ext.isNotBlank()) {
                "${Res.string(R.string.common_datei)} ($ext)"
            } else {
                Res.string(R.string.common_datei)
            }
        }

        val sizeFormatted = if (isDirectory) {
            val children = file.listFiles()
            val count = children?.size ?: 0
            val totalBytes = children?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
            "${formatChildCount(count)} (${formatFileSize(totalBytes)})"
        } else {
            val bytes = file.length()
            "${formatFileSize(bytes)} ($bytes Bytes)"
        }

        val modifiedFormatted = formatFileDate(file.lastModified())
        val permissions = formatFilePermissions(file)

        return FilePropertiesData(
            name = name,
            path = path,
            type = type,
            sizeFormatted = sizeFormatted,
            modifiedFormatted = modifiedFormatted,
            permissions = permissions
        )
    }

    fun formatSubtitle(file: File, isDirectory: Boolean): String {
        val dateText = formatFileDate(file.lastModified())
        return if (isDirectory) {
            val childCount = file.list()?.size ?: 0
            val countText = formatChildCount(childCount)
            "$countText • $dateText"
        } else {
            val sizeText = formatFileSize(file.length())
            "$sizeText • $dateText"
        }
    }
}
