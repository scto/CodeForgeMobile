/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Rückfrage eines Plugins an den Nutzer. Bei [options] mit einem Eintrag ist es eine Bestätigung (Knopf + Abbrechen),
 * sonst eine Auswahlliste.
 */
data class PluginPrompt(
    val id: Long,
    val pluginId: String,
    val title: String,
    val message: String,
    val options: List<String>
) {
    /** Ob nur bestätigt/abgebrochen wird. */
    val isConfirm: Boolean get() = options.size == 1
}

/**
 * Prozessweite Warteschlange der Rückfragen von Plugins. Die App zeigt die erste als Dialog; das Plugin wartet
 * (suspendierend) auf die Antwort. Abbruch der wartenden Coroutine oder Entladen des Plugins entfernt die Rückfrage.
 */
object PluginPromptRegistry {
    private const val MAX_TITLE = 120
    private const val MAX_MESSAGE = 2_000
    private const val MAX_OPTIONS = 50
    private const val MAX_OPTION = 200
    private const val MAX_PENDING_PER_PLUGIN = 3

    private val ids = AtomicLong()
    private val answers = ConcurrentHashMap<Long, CompletableDeferred<Int?>>()
    private val _prompts = MutableStateFlow<List<PluginPrompt>>(emptyList())

    /** Offene Rückfragen in Reihenfolge des Eingangs. */
    val prompts: StateFlow<List<PluginPrompt>> = _prompts.asStateFlow()

    /**
     * Stellt die Rückfrage und wartet auf die Antwort: Index der gewählten Option oder `null` (abgebrochen).
     *
     * @throws IllegalStateException wenn das Plugin schon zu viele offene Rückfragen hat oder [options] leer ist.
     */
    suspend fun ask(pluginId: String, title: String, message: String, options: List<String>): Int? {
        val cleaned = options.take(MAX_OPTIONS).map { it.take(MAX_OPTION) }
        check(cleaned.isNotEmpty()) { "Keine Optionen" }
        check(_prompts.value.count { it.pluginId == pluginId } < MAX_PENDING_PER_PLUGIN) { "Zu viele offene Rückfragen" }
        val prompt = PluginPrompt(ids.incrementAndGet(), pluginId, title.take(MAX_TITLE), message.take(MAX_MESSAGE), cleaned)
        val answer = CompletableDeferred<Int?>()
        answers[prompt.id] = answer
        _prompts.update { it + prompt }
        return try {
            answer.await()
        } finally {
            answers.remove(prompt.id)
            _prompts.update { list -> list.filterNot { it.id == prompt.id } }
        }
    }

    /** Antwort des Nutzers auf [id]: Index der Option oder `null` für Abbrechen. */
    fun answer(id: Long, choice: Int?) {
        val options = _prompts.value.firstOrNull { it.id == id }?.options?.size ?: return
        answers[id]?.complete(choice?.takeIf { it in 0 until options })
    }

    /** Bricht alle offenen Rückfragen von [pluginId] ab (beim Entladen); die Wartenden erhalten `null`. */
    fun cancelAll(pluginId: String) {
        _prompts.value.filter { it.pluginId == pluginId }.forEach { answers[it.id]?.complete(null) }
    }
}
