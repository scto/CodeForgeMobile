// Modul: :core:ui
package com.codeforge.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.delay

/**
 * Info-Dialog für einen Language Server mit drei Tabs: Zustand (Pfad, Diagnose), Laufzeit (Speicher,
 * Version, Laufzeit, Start, letzte Nutzung) und Allgemein (Entwickler, Links, Lizenz).
 * [runtime] wird vom Aufrufer regelmäßig aktualisiert; Laufzeit und „vor …“-Angaben tickt der Dialog selbst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LspInfoDialog(
    model: LspInfoModel,
    runtime: LspRuntimeInfo?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringRes(R.string.lspinfo_button), fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.lspinfo_close))
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                HeroHeader(model, runtime)
                val tabs = listOf(
                    Icons.Filled.HealthAndSafety to R.string.lspinfo_tab_status,
                    Icons.Filled.Memory to R.string.lspinfo_tab_runtime,
                    Icons.Filled.Info to R.string.lspinfo_tab_general,
                )
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, (icon, label) ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            text = { Text(stringRes(label), maxLines = 1) }
                        )
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (tab) {
                        0 -> StatusTab(model, runtime)
                        1 -> RuntimeTab(model, runtime, now)
                        else -> GeneralTab(model)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(model: LspInfoModel, runtime: LspRuntimeInfo?) {
    val (label, color) = when {
        runtime?.running == true && runtime.starting -> stringRes(R.string.lspinfo_state_starting) to MaterialTheme.colorScheme.tertiary
        runtime?.running == true -> stringRes(R.string.lspinfo_state_running) to Color(0xFF2E9E5B)
        model.installed -> stringRes(R.string.lspinfo_state_ready) to MaterialTheme.colorScheme.primary
        else -> stringRes(R.string.lspinfo_state_missing) to MaterialTheme.colorScheme.error
    }
    Box(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), MaterialTheme.colorScheme.surface)
            )
        ).padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(model.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(model.languages, "v${model.version}").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Tab 1: Zustand

@Composable
private fun StatusTab(model: LspInfoModel, runtime: LspRuntimeInfo?) {
    SectionCard(Icons.Filled.Folder, stringRes(R.string.lspinfo_section_install)) {
        InfoRow(Icons.Filled.Folder, stringRes(R.string.lspinfo_install_path), model.installPath ?: stringRes(R.string.lspinfo_not_installed), mono = model.installPath != null)
        InfoRow(Icons.Filled.Terminal, stringRes(R.string.lspinfo_launch), model.launchCommand, mono = true)
    }
    SectionCard(Icons.Filled.HealthAndSafety, stringRes(R.string.lspinfo_section_diagnosis)) {
        model.checks.forEach { CheckRow(it) }
        CheckRow(runtimeCheck(runtime))
    }
}

@Composable
private fun runtimeCheck(runtime: LspRuntimeInfo?): LspCheck = when {
    runtime?.running == true -> LspCheck(
        stringRes(R.string.lspinfo_check_running),
        stringRes(R.string.lspinfo_check_running_ok, runtime.sessionCount),
        LspCheckLevel.OK
    )
    else -> LspCheck(stringRes(R.string.lspinfo_check_running), stringRes(R.string.lspinfo_check_running_idle), LspCheckLevel.WARN)
}

@Composable
private fun CheckRow(check: LspCheck) {
    val (icon, tint) = when (check.level) {
        LspCheckLevel.OK -> Icons.Filled.CheckCircle to Color(0xFF2E9E5B)
        LspCheckLevel.WARN -> Icons.Filled.Warning to Color(0xFFE09A1A)
        LspCheckLevel.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(check.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Tab 2: Laufzeit

@Composable
private fun RuntimeTab(model: LspInfoModel, runtime: LspRuntimeInfo?, now: Long) {
    SectionCard(Icons.Filled.Memory, stringRes(R.string.lspinfo_section_memory)) {
        val unknown = stringRes(R.string.lspinfo_memory_unknown)
        InfoRow(Icons.Filled.Memory, stringRes(R.string.lspinfo_memory),
            if (runtime?.running == true) runtime.memoryBytes?.let { LspInfoFormat.bytes(it) } ?: unknown else "–", emphasize = true)
        InfoRow(Icons.Filled.Tag, stringRes(R.string.lspinfo_version), model.version)
        InfoRow(Icons.Filled.Timer, stringRes(R.string.lspinfo_uptime),
            runtime?.startedAtMillis?.takeIf { runtime.running }?.let { LspInfoFormat.duration(now - it) } ?: "–", emphasize = true)
        InfoRow(Icons.Filled.PlayArrow, stringRes(R.string.lspinfo_started),
            runtime?.startedAtMillis?.takeIf { runtime.running }?.let { LspInfoFormat.dateTime(it) } ?: "–")
        InfoRow(Icons.Filled.History, stringRes(R.string.lspinfo_last_used),
            runtime?.lastUsedMillis?.takeIf { runtime.running }?.let { relative(now, it) } ?: "–")
        if (runtime?.running == true) {
            InfoRow(Icons.Filled.Layers, stringRes(R.string.lspinfo_sessions), runtime.sessionCount.toString())
            InfoRow(Icons.Filled.Description, stringRes(R.string.lspinfo_documents), runtime.openDocuments.toString())
            if (runtime.processIds.isNotEmpty()) {
                InfoRow(Icons.Filled.Schedule, stringRes(R.string.lspinfo_pids), runtime.processIds.joinToString(", "), mono = true)
            }
        }
    }
    if (runtime?.running != true) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringRes(R.string.lspinfo_idle_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringRes(R.string.lspinfo_idle_text), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun relative(now: Long, then: Long): String {
    val diff = now - then
    return if (diff < 5_000) stringRes(R.string.lspinfo_just_now) else stringRes(R.string.lspinfo_ago, LspInfoFormat.duration(diff))
}

// ---------------------------------------------------------------- Tab 3: Allgemein

@Composable
private fun GeneralTab(model: LspInfoModel) {
    val about = model.about
    val uriHandler = LocalUriHandler.current
    if (about.description.isNotBlank()) {
        Text(about.description, style = MaterialTheme.typography.bodyLarge)
    }
    SectionCard(Icons.Filled.Info, stringRes(R.string.lspinfo_section_about)) {
        if (about.author.isNotBlank()) InfoRow(Icons.Filled.Person, stringRes(R.string.lspinfo_author), about.author)
        if (about.languages.isNotBlank()) InfoRow(Icons.Filled.Language, stringRes(R.string.lspinfo_languages), about.languages)
        if (about.homepageUrl.isNotBlank()) InfoRow(Icons.Filled.Link, stringRes(R.string.lspinfo_homepage), about.homepageUrl.removePrefix("https://"),
            link = true) { runCatching { uriHandler.openUri(about.homepageUrl) } }
        if (about.repositoryUrl.isNotBlank()) InfoRow(Icons.Filled.Code, stringRes(R.string.lspinfo_repository), about.repositoryUrl.removePrefix("https://"),
            link = true) { runCatching { uriHandler.openUri(about.repositoryUrl) } }
        if (about.license.isNotBlank()) InfoRow(Icons.Filled.Gavel, stringRes(R.string.lspinfo_license), about.license)
        if (about.source.isNotBlank()) InfoRow(Icons.Filled.Download, stringRes(R.string.lspinfo_source), about.source, mono = true)
        InfoRow(Icons.Filled.Layers, stringRes(R.string.lspinfo_requires), about.requires.ifBlank { stringRes(R.string.lspinfo_none) })
    }
}

// ---------------------------------------------------------------- Bausteine

@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    mono: Boolean = false,
    emphasize: Boolean = false,
    link: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
