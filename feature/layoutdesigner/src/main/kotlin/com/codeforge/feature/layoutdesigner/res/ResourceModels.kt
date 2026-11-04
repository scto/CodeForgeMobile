/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Reine Kotlin-Modelle für Android-Ressourcen und Qualifizierer-Konfigurationen.
 */
package com.codeforge.feature.layoutdesigner.res

/** Ressourcen-Konfiguration (Ordner und Qualifizierer). */
data class ResourceConfig(
    val isNight: Boolean = false,
    val language: String = "en",
    val isLandscape: Boolean = false,
    val swDp: Int = 360,
    val wDp: Int = 360,
    val apiLevel: Int = 34,
)

/** Ressourcentyp. */
enum class ResType {
    STRING, COLOR, DIMEN, INTEGER, BOOL, DRAWABLE, STYLE, ARRAY
}

/** Eintrag einer Ressource. */
data class ResourceEntry(
    val type: ResType,
    val name: String,
    val value: String,
    val qualifier: String = "",
    val sourceFile: String = "",
)

/** Eintrag eines Styles. */
data class StyleItem(
    val name: String,
    val value: String,
)

/** Style mit Eltern und Einträgen. */
data class StyleDef(
    val name: String,
    val parent: String?,
    val items: Map<String, String>,
)
