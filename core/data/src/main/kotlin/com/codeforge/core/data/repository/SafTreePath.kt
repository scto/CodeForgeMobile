/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import java.io.File

/** Übersetzt SAF-Dokument-IDs des lokalen Speicher-Providers in echte Dateipfade. */
internal object SafTreePath {

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * [documentId] hat die Form `primary:Ordner/Unterordner` bzw. `<VOLUME-UUID>:Ordner`.
     * [primaryRoot] ist der Pfad des primären Speichers (`/storage/emulated/0`).
     * Liefert `null` für fremde Provider oder ungültige IDs; `..`-Segmente werden abgelehnt.
     */
    fun resolve(authority: String?, documentId: String?, primaryRoot: File, storageRoot: File = File("/storage")): File? {
        if (authority != EXTERNAL_STORAGE_AUTHORITY || documentId.isNullOrBlank()) return null
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        val relative = documentId.substringAfter(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        if (relative.split('/').any { it == ".." }) return null
        val base = if (volume == "primary") primaryRoot else File(storageRoot, volume)
        return if (relative.isEmpty()) base else File(base, relative)
    }
}
