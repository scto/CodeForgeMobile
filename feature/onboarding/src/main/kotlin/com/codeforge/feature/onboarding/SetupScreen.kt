// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.designsystem.CodeForgeTheme
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Bildschirm der Terminal-Einrichtung: feste Umgebung (SDK, JDK 21, NDK r29, CMake 4.1.2), nur „Einrichtung starten“. */
@Composable
fun SetupScreen(
    setupPhase: SetupPhase,
    setupErrorMessage: String?,
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

@Preview(name = "Setup - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SetupScreenLightPreview() {
    CodeForgeTheme(themeState = ThemeConfig.newBuilder().setMode(ThemeMode.LIGHT).build()) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            SetupScreen(
                setupPhase = SetupPhase.IDLE,
                setupErrorMessage = null,
                onStartSetup = {},
                onRetry = {}
            )
        }
    }
}

@Preview(name = "Setup - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SetupScreenDarkPreview() {
    CodeForgeTheme(themeState = ThemeConfig.newBuilder().setMode(ThemeMode.DARK).build()) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            SetupScreen(
                setupPhase = SetupPhase.IDLE,
                setupErrorMessage = null,
                onStartSetup = {},
                onRetry = {}
            )
        }
    }
}
