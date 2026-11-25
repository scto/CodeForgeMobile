// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

/**
 * Mitgelieferte Projektvorlagen: normale Vorlagen-Plugins (Quellen unter `project-templates/src/`), die die App als
 * ZIP in den Assets (`builtin-templates/<id>.zip`) mitbringt und beim Start vorinstalliert.
 */
object BuiltinTemplates {
    /** Asset-Ordner der Plugin-ZIPs. */
    const val ASSET_DIR = "builtin-templates"

    /** Plugin-IDs in Anzeigereihenfolge. */
    val IDS: List<String> = listOf(
        "android-no-activity",
        "android-empty-activity",
        "android-native-cpp",
        "android-basic-activity",
        "android-navigation-drawer",
        "android-bottom-navigation",
        "android-tabs",
        "android-no-androidx",
        "android-compose-empty",
    ).map { "com.codeforge.plugin.$it.template" }

    /** `true`, wenn [pluginId] eine mitgelieferte Vorlage ist. */
    fun isBuiltin(pluginId: String): Boolean = pluginId in IDS

    /** Sortierschlüssel: mitgelieferte in fester Reihenfolge vor allen anderen. */
    fun order(pluginId: String): Int = IDS.indexOf(pluginId).let { if (it < 0) IDS.size else it }
}
