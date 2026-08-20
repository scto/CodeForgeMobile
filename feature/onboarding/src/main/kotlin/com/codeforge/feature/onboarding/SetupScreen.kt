// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val distros = listOf("alpine" to "Alpine (schlank, empfohlen)", "ubuntu" to "Ubuntu", "debian" to "Debian")

@Composable
fun SetupScreen(
    selectedDistro: String,
    setupPhase: SetupPhase,
    setupProgressPercent: Int,
    setupErrorMessage: String?,
    onDistroSelected: (String) -> Unit,
    onStartSetup: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Linux-Umgebung einrichten", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Wähle eine Distribution für Terminal, Gradle und Build-Tools. Der Download erfolgt einmalig.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        val setupInProgress = setupPhase != SetupPhase.IDLE && setupPhase != SetupPhase.FAILED && setupPhase != SetupPhase.DONE

        distros.forEach { (id, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = id == selectedDistro,
                        enabled = !setupInProgress,
                        onClick = { onDistroSelected(id) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = id == selectedDistro,
                    onClick = { onDistroSelected(id) },
                    enabled = !setupInProgress
                )
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (setupPhase) {
            SetupPhase.IDLE -> {
                Button(onClick = onStartSetup, modifier = Modifier.fillMaxWidth()) {
                    Text("Einrichtung starten")
                }
            }

            SetupPhase.DOWNLOADING, SetupPhase.EXTRACTING, SetupPhase.FINALIZING -> {
                Text(phaseLabel(setupPhase), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { setupProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$setupProgressPercent %", modifier = Modifier.padding(top = 4.dp))
            }

            SetupPhase.DONE -> {
                Text("Einrichtung abgeschlossen ✓", style = MaterialTheme.typography.bodyMedium)
            }

            SetupPhase.FAILED -> {
                Text(
                    setupErrorMessage ?: "Einrichtung fehlgeschlagen.",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Erneut versuchen")
                }
            }
        }
    }
}

private fun phaseLabel(phase: SetupPhase): String = when (phase) {
    SetupPhase.DOWNLOADING -> "Lade Rootfs herunter …"
    SetupPhase.EXTRACTING -> "Entpacke Distribution …"
    SetupPhase.FINALIZING -> "Schließe Einrichtung ab …"
    else -> ""
}
