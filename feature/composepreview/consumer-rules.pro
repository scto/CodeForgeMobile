# feature:composepreview — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Die Vorschau lädt Klassen des kompilierten Nutzerprojekts per ClassLoader.loadClass (nicht aus der App).
-keep class com.codeforge.feature.composepreview.** { *; }

# Der Vorschau-Code des Nutzers wird gegen die Compose-/Kotlin-Bibliotheken (Jars der Vorschau-Laufzeit) kompiliert,
# zur Laufzeit aber über den ClassLoader der App gelinkt: Er muss dieselben Klassen sehen, sonst passen Composer,
# Modifier usw. nicht zusammen. R8 darf diese Bibliotheken deshalb weder umbenennen noch kürzen – sonst
# „Failed resolution of: Landroidx/compose/runtime/Composer;“ bzw. NoSuchMethodError in Release-Builds.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.ripple.** { *; }
-keep class androidx.compose.material.icons.Icons { *; }
-keep class androidx.compose.material.icons.Icons$* { *; }
-keep class androidx.compose.material.icons.filled.** { *; }
# Erweiterte Symbole (mehrere tausend Klassen): nur Namen erhalten – genutzte bleiben auffindbar, ungenutzte entfernt R8.
-keepnames class androidx.compose.material.icons.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.compose.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
