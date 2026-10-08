/**
 * Modul: :core:navigation
 * @author Thomas Schmid
 */
package com.codeforge.core.navigation

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ActiveComposableFile(
    val path: String,
    val content: String,
    val composableFunctionNames: List<String>
)

/**
 * Kommunikationskanal zwischen :feature:editor (Publisher: aktuell offene Datei) und
 * :feature:composepreview (Consumer) sowie dem :app-NavHost (entscheidet, ob ein
 * "Preview"-Tab neben dem Editor angezeigt wird). Liegt in :core:navigation, da die
 * Dependency-Regel direkte :feature:*→:feature:*-Abhängigkeiten verbietet — beide
 * Features und :app dürfen :core:navigation referenzieren.
 */
interface ActiveComposablePreviewBridge {
    val activeFile: StateFlow<ActiveComposableFile?>
    fun publish(file: ActiveComposableFile?)
}

@javax.inject.Singleton
class ActiveComposablePreviewBridgeImpl @Inject constructor() : ActiveComposablePreviewBridge {
    private val _activeFile = MutableStateFlow<ActiveComposableFile?>(null)
    override val activeFile: StateFlow<ActiveComposableFile?> = _activeFile.asStateFlow()

    override fun publish(file: ActiveComposableFile?) {
        _activeFile.value = file
    }
}
