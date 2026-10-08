# Task 14 – Adaptive/Edge-to-Edge/Expressive verifizieren

1. `./gradlew :app:assembleDebug` – Versionen in `gradle/libs.versions.toml` (Kotlin 2.1.21, KSP, Hilt 2.56.2, BOM 2025.09.00, adaptive 1.1.0) gegen Maven/Google prüfen und korrigieren; `composeCompiler="1.5.15"` ist vermutlich ungenutzt.
2. Falls `MaterialExpressiveTheme`/`MotionScheme.expressive()` unauflösbar: BOM/material3 anheben (ggf. compileSdk) oder Aufruf in `CodeForgeTheme.kt` auf `MaterialTheme` zurücksetzen.
3. `WelcomeScreen`: adaptive-1.1.0-Imports (`layout.AnimatedPane`, `layout.ListDetailPaneScaffoldRole`, `navigation.*`), `navigateTo` als suspend prüfen.
4. Gerätetests: Phone Hochformat/Quer, Tablet (≥840dp: Permanent-Drawer), Faltbar, Split-Screen, Gesten- und 3-Tasten-Navigation, Cutout, Tastatur im Editor/Terminal (kein doppeltes Padding, nichts unter Status-/Navigationsleiste).
5. Dark/Light-Wechsel in der App bei umgekehrtem Systemmodus: Statusleisten-Icons lesbar?
