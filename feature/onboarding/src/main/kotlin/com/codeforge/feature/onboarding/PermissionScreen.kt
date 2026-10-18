// Modul: :feature:onboarding
package com.codeforge.feature.onboarding

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.designsystem.CodeForgeTheme
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun PermissionScreen(
    storageGranted: Boolean,
    installPackagesGranted: Boolean,
    deletePackagesInfo: Boolean,
    notificationGranted: Boolean,
    onRequestStorage: () -> Unit,
    onRequestInstallPackages: () -> Unit,
    onRequestNotification: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(stringRes(R.string.onboarding_berechtigungen), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringRes(R.string.onboarding_codeforge_mobile_benoetigt_zugriff_auf),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        PermissionRow(
            icon = Icons.Filled.Folder,
            title = stringRes(R.string.onboarding_speicherzugriff),
            description = stringRes(R.string.onboarding_zum_oeffnen_erstellen_und_bearbeiten),
            granted = storageGranted,
            onRequest = onRequestStorage
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.InstallMobile,
            title = stringRes(R.string.onboarding_apps_installieren),
            description = stringRes(R.string.onboarding_apps_installieren_beschreibung),
            granted = installPackagesGranted,
            onRequest = onRequestInstallPackages
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.Delete,
            title = stringRes(R.string.onboarding_apps_loeschen),
            description = stringRes(R.string.onboarding_apps_loeschen_beschreibung),
            granted = deletePackagesInfo,
            onRequest = null
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            icon = Icons.Filled.Notifications,
            title = stringRes(R.string.onboarding_benachrichtigungen),
            description = stringRes(R.string.onboarding_fuer_build_status_und_lang),
            granted = notificationGranted,
            onRequest = onRequestNotification
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = storageGranted
        ) {
            Text(stringRes(R.string.onboarding_weiter))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: (() -> Unit)?
) {
    val canRequest = !granted && onRequest != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canRequest) Modifier.clickable(onClick = { onRequest?.invoke() }) else Modifier)
    ) {
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
                    contentDescription = stringRes(R.string.onboarding_erteilt),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.RadioButtonUnchecked,
                    contentDescription = stringRes(R.string.onboarding_ausstehend)
                )
            }
        }
    }
}

@Preview(name = "Permissions - Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PermissionScreenLightPreview() {
    CodeForgeTheme(themeState = ThemeConfig.newBuilder().setMode(ThemeMode.LIGHT).build()) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            PermissionScreen(
                storageGranted = true,
                installPackagesGranted = false,
                deletePackagesInfo = true,
                notificationGranted = false,
                onRequestStorage = {},
                onRequestInstallPackages = {},
                onRequestNotification = {},
                onContinue = {}
            )
        }
    }
}

@Preview(name = "Permissions - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionScreenDarkPreview() {
    CodeForgeTheme(themeState = ThemeConfig.newBuilder().setMode(ThemeMode.DARK).build()) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            PermissionScreen(
                storageGranted = false,
                installPackagesGranted = false,
                deletePackagesInfo = true,
                notificationGranted = false,
                onRequestStorage = {},
                onRequestInstallPackages = {},
                onRequestNotification = {},
                onContinue = {}
            )
        }
    }
}
