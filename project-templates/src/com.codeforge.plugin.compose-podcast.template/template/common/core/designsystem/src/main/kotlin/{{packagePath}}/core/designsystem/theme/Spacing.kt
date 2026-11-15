package {{packageName}}.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Abstände der App; überall [AppTheme.spacing] statt fester dp-Werte verwenden. */
@Immutable
data class Spacing(val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp, val l: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp)
