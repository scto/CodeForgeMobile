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

/** Compose-Vorschau (`@Preview`-Funktion) in Zeile [line] (0-basiert). */
@androidx.compose.runtime.Immutable
data class PreviewGutterTarget(val line: Int, val functionName: String)

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
class TestGutterIcons(
    context: Context,
    private val onClick: (TestTarget) -> Unit,
    private val onPreviewClick: (String) -> Unit = {}
) : ExtraStylesProvider {

    private val previewDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.cf_gutter_preview)

    private val drawables: Map<TestIconKind, Drawable?> = mapOf(
        TestIconKind.RUN to ContextCompat.getDrawable(context, R.drawable.cf_gutter_run),
        TestIconKind.PASSED to ContextCompat.getDrawable(context, R.drawable.cf_gutter_run_passed),
        TestIconKind.FAILED to ContextCompat.getDrawable(context, R.drawable.cf_gutter_run_failed),
        TestIconKind.SCREENSHOT to ContextCompat.getDrawable(context, R.drawable.cf_gutter_screenshot),
        TestIconKind.SCREENSHOT_FAILED to ContextCompat.getDrawable(context, R.drawable.cf_gutter_screenshot_failed)
    )

    @Volatile private var targets: List<TestTarget> = emptyList()
    @Volatile private var previews: List<PreviewGutterTarget> = emptyList()
    @Volatile private var icons: Map<Int, Pair<LineSideIcon, TestTarget>> = emptyMap()
    @Volatile private var previewIcons: Map<Int, Pair<LineSideIcon, String>> = emptyMap()

    /** Hängt sich an [editor] (Symbole und Tipps). */
    fun attach(editor: CodeEditor) {
        editor.registerExtraStylesProvider(this)
        editor.subscribeAlways<SideIconClickEvent> { event ->
            if (!handleTap(event.clickedIcon.line, event.clickedIcon)) return@subscribeAlways
            event.intercept()
        }
    }

    /**
     * Tipp auf das Symbol in Zeile [line] (0-basiert); `true`, wenn behandelt. [icon] grenzt auf genau dieses
     * Symbol ein (sora-Ereignis), `null` nimmt das Symbol der Zeile (Tipp über die Compose-Randebene).
     */
    fun handleTap(line: Int, icon: LineSideIcon? = null): Boolean {
        val test = icons[line]?.takeIf { icon == null || it.first === icon }
        val preview = previewIcons[line]?.takeIf { icon == null || it.first === icon }
        when {
            test != null -> onClick(test.second)
            preview != null -> onPreviewClick(preview.second)
        }
        return test != null || preview != null
    }

    /** Übernimmt Tests (je Zeile das erste Ziel) und Vorschauen; zeichnet neu, wenn sich etwas geändert hat. */
    fun update(editor: CodeEditor, list: List<TestTarget>, previewList: List<PreviewGutterTarget> = emptyList()) {
        if (list == targets && previewList == previews) return
        targets = list
        previews = previewList
        icons = list.groupBy { it.line }.mapNotNull { (line, items) ->
            val target = items.first()
            drawables[iconKind(target)]?.let { line to (LineSideIcon(line, it) to target) }
        }.toMap()
        // Ein Test-Symbol (z. B. @PreviewTest) hat in derselben Zeile Vorrang.
        val drawable = previewDrawable
        previewIcons = if (drawable == null) {
            emptyMap()
        } else {
            previewList.filter { it.line !in icons }.associate { it.line to (LineSideIcon(it.line, drawable) to it.functionName) }
        }
        editor.invalidate()
    }

    override fun getExtraStyles(line: Int, styles: MutableList<LineAnchorStyle>) {
        (icons[line]?.first ?: previewIcons[line]?.first)?.let { styles += it }
    }
}
