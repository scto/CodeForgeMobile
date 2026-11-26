// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.codeforge.feature.editor.R
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.lang.styling.ExtraStylesProvider
import io.github.rosemoe.sora.lang.styling.line.LineAnchorStyle
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways

/** Welches Symbol ein Test in der Randspalte bekommt. */
enum class TestIconKind { RUN, PASSED, FAILED, SCREENSHOT, SCREENSHOT_FAILED }

/** Symbolart zu [target]: Screenshot-Tests zeigen ein Bild, sonst ▶ bzw. das letzte Ergebnis. */
fun iconKind(target: TestTarget): TestIconKind = when {
    target.screenshot && target.passed == false -> TestIconKind.SCREENSHOT_FAILED
    target.screenshot -> TestIconKind.SCREENSHOT
    target.passed == true -> TestIconKind.PASSED
    target.passed == false -> TestIconKind.FAILED
    else -> TestIconKind.RUN
}

/**
 * Test-Symbole in der Randspalte (wie in Android Studio): sora zeichnet sie in der eigenen Symbolspalte zwischen
 * Zeilennummern und Faltmarken und meldet Tipps als [SideIconClickEvent]. Unabhängig von der Sprachanalyse, darum
 * als [ExtraStylesProvider] statt in den Styles der Analyse.
 */
class TestGutterIcons(context: Context, private val onClick: (TestTarget) -> Unit) : ExtraStylesProvider {

    private val drawables: Map<TestIconKind, Drawable?> = mapOf(
        TestIconKind.RUN to ContextCompat.getDrawable(context, R.drawable.cf_gutter_run),
        TestIconKind.PASSED to ContextCompat.getDrawable(context, R.drawable.cf_gutter_run_passed),
        TestIconKind.FAILED to ContextCompat.getDrawable(context, R.drawable.cf_gutter_run_failed),
        TestIconKind.SCREENSHOT to ContextCompat.getDrawable(context, R.drawable.cf_gutter_screenshot),
        TestIconKind.SCREENSHOT_FAILED to ContextCompat.getDrawable(context, R.drawable.cf_gutter_screenshot_failed)
    )

    @Volatile private var targets: List<TestTarget> = emptyList()
    @Volatile private var icons: Map<Int, Pair<LineSideIcon, TestTarget>> = emptyMap()

    /** Hängt sich an [editor] (Symbole und Tipps). */
    fun attach(editor: CodeEditor) {
        editor.registerExtraStylesProvider(this)
        editor.subscribeAlways<SideIconClickEvent> { event ->
            icons[event.clickedIcon.line]?.takeIf { it.first === event.clickedIcon }?.let { (_, target) ->
                event.intercept()
                onClick(target)
            }
        }
    }

    /** Übernimmt [list] (je Zeile das erste Ziel) und zeichnet neu, wenn sich etwas geändert hat. */
    fun update(editor: CodeEditor, list: List<TestTarget>) {
        if (list == targets) return
        targets = list
        icons = list.groupBy { it.line }.mapNotNull { (line, items) ->
            val target = items.first()
            drawables[iconKind(target)]?.let { line to (LineSideIcon(line, it) to target) }
        }.toMap()
        editor.invalidate()
    }

    override fun getExtraStyles(line: Int, styles: MutableList<LineAnchorStyle>) {
        icons[line]?.let { styles += it.first }
    }
}
