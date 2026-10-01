package com.codeforge.feature.themebuilder

import android.os.Build
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode

data class ThemeBuilderUiState(
    val isLoading: Boolean = false,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val useAmoled: Boolean = false
) {
    val themeMode: ThemeMode get() = mode
    val isDynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

    val previewTheme: ThemeConfig
        get() = ThemeConfig.newBuilder()
            .setMode(mode)
            .setUseDynamicColor(useDynamicColor)
            .setUseAmoled(useAmoled)
            .build()
}

sealed interface ThemeBuilderUiEvent {
    data class ModeSelected(val mode: ThemeMode) : ThemeBuilderUiEvent
    data object DynamicColorToggled : ThemeBuilderUiEvent
    data object AmoledToggled : ThemeBuilderUiEvent
}
