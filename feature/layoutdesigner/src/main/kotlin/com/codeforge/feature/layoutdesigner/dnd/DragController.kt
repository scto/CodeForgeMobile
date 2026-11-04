/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * State-Holder für Drag & Drop-Interaktionen in der UI.
 */
package com.codeforge.feature.layoutdesigner.dnd

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.LayoutDocument

/** Merkt die Bildschirmgrenzen der Knoten für Ablageziele. */
@Stable
class NodeBoundsRegistry {
    private val _bounds = mutableStateMapOf<Int, DesignerRect>()
    /** Grenzen je Knoten-UID. */
    val bounds: Map<Int, DesignerRect> get() = _bounds

    /** Trägt die Grenzen eines Knotens ein. */
    fun register(uid: Int, rect: DesignerRect) {
        _bounds[uid] = rect
    }

    /** Entfernt einen Knoten. */
    fun unregister(uid: Int) {
        _bounds.remove(uid)
    }

    /** Leert die Einträge. */
    fun clear() {
        _bounds.clear()
    }
}

/** Steuert das Ziehen und Ablegen von Widgets. */
@Stable
class DragController(
    val boundsRegistry: NodeBoundsRegistry = NodeBoundsRegistry(),
) {
    /** Gezogene Nutzlast oder `null`. */
    var activePayload by mutableStateOf<DragPayload?>(null)
        private set

    /** Aktuelle Zeigerposition. */
    var dragPosition by mutableStateOf(DesignerOffset(0f, 0f))
        private set

    /** Aktuelles Ablageziel. */
    var currentTarget by mutableStateOf<DropTarget?>(null)
        private set

    /** Ob gerade gezogen wird. */
    val isDragging: Boolean get() = activePayload != null

    /** Beginnt das Ziehen. */
    fun startDrag(payload: DragPayload, initialPointer: DesignerOffset, document: LayoutDocument, density: Float = 1f) {
        activePayload = payload
        dragPosition = initialPointer
        updatePointer(initialPointer, document, density)
    }

    /** Aktualisiert Zeiger und Ablageziel. */
    fun updatePointer(pointer: DesignerOffset, document: LayoutDocument, density: Float = 1f) {
        dragPosition = pointer
        val payload = activePayload ?: return
        currentTarget = DropResolver.resolveDrop(
            document = document,
            boundsMap = boundsRegistry.bounds,
            pointer = pointer,
            payload = payload,
            densityDpFactor = density,
        )
    }

    /** Beendet das Ziehen und liefert das Ziel. */
    fun endDrag(): DropTarget? {
        val target = currentTarget
        activePayload = null
        currentTarget = null
        return target
    }

    /** Bricht das Ziehen ab. */
    fun cancelDrag() {
        activePayload = null
        currentTarget = null
    }
}
