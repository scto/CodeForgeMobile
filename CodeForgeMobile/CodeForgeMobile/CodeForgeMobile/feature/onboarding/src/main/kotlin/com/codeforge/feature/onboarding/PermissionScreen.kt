// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
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
    onRequestStorage: () -> Unit,
    onRequestNotification: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Berechtigungen", style = MaterialTheme.typography.headlineSmall)
        Text(
            "CodeForge Mobile benötigt Zugriff auf deinen Projektspeicher und darf dich über den Build-Status informieren.",
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
