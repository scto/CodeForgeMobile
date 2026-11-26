/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.domain.extension.TerminalPalette
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Abschnitt „Farbschema“: große Vorschau des aktiven Schemas und Auswahl als Kacheln mit Mini-Vorschau. */
@Composable
internal fun TerminalThemeCard(
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = uiState.terminalThemes.firstOrNull { it.id == uiState.terminalThemeId }
    val palette = selected?.palette ?: uiState.customTerminalPalette ?: uiState.terminalThemes.firstOrNull()?.palette
    if (uiState.terminalThemeMessage != null) {
        LaunchedEffect(uiState.terminalThemeMessage) {
            kotlinx.coroutines.delay(MESSAGE_MS)
            onEvent(TerminalSettingsUiEvent.TerminalThemeMessageShown)
        }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(R.string.settings_terminal_theme), style = MaterialTheme.typography.titleSmall)
            Text(
                stringRes(R.string.settings_terminal_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            palette?.let { TerminalPreview(it, Modifier.fillMaxWidth()) }
            if (uiState.terminalThemeId == TerminalThemes.CUSTOM_ID) {
                Text(stringRes(R.string.settings_terminal_theme_custom), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.terminalThemes.forEach { option ->
                    ThemeTile(option, option.id == uiState.terminalThemeId) {
                        onEvent(TerminalSettingsUiEvent.TerminalThemeSelected(option.id))
                    }
                }
            }
            uiState.terminalThemeMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ThemeTile(option: TerminalThemeOption, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(onClick = onClick, border = border, modifier = Modifier.width(TILE_WIDTH.dp)) {
        Column(modifier = Modifier.background(option.palette.background.rgb()).padding(8.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = option.palette.ansi[GREEN].rgb())) { append("~ ") }
                    withStyle(SpanStyle(color = option.palette.foreground.rgb())) { append("ls") }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                option.palette.ansi.take(NORMAL_COLORS).drop(1).forEach { c ->
                    Box(Modifier.size(SWATCH.dp).clip(RoundedCornerShape(2.dp)).background(c.rgb()))
                }
            }
        }
        Text(
            option.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

// Beispielausgabe im Terminal mit Prompt, Befehlen und allen 16 Farben.
@Composable
private fun TerminalPreview(p: TerminalPalette, modifier: Modifier = Modifier) {
    fun c(i: Int) = p.ansi[i].rgb()
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = c(GREEN), fontWeight = FontWeight.Bold)) { append("thomas@codeforge") }
        withStyle(SpanStyle(color = p.foreground.rgb())) { append(":") }
        withStyle(SpanStyle(color = c(BLUE), fontWeight = FontWeight.Bold)) { append("~/project") }
        withStyle(SpanStyle(color = p.foreground.rgb())) { append("$ git status\n") }
        withStyle(SpanStyle(color = p.foreground.rgb())) { append("On branch ") }
        withStyle(SpanStyle(color = c(CYAN))) { append("main\n") }
        withStyle(SpanStyle(color = c(GREEN))) { append("\tmodified:   app/build.gradle.kts\n") }
        withStyle(SpanStyle(color = c(RED))) { append("\tdeleted:    old/Legacy.kt\n") }
        withStyle(SpanStyle(color = c(YELLOW))) { append("warning: ") }
        withStyle(SpanStyle(color = p.foreground.rgb())) { append("LF will be replaced by CRLF\n") }
        withStyle(SpanStyle(color = c(MAGENTA))) { append("BUILD SUCCESSFUL") }
        withStyle(SpanStyle(color = p.foreground.rgb())) { append(" in 12s ") }
        withStyle(SpanStyle(background = p.cursor.rgb())) { append(" ") }
    }
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(p.background.rgb()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until NORMAL_COLORS) {
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(2.dp)).background(c(row * NORMAL_COLORS + i)))
                }
            }
        }
    }
}

private fun Int.rgb() = Color(0xFF000000.toInt() or (this and 0xFFFFFF))

private const val RED = 1
private const val GREEN = 2
private const val YELLOW = 3
private const val BLUE = 4
private const val MAGENTA = 5
private const val CYAN = 6
private const val NORMAL_COLORS = 8
private const val TILE_WIDTH = 116
private const val SWATCH = 10
private const val MESSAGE_MS = 5_000L
