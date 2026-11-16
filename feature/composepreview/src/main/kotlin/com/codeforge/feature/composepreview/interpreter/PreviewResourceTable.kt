/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import androidx.compose.runtime.Composer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import com.codeforge.feature.composepreview.PreviewResourceStubs
import java.util.Locale

/**
 * `R` der Zieldatei ohne Gradle-Build: IDs werden beim ersten Zugriff vergeben, Werte kommen aus den
 * `res/values*`-Ordnern des Projekts. IDs aus vorkompilierten Nachbarn (Ersatz-`R` der kompilierten Vorschau)
 * gehen an deren Hilfsfunktionen.
 */
internal class PreviewResourceTable(
    private val values: Map<String, PreviewResourceStubs.Values>,
    private val loader: ClassLoader,
) {
    private val ids = HashMap<String, Int>()
    private val names = HashMap<Int, Pair<String, String>>()

    fun idFor(type: String, name: String): Int = ids.getOrPut("$type/$name") {
        (NATIVE_BASE + ids.size).also { names[it] = type to name }
    }

    fun isNative(id: Int) = id in NATIVE_BASE until NATIVE_BASE + 0x100000
    fun isCompiledStub(id: Int) = id in COMPILED_STUB_BASE until COMPILED_STUB_BASE + 0x100000

    private fun keys(locale: Locale) = listOf(locale.language + "-" + locale.country, locale.language, "").distinct()

    private fun <V> lookup(locale: Locale, pick: (PreviewResourceStubs.Values) -> V?): V? =
        keys(locale).firstNotNullOfOrNull { k -> values[k]?.let(pick) } ?: values.values.firstNotNullOfOrNull(pick)

    fun resolve(function: String, id: Int, args: List<Any?>, locale: Locale): Any? {
        val (type, name) = names[id] ?: throw UnsupportedKotlinException("Unbekannte Ressourcen-ID $id")
        return when (function) {
            "stringResource" -> {
                val text = lookup(locale) { it.strings[name] } ?: throw UnsupportedKotlinException("Text R.$type.$name nicht gefunden")
                if (args.isEmpty()) text else text.format(*args.toTypedArray())
            }
            "pluralStringResource" -> {
                val forms = lookup(locale) { it.plurals[name] } ?: throw UnsupportedKotlinException("Plural R.$type.$name nicht gefunden")
                val count = (args.firstOrNull() as? Number)?.toInt() ?: 0
                val quantity = runCatching { android.icu.text.PluralRules.forLocale(locale).select(count.toDouble()) }
                    .getOrDefault(if (count == 1) "one" else "other")
                val text = forms[quantity] ?: forms["other"] ?: forms.values.first()
                val fmt = args.drop(1)
                if (fmt.isEmpty()) text else text.format(*fmt.toTypedArray())
            }
            "stringArrayResource" -> (lookup(locale) { it.arrays[name] } ?: emptyList()).toTypedArray()
            "colorResource" -> {
                val raw = (lookup(locale) { it.colors[name] } ?: return Color.Magenta).removePrefix("#")
                val argb = when (raw.length) {
                    3 -> "FF" + raw.map { "$it$it" }.joinToString("")
                    4 -> raw.map { "$it$it" }.joinToString("")
                    6 -> "FF$raw"
                    else -> raw
                }
                argb.toLongOrNull(16)?.let { Color(it.toInt()) } ?: Color.Magenta
            }
            "dimensionResource" -> {
                val raw = lookup(locale) { it.dimens[name] } ?: return 0.dp
                (raw.removeSuffix("dip").removeSuffix("dp").removeSuffix("sp").removeSuffix("px").toFloatOrNull() ?: 0f).dp
            }
            "painterResource" -> ColorPainter(Color(0x33888888))
            else -> throw UnsupportedKotlinException("Ressourcenfunktion $function")
        }
    }

    /** Ruft die Hilfsfunktion des Ersatz-`R` aus vorkompilierten Nachbarn auf. */
    fun callCompiledStub(function: String, args: List<Any?>, composer: Composer): Any? {
        val helper = runCatching { Class.forName("$STUB_PACKAGE.PreviewResourcesKt", false, loader) }.getOrNull()
            ?: throw UnsupportedKotlinException("Ressource aus Nachbardatei ohne Ersatz-R")
        val m = helper.methods.firstOrNull { it.name == function && it.parameterCount == args.size + 2 && !it.isVarArgs }
            ?: helper.methods.firstOrNull { it.name == function }
            ?: throw UnsupportedKotlinException("$function im Ersatz-R fehlt")
        val jvmArgs = ArrayList<Any?>()
        val types = m.parameterTypes
        var i = 0
        jvmArgs += args.firstOrNull(); i++
        if (types.size > 3 && types[1].isArray) { jvmArgs.add(args.drop(1).toTypedArray()); i++ }
        else if (types.size > 3 && types[1] == Int::class.javaPrimitiveType) { jvmArgs.add(args.getOrNull(1)); i++; if (types[2].isArray) { jvmArgs.add(args.drop(2).toTypedArray()); i++ } }
        jvmArgs += composer
        while (jvmArgs.size < types.size) jvmArgs += 0
        return m.invoke(null, *jvmArgs.toTypedArray())
    }

    companion object {
        const val NATIVE_BASE = 0x7d000000
        const val COMPILED_STUB_BASE = 0x7e000000
        const val STUB_PACKAGE = PreviewResourceStubs.STUB_PACKAGE
    }
}
