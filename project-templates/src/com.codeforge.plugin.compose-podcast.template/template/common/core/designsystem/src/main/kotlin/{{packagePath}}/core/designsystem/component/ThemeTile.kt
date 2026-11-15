package {{packageName}}.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import {{packageName}}.core.designsystem.theme.AppTheme

/** Darstellung, die eine [ThemeTile] als Mini-Vorschau zeichnet. */
enum class TilePreview { LIGHT, DARK, SPLIT }

/** Auswahlkachel mit gezeichneter Mini-Vorschau (hell, dunkel oder geteilt für „System“). */
@Composable
fun ThemeTile(label: String, preview: TilePreview, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, label = "tileBorder")
    val accent = AppTheme.colors.gradientStart
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(borderWidth, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(
            Modifier.padding(AppTheme.spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)
        ) {
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val radius = CornerRadius(12.dp.toPx())
                val light = Color(0xFFF6F1F9)
                val dark = Color(0xFF1C1B22)
                when (preview) {
                    TilePreview.LIGHT -> drawRoundRect(light, cornerRadius = radius)

                    TilePreview.DARK -> drawRoundRect(dark, cornerRadius = radius)

                    TilePreview.SPLIT -> {
                        drawRoundRect(light, cornerRadius = radius)
                        drawRoundRect(
                            dark,
                            topLeft = Offset(size.width / 2, 0f),
                            size = Size(size.width / 2, size.height),
                            cornerRadius = radius
                        )
                    }
                }
                val bar = Size(size.width * 0.6f, 8.dp.toPx())
                drawRoundRect(
                    accent,
                    topLeft = Offset(size.width * 0.2f, size.height * 0.25f),
                    size = bar,
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    accent.copy(alpha = 0.5f),
                    topLeft = Offset(size.width * 0.2f, size.height * 0.55f),
                    size = Size(size.width * 0.4f, 8.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
