// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import java.io.File
import java.io.InputStream

/** Lesezugriff auf die mitgelieferten Vorlagen-Dateien (App-Assets; in Tests ein Ordner). */
interface AssetSource {
    /** Namen der direkten Kinder von [path]; leer bei Dateien und unbekannten Pfaden. */
    fun list(path: String): List<String>
    fun open(path: String): InputStream
}

/** Dateisystem-Variante — für JVM-Tests und Werkzeuge ohne Android. */
class DirectoryAssetSource(private val root: File) : AssetSource {
    override fun list(path: String): List<String> = File(root, path).list()?.toList().orEmpty()
    override fun open(path: String): InputStream = File(root, path).inputStream()
}
