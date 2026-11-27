/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.StateFlow

/** Art einer Plugin-Einstellung. */
enum class PluginSettingType { TEXT, BOOLEAN, CHOICE, NUMBER }

/** Auswahlmöglichkeit einer [PluginSettingType.CHOICE]-Einstellung. */
data class PluginSettingOption(val value: String, val label: String)

/**
 * Eine Einstellung aus `contributes.settings.items`. Werte werden als Text gespeichert; [normalize] macht daraus
 * einen gültigen Wert ([default] bei ungültiger Eingabe).
 */
data class PluginSettingItem(
    val key: String,
    val type: PluginSettingType,
    val title: String,
    val description: String = "",
    val default: String = "",
    val options: List<PluginSettingOption> = emptyList(),
    val min: Int? = null,
    val max: Int? = null
)

/** Einstellungsseite eines Plugins (`contributes.settings`). */
data class PluginSettingsContribution(val title: String?, val items: List<PluginSettingItem>)

/** Gültiger Wert für [raw]: Ja/Nein, Zahl im Bereich, bekannte Auswahl; sonst [PluginSettingItem.default]. */
fun PluginSettingItem.normalize(raw: String?): String {
    val value = raw ?: return default
    return when (type) {
        PluginSettingType.TEXT -> value.take(PluginSettingsRules.MAX_TEXT)
        PluginSettingType.BOOLEAN -> value.toBooleanStrictOrNull()?.toString() ?: default
        PluginSettingType.CHOICE -> value.takeIf { v -> options.any { it.value == v } } ?: default
        PluginSettingType.NUMBER -> clampNumber(value) ?: default
    }
}

private fun PluginSettingItem.clampNumber(value: String): String? =
    value.trim().toIntOrNull()?.coerceIn(min ?: Int.MIN_VALUE, max ?: Int.MAX_VALUE)?.toString()

/** Wirksame Werte aller [items]: gespeicherte, sonst Vorgaben. */
fun List<PluginSettingItem>.effectiveValues(stored: Map<String, String>): Map<String, String> =
    associate { it.key to it.normalize(stored[it.key]) }

/**
 * Ersetzt `${setting:key}` in [text] durch den Wert aus [values] (unbekannte Schlüssel → leer). Bedingt:
 * `${setting:key?TEXT}` wird zu TEXT, wenn der Wert `true` ist, `${setting:key=WERT?TEXT}`, wenn er WERT ist – sonst leer.
 */
fun resolveSettingPlaceholders(text: String, values: Map<String, String>): String =
    PluginSettingsRules.CONDITIONAL.replace(text) { m ->
        val (key, expected, emit) = m.destructured
        val value = values[key].orEmpty()
        if (value == expected.ifEmpty { "true" }) emit else ""
    }.let { resolved -> PluginSettingsRules.PLACEHOLDER.replace(resolved) { values[it.groupValues[1]].orEmpty() } }

/**
 * Setzt die Einstellungen in die Argumente [args] eines Kommandos ein. Elemente mit einer Bedingung
 * (`${setting:key?--flag}`), die leer ergeben, entfallen – so werden Schalter nur bei Bedarf übergeben.
 */
fun resolveSettingArgs(args: List<String>, values: Map<String, String>): List<String> = args.mapNotNull { arg ->
    val resolved = resolveSettingPlaceholders(arg, values)
    resolved.takeUnless { it.isEmpty() && PluginSettingsRules.CONDITIONAL.containsMatchIn(arg) }
}

/** Grenzen und Platzhalter der Plugin-Einstellungen. */
object PluginSettingsRules {
    /** Höchstzahl an Einstellungen je Plugin. */
    const val MAX_ITEMS = 50

    /** Höchstlänge eines Textwerts. */
    const val MAX_TEXT = 2_000

    /** Gültige Schlüssel. */
    val KEY: Regex = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")

    /** Platzhalter in Kommandos und Umgebung deklarativer Beiträge. */
    val PLACEHOLDER: Regex = Regex("""\$\{setting:([A-Za-z][A-Za-z0-9_.-]{0,63})\}""")

    /** Bedingter Platzhalter `${setting:key?TEXT}` bzw. `${setting:key=WERT?TEXT}`. */
    val CONDITIONAL: Regex = Regex("""\$\{setting:([A-Za-z][A-Za-z0-9_.-]{0,63})(?:=([^?}]*))?\?([^}]*)\}""")
}

/** Gespeicherte Werte der Plugin-Einstellungen (nur vom Nutzer geänderte; Vorgaben kommen aus dem Manifest). */
interface PluginSettingsRepository {
    /** Gespeicherte Werte von [pluginId]. */
    fun observe(pluginId: String): StateFlow<Map<String, String>>

    /** Speichert [value] für [key]; `null` setzt auf die Vorgabe zurück. */
    suspend fun set(pluginId: String, key: String, value: String?)

    /** Löscht alle Werte von [pluginId] (beim Entfernen des Plugins). */
    suspend fun clear(pluginId: String)
}
