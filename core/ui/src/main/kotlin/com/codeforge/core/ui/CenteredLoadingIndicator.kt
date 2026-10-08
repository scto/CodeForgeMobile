/**
 * Modul: :core:ui
 * @author Thomas Schmid
 */
package com.codeforge.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Zentrierter Ladeindikator. Ersetzt das in mehreren Screens (FileTree, Git, SdkManager,
 * ProjectWizard) duplizierte "Box(fillMaxSize) { CircularProgressIndicator(Center) }"-Muster.
 */
@Composable
fun CenteredLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
