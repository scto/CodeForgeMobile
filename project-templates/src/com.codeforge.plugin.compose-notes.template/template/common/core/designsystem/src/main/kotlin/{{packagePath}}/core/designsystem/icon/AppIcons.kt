package {{packageName}}.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Eigene Symbole, die es in `material-icons-core` nicht gibt (24 × 24, Füllfarbe wird per `tint` gesetzt). */
object AppIcons {
    /** Pause: zwei Balken. */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            moveTo(6f, 5f)
            horizontalLineTo(10f)
            verticalLineTo(19f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            horizontalLineTo(18f)
            verticalLineTo(19f)
            horizontalLineTo(14f)
            close()
        }
    }

    /** Zurückspulen: zwei Dreiecke nach links. */
    val Rewind: ImageVector by lazy {
        icon("Rewind") {
            moveTo(11f, 18f)
            verticalLineTo(6f)
            lineTo(2.5f, 12f)
            close()
            moveTo(20.5f, 18f)
            verticalLineTo(6f)
            lineTo(12f, 12f)
            close()
        }
    }

    /** Vorspulen: zwei Dreiecke nach rechts. */
    val FastForward: ImageVector by lazy {
        icon("FastForward") {
            moveTo(4f, 18f)
            lineTo(12.5f, 12f)
            lineTo(4f, 6f)
            close()
            moveTo(13f, 18f)
            lineTo(21.5f, 12f)
            lineTo(13f, 6f)
            close()
        }
    }

    /** Kopfhörer (Bibliothek). */
    val Headphones: ImageVector by lazy {
        icon("Headphones") {
            moveTo(12f, 3f)
            curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(8f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(12f)
            curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
            curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
            verticalLineTo(13f)
            horizontalLineTo(16f)
            verticalLineTo(21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(12f)
            curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
            close()
        }
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(fill = SolidColor(Color.Black), pathBuilder = block)
            .build()
}
