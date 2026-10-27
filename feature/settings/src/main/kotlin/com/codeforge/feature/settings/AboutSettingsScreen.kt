/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Über die App: Version, Autor, Quellcode-Link und verwendete Software.
 */
package com.codeforge.feature.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

@Immutable
data class AboutInfo(val versionName: String, val versionCode: Long)

/** Liest Version und Build-Nummer aus dem Paket; Fallback bei Fehlern. */
fun loadAboutInfo(context: Context): AboutInfo = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    AboutInfo(info.versionName ?: "?", code)
}.getOrDefault(AboutInfo("?", 0L))

const val ABOUT_SOURCE_URL = "https://github.com/scto/CodeForgeMobile"

@Composable
fun AboutSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val info = remember(context) { loadAboutInfo(context) }
    AboutSettingsScreen(modifier = modifier, info = info, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    modifier: Modifier = Modifier,
    info: AboutInfo,
    onBack: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.about_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringRes(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringRes(R.string.about_version, info.versionName, info.versionCode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringRes(R.string.about_author), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { runCatching { uriHandler.openUri(ABOUT_SOURCE_URL) } }) {
                        Text(stringRes(R.string.about_source))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringRes(R.string.about_credits), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        R.string.about_credit_sora,
                        R.string.about_credit_termux,
                        R.string.about_credit_jgit,
                        R.string.about_credit_jetbrains,
                    ).forEach { Text(stringRes(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringRes(R.string.about_credits_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
