@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
// Modul: :feature:terminal
package com.codeforge.feature.terminal

import com.codeforge.core.resources.ResGetter

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var sessionToRename by remember { mutableStateOf<TerminalSessionUiModel?>(null) }
    var activeTerminalView by remember { mutableStateOf<com.nyamux.view.TerminalView?>(null) }
    var activeClient by remember { mutableStateOf<com.codeforge.feature.terminal.TerminalViewClientImpl?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                TerminalUiEvent.NavigateBack -> onNavigateBack()
                TerminalUiEvent.NavigateToSettings -> onNavigateToSettings()
                is TerminalUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1E1E1E),
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header: Sitzungen title + Add & Settings icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sitzungen",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row {
                            IconButton(onClick = {
                                viewModel.onAction(TerminalUiAction.OnCreateNewSession)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_new_session), tint = Color.White)
                            }
                            IconButton(onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToSettings()
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_settings), tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Active Sessions list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.activeSessions, key = { it.id }) { session ->
                            val isSelected = session.id == uiState.currentSessionId
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF333333) else Color(0xFF252525),
                                onClick = {
                                    viewModel.onAction(TerminalUiAction.OnSessionSelected(session.id))
                                    scope.launch { drawerState.close() }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = session.title,
                                        color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Rename pencil icon button
                                    IconButton(
                                        onClick = { sessionToRename = session },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_rename),
                                            tint = Color(0xFFAAAAAA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete trash icon button
                                    IconButton(
                                        onClick = {
                                            viewModel.onAction(TerminalUiAction.OnCloseSession(session.id))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_close),
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .background(Color.Black)
            ) {
                // Top Bar with Drawer Toggle, Back, Sessions Tabs & WakeLock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_menu), tint = Color.White)
                    }

                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back), tint = Color.White)
                    }

                    if (uiState.activeSessions.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(uiState.activeSessions.size) { index ->
                                val session = uiState.activeSessions[index]
                                val isSelected = session.id == uiState.currentSessionId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) Color(0xFF333333) else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            viewModel.onAction(TerminalUiAction.OnSessionSelected(session.id))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = session.title,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✕",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            viewModel.onAction(TerminalUiAction.OnCloseSession(session.id))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.onAction(TerminalUiAction.OnCreateNewSession) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    TextButton(
                        onClick = { viewModel.onAction(TerminalUiAction.OnToggleWakeLock) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (uiState.isWakeLockAcquired) Color(0xFF1B5E20) else Color(0xFF333333),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (uiState.isWakeLockAcquired) "⚡ WakeLock ON" else "💤 WakeLock OFF",
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_settings), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Terminal Emulator View or Installation UI Component
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (uiState.isInstalling || uiState.installErrorMessage != null) {
                        TerminalInstallationView(
                            uiState = uiState,
                            onRetry = { viewModel.onAction(TerminalUiAction.OnRetryInstallation) }
                        )
                    } else if (uiState.isLoading) {
                        Text("Lade Terminal...", color = Color.Gray)
                    } else if (uiState.currentSessionId == null) {
                        Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_no_open_session), color = Color.Gray)
                    } else {
                        AndroidView(
                            factory = { context ->
                                com.nyamux.view.TerminalView(context, null).apply {
                                    val client = com.codeforge.feature.terminal.TerminalViewClientImpl(context, this, viewModel)
                                    setTerminalViewClient(client)
                                    this.tag = client
                                    activeTerminalView = this
                                    activeClient = client
                                    val sessionId = uiState.currentSessionId
                                    if (sessionId != null) {
                                        val nativeSession = viewModel.getNativeSession(sessionId) as? com.nyamux.terminal.TerminalSession
                                        if (nativeSession != null) {
                                            attachSession(nativeSession)
                                            nativeSession.updateTerminalSessionClient(client)
                                        }
                                    }
                                    setTextSize(uiState.fontSize)
                                    applyColorScheme(this, uiState.terminalColorScheme)
                                }
                            },
                            update = { view ->
                                activeTerminalView = view
                                activeClient = view.tag as? com.codeforge.feature.terminal.TerminalViewClientImpl
                                val sessionId = uiState.currentSessionId
                                if (sessionId != null) {
                                    val nativeSession = viewModel.getNativeSession(sessionId) as? com.nyamux.terminal.TerminalSession
                                    if (nativeSession != null && view.currentSession != nativeSession) {
                                        view.attachSession(nativeSession)
                                        (view.tag as? com.codeforge.feature.terminal.TerminalViewClientImpl)?.let { nativeSession.updateTerminalSessionClient(it) }
                                    }
                                }
                                view.setTextSize(uiState.fontSize)
                                applyColorScheme(view, uiState.terminalColorScheme)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Termux-Style Native Extra Keys Bar & Symbol Toolbar
                if (uiState.isVirtualKeysVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(vertical = 2.dp)
                    ) {
                        val currentTermView = activeTerminalView
                        val currentClientInstance = activeClient
                        if (currentTermView != null) {
                            AndroidView(
                                factory = { context ->
                                    val themedContext = androidx.appcompat.view.ContextThemeWrapper(
                                        context,
                                        com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar
                                    )
                                    com.nyamux.shared.termux.extrakeys.ExtraKeysView(themedContext, null).apply {
                                        val extraKeysInfo = com.nyamux.shared.termux.extrakeys.ExtraKeysInfo(
                                            "[[\"ESC\",\"TAB\",\"CTRL\",\"ALT\",\"-\",\"/\",\"DOWN\",\"UP\"],[\"KEYBOARD\",\"PASTE\",\"LEFT\",\"RIGHT\",\"HOME\",\"END\",\"PGUP\",\"PGDN\"]]",
                                            com.nyamux.shared.termux.extrakeys.ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
                                            com.nyamux.shared.termux.extrakeys.ExtraKeysConstants.CONTROL_CHARS_ALIASES
                                        )
                                        extraKeysViewClient = com.nyamux.shared.termux.terminal.io.TerminalExtraKeys(currentTermView)
                                        reload(extraKeysInfo, 36f * context.resources.displayMetrics.density)
                                        currentClientInstance?.extraKeysView = this
                                    }
                                },
                                update = { extraView ->
                                    currentClientInstance?.extraKeysView = extraView
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            )
                        }

                        val symbolKeys = listOf(
                            "-", "/", "|", "\\", "~", "_", "\"", "'", ":", ";",
                            "$", "{", "}", "[", "]", "(", ")", "<", ">", "=", "+", "?", "!"
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(symbolKeys.size) { index ->
                                val key = symbolKeys[index]
                                TextButton(
                                    onClick = { viewModel.onAction(TerminalUiAction.OnVirtualKeyPressed(key)) },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = Color(0xFF2A2A2A),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(text = key, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Session Dialog
    sessionToRename?.let { targetSession ->
        var editedTitle by remember(targetSession) { mutableStateOf(targetSession.title) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_rename_title)) },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_terminal_session_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedTitle.isNotBlank()) {
                            viewModel.onAction(TerminalUiAction.OnRenameSession(targetSession.id, editedTitle.trim()))
                        }
                        sessionToRename = null
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

fun applyColorScheme(view: com.nyamux.view.TerminalView, scheme: String) {
    val props = java.util.Properties()
    when (scheme.lowercase()) {
        "dracula" -> {
            props.setProperty("background", "#282A36")
            props.setProperty("foreground", "#F8F8F2")
            props.setProperty("cursor", "#FF79C6")
        }
        "nord" -> {
            props.setProperty("background", "#2E3440")
            props.setProperty("foreground", "#D8DEE9")
            props.setProperty("cursor", "#88C0D0")
        }
        "cyberpunk" -> {
            props.setProperty("background", "#000B1E")
            props.setProperty("foreground", "#00F0FF")
            props.setProperty("cursor", "#FF007F")
        }
        "solarized" -> {
            props.setProperty("background", "#002B36")
            props.setProperty("foreground", "#839496")
            props.setProperty("cursor", "#93A1A1")
        }
        "gruvbox" -> {
            props.setProperty("background", "#282828")
            props.setProperty("foreground", "#EBDBB2")
            props.setProperty("cursor", "#FE8019")
        }
        "monokai" -> {
            props.setProperty("background", "#2D2A2E")
            props.setProperty("foreground", "#FCFCFA")
            props.setProperty("cursor", "#FF6188")
        }
        "onedark" -> {
            props.setProperty("background", "#282C34")
            props.setProperty("foreground", "#ABB2BF")
            props.setProperty("cursor", "#61AFEF")
        }
        "tokyonight" -> {
            props.setProperty("background", "#1A1B26")
            props.setProperty("foreground", "#A9B1D6")
            props.setProperty("cursor", "#7AA2F7")
        }
        "matrix" -> {
            props.setProperty("background", "#000E00")
            props.setProperty("foreground", "#00FF41")
            props.setProperty("cursor", "#00FF41")
        }
        "catppuccin" -> {
            props.setProperty("background", "#1E1E2E")
            props.setProperty("foreground", "#CDD6F4")
            props.setProperty("cursor", "#89B4FA")
        }
        "light" -> {
            props.setProperty("background", "#FAFAFA")
            props.setProperty("foreground", "#1F2328")
            props.setProperty("cursor", "#0969DA")
        }
        "dark" -> {
            props.setProperty("background", "#1E1E1E")
            props.setProperty("foreground", "#FFFFFF")
            props.setProperty("cursor", "#FFFFFF")
        }
        else -> { // "default" / Cyberpunk Dark
            props.setProperty("background", "#0D1117")
            props.setProperty("foreground", "#C9D1D9")
            props.setProperty("cursor", "#58A6FF")
        }
    }
    runCatching {
        com.nyamux.terminal.TerminalColors.COLOR_SCHEME.updateWith(props)
        view.mTermSession?.emulator?.mColors?.reset()
        view.onScreenUpdated()
        view.postInvalidate()
    }
}

@Composable
fun TerminalInstallationView(
    uiState: TerminalUiState,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1117))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ubuntu Icon Circle Container
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = Color(0xFF1E222D)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🐧",
                    fontSize = 48.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Ubuntu 24.04 LTS Installation",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = uiState.installPhaseText,
            fontSize = 14.sp,
            color = Color(0xFFA0AEC0)
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.installErrorMessage != null) {
            Text(
                text = "Fehler bei der Installation:",
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.installErrorMessage,
                color = Color(0xFFFF8888),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                )
            ) {
                Text("Erneut versuchen", color = Color.White)
            }
        } else {
            // Animated / Linear Progress Bar
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { uiState.installProgressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF6366F1),
                    trackColor = Color(0xFF1E222D)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${uiState.installProgressPercent}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6366F1)
                )
            }
        }
    }
}
