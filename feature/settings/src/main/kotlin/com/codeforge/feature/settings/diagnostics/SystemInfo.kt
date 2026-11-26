// Modul: :feature:settings
package com.codeforge.feature.settings.diagnostics

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** Ein Eintrag der System-Diagnose (Beschriftung als Ressource, Wert als fertiger Text). */
@Immutable
data class InfoItem(@StringRes val label: Int, val value: String)

/** Abschnitte der System-Diagnose; [tiles] = als Kacheln (kurze Werte), sonst als Zeilen. */
@Immutable
data class InfoSection(
    val kind: InfoSectionKind,
    val items: List<InfoItem>,
    val tiles: Boolean = false,
)

/** Art eines Abschnitts (Titel und Symbol). */
enum class InfoSectionKind { DEVICE, DISPLAY, RUNTIME, STORAGE, APP }

/** Versionen der eingebundenen Bibliotheken (geprüft von `LibraryVersionsTest` gegen Version Catalog und Build-Dateien). */
object LibraryVersions {
    /** sora-editor-Fork mit Code-Folding (Version Catalog `sora`). */
    const val SORA_EDITOR = "0.24.7-cfm.1"
    /** android-tree-sitter (Version Catalog `treesitter`). */
    const val TREE_SITTER = "4.3.2"
    /** Compose BOM (Version Catalog `composeBom`). */
    const val COMPOSE_BOM = "2025.09.00"
}

/** Formatierungshilfen ohne Android-Abhängigkeit (testbar). */
object InfoFormat {
    private const val MIB = 1024L * 1024L
    private const val GIB = MIB * 1024L

    /** Bytes als `15.8 GB` bzw. `512 MB` (Dezimalpunkt, eine Nachkommastelle ab GB). */
    fun bytes(value: Long): String =
        if (value >= GIB) String.format(java.util.Locale.ROOT, "%.1f GB", value.toDouble() / GIB) else "${value / MIB} MB"

    /** OpenGL-ES-Version aus `reqGlEsVersion` (oberes/unteres Halbwort), z. B. `0x30002` → `3.2`. */
    fun glEs(version: Int): String = "${version shr 16}.${version and 0xFFFF}"

    /** Kurzbericht als Text (zum Kopieren in Fehlermeldungen). */
    fun report(sections: List<InfoSection>, title: (InfoSectionKind) -> String, label: (Int) -> String): String =
        sections.joinToString("\n\n") { section ->
            "## ${title(section.kind)}\n" + section.items.joinToString("\n") { "- ${label(it.label)}: ${it.value}" }
        }
}
