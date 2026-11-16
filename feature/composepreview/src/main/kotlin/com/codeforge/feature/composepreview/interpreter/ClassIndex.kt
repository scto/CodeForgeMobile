/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import java.io.File
import java.util.zip.ZipFile

/**
 * Welche Klassen liegen in einem Paket? Nötig, um Top-Level-Funktionen zu finden (`padding` steckt z. B. in
 * `androidx.compose.foundation.layout.PaddingKt`, der Name der Datei-Klasse steht nirgends im Quelltext).
 */
internal interface ClassIndex {
    fun classesIn(packageName: String): List<String>
}

/** Index aus fertigen Klassennamen-Listen (mehrere Quellen werden zusammengeführt). */
internal class MapClassIndex(classNames: Sequence<String>) : ClassIndex {
    private val byPackage: Map<String, List<String>> = classNames
        .filter { !it.contains('$') || it.endsWith("\$DefaultImpls") }
        .groupBy { it.substringBeforeLast('.', "") }

    override fun classesIn(packageName: String): List<String> = byPackage[packageName].orEmpty()
}

internal object ClassIndexes {

    private val isAndroid: Boolean = System.getProperty("java.vm.vendor").orEmpty().contains("Android", ignoreCase = true) ||
        System.getProperty("java.vm.name").orEmpty().contains("Dalvik", ignoreCase = true)

    @Volatile private var hostIndex: List<String>? = null

    /** Klassen der laufenden App (bzw. des JVM-Klassenpfads in Tests); einmal ermittelt, dann zwischengespeichert. */
    fun hostClasses(apkPaths: List<String>): List<String> {
        hostIndex?.let { return it }
        synchronized(this) {
            hostIndex?.let { return it }
            val list = if (isAndroid) apkPaths.flatMap { dexClassNames(it) } else jvmClassPathNames()
            hostIndex = list
            return list
        }
    }

    /** Klassennamen einer DEX-/APK-Datei (Android). */
    @Suppress("DEPRECATION")
    fun dexClassNames(path: String): List<String> = runCatching {
        val dex = dalvik.system.DexFile(path)
        try { dex.entries().toList() } finally { runCatching { dex.close() } }
    }.getOrDefault(emptyList())

    /** Klassennamen aus dem JVM-Klassenpfad (für Unit-Tests). */
    fun jvmClassPathNames(): List<String> {
        val result = ArrayList<String>()
        System.getProperty("java.class.path").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.forEach { entry ->
            val f = File(entry)
            when {
                f.isDirectory -> f.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach {
                    result += it.relativeTo(f).path.removeSuffix(".class").replace(File.separatorChar, '.')
                }
                f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".zip")) -> runCatching {
                    ZipFile(f).use { z -> z.entries().asSequence().filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF") }.forEach { result += it.name.removeSuffix(".class").replace('/', '.') } }
                }
            }
        }
        return result
    }
}
