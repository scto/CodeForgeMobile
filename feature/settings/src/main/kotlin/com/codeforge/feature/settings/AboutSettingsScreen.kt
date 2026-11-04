/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Über die App: Kopfkarte (Icon, Name, Version), Abschnitt „Links“ und „Weiteres“ (Entwickler, verwendete Software).
 */
package com.codeforge.feature.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.codeforge.core.designsystem.BastiSprite
import com.codeforge.core.designsystem.R as DesignSystemR
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

/** Angaben zur App: Versionsname, Build-Nummer und ob es ein Debug-Build ist. */
@Immutable
data class AboutInfo(val versionName: String, val versionCode: Long, val isDebug: Boolean = false)

/** Liest Version, Build-Nummer und Build-Art aus dem Paket; Fallback bei Fehlern. */
fun loadAboutInfo(context: Context): AboutInfo = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    AboutInfo(info.versionName ?: "?", code, debug)
}.getOrDefault(AboutInfo("?", 0L))

/** Adresse des Quellcodes auf GitHub. */
const val ABOUT_SOURCE_URL = "https://github.com/scto/CodeForgeMobile"
/** Adresse der Fehlermeldungen (Issues). */
const val ABOUT_ISSUES_URL = "$ABOUT_SOURCE_URL/issues"
/** Adresse der Releases. */
const val ABOUT_RELEASES_URL = "$ABOUT_SOURCE_URL/releases"

/** Route „Über die App“: liest die Paketdaten und zeigt den Bildschirm. */
@Composable
fun AboutSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenBasti: () -> Unit = {},
) {
    val context = LocalContext.current
    val info = remember(context) { loadAboutInfo(context) }
    AboutSettingsScreen(modifier = modifier, info = info, onBack = onBack, onOpenBasti = onOpenBasti)
}

/**
 * Bildschirm „Über die App“ mit Kopfkarte, Links und weiteren Angaben.
 *
 * @param modifier Modifier.
 * @param info Anzuzeigende App-Daten.
 * @param onBack Zurück-Aktion.
 * @param onOpenBasti öffnet „Über Basti“ (Figur neben dem App-Icon und Eintrag in „Weiteres“).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    modifier: Modifier = Modifier,
    info: AboutInfo,
    onBack: () -> Unit = {},
    onOpenBasti: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    var creditsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.about_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutHeader(info = info, onOpenBasti = onOpenBasti)

            AboutSection(title = stringRes(R.string.about_section_links)) {
                AboutItem(Icons.Filled.Language, R.string.about_website, ABOUT_SOURCE_URL) { open(ABOUT_SOURCE_URL) }
                AboutItem(Icons.Filled.BugReport, R.string.about_issues, ABOUT_ISSUES_URL) { open(ABOUT_ISSUES_URL) }
                AboutItem(Icons.Filled.NewReleases, R.string.about_releases, ABOUT_RELEASES_URL) { open(ABOUT_RELEASES_URL) }
            }

            AboutSection(title = stringRes(R.string.about_section_misc)) {
                AboutItem(Icons.Filled.Person, R.string.about_developer, stringRes(R.string.about_author))
                AboutItem(Icons.Filled.Pets, R.string.about_basti_item, stringRes(R.string.about_basti_hint), onClick = onOpenBasti)
                AboutItem(Icons.Filled.Code, R.string.about_source, stringRes(R.string.about_source_desc)) { open(ABOUT_SOURCE_URL) }
                AboutItem(
                    icon = Icons.Filled.Favorite,
                    title = R.string.about_credits,
                    subtitle = stringRes(R.string.about_credits_hint),
                ) { creditsExpanded = !creditsExpanded }
                if (creditsExpanded) {
                    listOf(
                        R.string.about_credit_sora,
                        R.string.about_credit_termux,
                        R.string.about_credit_jgit,
                        R.string.about_credit_jetbrains,
                    ).forEach { AboutItem(Icons.Filled.Widgets, it, null) }
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(info: AboutInfo, modifier: Modifier = Modifier, onOpenBasti: () -> Unit = {}) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(id = DesignSystemR.drawable.icon),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                // Basti sitzt neben dem Logo (Widmung); Antippen öffnet „Über Basti“.
                BastiSprite(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenBasti),
                    size = 72.dp,
                    contentDescription = stringRes(R.string.basti_sprite_cd),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringRes(R.string.app_name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringRes(R.string.welcome_subtitle),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val accent = if (info.isDebug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val buildType = stringRes(if (info.isDebug) R.string.about_build_debug else R.string.about_build_release)
            Text(
                text = buildAnnotatedString {
                    append(stringRes(R.string.about_version_short, info.versionName, info.versionCode))
                    append(" (")
                    withStyle(SpanStyle(color = accent)) { append(buildType) }
                    append(")")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun AboutItem(
    icon: ImageVector,
    @StringRes title: Int,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringRes(title)) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.tertiary) } },
    )
}
