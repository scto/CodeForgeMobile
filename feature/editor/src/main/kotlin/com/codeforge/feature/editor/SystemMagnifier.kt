package com.codeforge.feature.editor

import android.os.Build
import androidx.annotation.RequiresApi
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.Magnifier

/**
 * Lupe über die System-Lupe von Android ([android.widget.Magnifier], API 28+).
 *
 * Die eingebaute Lupe von sora-editor kopiert den Fensterinhalt per PixelCopy in ein eigenes PopupWindow; in der
 * Compose-Oberfläche (AndroidView im Compose-Fenster) blieb sie auf Geräten unsichtbar. Die System-Lupe zeichnet
 * direkt aus der View, folgt dem Finger und sieht aus wie in allen anderen Textfeldern. Unter API 28 bleibt das
 * Verhalten von sora-editor.
 */
internal class SystemMagnifier(private val editor: CodeEditor) : Magnifier(editor) {

    private var system: android.widget.Magnifier? = null
    private var showing = false

    override fun show(x: Int, y: Int) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            super.show(x, y)
            return
        }
        showSystem(x.toFloat(), y.toFloat())
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showSystem(x: Float, y: Float) {
        val magnifier = system ?: create().also { system = it }
        // Quelle: Mitte der Zeile über dem Finger; die Lupe erscheint darüber.
        magnifier.show(x.coerceIn(0f, editor.width.toFloat()), y.coerceIn(0f, editor.height.toFloat()))
        showing = true
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun create(): android.widget.Magnifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dp = editor.dpUnit
            android.widget.Magnifier.Builder(editor)
                .setInitialZoom(ZOOM)
                .setSize((WIDTH_DP * dp).toInt(), (HEIGHT_DP * dp).toInt())
                .setCornerRadius(CORNER_DP * dp)
                .setDefaultSourceToMagnifierOffset(0, (-OFFSET_DP * dp).toInt())
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.widget.Magnifier(editor)
        }

    override fun isShowing(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) super.isShowing() else showing

    override fun dismiss() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            super.dismiss()
            return
        }
        system?.dismiss()
        showing = false
    }

    override fun updateDisplay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            super.updateDisplay()
            return
        }
        if (showing) system?.update()
    }

    private companion object {
        const val ZOOM = 1.5f
        const val WIDTH_DP = 140f
        const val HEIGHT_DP = 48f
        const val CORNER_DP = 12f
        const val OFFSET_DP = 42f
    }
}
