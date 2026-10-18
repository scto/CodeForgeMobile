/**
 * Modul: :feature:composepreview
 * Instrumentierter Test für Task 27b: Offscreen-Rendering von Compose-Previews.
 *
 * Beinhaltet:
 *   - 27b.0: Reproduktion des alten Fehlers (leeres Bitmap mit LegacyComposeViewBitmapRenderer)
 *   - 27b.2: Verifikation des neuen Renderers (> 90 % rote Pixel bei RedSquareSample)
 *   - 27b.2: Fehlerisolation bei werfenden Composables (Failed statt App-Absturz)
 *   - 27b.2: Ablehnung von Composables mit Parametern mit klarer Fehlermeldung
 */
package com.codeforge.feature.composepreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Method
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Altes Verfahren vor Task 27b (Reproduktion für 27b.0):
 * ComposeView ohne Fenster, synchrones measure/layout/draw direkt nach setContent.
 */
private class LegacyComposeViewBitmapRenderer(private val context: Context) {

    private class LegacyHostOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {
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

    fun renderToBitmap(widthPx: Int, heightPx: Int, content: @Composable () -> Unit): Bitmap {
        val owner = LegacyHostOwner().apply { start() }

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

@RunWith(AndroidJUnit4::class)
class ComposePreviewRenderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * 27b.0: Reproduzieren – Altes Rendering liefert ein leeres (100 % transparentes) Bitmap,
     * da die Komposition über den Recomposer noch nicht stattgefunden hat.
     */
    @Test
    fun testLegacyRendererProducesEmptyBitmap() {
        val targetMethod = findSampleMethod("RedSquareSample")

        val legacyRenderer = LegacyComposeViewBitmapRenderer(context)
        val bitmap = legacyRenderer.renderToBitmap(widthPx = 200, heightPx = 200) {
            ReflectiveComposableHost(targetMethod)
        }

        val nonTransparentPixels = countNonTransparentPixels(bitmap)
        val nonTransparentRatio = nonTransparentPixels.toDouble() / (bitmap.width * bitmap.height)

        // Erwartung: 0 % nicht-transparente Pixel, da der Recomposer nicht gelaufen ist
        assertEquals(
            "Legacy-Renderer sollte ein leeres Bitmap liefern (0 % Pixel)",
            0.0,
            nonTransparentRatio,
            0.001
        )
    }

    /**
     * 27b.2: Neuer Renderer mit GraphicsLayer liefert das erwartete Bild (> 90 % rote Pixel).
     */
    @Test
    fun testRedSquareRendersWithExpectedRedArea() = runBlocking {
        val targetMethod = findSampleMethod("RedSquareSample")

        val renderer = ComposeViewBitmapRenderer(context)
        val bitmap = renderer.renderToBitmap(widthPx = 200, heightPx = 200) { onError ->
            ReflectiveComposableHost(targetMethod, onError = onError)
        }

        val redPixels = countRedPixels(bitmap)
        val totalPixels = bitmap.width * bitmap.height
        val redRatio = redPixels.toDouble() / totalPixels

        assertTrue(
            "Anteil roter Pixel sollte > 90 % sein, war aber ${(redRatio * 100).toInt()}% ($redPixels / $totalPixels)",
            redRatio > 0.90
        )
    }

    /**
     * 27b.2: Composable wirft Exception -> Fehler wird abgefangen, kein Absturz der App.
     */
    @Test
    fun testThrowingComposableFailsGracefullyWithoutCrashing() = runBlocking {
        val targetMethod = findSampleMethod("ThrowingSample")

        val renderer = ComposeViewBitmapRenderer(context)
        var caughtException: Throwable? = null

        try {
            renderer.renderToBitmap(widthPx = 200, heightPx = 200) { onError ->
                ReflectiveComposableHost(targetMethod, onError = onError)
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        assertTrue("Eine Exception sollte gefangen worden sein", caughtException != null)
        val rootCause = caughtException?.cause ?: caughtException
        assertTrue(
            "Fehlermeldung sollte 'Intentional preview test exception' enthalten, war aber: ${rootCause?.message}",
            rootCause?.message?.contains("Intentional preview test exception") == true
        )
    }

    /**
     * 27b.2: Composable mit Parametern wird explizit mit klarer Fehlermeldung abgelehnt.
     */
    @Test
    fun testParameterizedComposableRejectedWithClearErrorMessage() {
        val targetMethod = findSampleMethod("ParameterizedSample")

        try {
            ReflectiveComposableValidator.validateParameterless(targetMethod)
            fail("Sollte ParameterizedComposableException werfen")
        } catch (e: ParameterizedComposableException) {
            assertEquals("ParameterizedSample", e.functionName)
            assertTrue(
                "Fehlermeldung sollte Funktionsnamen enthalten",
                e.message.contains("ParameterizedSample")
            )
        }
    }

    private fun findSampleMethod(name: String): Method {
        val sampleClass = Class.forName("com.codeforge.feature.composepreview.SampleKt")
        return sampleClass.methods.firstOrNull { it.name == name }
            ?: throw NoSuchMethodException("Methode $name in SampleKt nicht gefunden")
    }

    private fun countNonTransparentPixels(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    count++
                }
            }
        }
        return count
    }

    private fun countRedPixels(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                if (alpha > 200 && red > 200 && green < 50 && blue < 50) {
                    count++
                }
            }
        }
        return count
    }
}
