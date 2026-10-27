// Modul: :core:resources
package com.codeforge.core.resources

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Überschreibungsschicht für App-Texte, gespeist von einem Sprachpaket-Plugin
 * (`contributes.languagePacks`). Schlüssel sind die **Namen der String-Ressourcen**
 * (z. B. `git_error_no_remote`), Werte die übersetzten Texte mit denselben Platzhaltern wie das Original.
 *
 * Wirkt auf [Res.string] und [stringRes]. Nicht abgedeckt: Plurals, direkte `Context.getString`-Aufrufe,
 * System-Benachrichtigungen. Fehlt ein Schlüssel oder passt der Platzhalter nicht, gilt der Originaltext.
 */
object LanguageOverrides {

    // Snapshot-State: Composables, die stringRes() lesen, werden bei Paketwechsel neu gezeichnet.
    private var current: Map<String, String> by mutableStateOf(emptyMap())
    private val entryNames = ConcurrentHashMap<Int, String>()

    /** Aktuelle Überschreibungen (leer = keine). Lesen im Composable abonniert Änderungen. */
    val active: Map<String, String> get() = current

    fun set(strings: Map<String, String>) {
        current = strings
    }

    fun clear() = set(emptyMap())

    /** Übersetzter und formatierter Text für [id] oder `null` (→ Originaltext verwenden). */
    fun resolve(resources: Resources, id: Int, args: Array<out Any>, overrides: Map<String, String> = current): String? {
        if (overrides.isEmpty()) return null
        val name = entryNames.getOrPut(id) {
            runCatching { resources.getResourceEntryName(id) }.getOrDefault("")
        }
        return format(overrides[name] ?: return null, args)
    }

    /** Platzhalter-sicheres Formatieren: Fehler (falsche/zu viele Platzhalter) ergeben `null`. */
    internal fun format(template: String, args: Array<out Any>): String? {
        if (args.isEmpty()) return template
        return runCatching { String.format(Locale.getDefault(), template, *args) }.getOrNull()
    }
}
