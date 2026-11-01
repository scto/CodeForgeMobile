// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.extension.RequirementManager

/**
 * Übersetzt die `requires`-Namen der LSP-Pakete (z. B. „openjdk-17“, „nodejs“) in installierbare
 * APT-Pakete samt Prüf-Programm. Das Prüf-Programm entscheidet, ob die Voraussetzung schon erfüllt ist.
 */
internal object LspDependencies {

    /** Prüf-Programme je Anforderung; mehrere Alternativen genügen einzeln (z. B. `python3` oder `python`). */
    fun checkBinaries(requirement: String): List<String> = when (requirement) {
        "openjdk-17", "java" -> listOf("java")
        "nodejs", "node" -> listOf("node")
        "npm" -> listOf("npm")
        "python", "python3" -> listOf("python3", "python")
        "clang" -> listOf("clangd", "clang")
        else -> listOf(requirement)
    }

    /** APT-Paket, das die Anforderung erfüllt. `npm` kommt mit `nodejs`. */
    fun aptPackage(requirement: String): String = when (requirement) {
        "java" -> "openjdk-17"
        "node", "npm" -> "nodejs"
        "python3" -> "python"
        else -> requirement
    }

    /** Eindeutige Installationsliste (z. B. `nodejs` + `npm` → nur ein Paket). */
    fun toInstallList(requirements: List<String>): List<RequirementContribution> =
        requirements.map { aptPackage(it) }.distinct().map { apt ->
            val request = requirements.first { aptPackage(it) == apt }
            RequirementContribution(RequirementManager.PKG, apt, checkBinaries(request).first())
        }
}
