/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Berechtigungen für Werkzeugaufrufe des Agenten.
 */
package com.codeforge.libs.agent

import kotlinx.serialization.json.JsonObject

/** Wie der Agent mit schreibenden Werkzeugen umgeht. */
enum class PermissionMode {
    /** Jede Änderung/Ausführung wird einzeln beim Nutzer erfragt. */
    ASK_EACH,

    /** Änderungen und Ausführungen sind ohne Rückfrage erlaubt (rückgängig machbar über Checkpoints). */
    AUTO_ACCEPT,

    /** Nur lesen: schreibende Werkzeuge werden abgelehnt, der Agent soll einen Plan vorlegen. */
    PLAN_ONLY,
}

/** Freigabeanfrage an den Nutzer. */
data class ApprovalRequest(val toolName: String, val kind: ToolKind, val summary: String, val arguments: JsonObject)

/** Fragt den Nutzer; `true` = erlaubt. Läuft suspendierend, bis die Antwort da ist. */
fun interface ToolApprover {
    /** Holt die Entscheidung für [request]. */
    suspend fun approve(request: ApprovalRequest): Boolean
}

/** Ergebnis der Berechtigungsprüfung. */
sealed interface PolicyVerdict {
    /** Aufruf erlaubt. */
    data object Allowed : PolicyVerdict

    /** Aufruf abgelehnt; [reason] geht an das Modell. */
    data class Denied(val reason: String) : PolicyVerdict
}

/**
 * Entscheidet, ob ein Werkzeugaufruf laufen darf. Lesende Werkzeuge sind immer erlaubt.
 *
 * @param initialMode Startmodus; später über [mode] änderbar (z. B. durch einen Schalter im Chat).
 * @param approver Rückfrage im Modus [PermissionMode.ASK_EACH]; ohne Angabe wird alles Schreibende abgelehnt.
 */
class AgentPermissionPolicy(
    initialMode: PermissionMode = PermissionMode.ASK_EACH,
    private val approver: ToolApprover = ToolApprover { false },
) {
    /** Aktueller Modus. */
    @Volatile
    var mode: PermissionMode = initialMode

    /** Prüft den Aufruf von [tool] mit [arguments]. */
    suspend fun check(tool: AgentTool, arguments: JsonObject): PolicyVerdict {
        if (tool.kind == ToolKind.READ) return PolicyVerdict.Allowed
        return when (mode) {
            PermissionMode.AUTO_ACCEPT -> PolicyVerdict.Allowed
            PermissionMode.PLAN_ONLY ->
                PolicyVerdict.Denied("Plan-Modus: '${tool.name}' ist nicht erlaubt. Beschreibe stattdessen, was du ändern würdest.")
            PermissionMode.ASK_EACH ->
                if (approver.approve(ApprovalRequest(tool.name, tool.kind, tool.describeCall(arguments), arguments))) {
                    PolicyVerdict.Allowed
                } else {
                    PolicyVerdict.Denied("Der Nutzer hat '${tool.name}' abgelehnt. Frage nach, wie du vorgehen sollst.")
                }
        }
    }
}
