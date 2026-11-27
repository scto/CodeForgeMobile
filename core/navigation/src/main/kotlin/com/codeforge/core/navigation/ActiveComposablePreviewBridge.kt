/**
 * Modul: :core:navigation
 * @author Thomas Schmid
 */
package com.codeforge.core.navigation

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Aktive Datei mit Composables (Pfad, Inhalt, Funktionsnamen). */
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
    /** Aktive Datei oder `null`. */
    val activeFile: StateFlow<ActiveComposableFile?>
    /** Vorschau einer Funktion angefordert (Randsymbol neben `@Preview` im Editor): Tab zeigen, Funktion wählen. */
    val showRequests: Flow<String> get() = emptyFlow()

    /** Meldet die aktive Datei. */
    fun publish(file: ActiveComposableFile?)

    /** Fordert die Vorschau von [functionName] an. */
    fun requestShow(functionName: String) = Unit
}

/** Standardimplementierung. */
@javax.inject.Singleton
class ActiveComposablePreviewBridgeImpl @Inject constructor() : ActiveComposablePreviewBridge {
    private val _activeFile = MutableStateFlow<ActiveComposableFile?>(null)
    override val activeFile: StateFlow<ActiveComposableFile?> = _activeFile.asStateFlow()

    private val _showRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val showRequests: SharedFlow<String> = _showRequests.asSharedFlow()

    override fun publish(file: ActiveComposableFile?) {
        _activeFile.value = file
    }

    override fun requestShow(functionName: String) {
        _showRequests.tryEmit(functionName)
    }
}
