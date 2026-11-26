/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 *
 * Einstellungsseite „Mit Claude verwenden“: schaltet den lokalen MCP-Server des Agenten (Port 8765, Bearer-Token)
 * und zeigt die Befehle für Claude Code und Claude Desktop zum Kopieren an.
 */
package com.codeforge.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsHint
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import com.codeforge.core.ui.SettingsSwitchRow
import com.codeforge.libs.agent.LoopbackRedirect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Seite „Mit Claude verwenden“. */
@Immutable
data class UseWithClaudeState(val enabled: Boolean = false, val token: String = "") {
    /** Adresse des MCP-Servers (über `adb forward` auf dem Rechner gleich). */
    val url: String get() = "http://127.0.0.1:${AgentConnections.HOST_PORT}/mcp"

    /** `adb`-Weiterleitung. */
    val adbCommand: String get() = "adb forward tcp:${AgentConnections.HOST_PORT} tcp:${AgentConnections.HOST_PORT}"

    /** Einmaliger Befehl für Claude Code. */
    val claudeCodeCommand: String
        get() = "claude mcp add --transport http --scope user codeforge $url --header \"Authorization: Bearer $token\""

    /** Eintrag für `claude_desktop_config.json` (über `mcp-remote`). */
    val desktopConfig: String
        get() = """
            |{
            |  "mcpServers": {
            |    "codeforge": {
            |      "command": "npx",
            |      "args": ["-y", "mcp-remote", "$url", "--allow-http",
            |               "--header", "Authorization: Bearer $token"]
            |    }
            |  }
            |}
        """.trimMargin()
}

/** ViewModel: liest und speichert die Host-Einstellungen in [AgentConnections]. */
@HiltViewModel
class UseWithClaudeViewModel @Inject constructor(
    private val connections: AgentConnections
) : ViewModel() {

    private val _state = MutableStateFlow(UseWithClaudeState())
    /** UI-Zustand. */
    val state: StateFlow<UseWithClaudeState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val (enabled, token) = connections.hostSettings()
            _state.value = UseWithClaudeState(enabled, token)
        }
    }

    /** Server an-/ausschalten (gilt beim nächsten Öffnen des Agenten-Panels). */
    fun setEnabled(enabled: Boolean) = save(_state.value.copy(enabled = enabled))

    /** Neuen Token erzeugen; der alte verliert sofort seine Gültigkeit beim nächsten Serverstart. */
    fun regenerateToken() = save(_state.value.copy(token = LoopbackRedirect.randomToken(TOKEN_BYTES)))

    private fun save(next: UseWithClaudeState) {
        _state.value = next
        viewModelScope.launch(Dispatchers.IO) { connections.saveHost(next.enabled, next.token) }
    }

    private companion object {
        const val TOKEN_BYTES = 24
    }
}

/** Route der Seite „Mit Claude verwenden“. */
@Composable
fun UseWithClaudeRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: UseWithClaudeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    SettingsScaffold(title = stringRes(R.string.use_with_claude_title), onBack = onBack, modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsHint(stringRes(R.string.use_with_claude_intro), Modifier.padding(horizontal = 4.dp))
            SettingsGroup(Modifier.padding(top = 8.dp)) {
                SettingsSwitchRow(
                    title = stringRes(R.string.use_with_claude_enable),
                    summary = stringRes(R.string.use_with_claude_enable_desc),
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
                Text(
                    stringRes(R.string.use_with_claude_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
            if (state.enabled) {
                SettingsSectionLabel(stringRes(R.string.use_with_claude_setup))
                SettingsGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepTitle(stringRes(R.string.use_with_claude_step_adb))
                        CodeBlock(state.adbCommand)
                        StepTitle(stringRes(R.string.use_with_claude_step_code))
                        CodeBlock(state.claudeCodeCommand)
                        StepTitle(stringRes(R.string.use_with_claude_step_desktop))
                        CodeBlock(state.desktopConfig)
                        Text(
                            stringRes(R.string.use_with_claude_desktop_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = viewModel::regenerateToken) { Text(stringRes(R.string.use_with_claude_new_token)) }
                    }
                }
                SettingsHint(stringRes(R.string.use_with_claude_runtime_hint), Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CodeBlock(code: String) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 44.dp)
            )
            Row(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringRes(R.string.use_with_claude_copy))
                }
            }
        }
    }
}
