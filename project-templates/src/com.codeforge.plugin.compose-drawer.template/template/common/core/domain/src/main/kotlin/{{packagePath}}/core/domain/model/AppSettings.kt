package {{packageName}}.core.domain.model

/** Helles, dunkles oder System-Design. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Einstellungen der App. */
data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)
