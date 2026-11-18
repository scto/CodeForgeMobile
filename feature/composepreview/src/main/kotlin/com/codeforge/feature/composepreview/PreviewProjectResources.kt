/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * Echte Projekt-Ressourcen für die Vorschau: Die vom Gradle-Build gelinkten Ressourcen (`*.ap_`) werden in einen
 * eigenen [AssetManager] geladen. Bilder, Fonts, Texte und Farben stammen damit aus dem Projekt statt aus Platzhaltern.
 */
package com.codeforge.feature.composepreview

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.TypedValue
import java.io.File

internal object PreviewProjectResources {

    private var cacheKey: String? = null
    private var cachedAssets: AssetManager? = null

    private val addAssetPath by lazy {
        runCatching { AssetManager::class.java.getMethod("addAssetPath", String::class.java) }.getOrNull()
    }

    /** Kontext, dessen Ressourcen aus [apk] kommen; `null`, wenn sich die Datei nicht laden lässt. */
    @Synchronized
    fun contextFor(host: Context, apk: File, configuration: Configuration): Context? {
        val assets = assetsFor(apk) ?: return null
        @Suppress("DEPRECATION")
        val resources = FallbackResources(assets, host.resources.displayMetrics, configuration, host.resources)
        return object : ContextWrapper(host) {
            private val projectTheme by lazy { resources.newTheme() }
            override fun getAssets(): AssetManager = assets
            override fun getResources(): Resources = resources
            override fun getTheme(): Resources.Theme = projectTheme
        }
    }

    private fun assetsFor(apk: File): AssetManager? {
        if (!apk.isFile) return null
        val key = apk.absolutePath + ":" + apk.lastModified()
        if (key == cacheKey) cachedAssets?.let { return it }
        val method = addAssetPath ?: return null
        return runCatching {
            @Suppress("DEPRECATION")
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val cookie = method.invoke(assets, apk.absolutePath) as Int
            if (cookie == 0) {
                assets.close()
                null
            } else {
                // Das alte AssetManager-Objekt bleibt offen: eine noch laufende Komposition kann es benutzen.
                cachedAssets = assets
                cacheKey = key
                assets
            }
        }.getOrNull()
    }

    /**
     * Projekt-Ressourcen mit Rückfall auf die der IDE: Compose und Material fragen eigene Texte (z. B. für
     * Bedienungshilfen) mit den IDs der IDE ab. Fehlt eine solche ID im Projekt, kommt sie aus [host] statt zu werfen.
     */
    @Suppress("DEPRECATION")
    private class FallbackResources(
        assets: AssetManager,
        metrics: android.util.DisplayMetrics,
        config: Configuration,
        private val host: Resources,
    ) : Resources(assets, metrics, config) {

        private inline fun <T> orHost(project: () -> T, fallback: () -> T): T =
            try { project() } catch (_: NotFoundException) { fallback() }

        override fun getText(id: Int): CharSequence = orHost({ super.getText(id) }, { host.getText(id) })
        override fun getQuantityText(id: Int, quantity: Int): CharSequence =
            orHost({ super.getQuantityText(id, quantity) }, { host.getQuantityText(id, quantity) })
        override fun getTextArray(id: Int): Array<CharSequence> = orHost({ super.getTextArray(id) }, { host.getTextArray(id) })
        override fun getStringArray(id: Int): Array<String> = orHost({ super.getStringArray(id) }, { host.getStringArray(id) })
        override fun getIntArray(id: Int): IntArray = orHost({ super.getIntArray(id) }, { host.getIntArray(id) })
        override fun getDimension(id: Int): Float = orHost({ super.getDimension(id) }, { host.getDimension(id) })
        override fun getDimensionPixelSize(id: Int): Int = orHost({ super.getDimensionPixelSize(id) }, { host.getDimensionPixelSize(id) })
        override fun getDimensionPixelOffset(id: Int): Int = orHost({ super.getDimensionPixelOffset(id) }, { host.getDimensionPixelOffset(id) })
        override fun getBoolean(id: Int): Boolean = orHost({ super.getBoolean(id) }, { host.getBoolean(id) })
        override fun getInteger(id: Int): Int = orHost({ super.getInteger(id) }, { host.getInteger(id) })
        override fun getColor(id: Int, theme: Theme?): Int = orHost({ super.getColor(id, theme) }, { host.getColor(id, null) })
        override fun getDrawable(id: Int, theme: Theme?): Drawable = orHost({ super.getDrawable(id, theme) }, { host.getDrawable(id, null) })
        override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) =
            orHost({ super.getValue(id, outValue, resolveRefs) }, { host.getValue(id, outValue, resolveRefs) })
    }
}
