// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import android.content.res.AssetManager
import freemarker.cache.TemplateLoader
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader

/**
 * Liest .ftl-Templates aus assets/<basePath>/<name> statt vom Dateisystem, da
 * Templates als App-Assets ausgeliefert werden (kein Netzwerk-/Extraktionsschritt nötig).
 */
class AssetTemplateLoader(
    private val assetManager: AssetManager,
    private val basePath: String
) : TemplateLoader {

    override fun findTemplateSource(name: String): Any? {
        val path = "$basePath/$name"
        return try {
            assetManager.open(path).close()
            path
        } catch (e: IOException) {
            null
        }
    }

    override fun getLastModified(templateSource: Any): Long = 0L

    override fun getReader(templateSource: Any, encoding: String): Reader {
        val path = templateSource as String
        return InputStreamReader(assetManager.open(path), encoding)
    }

    override fun closeTemplateSource(templateSource: Any) {
        // AssetManager-Streams werden bereits in getReader/findTemplateSource geschlossen bzw.
        // vom Reader verwaltet; hier ist nichts zu tun.
    }
}
