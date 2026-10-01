package com.nyamux.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.nyamux.terminal.TerminalEmulator

class TerminalRenderer(
    @JvmField var mTextSize: Int,
    @JvmField var mTypeface: Typeface
) {
    @JvmField var mFontWidth: Float = mTextSize * 0.6f
    @JvmField var mFontLineSpacing: Int = (mTextSize * 1.2f).toInt()
    @JvmField var mFontLineSpacingAndAscent: Int = (mTextSize * 0.9f).toInt()

    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = mTextSize.toFloat()
        typeface = mTypeface
    }

    init {
        val fm = paint.fontMetrics
        mFontWidth = paint.measureText("X")
        mFontLineSpacing = Math.ceil((fm.descent - fm.ascent).toDouble()).toInt()
        mFontLineSpacingAndAscent = Math.ceil((-fm.ascent).toDouble()).toInt()
    }

    fun getFontWidth(): Float = mFontWidth
    fun getFontLineSpacing(): Int = mFontLineSpacing

    fun render(
        emulator: TerminalEmulator,
        canvas: Canvas,
        topRow: Int,
        sel0: Int,
        sel1: Int,
        sel2: Int,
        sel3: Int
    ) {
        // Basic rendering delegate
    }
}
