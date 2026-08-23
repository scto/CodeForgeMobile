/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * UNVERIFIZIERT: Die Compose-Compiler-Umschreibung fügt jeder @Composable-Funktion einen
 * (Composer, Int)-Parameter hinzu. Diese Klasse ruft eine reflektiv geladene Top-Level-
 * Composable-Funktion auf, indem sie ihr den aktuell aktiven Composer der umgebenden
 * Komposition übergibt — eine Technik, die in Community-Prototypen für Hot-Reload
 * dokumentiert ist, hier aber nicht real getestet werden konnte (siehe README-Hinweis).
 * Der $changed-Bitmask-Wert wird konservativ auf "immer neu komponieren" gesetzt
 * (korrekt, aber nicht performance-optimal — für einen einmaligen Preview-Render ohne
 * Bedeutung).
 */
package com.codeforge.feature.composepreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import java.lang.reflect.Method

@Composable
@OptIn(InternalComposeApi::class)
internal fun ReflectiveComposableHost(targetMethod: Method) {
    val composer = currentComposer
    // Konservativer $changed-Wert: erzwingt Rekomposition, vermeidet Skip-Optimierungen,
    // die ohne korrekt propagierte Slot-Table-Historie zu falschem Verhalten führen könnten.
    targetMethod.invoke(null, composer, 1)
}
