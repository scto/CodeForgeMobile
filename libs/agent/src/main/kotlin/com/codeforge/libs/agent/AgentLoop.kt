/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Die Agentenschleife: Anfrage → Antwort streamen → Werkzeuge ausführen → Ergebnisse anhängen → wiederholen,
 * bis das Modell ohne Werkzeugaufruf endet.
 */
package com.codeforge.libs.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/** Warum die Schleife geendet hat. */
enum class FinishReason {
    /** Das Modell hat ohne weiteren Werkzeugaufruf geantwortet. */
    END_TURN,

    /** Die Antwort wurde wegen des Token-Limits abgeschnitten. */
    TRUNCATED,

    /** Die maximale Schrittzahl wurde erreicht. */
    MAX_STEPS,
}

/** Ereignisse eines Agentenlaufs für die Oberfläche. */
sealed interface AgentEvent {
    /** Teilstück des Antworttextes. */
    data class TextDelta(val text: String) : AgentEvent

    /** Ein Werkzeugaufruf beginnt. */
    data class ToolCallStarted(val id: String, val name: String, val arguments: JsonObject) : AgentEvent

    /** Ein Werkzeugaufruf ist beendet; [denied] = durch die Berechtigungen abgelehnt. */
    data class ToolCallFinished(val id: String, val name: String, val result: ToolResult, val denied: Boolean) : AgentEvent

    /** Token-Verbrauch des letzten Modellaufrufs. */
    data class Usage(val inputTokens: Int, val outputTokens: Int) : AgentEvent

    /** Lauf beendet; [messages] ist der vollständige neue Gesprächsverlauf zum Weiterführen/Speichern. */
    data class Finished(val messages: List<AgentMessage>, val reason: FinishReason) : AgentEvent

    /** Lauf mit Fehler beendet; [messages] enthält den Stand bis dahin. */
    data class Failed(val message: String, val messages: List<AgentMessage>) : AgentEvent
}

/**
 * Treibt einen Agentenlauf.
 *
 * @param llm Sprachmodell mit Werkzeugaufrufen.
 * @param tools verfügbare Werkzeuge.
 * @param policy Berechtigungen für schreibende Werkzeuge.
 * @param systemPrompt Systemanweisung (siehe [AgentSystemPrompt]).
 * @param compactor kürzt alte Werkzeugergebnisse in der Anfrage (der gespeicherte Verlauf bleibt vollständig).
 * @param maxSteps Obergrenze der Modellaufrufe je Nutzernachricht.
 * @param maxTokens Token-Limit je Modellantwort.
 */
class AgentLoop(
    private val llm: LlmClient,
    private val tools: AgentToolRegistry,
    private val policy: AgentPermissionPolicy,
    private val systemPrompt: String,
    private val compactor: HistoryCompactor = HistoryCompactor(),
    private val maxSteps: Int = 25,
    private val maxTokens: Int = 4096,
) {
    /** Führt [userText] mit dem bisherigen [history] aus und liefert die Ereignisse des Laufs. */
    fun run(history: List<AgentMessage>, userText: String): Flow<AgentEvent> = flow {
        val messages = history.toMutableList()
        messages += AgentMessage(AgentRole.USER, listOf(ContentPart.Text(userText)))

        var steps = 0
        while (true) {
            if (steps++ >= maxSteps) {
                emit(AgentEvent.Finished(messages.toList(), FinishReason.MAX_STEPS))
                return@flow
            }

            val text = StringBuilder()
            val calls = mutableListOf<ContentPart.ToolUse>()
            var stop = StopReason.END_TURN
            var failure: String? = null
            val request = LlmRequest(systemPrompt, compactor.compact(messages.toList()), tools.specs(), maxTokens)

            llm.stream(request).collect { event ->
                when (event) {
                    is LlmStreamEvent.TextDelta -> { text.append(event.text); emit(AgentEvent.TextDelta(event.text)) }
                    is LlmStreamEvent.ToolCallCompleted -> calls += ContentPart.ToolUse(event.id, event.name, event.arguments)
                    is LlmStreamEvent.Usage -> emit(AgentEvent.Usage(event.inputTokens, event.outputTokens))
                    is LlmStreamEvent.Completed -> stop = event.stopReason
                    is LlmStreamEvent.Failed -> failure = event.message
                }
            }
            failure?.let { emit(AgentEvent.Failed(it, messages.toList())); return@flow }

            val assistantParts = buildList {
                if (text.isNotBlank()) add(ContentPart.Text(text.toString()))
                addAll(calls)
            }
            if (assistantParts.isNotEmpty()) messages += AgentMessage(AgentRole.ASSISTANT, assistantParts)

            if (calls.isEmpty()) {
                val reason = if (stop == StopReason.MAX_TOKENS) FinishReason.TRUNCATED else FinishReason.END_TURN
                emit(AgentEvent.Finished(messages.toList(), reason))
                return@flow
            }

            val results = mutableListOf<ContentPart>()
            for (call in calls) {
                emit(AgentEvent.ToolCallStarted(call.id, call.name, call.arguments))
                val (result, denied) = execute(call)
                emit(AgentEvent.ToolCallFinished(call.id, call.name, result, denied))
                results += ContentPart.ToolResultPart(call.id, result.content, result.isError)
            }
            messages += AgentMessage(AgentRole.USER, results)
        }
    }

    private suspend fun execute(call: ContentPart.ToolUse): Pair<ToolResult, Boolean> {
        val tool = tools[call.name]
            ?: return ToolResult.error("Unbekanntes Werkzeug '${call.name}'. Verfügbar: ${tools.specs().joinToString { it.name }}") to false
        return when (val verdict = policy.check(tool, call.arguments)) {
            is PolicyVerdict.Denied -> ToolResult.error(verdict.reason) to true
            PolicyVerdict.Allowed -> try {
                tool.execute(call.arguments) to false
            } catch (e: CancellationException) {
                throw e
            } catch (e: ToolArgumentException) {
                ToolResult.error(e.message ?: "Ungültige Argumente") to false
            } catch (e: Exception) {
                ToolResult.error("${tool.name} fehlgeschlagen: ${e.message ?: e::class.simpleName}") to false
            }
        }
    }
}

/** Standard-Systemanweisung. */
object AgentSystemPrompt {
    /** Anweisung für ein Projekt mit dem Namen [projectName]. */
    fun default(projectName: String): String = """
        You are the coding agent of CodeForge Mobile, an IDE that runs on an Android phone. You work in the project "$projectName".
        Use the tools to read, search and change project files; all paths are relative to the project root.
        Rules:
        - Read a file before you edit it. Prefer edit_file with a small, unique old_string over rewriting whole files.
        - Make the smallest change that solves the task and keep the existing code style.
        - After changes, call get_diagnostics for the files you touched and fix errors you introduced.
        - If run_gradle is available, use targeted compile/test tasks (a full build is slow on a phone) and fix the reported errors.
        - For multi-step work keep a short todo list with todo_write.
        - If a tool call is denied, do not retry it unchanged; explain what you wanted to do and ask.
        - You cannot run builds or shell commands unless a tool for it is listed.
        - Answer in the language of the user, concisely.
    """.trimIndent()
}
