// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.runtime.Immutable

/** Schalter der eingebauten Konverter: [j2k] Java→Kotlin, [x2c] XML→Compose (Standard: beide an). */
@Immutable
data class ConverterToggles(val j2k: Boolean = true, val x2c: Boolean = true)

/** Wie lange die Schalter nach dem letzten Beobachter weiter gelesen werden. */
internal const val CONVERTER_STOP_TIMEOUT_MS = 5_000L
