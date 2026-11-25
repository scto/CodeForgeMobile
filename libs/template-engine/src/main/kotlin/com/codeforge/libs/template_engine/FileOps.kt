// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import java.io.File

/** Kopiert das Asset [assetPath] nach [dst] und legt fehlende Elternordner an. */
internal fun copyAsset(assets: AssetSource, assetPath: String, dst: File) {
    dst.parentFile?.mkdirs()
    assets.open(assetPath).use { input -> dst.outputStream().use { input.copyTo(it) } }
}

/** Kopiert den Asset-Ordner [assetRoot] rekursiv nach [outRoot] (relative Struktur ab [assetRoot]). */
internal fun copyAssetsDir(assets: AssetSource, assetRoot: String, outRoot: File) {
    fun copyDir(path: String) {
        for (name in assets.list(path)) {
            val child = "$path/$name"
            if (assets.list(child).isNotEmpty()) {
                copyDir(child)
            } else {
                copyAsset(assets, child, File(outRoot, child.removePrefix("$assetRoot/")))
            }
        }
    }
    copyDir(assetRoot)
}
