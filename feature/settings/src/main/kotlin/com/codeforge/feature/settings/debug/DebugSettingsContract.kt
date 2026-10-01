/**
 * Modul: :feature:settings:debug
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.debug

import androidx.compose.runtime.Immutable
import com.codeforge.core.datastore.proto.LogLevelProto

@Immutable
data class DebugSettingsUiState(
    val loggingEnabled: Boolean = false,
    val logLevel: LogLevelProto = LogLevelProto.LOG_LEVEL_INFO,
    val excessiveTracingEnabled: Boolean = false,
    val onboardingCompleted: Boolean = true,
    val isWipingTerminal: Boolean = false,
    val wipeMessage: String? = null,
    val onboardingMessage: String? = null,
    val isLoading: Boolean = true
)

sealed interface DebugSettingsUiEvent {
    data class LoggingToggled(val isEnabled: Boolean) : DebugSettingsUiEvent
    data class LogLevelSelected(val level: LogLevelProto) : DebugSettingsUiEvent
    data class ExcessiveTracingToggled(val isEnabled: Boolean) : DebugSettingsUiEvent
    data class OnboardingCompletedToggled(val isCompleted: Boolean) : DebugSettingsUiEvent
    data object ResetOnboardingClicked : DebugSettingsUiEvent
    data object WipeTerminalClicked : DebugSettingsUiEvent
}
