// Modul: :feature:editor
package com.codeforge.feature.editor

import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Zusätzliche Schaltfläche im Kontextmenü des Editors (Long-Click / Auswahl), hinter Kopieren/Einfügen/Ausschneiden.
 *
 * @param id Kennung, die an den Aufrufer zurückgegeben wird.
 * @param label Beschriftung (Tooltip, Bedienungshilfe).
 * @param icon Vektorgrafik (20 dp, wird in der Textfarbe des Editor-Themes eingefärbt).
 * @param needsEditable nur bei bearbeitbarem Text zeigen (z. B. Formatieren, Umbenennen).
 */
@Immutable
data class EditorTextAction(
    val id: String,
    val label: String,
    @DrawableRes val icon: Int,
    val needsEditable: Boolean = false,
)

/**
 * Sora-Textaktionsfenster mit eigenen Schaltflächen. Die eingebauten (Alles auswählen, Kopieren, Einfügen,
 * Ausschneiden) bleiben; die Leiste scrollt horizontal, wenn sie breiter als das Fenster wird.
 *
 * @param codeEditor Editor, zu dem das Fenster gehört.
 * @param actions aktuelle Aktionen (bei jedem Anzeigen neu gelesen).
 * @param onAction wird mit der ID der gewählten Aktion aufgerufen, das Fenster schließt sich vorher.
 */
internal class CfTextActionWindow(
    private val codeEditor: CodeEditor,
    private val actions: () -> List<EditorTextAction>,
    private val onAction: (String) -> Unit,
) : EditorTextActionWindow(codeEditor) {

    private val row: LinearLayout? = (view.getChildAt(0) as? HorizontalScrollView)?.getChildAt(0) as? LinearLayout
    private val builtinCount = row?.childCount ?: 0
    private var shownIds: List<String> = emptyList()

    override fun displayWindow() {
        syncButtons()
        super.displayWindow()
    }

    // Eigene Schaltflächen passend zu den aktuellen Aktionen und zur Bearbeitbarkeit neu aufbauen.
    private fun syncButtons() {
        val target = row ?: return
        val wanted = actions().filter { !it.needsEditable || codeEditor.isEditable }
        val tint = ColorStateList.valueOf(codeEditor.colorScheme.getColor(EditorColorScheme.TEXT_NORMAL))
        if (wanted.map { it.id } == shownIds) {
            // Nur die Farbe dem aktuellen Editor-Theme anpassen.
            for (i in builtinCount until target.childCount) (target.getChildAt(i) as? ImageButton)?.imageTintList = tint
            return
        }
        while (target.childCount > builtinCount) target.removeViewAt(target.childCount - 1)
        val size = (BUTTON_DP * codeEditor.dpUnit).toInt()
        wanted.forEach { action ->
            target.addView(
                ImageButton(codeEditor.context).apply {
                    setImageResource(action.icon)
                    imageTintList = tint
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    contentDescription = action.label
                    tooltipText = action.label
                    setOnClickListener {
                        dismiss()
                        onAction(action.id)
                    }
                },
                ViewGroup.LayoutParams(size, size),
            )
        }
        shownIds = wanted.map { it.id }
    }

    private companion object {
        /** Wie die eingebauten Schaltflächen (45 dp). */
        const val BUTTON_DP = 45f
    }
}
