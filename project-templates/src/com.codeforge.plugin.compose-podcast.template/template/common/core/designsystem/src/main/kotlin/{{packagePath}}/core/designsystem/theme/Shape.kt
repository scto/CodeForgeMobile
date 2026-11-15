package {{packageName}}.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Großzügig gerundete Formen statt der Material-Standardwerte. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Pillenform für Chips und Knöpfe. */
val PillShape = RoundedCornerShape(percent = 50)

/** Unten geschwungene Form für Kopfbereiche mit Verlauf. */
object WaveBottomShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val wave = with(density) { 36.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - wave)
            cubicTo(
                size.width * 0.75f,
                size.height + wave * 0.4f,
                size.width * 0.3f,
                size.height - wave * 1.6f,
                0f,
                size.height - wave * 0.4f
            )
            close()
        }
        return Outline.Generic(path)
    }
}
