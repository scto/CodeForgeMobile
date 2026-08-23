// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GeneratingScreen(
    phase: GenerationPhase,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (phase) {
            GenerationPhase.RUNNING -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Projekt wird erstellt …", style = MaterialTheme.typography.bodyMedium)
            }

            GenerationPhase.DONE -> {
                Text("Projekt erstellt ✓", style = MaterialTheme.typography.titleMedium)
            }

            GenerationPhase.FAILED -> {
                Text(
                    errorMessage ?: "Erstellung fehlgeschlagen.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Erneut versuchen")
                }
            }

            GenerationPhase.IDLE -> Unit
        }
    }
}
