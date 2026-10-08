/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.model

import androidx.compose.runtime.Immutable

/**
 * Ein View/ViewGroup im Layout-Baum. [uid] ist nur innerhalb eines [LayoutDocument] eindeutig und
 * dient der Auswahl (das `android:id`-Attribut kann fehlen oder doppelt sein). [attributes] behält
 * die Reihenfolge der Quelldatei; Namespace-Deklarationen (`xmlns:*`) stehen nicht darin.
 */
@Immutable
data class LayoutNode(
    val uid: Int,
    val tag: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<LayoutNode> = emptyList(),
) {
    fun attr(name: String): String? = attributes[name]
    val widget: WidgetDef? get() = WidgetCatalog.find(tag)
    val isContainer: Boolean get() = widget?.isContainer ?: children.isNotEmpty()
}

/** Ganzes Layout: Wurzel + Zähler für neue [LayoutNode.uid]s. */
@Immutable
data class LayoutDocument(
    val root: LayoutNode,
    val nextUid: Int,
) {
    companion object {
        /** Leeres Standard-Layout für neue Dateien. */
        fun newLinearLayout(): LayoutDocument = LayoutDocument(
            root = LayoutNode(
                uid = 0,
                tag = "LinearLayout",
                attributes = linkedMapOf(
                    "android:layout_width" to "match_parent",
                    "android:layout_height" to "match_parent",
                    "android:orientation" to "vertical",
                ),
            ),
            nextUid = 1,
        )
    }
}
