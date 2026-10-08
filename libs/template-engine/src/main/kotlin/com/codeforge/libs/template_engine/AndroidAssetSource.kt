// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import android.content.res.AssetManager
import java.io.InputStream

class AndroidAssetSource(private val assets: AssetManager) : AssetSource {
    override fun list(path: String): List<String> = assets.list(path)?.toList().orEmpty()
    override fun open(path: String): InputStream = assets.open(path)
}
