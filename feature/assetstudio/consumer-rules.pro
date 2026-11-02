# feature:assetstudio — der M3-Icon-Browser lädt Material-Icons per Reflection über ihren Namen
# (androidx.compose.material.icons.filled.<Name>Kt). Ohne diese Regel entfernt R8 die Klassen im Release.
-keep class androidx.compose.material.icons.filled.** { *; }
