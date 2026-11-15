package {{packageName}}.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.designsystem.theme.WaveBottomShape

/**
 * Kopfbereich mit Akzentverlauf, dekorativen Kreisen und geschwungener Unterkante.
 * [trailing] erscheint rechts neben dem Titel, [content] darunter (z. B. ein Suchfeld).
 */
@Composable
fun GradientHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(WaveBottomShape)
            .background(AppGradients.accent)
    ) {
        DecorativeCircles(Modifier.matchParentSize())
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 52.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.align(androidx.compose.ui.Alignment.CenterStart).padding(end = 56.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = AppTheme.colors.onGradient,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.onGradient.copy(alpha = 0.85f)
                        )
                    }
                }
                Box(Modifier.align(androidx.compose.ui.Alignment.CenterEnd)) { trailing() }
            }
            content()
        }
    }
}

/** Halbtransparente Kreise als Schmuck im Verlauf. */
@Composable
private fun DecorativeCircles(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, 0f))
        drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.1f, size.height))
        drawCircle(
            Color.White.copy(alpha = 0.12f),
            radius = size.minDimension * 0.08f,
            center = Offset(
                size.width * 0.7f,
                size.height * 0.7f
            )
        )
    }
}

/** Karte mit Glas-Optik: halbtransparente Fläche mit feinem Rand. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = AppTheme.colors.glass,
        border = BorderStroke(1.dp, AppTheme.colors.glassBorder),
        shadowElevation = 2.dp
    ) {
        Box(Modifier.padding(AppTheme.spacing.l), content = content)
    }
}

@Preview
@Composable
private fun GradientHeaderPreview() {
    AppTheme { GradientHeader(title = "Entdecken", subtitle = "Guten Abend") }
}
