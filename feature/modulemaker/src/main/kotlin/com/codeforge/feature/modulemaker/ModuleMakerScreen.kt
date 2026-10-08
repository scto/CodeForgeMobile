/**
 * Modul: :feature:modulemaker
 * @author Thomas Schmid
 *
 * „Submodule Maker“: Gradle-Notation (`:lib:core:libcoredu`) eingeben → Ordnerstruktur,
 * build.gradle(.kts), Dummy-Kotlin-Datei und `include(...)` in settings.gradle(.kts).
 */
package com.codeforge.feature.modulemaker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.code_tools.module.ModuleType

@Composable
fun ModuleMakerRoute(
    rootPath: String,
    modifier: Modifier = Modifier,
    onFileOpened: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    viewModel: ModuleMakerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(rootPath) { viewModel.onEvent(ModuleMakerUiEvent.Initialize(rootPath)) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect {
            when (it) {
                ModuleMakerUiEffect.FileOpened -> onFileOpened()
                is ModuleMakerUiEffect.ShowSnackbar -> onMessage(it.message)
            }
        }
    }
    ModuleMakerScreen(modifier = modifier, state = state, onEvent = viewModel::onEvent)
}

@Composable
fun ModuleMakerScreen(
    modifier: Modifier = Modifier,
    state: ModuleMakerUiState,
    onEvent: (ModuleMakerUiEvent) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringRes(R.string.modulemaker_submodul_erstellen), style = MaterialTheme.typography.titleMedium)

        if (!state.hasSettingsFile) {
            Banner(Res.string(R.string.modulemaker_keine_settings_gradle_kts_im), isError = true)
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = { onEvent(ModuleMakerUiEvent.InputChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Gradle-Pfad") },
            placeholder = { Text(":lib:core:libcoredu") },
            isError = state.inputError != null,
            supportingText = { Text(state.inputError ?: stringRes(R.string.modulemaker_notation_wie_in_settings_gradle)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        )

        Text(stringRes(R.string.modulemaker_typ), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.type == ModuleType.ANDROID_LIBRARY,
                onClick = { onEvent(ModuleMakerUiEvent.TypeChanged(ModuleType.ANDROID_LIBRARY)) },
                label = { Text(stringRes(R.string.modulemaker_android_library)) },
            )
            FilterChip(
                selected = state.type == ModuleType.KOTLIN_JVM_LIBRARY,
                onClick = { onEvent(ModuleMakerUiEvent.TypeChanged(ModuleType.KOTLIN_JVM_LIBRARY)) },
                label = { Text(stringRes(R.string.modulemaker_kotlin_jvm)) },
            )
        }

        OutlinedTextField(
            value = state.basePackageOverride,
            onValueChange = { onEvent(ModuleMakerUiEvent.BasePackageChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringRes(R.string.modulemaker_basis_package_optional)) },
            supportingText = { Text(stringRes(R.string.common_erkannt, state.detectedBasePackage ?: "–")) },
        )

        state.preview?.let { p ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringRes(R.string.common_vorschau), style = MaterialTheme.typography.labelLarge)
                    PreviewLine(Res.string(R.string.common_ordner), p.relativeDir + "/")
                    PreviewLine(Res.string(R.string.modulemaker_build), "${p.relativeDir}/${p.buildFileName}")
                    PreviewLine(Res.string(R.string.modulemaker_package), p.packageName)
                    PreviewLine(Res.string(R.string.common_datei), "${p.className}.kt")
                    PreviewLine("settings", "include(\"${p.gradlePath}\")")
                }
            }
        }

        Button(onClick = { onEvent(ModuleMakerUiEvent.Create) }, enabled = state.canCreate, modifier = Modifier.fillMaxWidth()) {
            if (state.isCreating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
            Text(stringRes(R.string.modulemaker_modul_erstellen), modifier = Modifier.padding(start = 8.dp))
        }

        state.error?.let { Banner(it, isError = true) }

        state.result?.let { r ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringRes(R.string.modulemaker_angelegt, r.gradlePath), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        buildString {
                            append(if (r.settingsChanged) Res.string(R.string.modulemaker_in_settings_eingetragen) else Res.string(R.string.modulemaker_bereits_in_settings_vorhanden))
                            if (r.rootBuildChanged) append(Res.string(R.string.modulemaker_root_build_ergaenzt))
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    r.createdFiles.forEach { rel ->
                        val abs = state.rootPath.trimEnd('/') + "/" + rel
                        Text(
                            rel,
                            modifier = Modifier.fillMaxWidth().clickable { onEvent(ModuleMakerUiEvent.OpenFile(abs)) }.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    r.warnings.forEach { Banner(it, isError = false) }
                    Text(
                        stringRes(R.string.modulemaker_hinweis_gradle_sync_ist_noetig),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onEvent(ModuleMakerUiEvent.DismissResult) }) { Text(stringRes(R.string.common_schliessen)) }
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).weight(0.28f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.72f))
    }
}

@Composable
private fun Banner(text: String, isError: Boolean) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
