package com.nyamux.shared.theme

import android.content.Context
import android.util.TypedValue

object ThemeUtils {
    @JvmStatic
    fun getSystemAttrColor(context: Context, attr: Int, defaultColor: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            defaultColor
        }
    }
}
