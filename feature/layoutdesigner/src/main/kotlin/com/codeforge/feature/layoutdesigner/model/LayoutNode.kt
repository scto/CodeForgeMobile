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
 *
 * [leadingComments] enthält Kommentare direkt vor diesem Knoten.
 * [trailingComments] enthält Kommentare nach dem letzten Kind vor dem schließenden Tag (nur bei Containern).
 * [textContent] enthält optionalen Text-Inhalt zwischen Tags.
 * [isNew] kennzeichnet neu angelegte Knoten (bei denen `id/width/height` vorn platziert werden).
 * [firstAttrInline] merkt sich, ob das erste Attribut in der Quelldatei auf derselben Zeile wie das Tag stand.
 */
@Immutable
data class LayoutNode(
    val uid: Int,
    val tag: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<LayoutNode> = emptyList(),
    val leadingComments: List<String> = emptyList(),
    val trailingComments: List<String> = emptyList(),
    val textContent: String? = null,
    val isNew: Boolean = false,
    val firstAttrInline: Boolean = false,
) {
    fun attr(name: String): String? = attributes[name]
    val widget: WidgetDef? get() = WidgetCatalog.find(tag)
    val isContainer: Boolean get() = widget?.isContainer ?: children.isNotEmpty()
}

/**
 * Ganzes Layout: Wurzel + Zähler für neue [LayoutNode.uid]s.
 *
 * [leadingComments] enthält Kommentare vor dem Wurzelelement.
 * [rootNamespaces] enthält Namespace-Deklarationen der Wurzel (z. B. `xmlns:android`, `xmlns:custom`).
 * [indentation] ist die erkannte Quell-Einrückung (z. B. 2/4 Leerzeichen oder Tabs).
 * [reformatOnSave] steuert, ob beim Speichern neu formatiert oder die Original-Formatierung erhalten wird.
 */
@Immutable
data class LayoutDocument(
    val root: LayoutNode,
    val nextUid: Int,
    val leadingComments: List<String> = emptyList(),
    val rootNamespaces: Map<String, String> = emptyMap(),
    val indentation: String = "    ",
    val reformatOnSave: Boolean = false,
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
                isNew = true,
                firstAttrInline = false,
            ),
            nextUid = 1,
            rootNamespaces = linkedMapOf(
                "android" to "http://schemas.android.com/apk/res/android",
            ),
            indentation = "    ",
        )
    }
}
