// Modul: :core:designsystem
package com.codeforge.core.designsystem

data class ThemePreset(
    val id: String,
    val label: String,
    val primaryHex: String,
    val secondaryHex: String,
    val tertiaryHex: String,
    val isDark: Boolean = true
)

/**
 * Vordefinierte Farbschemata für CodeForge Mobile.
 * Passend zu den TextMate-, Monarch- und Editor-Farbschemen im Asset-Ordner.
 */
object ThemePresets {
    val codeforge = ThemePreset("codeforge", "CodeForge Dark", "#1E2330", "#6366F1", "#06B6D4", isDark = true)
    val darcula = ThemePreset("darcula", "Darcula", "#2B2B2B", "#BBB529", "#6897BB", isDark = true)
    val quietlight = ThemePreset("quietlight", "Quiet Light", "#F5F5F5", "#7A3E9D", "#005A9C", isDark = false)
    val ayuDark = ThemePreset("ayu-dark", "Ayu Dark", "#0F1419", "#FFB454", "#39BAE6", isDark = true)
    val ayuLight = ThemePreset("ayu_light", "Ayu Light", "#FAFAFA", "#FF9940", "#55B4D4", isDark = false)
    val ayuMirage = ThemePreset("ayu_mirage", "Ayu Mirage", "#1F2430", "#FFCC66", "#5CCFE6", isDark = true)
    val solarizedDark = ThemePreset("solarized_dark", "Solarized Dark", "#002B36", "#2AA198", "#268BD2", isDark = true)
    val eclipseDark = ThemePreset("eclipse_dark", "Eclipse Dark", "#1F1F1F", "#CC7832", "#6897BB", isDark = true)
    val eclipseLight = ThemePreset("eclipse_light", "Eclipse Light", "#FFFFFF", "#7F0055", "#0000C0", isDark = false)
    val onedark = ThemePreset("onedark", "One Dark", "#282C34", "#E06C75", "#61AFEF", isDark = true)
    val githubLight = ThemePreset("github_light", "GitHub Light", "#FFFFFF", "#0366D6", "#28A745", isDark = false)
    val vscodeDark = ThemePreset("vscode_dark", "VS Code Dark+", "#1E1E1E", "#569CD6", "#4EC9B0", isDark = true)
    val notepad = ThemePreset("notepad", "Notepad++", "#FFFFFF", "#0000FF", "#008000", isDark = false)
    val cyberpunk = ThemePreset("cyberpunk", "Cyberpunk Neon", "#0D0D15", "#FF0055", "#00F0FF", isDark = true)
    val forest = ThemePreset("forest", "Wald", "#2E7D32", "#558B2F", "#00695C", isDark = true)
    val ocean = ThemePreset("ocean", "Ozean", "#0277BD", "#00838F", "#1565C0", isDark = true)

    val all = listOf(
        codeforge, darcula, quietlight, ayuDark, ayuLight, ayuMirage,
        solarizedDark, eclipseDark, eclipseLight, onedark, githubLight,
        vscodeDark, notepad, cyberpunk, forest, ocean
    )

    val customPresets = listOf(
        ThemePreset("c_neon", "Cyberpunk Neon", "#FF0055", "#00F0FF", "#FFE600"),
        ThemePreset("c_matrix", "Matrix Green", "#00FF66", "#003311", "#00CC44"),
        ThemePreset("c_emerald", "Emerald Forest", "#10B981", "#047857", "#065F46"),
        ThemePreset("c_midnight", "Midnight Blue", "#3B82F6", "#1D4ED8", "#1E40AF"),
        ThemePreset("c_sunset", "Sunset Amber", "#F59E0B", "#D97706", "#B45309"),
        ThemePreset("c_rose", "Rose Pink", "#EC4899", "#BE185D", "#9D174D"),
        ThemePreset("c_purple", "Deep Purple", "#8B5CF6", "#6D28D9", "#5B21B6"),
        ThemePreset("c_crimson", "Crimson Red", "#EF4444", "#DC2626", "#991B1B"),
        ThemePreset("c_slate", "Slate Grey", "#64748B", "#475569", "#334155"),
        ThemePreset("c_gold", "Gold Rush", "#EAB308", "#CA8A04", "#854D0E")
    )

    fun findById(id: String): ThemePreset? = all.find { it.id == id }
}
