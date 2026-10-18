/**
 * Modul: :feature:composepreview
 * Vorab kompilierte @Composable-Funktionen für instrumentierte Tests (Task 27b).
 */
package com.codeforge.feature.composepreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun RedSquareSample() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red)
    )
}

@Composable
fun ThrowingSample(): Unit = throw IllegalStateException("Intentional preview test exception")

@Composable
fun ParameterizedSample(label: String) {
    Text(text = label)
}
