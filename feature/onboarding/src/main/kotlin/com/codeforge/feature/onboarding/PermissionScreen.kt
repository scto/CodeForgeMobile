@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(
    storageGranted: Boolean,
    notificationGranted: Boolean,
    batteryOptimizationGranted: Boolean,
    writeSecureSettingsGranted: Boolean,
    installPackagesGranted: Boolean,
    onRequestStorage: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestWriteSecureSettings: () -> Unit,
    onRequestInstallPackages: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_onboarding_perm_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            "CodeForge Mobile benötigt verschiedene Systemrechte für Terminals, Hintergrund-Builds und App-Installationen.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        PermissionRow(
            icon = Icons.Filled.Folder,
            title = "Speicherzugriff",
            description = "Zum Öffnen, Erstellen und Bearbeiten von Projekten (MANAGE_EXTERNAL_STORAGE).",
            granted = storageGranted,
            onRequest = onRequestStorage
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.Notifications,
            title = "Benachrichtigungen",
            description = "Für Build-Status und lang laufende Gradle-Tasks im Hintergrund.",
            granted = notificationGranted,
            onRequest = onRequestNotification
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.BatterySaver,
            title = "Akku-Optimierung ausnehmen",
            description = "Verhindert das Beenden von Hintergrund-Builds und PRoot-Terminals durch das System.",
            granted = batteryOptimizationGranted,
            onRequest = onRequestBatteryOptimization
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.AdminPanelSettings,
            title = "Systemeinstellungen schreiben",
            description = "Erlaubt erweiterte Systemeinstellungen (WRITE_SECURE_SETTINGS, z. B. per ADB).",
            granted = writeSecureSettingsGranted,
            onRequest = onRequestWriteSecureSettings
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.GetApp,
            title = "APKs & Pakete installieren",
            description = "Ermöglicht die direkte Installation selbst erstellter APKs (REQUEST_INSTALL_PACKAGES).",
            granted = installPackagesGranted,
            onRequest = onRequestInstallPackages
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = storageGranted
        ) {
            Text("Weiter")
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Erteilt",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.RadioButtonUnchecked,
                    contentDescription = "Ausstehend",
                    modifier = Modifier.clickable(onClick = onRequest)
                )
            }
        }
    }
}
