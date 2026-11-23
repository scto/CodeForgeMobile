// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import com.codeforge.libs.color_utilities.dynamiccolor.DynamicScheme

/**
 * Die 36 Farbrollen eines Compose-`ColorScheme` (ab material3 1.2).
 *
 * @property key Name in Compose und im Plugin-Farbschemaformat (`primaryContainer`).
 * @property xmlAttr Theme-Attribut in Material-Components-XML (`colorPrimaryContainer`), `null` = keins.
 * @param read Liest die Rolle aus einem Schema von material-color-utilities.
 */
enum class ColorRole(val key: String, val xmlAttr: String?, internal val read: (DynamicScheme) -> Int) {
    PRIMARY("primary", "colorPrimary", { it.primary }),
    ON_PRIMARY("onPrimary", "colorOnPrimary", { it.onPrimary }),
    PRIMARY_CONTAINER("primaryContainer", "colorPrimaryContainer", { it.primaryContainer }),
    ON_PRIMARY_CONTAINER("onPrimaryContainer", "colorOnPrimaryContainer", { it.onPrimaryContainer }),
    INVERSE_PRIMARY("inversePrimary", "colorPrimaryInverse", { it.inversePrimary }),
    SECONDARY("secondary", "colorSecondary", { it.secondary }),
    ON_SECONDARY("onSecondary", "colorOnSecondary", { it.onSecondary }),
    SECONDARY_CONTAINER("secondaryContainer", "colorSecondaryContainer", { it.secondaryContainer }),
    ON_SECONDARY_CONTAINER("onSecondaryContainer", "colorOnSecondaryContainer", { it.onSecondaryContainer }),
    TERTIARY("tertiary", "colorTertiary", { it.tertiary }),
    ON_TERTIARY("onTertiary", "colorOnTertiary", { it.onTertiary }),
    TERTIARY_CONTAINER("tertiaryContainer", "colorTertiaryContainer", { it.tertiaryContainer }),
    ON_TERTIARY_CONTAINER("onTertiaryContainer", "colorOnTertiaryContainer", { it.onTertiaryContainer }),
    ERROR("error", "colorError", { it.error }),
    ON_ERROR("onError", "colorOnError", { it.onError }),
    ERROR_CONTAINER("errorContainer", "colorErrorContainer", { it.errorContainer }),
    ON_ERROR_CONTAINER("onErrorContainer", "colorOnErrorContainer", { it.onErrorContainer }),
    BACKGROUND("background", "android:colorBackground", { it.background }),
    ON_BACKGROUND("onBackground", "colorOnBackground", { it.onBackground }),
    SURFACE("surface", "colorSurface", { it.surface }),
    ON_SURFACE("onSurface", "colorOnSurface", { it.onSurface }),
    SURFACE_VARIANT("surfaceVariant", "colorSurfaceVariant", { it.surfaceVariant }),
    ON_SURFACE_VARIANT("onSurfaceVariant", "colorOnSurfaceVariant", { it.onSurfaceVariant }),
    SURFACE_TINT("surfaceTint", null, { it.surfaceTint }),
    INVERSE_SURFACE("inverseSurface", "colorSurfaceInverse", { it.inverseSurface }),
    INVERSE_ON_SURFACE("inverseOnSurface", "colorOnSurfaceInverse", { it.inverseOnSurface }),
    OUTLINE("outline", "colorOutline", { it.outline }),
    OUTLINE_VARIANT("outlineVariant", "colorOutlineVariant", { it.outlineVariant }),
    SCRIM("scrim", null, { it.scrim }),
    SURFACE_BRIGHT("surfaceBright", "colorSurfaceBright", { it.surfaceBright }),
    SURFACE_DIM("surfaceDim", "colorSurfaceDim", { it.surfaceDim }),
    SURFACE_CONTAINER_LOWEST("surfaceContainerLowest", "colorSurfaceContainerLowest", { it.surfaceContainerLowest }),
    SURFACE_CONTAINER_LOW("surfaceContainerLow", "colorSurfaceContainerLow", { it.surfaceContainerLow }),
    SURFACE_CONTAINER("surfaceContainer", "colorSurfaceContainer", { it.surfaceContainer }),
    SURFACE_CONTAINER_HIGH("surfaceContainerHigh", "colorSurfaceContainerHigh", { it.surfaceContainerHigh }),
    SURFACE_CONTAINER_HIGHEST("surfaceContainerHighest", "colorSurfaceContainerHighest", { it.surfaceContainerHighest }),
    ;

    /** Ressourcenname `md_theme_<snake_case>` für `colors.xml`. */
    val resourceName: String get() = "md_theme_" + key.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }

    companion object {
        /** Paare (Vordergrund, Hintergrund), deren Kontrast der Builder anzeigt. */
        val CONTRAST_PAIRS: List<Pair<ColorRole, ColorRole>> = listOf(
            ON_PRIMARY to PRIMARY,
            ON_PRIMARY_CONTAINER to PRIMARY_CONTAINER,
            ON_SECONDARY to SECONDARY,
            ON_SECONDARY_CONTAINER to SECONDARY_CONTAINER,
            ON_TERTIARY to TERTIARY,
            ON_TERTIARY_CONTAINER to TERTIARY_CONTAINER,
            ON_ERROR to ERROR,
            ON_ERROR_CONTAINER to ERROR_CONTAINER,
            ON_SURFACE to SURFACE,
            ON_SURFACE_VARIANT to SURFACE_VARIANT,
            INVERSE_ON_SURFACE to INVERSE_SURFACE,
        )
    }
}
