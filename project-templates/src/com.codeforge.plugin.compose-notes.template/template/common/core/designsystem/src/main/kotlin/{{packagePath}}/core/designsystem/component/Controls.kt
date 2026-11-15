package {{packageName}}.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.designsystem.theme.PillShape

/** Knopf mit Akzentverlauf in Pillenform. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(AppGradients.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = AppTheme.colors.onGradient)
        Text(text, style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.onGradient)
    }
}

/** Auswahl-Chip in Pillenform; ausgewählt mit Akzentverlauf. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textColor by animateColorAsState(
        if (selected) AppTheme.colors.onGradient else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipText"
    )
    val background = if (selected) AppGradients.accent else Brush.linearGradient(List(2) { MaterialTheme.colorScheme.surfaceContainerHigh })
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(background)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

/** Runde Farbfläche zur Auswahl (Akzentfarbe, Kartenfarbe); ausgewählt mit Haken und leichtem Zoom. */
@Composable
fun ColorSwatch(brush: Brush, selected: Boolean, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swatchScale"
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(brush)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)), CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = contentDescription, tint = Color.White)
    }
}

/** Reihe von Elementen mit einheitlichem Abstand. */
@Composable
fun SpacedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), content = content)
}

/** Runder Knopf mit Akzentverlauf (z. B. Wiedergabe). */
@Composable
fun GradientIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier.size(size)
    ) {
        Box(Modifier.background(AppGradients.accent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = AppTheme.colors.onGradient, modifier = Modifier.size(size / 2))
        }
    }
}
