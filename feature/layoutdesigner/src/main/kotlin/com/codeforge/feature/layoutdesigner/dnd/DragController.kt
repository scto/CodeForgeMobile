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

@Stable
class NodeBoundsRegistry {
    private val _bounds = mutableStateMapOf<Int, DesignerRect>()
    val bounds: Map<Int, DesignerRect> get() = _bounds

    fun register(uid: Int, rect: DesignerRect) {
        _bounds[uid] = rect
    }

    fun unregister(uid: Int) {
        _bounds.remove(uid)
    }

    fun clear() {
        _bounds.clear()
    }
}

@Stable
class DragController(
    val boundsRegistry: NodeBoundsRegistry = NodeBoundsRegistry(),
) {
    var activePayload by mutableStateOf<DragPayload?>(null)
        private set

    var dragPosition by mutableStateOf(DesignerOffset(0f, 0f))
        private set

    var currentTarget by mutableStateOf<DropTarget?>(null)
        private set

    val isDragging: Boolean get() = activePayload != null

    fun startDrag(payload: DragPayload, initialPointer: DesignerOffset, document: LayoutDocument, density: Float = 1f) {
        activePayload = payload
        dragPosition = initialPointer
        updatePointer(initialPointer, document, density)
    }

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

    fun endDrag(): DropTarget? {
        val target = currentTarget
        activePayload = null
        currentTarget = null
        return target
    }

    fun cancelDrag() {
        activePayload = null
        currentTarget = null
    }
}
