# libs:plugin-ui-api — Consumer-R8-Regeln (automatisch in der App aktiv).
#
# Plugins mit Oberfläche linken zur Laufzeit gegen diese Schnittstellen und gegen Compose/Material 3 der App
# (siehe libs/plugin-api/consumer-rules.pro). Ohne diese Regeln laden sie im Release-Build nicht.
-keep class com.codeforge.libs.plugin_ui_api.** { *; }
-keep interface com.codeforge.libs.plugin_ui_api.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.ripple.** { *; }
# Symbole für Plugins: nur die gefüllten (die anderen Stile wären mehrere MB)
-keep class androidx.compose.material.icons.Icons** { *; }
-keep class androidx.compose.material.icons.filled.** { *; }
-keep class androidx.compose.material.icons.automirrored.filled.** { *; }
-dontwarn androidx.compose.**
