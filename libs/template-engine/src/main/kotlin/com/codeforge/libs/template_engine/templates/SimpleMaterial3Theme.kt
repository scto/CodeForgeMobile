package com.codeforge.libs.template_engine.templates

/**
 * Liefert den Text der Vorlagendatei `SimpleMaterial3Theme`.
 *
 * @param themeName Name des Themes.
 * @param actionBar Ob das Theme eine ActionBar hat.
 */
fun SimpleMaterial3Theme(themeName: String, actionBar: Boolean): String = """
    <resources xmlns:tools="http://schemas.android.com/tools">
        <style name="Base.$themeName" parent="Theme.Material3.DayNight${if (!actionBar) ".NoActionBar" else ""}">
        </style>
        <style name="$themeName" parent="Base.$themeName" />
    </resources>
""".trimIndent()
