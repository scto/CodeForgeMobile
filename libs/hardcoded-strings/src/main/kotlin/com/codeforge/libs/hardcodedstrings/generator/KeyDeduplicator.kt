package com.codeforge.libs.hardcodedstrings.generator

import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding

/**
 * Macht die Schlüssel einer Extraktion für eine `strings.xml` eindeutig – auch nach manueller Umbenennung und über
 * mehrere Fundstellen derselben Datei hinweg:
 * - gleicher Text → derselbe Schlüssel (ein Eintrag, mehrere Ersetzungen; auch ein schon vorhandener Eintrag),
 * - gleicher Schlüssel mit anderem Text → `_2`, `_3` … (auch gegen vorhandene Einträge).
 * Verglichen wird der für XML escapte Wert, denn so stehen vorhandene Einträge in der Datei.
 */
object KeyDeduplicator {

    /** Liefert [findings] mit eindeutigen `selectedKey`; [existing] = Schlüssel → Wert der Ziel-`strings.xml`. */
    fun resolve(findings: List<HardcodedStringFinding>, existing: Map<String, String>): List<HardcodedStringFinding> {
        val valueByKey = existing.toMutableMap()
        val keyByValue = existing.entries.associate { (k, v) -> v to k }.toMutableMap()
        return findings.map { finding ->
            val value = AndroidStringEscaper.escapeForXml(finding.rawValue, finding.isTemplate).xmlValue
            val key = keyByValue[value] ?: freeKey(finding.selectedKey, value, valueByKey)
            valueByKey[key] = value
            keyByValue.putIfAbsent(value, key)
            if (key == finding.selectedKey) finding else finding.copy(selectedKey = key)
        }
    }

    private fun freeKey(wanted: String, value: String, valueByKey: Map<String, String>): String {
        if (valueByKey[wanted] == null || valueByKey[wanted] == value) return wanted
        return generateSequence(2) { it + 1 }.map { "${wanted}_$it" }.first { valueByKey[it] == null || valueByKey[it] == value }
    }
}
