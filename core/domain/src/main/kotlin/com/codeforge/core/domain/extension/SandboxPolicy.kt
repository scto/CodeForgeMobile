/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Regeln für Sandbox-Plugins (`"sandbox": true`): Der Code läuft in einem isolierten Prozess
 * (`android:isolatedProcess`) – ohne Berechtigungen, ohne Zugriff auf Dateien der App oder des Nutzers und ohne Netz.
 * Er bekommt nur, was die App ihm schickt (Dateiinhalt, Cursor, Auswahl) und liefert Ergebnisse zurück, die die App
 * selbst anwendet. Deshalb dürfen solche Plugins auch aus dem Store kommen.
 */
package com.codeforge.core.domain.extension

object SandboxPolicy {
    /** Berechtigungen, die in der Sandbox Sinn ergeben. */
    val PERMISSIONS: Set<String> = setOf(PluginPermissions.EDITOR)

    /** Größte erlaubte `classes.dex` (geht per Binder in den Sandbox-Prozess). */
    const val MAX_DEX_BYTES: Long = 900L * 1024

    private val CODE_SUFFIXES = listOf(".dex", ".jar", ".so", ".apk", ".class", ".aar", ".sh")

    /**
     * Fehler oder `null`, wenn das Archiv als Sandbox-Plugin aus dem Store installiert werden darf: genau eine
     * `classes.dex` als Code, keine Werkzeuge/Agenten/Server, Voraussetzungen nur von der Freigabeliste.
     */
    fun storeViolation(fileNames: List<String>, manifest: PluginManifest): String? {
        if (!manifest.sandbox) return "Kein Sandbox-Plugin"
        if ("classes.dex" !in fileNames) return "classes.dex fehlt"
        fileNames.firstOrNull { name -> name != "classes.dex" && CODE_SUFFIXES.any { name.lowercase().endsWith(it) } }
            ?.let { return "Enthält weitere ausführbare Datei: $it" }
        val c = manifest.contributes
        if (c.tools.isNotEmpty() || c.agentTools.isNotEmpty() || c.mcpServers.isNotEmpty() || c.lspServers.isNotEmpty() || c.formatters.isNotEmpty()) {
            return "Sandbox-Plugins dürfen keine Programme starten (tools, agentTools, mcpServers, lspServers, formatters)"
        }
        c.requires.firstOrNull { !RequirementPolicy.isStoreAllowed(it) }
            ?.let { return "Voraussetzung nicht auf der Freigabeliste: ${it.manager.id}:${it.name}" }
        return null
    }

    /** Store-Regel: deklarativ wie bisher – oder ein Sandbox-Plugin. */
    fun storeInstallViolation(fileNames: List<String>, manifest: PluginManifest, allowTools: Boolean = false): String? =
        if (manifest.sandbox) storeViolation(fileNames, manifest) else DeclarativeOnly.violation(fileNames, manifest, allowTools)
}
