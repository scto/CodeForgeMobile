// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.repository.SdkSetupOptions
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
fun SetupScreen(
    options: SdkSetupOptions,
    setupPhase: SetupPhase,
    setupErrorMessage: String?,
    onJdkSelected: (String) -> Unit,
    onNdkSelected: (String) -> Unit,
    onInstallNdkChanged: (Boolean) -> Unit,
    onInstallCmakeChanged: (Boolean) -> Unit,
    onStartSetup: () -> Unit,
    onRetry: () -> Unit
) {
    val inProgress = setupPhase == SetupPhase.INSTALLING_TERMUX || setupPhase == SetupPhase.PREPARING_SCRIPT
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringRes(R.string.onboarding_entwicklungsumgebung_einrichten), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringRes(R.string.onboarding_codeforge_nutzt_termux_als_umgebung),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        ChoiceRow("JDK", SdkSetupOptions.JDK_CHOICES, options.jdk, !inProgress, onJdkSelected)
        Spacer(Modifier.height(12.dp))
        ToggleRow(Res.string(R.string.onboarding_ndk_installieren), options.installNdk, !inProgress, onInstallNdkChanged)
        if (options.installNdk) ChoiceRow("NDK", SdkSetupOptions.NDK_CHOICES, options.ndk, !inProgress, onNdkSelected)
        Spacer(Modifier.height(12.dp))
        ToggleRow(Res.string(R.string.onboarding_cmake_installieren, SdkSetupOptions.DEFAULT_CMAKE), options.installCmake, !inProgress, onInstallCmakeChanged)

        Spacer(modifier = Modifier.height(24.dp))

        when (setupPhase) {
            SetupPhase.IDLE -> Button(onClick = onStartSetup, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(R.string.onboarding_einrichtung_starten))
            }

            SetupPhase.INSTALLING_TERMUX, SetupPhase.PREPARING_SCRIPT -> {
                Text(
                    if (setupPhase == SetupPhase.INSTALLING_TERMUX) stringRes(R.string.onboarding_entpacke_termux_umgebung) else stringRes(R.string.onboarding_bereite_setup_skript_vor),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SetupPhase.DONE -> Text(stringRes(R.string.onboarding_umgebung_bereit_das_terminal_oeffnet), style = MaterialTheme.typography.bodyMedium)

            SetupPhase.FAILED -> {
                Text(setupErrorMessage ?: stringRes(R.string.onboarding_einrichtung_fehlgeschlagen), color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringRes(R.string.common_erneut_versuchen)) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, choices: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                enabled = enabled,
                onClick = { onSelect(choice) },
                label = { Text(choice) }
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
