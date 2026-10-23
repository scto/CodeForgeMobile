package com.codeforge.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.io.File

@Composable
fun DebugToolbar(
    state: DebugUiState,
    onIntent: (DebugUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Status-Zeile
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when (state.sessionState) {
                    DebugSessionState.Idle -> MaterialTheme.colorScheme.outline
                    DebugSessionState.Starting -> Color(0xFFFFA000)
                    DebugSessionState.Running -> Color(0xFF4CAF50)
                    is DebugSessionState.Paused -> Color(0xFF2196F3)
                    is DebugSessionState.Terminated -> MaterialTheme.colorScheme.outline
                    is DebugSessionState.Failed -> MaterialTheme.colorScheme.error
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))

                val statusText = when (val s = state.sessionState) {
                    DebugSessionState.Idle -> stringRes(R.string.debug_status_idle)
                    DebugSessionState.Starting -> stringRes(R.string.debug_status_starting)
                    DebugSessionState.Running -> stringRes(R.string.debug_status_running)
                    is DebugSessionState.Paused -> {
                        val frame = s.frames.firstOrNull()
                        val fileName = frame?.path?.let { File(it).name } ?: "Thread ${s.threadId}"
                        stringRes(R.string.debug_status_paused, fileName, frame?.line ?: 0)
                    }
                    is DebugSessionState.Terminated -> {
                        stringRes(R.string.debug_status_terminated, s.exitCode?.toString() ?: "0")
                    }
                    is DebugSessionState.Failed -> {
                        stringRes(R.string.debug_status_failed, s.message)
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.weight(1f))

                // Modus-Umschalter wenn inaktiv
                if (!state.canStop) {
                    TextButton(
                        onClick = {
                            val nextMode = if (state.debugMode == DebugMode.ANDROID) DebugMode.JVM else DebugMode.ANDROID
                            onIntent(DebugUiIntent.SetDebugMode(nextMode))
                        }
                    ) {
                        Text(
                            text = if (state.debugMode == DebugMode.ANDROID) {
                                stringRes(R.string.debug_mode_android)
                            } else {
                                stringRes(R.string.debug_mode_jvm)
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Steuerleiste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wenn Sitzung inaktiv ist: Start / Attach Button
                if (!state.canStop) {
                    if (state.debugMode == DebugMode.ANDROID) {
                        IconButton(
                            onClick = {
                                if (state.androidTarget.packageName.isNotBlank()) {
                                    onIntent(DebugUiIntent.AttachAndroid(state.androidTarget))
                                } else {
                                    onIntent(DebugUiIntent.SetShowAttachDialog(true))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = stringRes(R.string.debug_action_attach_android),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { onIntent(DebugUiIntent.SetShowAttachDialog(true)) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringRes(R.string.debug_action_configure_target),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onIntent(DebugUiIntent.Start(DebugConfig())) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringRes(R.string.debug_action_start),
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    }
                } else {
                    // Resume
                    IconButton(
                        onClick = { onIntent(DebugUiIntent.Resume) },
                        enabled = state.canResume
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringRes(R.string.debug_action_resume),
                            tint = if (state.canResume) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Pause
                    IconButton(
                        onClick = { onIntent(DebugUiIntent.Pause) },
                        enabled = state.canPause
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringRes(R.string.debug_action_pause),
                            tint = if (state.canPause) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Step Over
                    IconButton(
                        onClick = { onIntent(DebugUiIntent.StepOver) },
                        enabled = state.canStep
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringRes(R.string.debug_action_step_over),
                            tint = if (state.canStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Step Into
                    IconButton(
                        onClick = { onIntent(DebugUiIntent.StepInto) },
                        enabled = state.canStep
                    ) {
                        Icon(
                            imageVector = Icons.Default.South,
                            contentDescription = stringRes(R.string.debug_action_step_into),
                            tint = if (state.canStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Step Out
                    IconButton(
                        onClick = { onIntent(DebugUiIntent.StepOut) },
                        enabled = state.canStep
                    ) {
                        Icon(
                            imageVector = Icons.Default.North,
                            contentDescription = stringRes(R.string.debug_action_step_out),
                            tint = if (state.canStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Stop
                IconButton(
                    onClick = { onIntent(DebugUiIntent.Stop) },
                    enabled = state.canStop
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringRes(R.string.debug_action_stop),
                        tint = if (state.canStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}
