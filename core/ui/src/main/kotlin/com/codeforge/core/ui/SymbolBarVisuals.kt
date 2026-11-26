/**
 * Modul: :core:ui
 * @author Thomas Schmid
 *
 * Symbole und Namen der eingebauten Symbolleisten-Aktionen (IDs aus `SymbolBarAction`),
 * gemeinsam genutzt von Editor und Einstellungen.
 */
package com.codeforge.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.ui.graphics.vector.ImageVector
import com.codeforge.core.resources.R

/** Symbol einer Symbolleisten-Aktion. */
fun symbolActionIcon(id: String): ImageVector? = when (id) {
    "tab" -> Icons.AutoMirrored.Filled.KeyboardTab
    "line_comment" -> Icons.AutoMirrored.Filled.Comment
    "move_line_up" -> Icons.Filled.ArrowUpward
    "move_line_down" -> Icons.Filled.ArrowDownward
    "duplicate_line" -> Icons.Filled.ControlPointDuplicate
    "delete_line" -> Icons.Filled.DeleteSweep
    "indent" -> Icons.AutoMirrored.Filled.FormatIndentIncrease
    "dedent" -> Icons.AutoMirrored.Filled.FormatIndentDecrease
    "undo" -> Icons.AutoMirrored.Filled.Undo
    "redo" -> Icons.AutoMirrored.Filled.Redo
    "cursor_left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
    "cursor_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    else -> null
}

/** Name einer Symbolleisten-Aktion. */
@StringRes
fun symbolActionLabelRes(id: String): Int = when (id) {
    "tab" -> R.string.symbol_action_tab
    "line_comment" -> R.string.symbol_action_line_comment
    "move_line_up" -> R.string.symbol_action_move_line_up
    "move_line_down" -> R.string.symbol_action_move_line_down
    "duplicate_line" -> R.string.symbol_action_duplicate_line
    "delete_line" -> R.string.symbol_action_delete_line
    "indent" -> R.string.symbol_action_indent
    "dedent" -> R.string.symbol_action_dedent
    "undo" -> R.string.symbol_action_undo
    "redo" -> R.string.symbol_action_redo
    "cursor_left" -> R.string.symbol_action_cursor_left
    "cursor_right" -> R.string.symbol_action_cursor_right
    else -> R.string.symbol_action_unknown
}
