package com.codeforge.feature.assetstudio.logic

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.ConcurrentHashMap

/**
 * Katalog der Material-Icons (Filled). Die Namensliste liegt als Asset (`m3_icons_filled.txt`, eine
 * Kotlin-Klasse pro Zeile, z. B. `AccessAlarm`, `_10mp`); die Vektoren werden per Reflection aus
 * `androidx.compose.material.icons.filled.<Name>Kt` geladen (R8-Regel in consumer-rules.pro).
 */
object M3IconCatalog {
    private const val ASSET = "m3_icons_filled.txt"
    private val cache = ConcurrentHashMap<String, ImageVector>()

    @Volatile private var names: List<String>? = null

    /** Namen der Material-Symbole (zwischengespeichert). */
    fun names(context: Context): List<String> = names ?: synchronized(this) {
        names ?: IconNames.parseCatalog(
            context.assets.open(ASSET).bufferedReader().use { it.readLines() }
        ).also { names = it }
    }

    /** Lädt das Icon; `null`, wenn es nicht (mehr) existiert. */
    fun load(name: String): ImageVector? = cache[name] ?: runCatching {
        val cls = Class.forName("androidx.compose.material.icons.filled.${name}Kt")
        val getter = cls.getMethod("get$name", Icons.Filled::class.java)
        getter.invoke(null, Icons.Filled) as ImageVector
    }.getOrNull()?.also { cache[name] = it }
}
