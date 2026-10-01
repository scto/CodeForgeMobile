@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:settings:terminal
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.terminal

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

private val distros = listOf("alpine" to "Alpine Linux", "ubuntu" to "Ubuntu Linux", "debian" to "Debian Linux")

private data class TerminalThemePreset(
    val id: String,
    val name: String,
    val bg: Color,
    val fg: Color,
    val promptColor: Color,
    val dirColor: Color,
    val fileColor: Color,
    val paletteDots: List<Color>
)

private val themePresets = listOf(
    TerminalThemePreset(
        id = "default",
        name = "Standard",
        bg = Color(0xFF0D1117),
        fg = Color(0xFFC9D1D9),
        promptColor = Color(0xFF58A6FF),
        dirColor = Color(0xFF79C0FF),
        fileColor = Color(0xFFC9D1D9),
        paletteDots = listOf(Color(0xFF58A6FF), Color(0xFF3FB950), Color(0xFFD2A8FF), Color(0xFFFF7B72), Color(0xFFFFA657), Color(0xFF79C0FF))
    ),
    TerminalThemePreset(
        id = "dracula",
        name = "Dracula",
        bg = Color(0xFF282A36),
        fg = Color(0xFFF8F8F2),
        promptColor = Color(0xFFFF79C6),
        dirColor = Color(0xFFBD93F9),
        fileColor = Color(0xFFF8F8F2),
        paletteDots = listOf(Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD), Color(0xFF50FA7B), Color(0xFFF1FA8C), Color(0xFFFF5555))
    ),
    TerminalThemePreset(
        id = "nord",
        name = "Nord",
        bg = Color(0xFF2E3440),
        fg = Color(0xFFD8DEE9),
        promptColor = Color(0xFF88C0D0),
        dirColor = Color(0xFF81A1C1),
        fileColor = Color(0xFFD8DEE9),
        paletteDots = listOf(Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFF5E81AC), Color(0xFFA3BE8C), Color(0xFFEBCB8B), Color(0xFFBF616A))
    ),
    TerminalThemePreset(
        id = "cyberpunk",
        name = "Cyberpunk",
        bg = Color(0xFF000B1E),
        fg = Color(0xFF00F0FF),
        promptColor = Color(0xFFFF007F),
        dirColor = Color(0xFF00F0FF),
        fileColor = Color(0xFFFFE600),
        paletteDots = listOf(Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFFFFE600), Color(0xFF7000FF), Color(0xFF00FF66), Color(0xFFFF3366))
    ),
    TerminalThemePreset(
        id = "solarized",
        name = "Solarized Dark",
        bg = Color(0xFF002B36),
        fg = Color(0xFF839496),
        promptColor = Color(0xFF268BD2),
        dirColor = Color(0xFF2AA198),
        fileColor = Color(0xFF839496),
        paletteDots = listOf(Color(0xFF268BD2), Color(0xFF2AA198), Color(0xFF859900), Color(0xFFB58900), Color(0xFFCB4B16), Color(0xFFD33682))
    ),
    TerminalThemePreset(
        id = "gruvbox",
        name = "Gruvbox",
        bg = Color(0xFF282828),
        fg = Color(0xFFEBDBB2),
        promptColor = Color(0xFFFE8019),
        dirColor = Color(0xFF83A598),
        fileColor = Color(0xFFEBDBB2),
        paletteDots = listOf(Color(0xFFFE8019), Color(0xFFFABD2F), Color(0xFFB8BB26), Color(0xFF8EC07C), Color(0xFF83A598), Color(0xFFFB4934))
    ),
    TerminalThemePreset(
        id = "monokai",
        name = "Monokai Pro",
        bg = Color(0xFF2D2A2E),
        fg = Color(0xFFFCFCFA),
        promptColor = Color(0xFFFF6188),
        dirColor = Color(0xFF78DCE8),
        fileColor = Color(0xFFFCFCFA),
        paletteDots = listOf(Color(0xFFFF6188), Color(0xFFA9DC76), Color(0xFFFFD866), Color(0xFF78DCE8), Color(0xFFAB9DF2), Color(0xFFFF6188))
    ),
    TerminalThemePreset(
        id = "onedark",
        name = "One Dark",
        bg = Color(0xFF282C34),
        fg = Color(0xFFABB2BF),
        promptColor = Color(0xFF61AFEF),
        dirColor = Color(0xFF61AFEF),
        fileColor = Color(0xFFABB2BF),
        paletteDots = listOf(Color(0xFF61AFEF), Color(0xFF98C379), Color(0xFFE5C07B), Color(0xFFE06C75), Color(0xFFC678DD), Color(0xFF56B6C2))
    ),
    TerminalThemePreset(
        id = "tokyonight",
        name = "Tokyo Night",
        bg = Color(0xFF1A1B26),
        fg = Color(0xFFA9B1D6),
        promptColor = Color(0xFF7AA2F7),
        dirColor = Color(0xFF7DCFFF),
        fileColor = Color(0xFFA9B1D6),
        paletteDots = listOf(Color(0xFF7AA2F7), Color(0xFF7DCFFF), Color(0xFF9ECE6A), Color(0xFFE0AF68), Color(0xFFBB9AF7), Color(0xFFF7768E))
    ),
    TerminalThemePreset(
        id = "matrix",
        name = "Matrix",
        bg = Color(0xFF000E00),
        fg = Color(0xFF00FF41),
        promptColor = Color(0xFF00FF41),
        dirColor = Color(0xFF00FF66),
        fileColor = Color(0xFF00CC44),
        paletteDots = listOf(Color(0xFF00FF41), Color(0xFF003B00), Color(0xFF008F11), Color(0xFF00FF66), Color(0xFF00CC44), Color(0xFF009933))
    ),
    TerminalThemePreset(
        id = "catppuccin",
        name = "Catppuccin",
        bg = Color(0xFF1E1E2E),
        fg = Color(0xFFCDD6F4),
        promptColor = Color(0xFF89B4FA),
        dirColor = Color(0xFF89B4FA),
        fileColor = Color(0xFFCDD6F4),
        paletteDots = listOf(Color(0xFF89B4FA), Color(0xFFA6E3A1), Color(0xFFF9E2AF), Color(0xFFFAB387), Color(0xFFF5C2E7), Color(0xFFCBA6F7))
    ),
    TerminalThemePreset(
        id = "light",
        name = "Classic Light",
        bg = Color(0xFFFAFAFA),
        fg = Color(0xFF1F2328),
        promptColor = Color(0xFF0969DA),
        dirColor = Color(0xFF0969DA),
        fileColor = Color(0xFF1F2328),
        paletteDots = listOf(Color(0xFF0969DA), Color(0xFF1A7F37), Color(0xFF8250DF), Color(0xFFCF222E), Color(0xFF9A6700), Color(0xFF3192AA))
    )
)

private fun formatScrollback(lines: Int): String {
    return if (lines >= 1000) {
        val k = lines / 1000
        val rem = (lines % 1000) / 100
        if (rem > 0) "${k}.${rem}K" else "${k}K"
    } else {
        "$lines"
    }
}

@Composable
fun TerminalSettingsRoute(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TerminalSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    TerminalSettingsScreen(
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun TerminalSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: TerminalSettingsUiState,
    onEvent: (TerminalSettingsUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back)
                        )
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- ABSCHNITT 1: Status & System-Informationen ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isTerminalInstalled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (uiState.isTerminalInstalled)
                            "⚡ Terminal-Umgebung aktiv & bereit"
                        else
                            "⚠️ Terminal-Umgebung nicht eingerichtet",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (uiState.isTerminalInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aktive Distribution: ${uiState.defaultDistro.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isTerminalInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Distro-Version: ${uiState.distroVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isTerminalInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "PRoot-Engine: ${uiState.prootVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isTerminalInstalled)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // --- ABSCHNITT 2: Standard-Distribution ---
            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_default_distro), style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    distros.forEach { (id, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = id == uiState.defaultDistro,
                                    onClick = { onEvent(TerminalSettingsUiEvent.DistroSelected(id)) }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = id == uiState.defaultDistro,
                                onClick = { onEvent(TerminalSettingsUiEvent.DistroSelected(id)) }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            // --- ABSCHNITT 3: Puffer & Anzeige (Scrollback Lines Slider Card) ---
            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_scrollback_puffer), style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Scrollback Lines", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Anzahl der Zeilen im Terminal-Puffer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = formatScrollback(uiState.scrollbackLines),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = uiState.scrollbackLines.toFloat(),
                        onValueChange = { onEvent(TerminalSettingsUiEvent.ScrollbackLinesChanged(it.roundToInt())) },
                        valueRange = 500f..50000f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("500", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("10K", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("25K", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("50K", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // --- ABSCHNITT 4: Anzeige & Verhalten ---
            Text("Anzeige & Verhalten", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Schriftgröße", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                onEvent(TerminalSettingsUiEvent.FontSizeChanged((uiState.fontSize - 1).coerceAtLeast(8)))
                            }) {
                                Text("–")
                            }
                            Text("${uiState.fontSize} sp", modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = {
                                onEvent(TerminalSettingsUiEvent.FontSizeChanged((uiState.fontSize + 1).coerceAtMost(32)))
                            }) {
                                Text("+")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Virtuelle Tasten (Extra Keys)", style = MaterialTheme.typography.titleSmall)
                            Text("Spezialtasten (Tab, Ctrl, Alt, ESC, Pfad-Tasten) einblenden", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.isVirtualKeysVisible,
                            onCheckedChange = { onEvent(TerminalSettingsUiEvent.VirtualKeysToggled(it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Terminal Bell (Glocke)", style = MaterialTheme.typography.titleSmall)
                            Text("Akustische/visuelle Signale bei BEL-Zeichen", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.bellEnabled,
                            onCheckedChange = { onEvent(TerminalSettingsUiEvent.BellToggled(it)) }
                        )
                    }
                }
            }

            // --- ABSCHNITT 5: Color Scheme Horizontal Card Row (Terminal-Settings.png) ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Farbschema (Color Scheme)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Terminal- und App-Farbschema wählen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(themePresets) { preset ->
                        val isSelected = preset.id == uiState.terminalColorScheme
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onEvent(TerminalSettingsUiEvent.ColorSchemeSelected(preset.id))
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Mini Terminal Preview Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .background(preset.bg, shape = RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Row {
                                            Text("$ ", color = preset.promptColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            Text("ls -la", color = preset.fg, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Text("drwxr user", color = preset.dirColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Text("file.txt", color = preset.fileColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 6 Color Palette Dots
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    preset.paletteDots.take(6).forEach { dotColor ->
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- ABSCHNITT 6: Wartung & Aktionen ---
            Text("Wartung & Aktionen", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(
                        onClick = { onEvent(TerminalSettingsUiEvent.CheckBootstrapUpdateClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isCheckingBootstrapUpdate && !uiState.isReinstalling
                    ) {
                        Text(if (uiState.isCheckingBootstrapUpdate) "Prüfe auf Updates..." else "Auf Bootstrap-Updates prüfen")
                    }

                    uiState.bootstrapUpdateMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onEvent(TerminalSettingsUiEvent.ResetTerminalClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isReinstalling
                    ) {
                        Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_terminal_reset_settings))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = { onEvent(TerminalSettingsUiEvent.ReinstallTerminalClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isReinstalling
                    ) {
                        Text(if (uiState.isReinstalling) "Neuinstallation läuft..." else "Terminal neu installieren")
                    }

                    if (uiState.isReinstalling) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { uiState.reinstallProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Neuinstallation: ${uiState.reinstallProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    uiState.reinstallErrorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Fehler: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
