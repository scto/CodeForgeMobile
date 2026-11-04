/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Bestimmt die Größe der Vorschau. */
internal object ComposePreviewSizeResolver {

    /**
     * Ermittelt die Zielgröße für die Vorschau.
     * Verwendet das reale Seitenverhältnis des Geräts statt fixer 1080x1920.
     */
    fun resolve(context: Context, widthPx: Int?, heightPx: Int?): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.coerceAtLeast(360)
        val screenHeight = displayMetrics.heightPixels.coerceAtLeast(640)

        val targetWidth = if (widthPx != null && widthPx > 0) widthPx else screenWidth
        val targetHeight = if (heightPx != null && heightPx > 0) {
            heightPx
        } else {
            val ratio = screenHeight.toFloat() / screenWidth.toFloat()
            (targetWidth * ratio).toInt()
        }
        return Pair(targetWidth, targetHeight)
    }
}

/** Darstellung der Vorschau: Hell/Dunkel, deckender Hintergrund und optional feste Größe (dp). */
internal data class PreviewStyle(
    val dark: Boolean,
    val backgroundArgb: Int,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val fontScale: Float? = null,
    val locale: String? = null
)

@Composable
private fun PreviewStyledContent(style: PreviewStyle, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val config = remember(base, style) {
        Configuration(base).apply {
            style.locale?.let { setLocale(java.util.Locale.forLanguageTag(it)) }
            val night = if (style.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
    }
    // isSystemInDarkTheme() liest LocalConfiguration; LocalContentColor deckt Texte ohne eigenes Theme ab.
    val density = LocalDensity.current
    val rtl = style.locale?.let { android.text.TextUtils.getLayoutDirectionFromLocale(java.util.Locale.forLanguageTag(it)) == View.LAYOUT_DIRECTION_RTL }
    CompositionLocalProvider(
        LocalConfiguration provides config,
        LocalContentColor provides if (style.dark) Color(0xFFE6E1E5) else Color.Black,
        LocalDensity provides (style.fontScale?.let { Density(density.density, it) } ?: density),
        LocalLayoutDirection provides (when (rtl) { true -> LayoutDirection.Rtl; false -> LayoutDirection.Ltr; null -> LocalLayoutDirection.current })
    ) {
        var size: Modifier = Modifier
        style.widthDp?.let { size = size.width(it.dp) }
        style.heightDp?.let { size = size.height(it.dp) }
        Box(modifier = size.background(Color(style.backgroundArgb))) { content() }
    }
}

private class PreviewFallbackHostOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /** Startet den Renderer. */
    fun start() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Gibt den Renderer frei. */
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Rendert eine @Composable-Funktion in ein Bitmap.
 *
 * Nutzt `rememberGraphicsLayer()` und `layer.toImageBitmap()`.
 * Wird an das aktive Fenster angehängt (oder mit Recomposer-Infrastruktur gemessen/gezeichnet),
 * wartet auf den Frame und hält ein Zeitlimit von 5 Sekunden ein.
 */
internal class ComposeViewBitmapRenderer(private val context: Context) {

    private fun findActivity(ctx: Context): Activity? {
        var current: Context? = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return PreviewActivityHolder.currentActivity
    }

    /** Rendert den Inhalt in eine Bitmap. */
    suspend fun renderToBitmap(widthPx: Int? = null, heightPx: Int? = null, content: @Composable () -> Unit): Bitmap =
        renderToBitmapInternal(widthPx, heightPx, null) { _ -> content() }

    /** Rendert den Inhalt in eine Bitmap (mit Größenvorgabe). */
    suspend fun renderToBitmap(
        widthPx: Int? = null,
        heightPx: Int? = null,
        style: PreviewStyle? = null,
        content: @Composable (onError: (Throwable) -> Unit) -> Unit
    ): Bitmap = renderToBitmapInternal(widthPx, heightPx, style, content)

    private suspend fun renderToBitmapInternal(
        widthPx: Int?,
        heightPx: Int?,
        style: PreviewStyle? = null,
        content: @Composable (onError: (Throwable) -> Unit) -> Unit
    ): Bitmap = withContext(Dispatchers.Main) {
        val (targetWidth, targetHeight) = ComposePreviewSizeResolver.resolve(context, widthPx, heightPx)

        val activity = findActivity(context)
        val decorView = activity?.window?.decorView as? ViewGroup

        val fallbackOwner = if (decorView == null) {
            PreviewFallbackHostOwner().apply { start() }
        } else {
            null
        }

        val composeView = ComposeView(context).apply {
            if (fallbackOwner != null) {
                setViewTreeLifecycleOwner(fallbackOwner)
                setViewTreeSavedStateRegistryOwner(fallbackOwner)
                setViewTreeViewModelStoreOwner(fallbackOwner)
            }
            // Unsichtbar positionieren, um Flackern der UI zu vermeiden
            translationX = -50_000f
            alpha = 0f
        }

        val resultDeferred = CompletableDeferred<Bitmap>()

        composeView.setContent {
            val graphicsLayer = rememberGraphicsLayer()
            var hasRecorded by remember { mutableStateOf(false) }

            val styled: @Composable () -> Unit = {
                content { throwable ->
                    resultDeferred.completeExceptionally(throwable)
                }
            }
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                        hasRecorded = true
                    }
            ) {
                if (style == null) styled() else PreviewStyledContent(style, styled)
            }

            LaunchedEffect(hasRecorded) {
                if (hasRecorded) {
                    try {
                        withFrameNanos { /* Auf Render-Frame warten */ }
                        val imageBitmap = graphicsLayer.toImageBitmap()
                        val androidBitmap = imageBitmap.asAndroidBitmap()
                        resultDeferred.complete(androidBitmap)
                    } catch (t: Throwable) {
                        resultDeferred.completeExceptionally(t)
                    }
                }
            }
        }

        if (decorView != null) {
            decorView.addView(
                composeView,
                ViewGroup.LayoutParams(targetWidth, targetHeight)
            )
        } else {
            // Fallback für Headless-Umgebungen: Manuelles Messen und Zeichnen triggert drawWithContent
            val widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.AT_MOST)
            composeView.measure(widthSpec, heightSpec)
            val measuredW = if (composeView.measuredWidth > 0) composeView.measuredWidth else targetWidth
            val measuredH = if (composeView.measuredHeight > 0) composeView.measuredHeight else targetHeight
            composeView.layout(0, 0, measuredW, measuredH)

            val dummyBitmap = Bitmap.createBitmap(measuredW, measuredH, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(dummyBitmap))
        }

        try {
            withTimeout(RENDER_TIMEOUT_MS) {
                resultDeferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                Res.string(R.string.composepreview_zeitlimit_ueberschritten),
                e
            )
        } finally {
            if (decorView != null) {
                decorView.removeView(composeView)
            }
            fallbackOwner?.destroy()
            composeView.disposeComposition()
        }
    }

    companion object {
        private const val RENDER_TIMEOUT_MS = 5_000L
    }
}
