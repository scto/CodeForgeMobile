package com.codeforge.feature.assetstudio.logic

/** Namenshilfen für den M3-Icon-Browser und Ressourcennamen. */
object IconNames {

    private val resourceName = Regex("^[a-z][a-z0-9_]*$")

    /** Android-Ressourcennamen: Kleinbuchstaben, Ziffern, `_`; muss mit einem Buchstaben beginnen. */
    fun isValidResourceName(name: String): Boolean = resourceName.matches(name)

    /** `_10mp` → `10mp`, `_1kPlus` → `1k plus`, `AccessAlarm` → `access alarm`. */
    fun displayName(className: String): String {
        val raw = className.removePrefix("_")
        val sb = StringBuilder()
        for ((i, c) in raw.withIndex()) {
            if (i > 0 && c.isUpperCase() && !raw[i - 1].isUpperCase()) sb.append(' ')
            sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    /** Vorschlag für einen Ressourcennamen aus dem Anzeigenamen: `access alarm` → `ic_access_alarm`. */
    fun suggestResourceName(className: String): String =
        "ic_" + displayName(className).replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** Liest die Namensliste (eine Klasse pro Zeile) und sortiert sie nach Anzeigename. */
    fun parseCatalog(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { displayName(it) }

    /** Sucht nach Teilwörtern im Anzeigenamen (alle Wörter müssen vorkommen). */
    fun search(catalog: List<String>, query: String): List<String> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return catalog
        return catalog.filter { c ->
            val d = displayName(c)
            words.all { it in d }
        }
    }
}
