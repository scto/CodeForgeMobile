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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GeneratingScreen(
    phase: GenerationPhase,
    progress: Float = 0f,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenProject: (() -> Unit)? = null
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
                if (progress > 0f) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            GenerationPhase.DONE -> {
                Text("Projekt erstellt ✓", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                if (onOpenProject != null) {
                    Button(
                        onClick = onOpenProject,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Projekt öffnen")
                    }
                }
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Parameter bearbeiten")
                }
            }

            GenerationPhase.IDLE -> Unit
        }
    }
}
