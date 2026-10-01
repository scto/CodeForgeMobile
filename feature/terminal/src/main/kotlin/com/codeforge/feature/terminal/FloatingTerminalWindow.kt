package com.codeforge.feature.terminal

import com.codeforge.core.resources.ResGetter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun FloatingTerminalWindow(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(true) }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        label = "ArrowRotation"
    )

    var activeTerminalView by remember { mutableStateOf<com.nyamux.view.TerminalView?>(null) }
    var activeClient by remember { mutableStateOf<com.codeforge.feature.terminal.TerminalViewClientImpl?>(null) }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        color = Color(0xFF1E1E1E),
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // --- HEADER / TITELLEISTE (Drag-Handle & Controls) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF282828))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.activeSessions.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(uiState.activeSessions.size) { index ->
                            val session = uiState.activeSessions[index]
                            val isSelected = session.id == uiState.currentSessionId
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) Color(0xFF3E3E3E) else Color.Transparent,
                                onClick = {
                                    viewModel.onAction(TerminalUiAction.OnSessionSelected(session.id))
                                    if (!isExpanded) isExpanded = true
                                }
                            ) {
                                Text(
                                    text = session.title,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Terminal (RootFS Log & Console)",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                IconButton(
                    onClick = { viewModel.onAction(TerminalUiAction.OnCreateNewSession) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("+", color = Color.White, fontSize = 16.sp)
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                        tint = Color.White,
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_close),
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // --- AUSKLAPPBARER TERMINAL BODY ---
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color.Black)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                com.nyamux.view.TerminalView(ctx, null).apply {
                                    val client = com.codeforge.feature.terminal.TerminalViewClientImpl(ctx, this, viewModel)
                                    setTerminalViewClient(client)
                                    this.tag = client
                                    activeTerminalView = this
                                    activeClient = client
                                    uiState.currentSessionId?.let { sid ->
                                        (viewModel.getNativeSession(sid) as? com.nyamux.terminal.TerminalSession)?.let {
                                            attachSession(it)
                                            it.updateTerminalSessionClient(client)
                                        }
                                    }
                                    setTextSize(uiState.fontSize)
                                    applyColorScheme(this, uiState.terminalColorScheme)
                                }
                            },
                            update = { view ->
                                activeTerminalView = view
                                activeClient = view.tag as? com.codeforge.feature.terminal.TerminalViewClientImpl
                                uiState.currentSessionId?.let { sid ->
                                    val session = viewModel.getNativeSession(sid) as? com.nyamux.terminal.TerminalSession
                                    if (session != null && view.currentSession != session) {
                                        view.attachSession(session)
                                        activeClient?.let { session.updateTerminalSessionClient(it) }
                                    }
                                }
                                view.setTextSize(uiState.fontSize)
                                applyColorScheme(view, uiState.terminalColorScheme)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (uiState.isVirtualKeysVisible && activeTerminalView != null) {
                        AndroidView(
                            factory = { ctx ->
                                val themed = androidx.appcompat.view.ContextThemeWrapper(
                                    ctx,
                                    com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar
                                )
                                com.nyamux.shared.termux.extrakeys.ExtraKeysView(themed, null).apply {
                                    val info = com.nyamux.shared.termux.extrakeys.ExtraKeysInfo(
                                        "[[\"ESC\",\"TAB\",\"CTRL\",\"ALT\",\"-\",\"/\",\"DOWN\",\"UP\"]]",
                                        com.nyamux.shared.termux.extrakeys.ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
                                        com.nyamux.shared.termux.extrakeys.ExtraKeysConstants.CONTROL_CHARS_ALIASES
                                    )
                                    extraKeysViewClient = com.nyamux.shared.termux.terminal.io.TerminalExtraKeys(activeTerminalView!!)
                                    reload(info, 30f * ctx.resources.displayMetrics.density)
                                    activeClient?.extraKeysView = this
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                    }
                }
            }
        }
    }
}
