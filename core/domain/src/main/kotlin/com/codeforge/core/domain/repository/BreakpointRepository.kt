package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.Breakpoint
import kotlinx.coroutines.flow.Flow

/**
 * Persistiert und verwaltet Haltepunkte projektbezogen im privaten App-Speicher.
 */
interface BreakpointRepository {
    fun getBreakpoints(projectRoot: String): Flow<Map<String, List<Breakpoint>>>
    suspend fun setBreakpoints(projectRoot: String, path: String, breakpoints: List<Breakpoint>)
    suspend fun toggleBreakpoint(projectRoot: String, path: String, line: Int): Breakpoint?
    suspend fun updateBreakpoint(projectRoot: String, breakpoint: Breakpoint)
    suspend fun removeBreakpoint(projectRoot: String, breakpointId: String)
    suspend fun removeAll(projectRoot: String)
    suspend fun adjustBreakpointsOnEdit(projectRoot: String, path: String, changeLine: Int, lineDelta: Int)
}

/**
 * Reine Logik zur Anpassung von Haltepunkt-Zeilennummern nach Textänderungen.
 */
object BreakpointLineAdjuster {
    /**
     * Passt die Zeilen von Haltepunkten nach dem Einfügen oder Löschen von Zeilen an.
     * @param breakpoints Bestehende Haltepunkte
     * @param changeLine 1-basierte Zeilennummer, ab der geändert wurde
     * @param lineDelta Positiv für eingefügte Zeilen, negativ für gelöschte Zeilen
     */
    fun adjust(
        breakpoints: List<Breakpoint>,
        changeLine: Int,
        lineDelta: Int
    ): List<Breakpoint> {
        if (lineDelta == 0) return breakpoints
        return if (lineDelta > 0) {
            breakpoints.map { bp ->
                if (bp.line >= changeLine) {
                    bp.copy(line = bp.line + lineDelta)
                } else {
                    bp
                }
            }
        } else {
            val linesRemoved = -lineDelta
            val deletionEnd = changeLine + linesRemoved
            breakpoints.mapNotNull { bp ->
                when {
                    bp.line < changeLine -> bp
                    bp.line in changeLine until deletionEnd -> null // Haltepunkt lag in gelöschtem Bereich
                    else -> bp.copy(line = bp.line - linesRemoved)
                }
            }
        }
    }
}
