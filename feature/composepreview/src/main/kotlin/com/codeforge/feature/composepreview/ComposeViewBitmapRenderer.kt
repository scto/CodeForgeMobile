/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * UNVERIFIZIERT: ComposeView benötigt außerhalb einer Activity/eines Fragments einen
 * manuell bereitgestellten LifecycleOwner/SavedStateRegistryOwner/ViewModelStoreOwner,
 * sonst findet Compose keine aktive Composition-Wurzel und setContent bleibt wirkungslos.
 * Die hier verwendeten Extension-Funktionen (setViewTreeLifecycleOwner etc.) sind reale
 * androidx-APIs, ihr exaktes Zusammenspiel für Offscreen-Rendering außerhalb einer
 * Activity konnte in dieser Umgebung aber nicht getestet werden.
 */
package com.codeforge.feature.composepreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

private class PreviewHostOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun start() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
}

internal class ComposeViewBitmapRenderer(private val context: Context) {

    fun renderToBitmap(widthPx: Int, heightPx: Int, content: @Composable () -> Unit): Bitmap {
        val owner = PreviewHostOwner().apply { start() }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent(content)
        }

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        return bitmap
    }
}
