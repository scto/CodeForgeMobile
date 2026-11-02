package com.codeforge.feature.assetstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.assetstudio.logic.AssetFilePlanner

private val Palette = listOf(
    0xFF6200EE, 0xFF3700B3, 0xFF03DAC6, 0xFF018786, 0xFFB00020, 0xFFE91E63,
    0xFFFF5722, 0xFFFF9800, 0xFFFFC107, 0xFF4CAF50, 0xFF2196F3, 0xFF607D8B,
    0xFF000000, 0xFF424242, 0xFF9E9E9E, 0xFFFFFFFF,
).map { it.toInt() }

/** Farbzeile wie im Studio: Farbpunkt, „Wählen“ (Palette) und „Eigene“ (Hex). */
@Composable
fun ColorPickRow(
    argb: Int,
    onColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    var showPalette by remember { mutableStateOf(false) }
    var showHex by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch(argb, selected = false, size = 40, onClick = null)
        OutlinedButton(onClick = { showPalette = true }) {
            Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(R.string.assetstudio_choose))
        }
        OutlinedButton(onClick = { showHex = true }) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(R.string.assetstudio_custom))
        }
        trailing?.invoke()
    }
    if (showPalette) PaletteDialog(argb, onPick = { onColor(it); showPalette = false }, onDismiss = { showPalette = false })
    if (showHex) HexDialog(argb, onApply = { onColor(it); showHex = false }, onDismiss = { showHex = false })
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, size: Int, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            val onColor = if (Color(argb).luminanceApprox() > 0.5f) Color.Black else Color.White
            Icon(Icons.Filled.Check, contentDescription = null, tint = onColor, modifier = Modifier.size(20.dp))
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.assetstudio_pick_color)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Palette.forEach { c -> Swatch(c, selected = c == current, size = 44, onClick = { onPick(c) }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}

@Composable
private fun HexDialog(current: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(AssetFilePlanner.colorHex(current)) }
    val parsed = AssetFilePlanner.parseColor(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.assetstudio_hex_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringRes(R.string.assetstudio_hex_hint)) },
                singleLine = true,
                isError = parsed == null,
            )
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onApply) }) { Text(stringRes(R.string.assetstudio_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}
