/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.io.File

data class TextEdit(val start: Int, val end: Int, val expected: String, val replacement: String)

object VersionApplier {

    /** Wendet [edits] an; bricht ohne Änderung ab, wenn eine Stelle nicht mehr [TextEdit.expected] enthält oder sich Edits überlappen. */
    fun apply(text: String, edits: List<TextEdit>): Result<String> {
        val sorted = edits.distinctBy { it.start to it.end }.sortedBy { it.start }
        for ((i, e) in sorted.withIndex()) {
            if (e.start < 0 || e.end > text.length || e.start > e.end) {
                return Result.failure(IllegalStateException(Res.string(R.string.depupdate_ungueltiger_bereich, e.start, e.end)))
            }
            if (text.substring(e.start, e.end) != e.expected) {
                return Result.failure(IllegalStateException(Res.string(R.string.depupdate_datei_hat_sich_geaendert_erwartet, e.expected)))
            }
            if (i > 0 && sorted[i - 1].end > e.start) {
                return Result.failure(IllegalStateException(Res.string(R.string.depupdate_ueberlappende_aenderungen)))
            }
        }
        val sb = StringBuilder(text)
        for (e in sorted.asReversed()) sb.replace(e.start, e.end, e.replacement)
        return Result.success(sb.toString())
    }
}

object AtomicFiles {
    /** Schreibt über eine Temp-Datei im selben Ordner + Rename; Fallback: direktes Überschreiben. */
    fun write(file: File, text: String) {
        val tmp = File(file.parentFile, ".${file.name}.codeforge.tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
