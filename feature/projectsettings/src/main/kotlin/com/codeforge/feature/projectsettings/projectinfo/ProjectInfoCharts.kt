/**
 * Modul: :feature:projectsettings
 * @author Thomas Schmid
 */
package com.codeforge.feature.projectsettings.projectinfo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Kategorienfarben: mittlere Helligkeit, damit sie auf hellem und dunklem Grund lesbar sind; Grau ist für „Andere“
 * reserviert ([OTHER_COLOR]). Die Legende nennt jeden Wert, Farbe ist nie die einzige Information.
 */
internal val SLICE_COLORS = listOf(
    Color(0xFF4C8EDA), Color(0xFFE8853A), Color(0xFF3FA66B), Color(0xFF9B6BD6),
    Color(0xFFD9577A), Color(0xFF2BA3A8), Color(0xFFC9A227),
)
internal val OTHER_COLOR = Color(0xFF8A8A8A)

internal fun sliceColor(index: Int, slice: ChartSlice, otherLabel: String): Color =
    if (slice.label == otherLabel) OTHER_COLOR else SLICE_COLORS[index % SLICE_COLORS.size]

/** Ringdiagramm mit Summe in der Mitte und Legende (Name, Wert, Anteil). */
@Composable
internal fun DonutChart(
    slices: List<ChartSlice>,
    centerValue: String,
    centerLabel: String,
    otherLabel: String,
    formatValue: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val description = slices.joinToString { "${it.label} ${formatValue(it.value)} (${percent(it.fraction)})" }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(132.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(132.dp)) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                // Kleiner Spalt zwischen den Abschnitten; sehr kleine Abschnitte bleiben trotzdem sichtbar.
                val gap = if (slices.size > 1) 2f else 0f
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = s.fraction * 360f
                    drawArc(
                        sliceColor(i, s, otherLabel), start + gap / 2, (sweep - gap).coerceAtLeast(0.8f), false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(sliceColor(i, s, otherLabel)))
                    Text(
                        s.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        percent(s.fraction), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Waagrechte Balken (z. B. Codezeilen je Modul), längster Balken = volle Breite. */
@Composable
internal fun HorizontalBars(
    entries: List<Pair<String, Long>>,
    formatValue: (Long) -> String,
    modifier: Modifier = Modifier,
    color: Color = SLICE_COLORS.first(),
) {
    val max = entries.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { (label, value) ->
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f))
                    Text(formatValue(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(value.toFloat() / max).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
        }
    }
}

/** Gestapelter Balken für Code/Kommentar/Leer mit Legende. */
@Composable
internal fun StackedBar(parts: List<Triple<String, Long, Color>>, formatValue: (Long) -> String, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.takeIf { it > 0 } ?: return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            parts.filter { it.second > 0 }.forEach { (_, value, color) ->
                Box(Modifier.weight(value.toFloat() / total).fillMaxHeight().background(color))
            }
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEach { (label, value, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(
                        "$label ${formatValue(value)}", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp), maxLines = 1,
                    )
                }
            }
        }
    }
}

internal fun percent(fraction: Float): String = if (fraction < 0.01f && fraction > 0f) "<1 %" else "${Math.round(fraction * 100)} %"
