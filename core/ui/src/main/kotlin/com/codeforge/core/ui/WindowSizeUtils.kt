/**
 * Modul: :core:ui
 * @author Thomas Schmid
 */
package com.codeforge.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/** Material-3-Breakpoints: Compact < 600dp <= Medium < 840dp <= Expanded. */
private const val COMPACT_WIDTH_BREAKPOINT_DP = 600
private const val EXPANDED_WIDTH_BREAKPOINT_DP = 840

/** Eigene, API-stabile Breitenklassen (vermeidet Abhängigkeit von WindowSizeClass-API-Änderungen). */
@Immutable
enum class WidthClass { Compact, Medium, Expanded }

/** Breitenklasse des aktuellen Fensters (berücksichtigt Split-Screen/Foldables über die Konfiguration). */
@Composable
fun rememberWidthClass(): WidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp < COMPACT_WIDTH_BREAKPOINT_DP -> WidthClass.Compact
            widthDp < EXPANDED_WIDTH_BREAKPOINT_DP -> WidthClass.Medium
            else -> WidthClass.Expanded
        }
    }
}

/** true auf Telefonen (Hochformat), false auf Tablets/Foldables im aufgeklappten Zustand. */
@Composable
fun rememberIsCompactWidth(): Boolean = rememberWidthClass() == WidthClass.Compact

/** true ab 840dp: dauerhaft sichtbare Navigation (Permanent Drawer) ist sinnvoll. */
@Composable
fun rememberIsExpandedWidth(): Boolean = rememberWidthClass() == WidthClass.Expanded
