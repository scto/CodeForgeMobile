// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import com.codeforge.core.domain.extension.PluginLogLevel
import com.codeforge.core.domain.extension.PluginLogLine
import com.codeforge.core.domain.extension.PluginLogRepository
import com.codeforge.core.domain.extension.registry.Contribution
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCommand
import com.codeforge.feature.editor.palette.PaletteAction
import com.codeforge.feature.editor.palette.PaletteCategory
import com.codeforge.feature.editor.palette.PaletteItem
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginDiagnostic
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Verbindet die Editor-Aktionen und Schnellkorrektur-Quellen aus dem [ExtensionRegistry] (Plugins und App) mit
 * Palette, Refactoring-Menü und Schnellkorrekturen. Plugin-Code läuft abgesichert: Zeitlimit, Ausnahmen werden im
 * Plugin-Protokoll vermerkt und als Fehlschlag gemeldet, nie an die App durchgereicht.
 */
class PluginEditorBridge(
    private val registry: ExtensionRegistry,
    private val logs: PluginLogRepository?
) {
    // Aktionen aus Schnellkorrektur-Quellen gibt es nur für den Moment der Anzeige; Zugriff über ein Token.
    private val transient = ConcurrentHashMap<String, Contribution<EditorAction>>()
    private val tokens = AtomicLong()

    /** Einträge der Befehlspalette: alle für [context] verfügbaren Aktionen. */
    fun paletteItems(context: EditorContext): List<PaletteItem> = available(context).map { (owner, action) ->
        PaletteItem(
            action = PaletteAction.PluginEditorAction(refOf(owner, action)),
            title = action.title,
            category = PaletteCategory.PLUGIN,
            detail = owner.takeIf { it != ExtensionRegistry.HOST }
        )
    }

    /** Aktionen der Arten `refactor…`/`source…` als Code-Actions für das Refactoring-Menü. */
    fun refactorActions(context: EditorContext): List<LspCodeAction> = available(context)
        .filter { (_, a) -> a.kind.startsWith(EditorAction.KIND_REFACTOR) || a.kind.startsWith(EditorAction.KIND_SOURCE) }
        .map { (owner, a) -> codeAction(a, refOf(owner, a)) }

    /** Schnellkorrekturen: Aktionen der Art `quickfix` und Vorschläge der Quellen zu [diagnostics]. */
    suspend fun quickFixActions(context: EditorContext, diagnostics: List<PluginDiagnostic>): List<LspCodeAction> {
        transient.clear()
        val direct = available(context).filter { (_, a) -> a.kind.startsWith(EditorAction.KIND_QUICKFIX) }
            .map { (owner, a) -> codeAction(a, refOf(owner, a)) }
        if (diagnostics.isEmpty()) return direct
        val fromProviders = registry.contributions(PluginExtensionPoints.QUICK_FIX_PROVIDER).flatMap { provider ->
            guarded(provider.pluginId, "quickFixProvider") {
                provider.value.fixes(context, diagnostics)
            }.getOrDefault(emptyList()).map { action ->
                val token = "#${tokens.incrementAndGet()}"
                transient[token] = Contribution(provider.pluginId, action)
                codeAction(action, token)
            }
        }
        return direct + fromProviders
    }

    /** Führt die Aktion [ref] (aus einem Palette-Eintrag oder einer Code-Action) für [context] aus. */
    suspend fun run(ref: String, context: EditorContext): Result<List<EditorEffect>> {
        val target = resolve(ref) ?: return Result.failure(IllegalStateException("Aktion nicht mehr verfügbar: $ref"))
        return guarded(target.pluginId, target.value.id) { target.value.perform(context) }
    }

    private fun resolve(ref: String): Contribution<EditorAction>? =
        transient[ref] ?: registry.contributions(PluginExtensionPoints.EDITOR_ACTION)
            .firstOrNull { refOf(it.pluginId, it.value) == ref }

    private fun available(context: EditorContext): List<Pair<String, EditorAction>> =
        registry.contributions(PluginExtensionPoints.EDITOR_ACTION).mapNotNull { c ->
            val ok = runCatching { c.value.isAvailable(context) }
                .onFailure { log(c.pluginId, PluginLogLevel.WARN, "isAvailable(${c.value.id}) fehlgeschlagen", it) }
                .getOrDefault(false)
            if (ok) c.pluginId to c.value else null
        }

    // Plugin-Code darf die App mit keiner Ausnahme erreichen, daher bewusst Throwable.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(pluginId: String, what: String, block: suspend () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.Default) { withTimeout(TIMEOUT_MS) { block() } })
    } catch (e: CancellationException) {
        log(pluginId, PluginLogLevel.ERROR, "$what: Zeitlimit überschritten", e)
        Result.failure(e)
    } catch (e: Throwable) {
        log(pluginId, PluginLogLevel.ERROR, "$what fehlgeschlagen", e)
        Result.failure(e)
    }

    private fun log(pluginId: String, level: PluginLogLevel, message: String, throwable: Throwable?) {
        if (pluginId == ExtensionRegistry.HOST) return
        logs?.append(
            pluginId,
            PluginLogLine(System.currentTimeMillis(), level, "editor", message, throwable?.stackTraceToString())
        )
    }

    companion object {
        /** Befehl, unter dem Plugin-Aktionen als Code-Action laufen; Argument: Referenz der Aktion. */
        const val COMMAND = "codeforge.pluginEditorAction"
        private const val TIMEOUT_MS = 30_000L

        private fun refOf(pluginId: String, action: EditorAction) = "$pluginId/${action.id}"

        private fun codeAction(action: EditorAction, ref: String) = LspCodeAction(
            title = action.title,
            kind = action.kind,
            command = LspCommand(title = action.title, command = COMMAND, arguments = listOf(ref))
        )

        /**
         * Wendet [edits] auf [text] an (Offsets beziehen sich auf den Ausgangstext).
         *
         * @throws IllegalArgumentException bei Bereichen außerhalb des Textes oder Überlappungen.
         */
        fun applyEdits(text: String, edits: List<TextEdit>): String {
            val sorted = edits.sortedBy { it.start }
            sorted.forEach { require(it.start in 0..it.end && it.end <= text.length) { "Ungültiger Bereich ${it.start}..${it.end}" } }
            sorted.zipWithNext().forEach { (a, b) -> require(a.end <= b.start) { "Überlappende Ersetzungen bei ${b.start}" } }
            val sb = StringBuilder(text)
            sorted.asReversed().forEach { sb.replace(it.start, it.end, it.newText) }
            return sb.toString()
        }

        /** Offset von Zeile [line]/Spalte [column] (ab 0) in [text], begrenzt auf den Text. */
        fun offsetOf(text: String, line: Int, column: Int): Int {
            var offset = 0
            repeat(line) {
                val next = text.indexOf('\n', offset)
                if (next < 0) return text.length
                offset = next + 1
            }
            val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            return (offset + column).coerceAtMost(lineEnd)
        }
    }
}
