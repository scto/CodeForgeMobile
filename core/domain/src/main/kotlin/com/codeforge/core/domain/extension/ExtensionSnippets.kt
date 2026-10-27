/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.VsCodeSnippetJsonSerializer
import java.io.File

/** Wandelt die Snippet-Dateien (VS-Code-Format) der Plugins in schreibgeschützte [Snippet]s um. */
object ExtensionSnippets {
    const val ID_PREFIX = "ext:"

    fun isExtensionSnippet(id: String) = id.startsWith(ID_PREFIX)

    /** Deterministische IDs, Scope = Sprache des Beitrags, `isBuiltin` = true (nicht löschbar). Defekte Dateien ergeben eine leere Liste. */
    fun fromText(resolved: Resolved<SnippetContribution>, text: String): List<Snippet> {
        val lang = resolved.item.languageId.lowercase()
        return VsCodeSnippetJsonSerializer.deserialize(text).getOrDefault(emptyList()).map {
            it.copy(
                id = "$ID_PREFIX${resolved.pluginId}/$lang/${it.name}",
                scope = setOf(lang),
                isBuiltin = true,
                isEnabled = true
            )
        }
    }

    /** Liest alle Snippet-Dateien; Pfade außerhalb des Plugin-Verzeichnisses werden ignoriert. */
    fun load(contributions: ActiveContributions): List<Snippet> =
        contributions.snippets.flatMap { resolved ->
            val dir = File(resolved.pluginDir)
            val file = File(dir, resolved.item.path)
            val inside = runCatching { file.canonicalPath.startsWith(dir.canonicalPath + File.separator) }.getOrDefault(false)
            if (!inside || !file.isFile) emptyList() else fromText(resolved, runCatching { file.readText() }.getOrDefault(""))
        }
}
