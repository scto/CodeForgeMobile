package com.codeforge.feature.assetstudio.logic

import java.io.File

/** Eine zu schreibende Datei, relativ zum `res`-Ordner. */
class PlannedFile(val relativePath: String, val bytes: ByteArray) {
    constructor(relativePath: String, text: String) : this(relativePath, text.toByteArray(Charsets.UTF_8))
}

enum class IconShape { CIRCLE, SQUARE, ROUNDED }

/** Quelle des Foreground-Motivs: Vektor-XML oder fertig kodiertes PNG. */
sealed interface ForegroundSource {
    data class VectorXml(val xml: String) : ForegroundSource
    class Png(val bytes: ByteArray) : ForegroundSource
}

/** Erzeugt die Dateien für Drawables und Adaptive Icons – reine Logik, ohne Android-/IO-Abhängigkeit. */
object AssetFilePlanner {

    /** Einfaches Drawable: Vektor → `drawable/<name>.xml`, Bild → `drawable-nodpi/<name>.png`. */
    fun planDrawable(name: String, source: ForegroundSource): List<PlannedFile> = when (source) {
        is ForegroundSource.VectorXml -> listOf(PlannedFile("drawable/$name.xml", source.xml))
        is ForegroundSource.Png -> listOf(PlannedFile("drawable-nodpi/$name.png", source.bytes))
    }

    /**
     * Adaptive Launcher-Icon-Ressourcen:
     * `mipmap-anydpi-v26/<name>.xml` + `_round.xml`, `values/<name>_background.xml` (Farbe),
     * `drawable/<name>_fg` (Motiv) und `drawable/<name>_foreground.xml` (Inset-Wrapper für die Sicherheitszone).
     * @param scale Anteil der 108-dp-Fläche, den das Motiv einnimmt (0.3–1.0). Adaptive Icons maskieren
     * ihre Form selbst; die Form [IconShape] wirkt nur auf Vorschau und Legacy-PNGs.
     */
    fun planAdaptiveIcon(name: String, source: ForegroundSource, backgroundArgb: Int, scale: Float): List<PlannedFile> {
        val inset = insetPercent(scale)
        val files = ArrayList<PlannedFile>()
        val fgName = "${name}_fg"
        when (source) {
            is ForegroundSource.VectorXml -> files += PlannedFile("drawable/$fgName.xml", source.xml)
            is ForegroundSource.Png -> files += PlannedFile("drawable-nodpi/$fgName.png", source.bytes)
        }
        files += PlannedFile(
            "drawable/${name}_foreground.xml",
            """<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/$fgName"
    android:insetLeft="$inset%"
    android:insetTop="$inset%"
    android:insetRight="$inset%"
    android:insetBottom="$inset%" />
"""
        )
        files += PlannedFile(
            "values/${name}_background.xml",
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="${name}_background">${colorHex(backgroundArgb)}</color>
</resources>
"""
        )
        val adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/${name}_background" />
    <foreground android:drawable="@drawable/${name}_foreground" />
    <monochrome android:drawable="@drawable/${name}_foreground" />
</adaptive-icon>
"""
        files += PlannedFile("mipmap-anydpi-v26/$name.xml", adaptive)
        files += PlannedFile("mipmap-anydpi-v26/${name}_round.xml", adaptive)
        return files
    }

    /** Randabstand in Prozent so, dass das Motiv [scale] der Fläche belegt (0.6 → 20 %). */
    fun insetPercent(scale: Float): Int = kotlin.math.round(((1f - scale.coerceIn(0.3f, 1f)) / 2f) * 100f).toInt().coerceIn(0, 35)

    fun colorHex(argb: Int): String =
        if (argb ushr 24 == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)

    /** Parst `#RGB`, `#RRGGBB` oder `#AARRGGBB` (mit/ohne `#`); sonst `null`. Ergebnis immer ARGB. */
    fun parseColor(text: String): Int? {
        val s = text.trim().removePrefix("#")
        if (s.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
        return when (s.length) {
            3 -> s.map { "$it$it" }.joinToString("").toLong(16).toInt() or (0xFF shl 24)
            6 -> s.toLong(16).toInt() or (0xFF shl 24)
            8 -> s.toLong(16).toInt()
            else -> null
        }
    }

    /** Schreibt die geplanten Dateien nach [resDir]; gibt die relativen Pfade zurück. */
    fun write(resDir: File, files: List<PlannedFile>): List<String> = files.map { f ->
        val target = File(resDir, f.relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(f.bytes)
        f.relativePath
    }

    /** `res`-Ordner des Projekts: bevorzugt `app/src/main/res`, sonst das erste Modul mit `src/main/res` (Tiefe ≤ 3). */
    fun findResDir(root: File): File? {
        File(root, "app/src/main/res").takeIf { it.isDirectory }?.let { return it }
        if (File(root, "src/main/res").isDirectory) return File(root, "src/main/res")
        return root.walkTopDown().maxDepth(5)
            .onEnter { it.name !in setOf("build", ".git", ".gradle", "node_modules") }
            .firstOrNull { it.isDirectory && it.name == "res" && it.parentFile?.name == "main" && it.parentFile?.parentFile?.name == "src" }
    }
}
