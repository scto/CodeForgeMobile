package com.codeforge.feature.j2k.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codeforge.core.resources.stringRes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.resources.R
import com.codeforge.feature.j2k.J2kIntent
import com.codeforge.feature.j2k.J2kUiState

@Composable
fun J2kProgressScreen(
    state: J2kUiState,
    onIntent: (J2kIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isConverting) return

    val progress = state.progress
    val phaseName = when (progress) {
        is J2kProgress.Scanning -> "Analysiere Projekt: ${progress.message}"
        is J2kProgress.CreatingBackup -> "Erstelle Backup…"
        is J2kProgress.PreparingBuild -> "Bereite Build vor: ${progress.message}"
        is J2kProgress.ConvertingJava -> "Konvertiere Java (${progress.current}/${progress.total})…"
        is J2kProgress.ConvertingGradle -> "Konvertiere Gradle (${progress.current}/${progress.total})…"
        is J2kProgress.ApplyingChanges -> "Wende Änderungen an: ${progress.message}"
        is J2kProgress.VerifyingBuild -> "Prüfe Build: ${progress.task}"
        is J2kProgress.Completed -> "Abgeschlossen"
        is J2kProgress.Failed -> "Fehlgeschlagen: ${progress.error}"
        J2kProgress.Idle, null -> "Starte Migration…"
    }

    val progressFraction: Float? = when (progress) {
        is J2kProgress.ConvertingJava -> if (progress.total > 0) progress.current.toFloat() / progress.total.toFloat() else null
        is J2kProgress.ConvertingGradle -> if (progress.total > 0) progress.current.toFloat() / progress.total.toFloat() else null
        else -> null
    }

    val detailFile: String? = when (progress) {
        is J2kProgress.ConvertingJava -> progress.file
        is J2kProgress.ConvertingGradle -> progress.file
        else -> null
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringRes(R.string.j2k_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = phaseName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            detailFile?.let { filePath ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(onClick = { onIntent(J2kIntent.CancelMigration) }) {
                Text(text = stringRes(R.string.common_abbrechen))
            }
        }
    }
}

@Preview(name = "Progress Screen Preview")
@Composable
private fun J2kProgressScreenPreview() {
    MaterialTheme {
        J2kProgressScreen(
            state = J2kUiState(
                isConverting = true,
                progress = J2kProgress.ConvertingJava(
                    current = 4,
                    total = 10,
                    file = "app/src/main/java/com/example/User.java"
                )
            ),
            onIntent = {}
        )
    }
}
