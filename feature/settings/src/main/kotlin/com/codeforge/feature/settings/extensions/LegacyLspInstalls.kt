// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordner des früheren Erweiterungs-Managers (bis 2026-11 eingebaute Sprachserver). Die Server kommen jetzt aus
 * Plugins; die alten Dateien werden nicht mehr benutzt und können gelöscht werden.
 */
@Singleton
class LegacyLspInstalls internal constructor(private val dirs: List<File>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        listOf(File("/data/data/com.codeforge.app/files/usr/opt/extensions"), File(context.filesDir, "extensions"))
    )

    /** Belegter Speicher in Bytes (0 = nichts vorhanden). */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        dirs.filter { it.isDirectory }.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    /** Löscht die alten Ordner; liefert `false`, wenn etwas stehen blieb. */
    suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        dirs.filter { it.exists() }.all { it.deleteRecursively() }
    }
}
