// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** RGB-Farbwähler mit Reglern, Hex-Feld und Schnellauswahl; liefert `#RRGGBB`. */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialRgb = remember(initialHex) { RgbColorUtils.hexToRgb(initialHex) ?: Triple(99, 102, 241) }
    var red by remember { mutableFloatStateOf(initialRgb.first.toFloat()) }
    var green by remember { mutableFloatStateOf(initialRgb.second.toFloat()) }
    var blue by remember { mutableFloatStateOf(initialRgb.third.toFloat()) }
    var hexText by remember { mutableStateOf(RgbColorUtils.rgbToHex(initialRgb.first, initialRgb.second, initialRgb.third)) }

    fun syncHex() {
        hexText = RgbColorUtils.rgbToHex(red.toInt(), green.toInt(), blue.toInt())
    }

    val current = Color(red.toInt(), green.toInt(), blue.toInt())
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(current),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hexText,
                        color = if (red * 0.299 + green * 0.587 + blue * 0.114 > 186) Color.Black else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ColorSlider(stringRes(R.string.themebuilder_rot), red, Color.Red) { red = it; syncHex() }
                ColorSlider(stringRes(R.string.themebuilder_gruen), green, Color.Green) { green = it; syncHex() }
                ColorSlider(stringRes(R.string.themebuilder_blau), blue, Color.Blue) { blue = it; syncHex() }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        RgbColorUtils.hexToRgb(text)?.let { (r, g, b) ->
                            red = r.toFloat()
                            green = g.toFloat()
                            blue = b.toFloat()
                        }
                    },
                    label = { Text(stringRes(R.string.themebuilder_farbwert)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringRes(R.string.themebuilder_schnellauswahl), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RgbColorUtils.QUICK_PRESETS) { preset ->
                        val (r, g, b) = RgbColorUtils.hexToRgb(preset) ?: return@items
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(r, g, b)).clickable {
                                red = r.toFloat()
                                green = g.toFloat()
                                blue = b.toFloat()
                                syncHex()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(hexText) }) { Text(stringRes(R.string.themebuilder_farbe_anwenden)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toInt().toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha = 0.7f)),
        )
    }
}
