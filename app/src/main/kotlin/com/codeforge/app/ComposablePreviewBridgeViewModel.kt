/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import androidx.lifecycle.ViewModel
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Reiner Adapter auf die :core:navigation-Bridge, damit CodeForgeNavHost entscheiden
 * kann, ob neben dem Editor ein "Preview"-Tab erscheint — ohne dass :feature:editor
 * und :feature:composepreview sich direkt kennen (Dependency-Regel).
 */
@HiltViewModel
class ComposablePreviewBridgeViewModel @Inject constructor(
    bridge: ActiveComposablePreviewBridge
) : ViewModel() {
    val activeFile: StateFlow<ActiveComposableFile?> = bridge.activeFile
}
