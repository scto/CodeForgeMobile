/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

/**
 * Prüft, ob ein Plugin-Archiv rein deklarativ ist (nur Beitrags-Dateien, kein ausführbarer Code).
 * Der Store installiert ausschließlich solche Archive; Code-Plugins gibt es nur per manuellem Sideload.
 */
object DeclarativeOnly {
    private val CODE_SUFFIXES = listOf(".dex", ".jar", ".so", ".apk", ".class", ".aar", ".sh")

    /**
     * Fehlertext oder `null`, wenn das Archiv rein deklarativ ist. [fileNames] sind relative Pfade im Archiv.
     * [allowTools] (Store-Kategorie „Code-Tools“): `contributes.tools` mit Skripten (z. B. `.py`) ist erlaubt; Plugin-Code
     * (`entryPointClass`) und native/Java-Dateien sowie `.sh` bleiben verboten.
     */
    fun violation(fileNames: List<String>, manifest: PluginManifest, allowTools: Boolean = false): String? {
        if (manifest.entryPointClass != null) return "Enthält Plugin-Code (entryPointClass)"
        fileNames.firstOrNull { name -> CODE_SUFFIXES.any { name.lowercase().endsWith(it) } }
            ?.let { return "Enthält ausführbare Datei: $it" }
        if (!allowTools && manifest.contributes.tools.isNotEmpty()) return "Werkzeuge (tools) führen Programme aus und sind nur per Sideload erlaubt"
        if (manifest.contributes.agentTools.isNotEmpty() || manifest.contributes.mcpServers.isNotEmpty()) {
            return "Agent-Werkzeuge und MCP-Server starten Programme und sind nur per Sideload erlaubt"
        }
        manifest.contributes.requires.firstOrNull { !RequirementPolicy.isStoreAllowed(it) }
            ?.let { return "Voraussetzung nicht auf der Freigabeliste: ${it.manager.id}:${it.name}" }
        if (manifest.contributes.isEmpty) return "Enthält keine Beiträge (contributes)"
        return null
    }
}
