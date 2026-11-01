// Modul: :core:ui
package com.codeforge.core.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Formatierung für den Info-Dialog; sprachneutral (Einheiten-Kürzel), Datumsformat folgt der Gerätesprache. */
object LspInfoFormat {

    fun bytes(value: Long, locale: Locale = Locale.getDefault()): String {
        if (value < 1024) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(locale, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }

    /** `3 s`, `4 min 05 s`, `2 h 07 min`, `3 d 4 h`. */
    fun duration(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        val d = s / 86_400
        val h = s % 86_400 / 3_600
        val m = s % 3_600 / 60
        val sec = s % 60
        return when {
            d > 0 -> "$d d $h h"
            h > 0 -> "%d h %02d min".format(h, m)
            m > 0 -> "%d min %02d s".format(m, sec)
            else -> "$sec s"
        }
    }

    fun dateTime(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).format(Date(epochMillis))
}
