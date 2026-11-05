/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Kürzt alte Werkzeugergebnisse in der Modellanfrage. Ein Agentenlauf schickt bei jedem Schritt den ganzen
 * wachsenden Verlauf erneut; Werkzeugergebnisse (Dateiinhalte, Suchtreffer) sind der größte Teil davon.
 *
 * Kürzen verändert die Anfragebytes und entwertet damit den Prompt-Cache des Anbieters. Deshalb läuft die
 * Kürzung mit Hysterese: erst ab [triggerChars] wird gekürzt, dann in einem Zug bis [targetChars], und einmal
 * gekürzte Ergebnisse bleiben gekürzt. Dazwischen ist das Präfix der Anfrage byte-identisch.
 */
package com.codeforge.libs.agent

/**
 * @param triggerChars Gesamtgröße, ab der gekürzt wird.
 * @param targetChars Größe, bis zu der in einem Durchgang gekürzt wird.
 * @param keepRecentResults so viele jüngste Werkzeugergebnisse bleiben immer unangetastet.
 */
class HistoryCompactor(
    private val triggerChars: Int = 120_000,
    private val targetChars: Int = 60_000,
    private val keepRecentResults: Int = 6,
) {
    init {
        require(targetChars < triggerChars) { "targetChars muss kleiner als triggerChars sein" }
    }

    private val elided = HashSet<String>()

    /** Liefert die Sicht für die Anfrage; [messages] selbst wird nicht verändert. */
    fun compact(messages: List<AgentMessage>): List<AgentMessage> {
        var size = size(messages)
        if (size > triggerChars) {
            val results = messages.flatMap { it.parts }.filterIsInstance<ContentPart.ToolResultPart>()
            val candidates = results.dropLast(keepRecentResults).filter { it.toolUseId !in elided }
            for (r in candidates) {
                if (size <= targetChars) break
                val saved = r.content.length - placeholder(r).length
                if (saved <= 0) continue
                elided += r.toolUseId
                size -= saved
            }
        }
        if (elided.isEmpty()) return messages
        return messages.map { m ->
            if (m.parts.none { it is ContentPart.ToolResultPart && it.toolUseId in elided }) m
            else m.copy(parts = m.parts.map { p ->
                if (p is ContentPart.ToolResultPart && p.toolUseId in elided) p.copy(content = placeholder(p)) else p
            })
        }
    }

    private fun placeholder(r: ContentPart.ToolResultPart) =
        "[Ergebnis gekürzt (${r.content.length} Zeichen) – bei Bedarf erneut abfragen]"

    private fun size(messages: List<AgentMessage>): Int = messages.sumOf { m ->
        m.parts.sumOf { p ->
            when (p) {
                is ContentPart.Text -> p.text.length
                is ContentPart.ToolUse -> p.arguments.toString().length
                is ContentPart.ToolResultPart -> if (p.toolUseId in elided) placeholder(p).length else p.content.length
            }
        }
    }
}
