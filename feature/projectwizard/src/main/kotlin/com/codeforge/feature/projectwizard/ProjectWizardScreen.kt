// Modul: :feature:projectwizard
@file:OptIn(ExperimentalMaterial3Api::class)

package com.codeforge.feature.projectwizard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.ProjectInputValidator.Error
import com.codeforge.core.domain.model.ProjectInputValidator.Field
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.resources.R as CoreR
import com.codeforge.core.resources.Res

@Composable
fun ProjectWizardScreen(
    state: ProjectWizardUiState,
    onEvent: (ProjectWizardUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.pw_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ProjectWizardUiEvent.BackClicked) }, enabled = !state.isGenerating) {
                        Icon(painterResource(R.drawable.pw_ic_back), contentDescription = stringResource(CoreR.string.pw_back))
                    }
                },
            )
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when (state.step) {
            WizardStep.SELECT_TEMPLATE -> TemplateGrid(
                templates = state.templates,
                onSelect = { onEvent(ProjectWizardUiEvent.TemplateSelected(it)) },
                modifier = content,
            )
            WizardStep.CONFIGURE -> ConfigForm(state = state, onEvent = onEvent, modifier = content)
        }
    }

    state.generationError?.let { message ->
        AlertDialog(
            onDismissRequest = { onEvent(ProjectWizardUiEvent.ErrorDismissed) },
            title = { Text(stringResource(CoreR.string.pw_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { onEvent(ProjectWizardUiEvent.ErrorDismissed) }) {
                    Text(stringResource(CoreR.string.pw_ok))
                }
            },
        )
    }
}

@Composable
private fun TemplateGrid(
    templates: List<ProjectTemplateDescriptor>,
    onSelect: (ProjectTemplateDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(templates, key = { it.id }) { template ->
            val ui = template.kind.ui()
            Card(onClick = { onSelect(template) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(ui.image),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Text(stringResource(ui.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(ui.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigForm(
    state: ProjectWizardUiState,
    onEvent: (ProjectWizardUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = state.selected ?: return
    val enabled = !state.isGenerating
    fun error(field: Field): Error? = if (state.showErrors || field != Field.TARGET_DIR) state.errors[field] else null

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(template.kind.ui().title), style = MaterialTheme.typography.titleLarge)

        FormField(
            value = state.projectName,
            onChange = { onEvent(ProjectWizardUiEvent.ProjectNameChanged(it)) },
            label = CoreR.string.pw_field_name,
            error = if (state.showErrors) error(Field.PROJECT_NAME) else null,
            enabled = enabled,
        )
        FormField(
            value = state.packageName,
            onChange = { onEvent(ProjectWizardUiEvent.PackageNameChanged(it)) },
            label = CoreR.string.pw_field_package,
            error = if (state.showErrors) error(Field.PACKAGE_NAME) else null,
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
        )
        FormField(
            value = state.targetDir,
            onChange = { onEvent(ProjectWizardUiEvent.TargetDirChanged(it)) },
            label = CoreR.string.pw_field_location,
            error = if (state.showErrors) error(Field.TARGET_DIR) else null,
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
        )

        Dropdown(
            label = CoreR.string.pw_field_language,
            selectedText = if (state.language == ProjectLanguage.KOTLIN) Res.string(CoreR.string.projectwizard_kotlin) else Res.string(CoreR.string.projectwizard_java),
            options = ProjectLanguage.values().filter { it == ProjectLanguage.KOTLIN || template.supportsJava },
            optionText = { if (it == ProjectLanguage.KOTLIN) Res.string(CoreR.string.projectwizard_kotlin) else Res.string(CoreR.string.projectwizard_java) },
            onSelect = { onEvent(ProjectWizardUiEvent.LanguageChanged(it)) },
            enabled = enabled,
        )
        Dropdown(
            label = CoreR.string.pw_field_min_sdk,
            selectedText = "API ${state.minSdk}",
            options = ProjectRequest.MIN_SDK_OPTIONS,
            optionText = { "API $it" },
            onSelect = { onEvent(ProjectWizardUiEvent.MinSdkChanged(it)) },
            enabled = enabled,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CoreR.string.pw_field_kts), modifier = Modifier.weight(1f))
            Switch(
                checked = state.useKotlinDsl,
                onCheckedChange = { onEvent(ProjectWizardUiEvent.KotlinDslChanged(it)) },
                enabled = enabled,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CoreR.string.pw_field_git), modifier = Modifier.weight(1f))
            Switch(
                checked = state.initGit,
                onCheckedChange = { onEvent(ProjectWizardUiEvent.InitGitChanged(it)) },
                enabled = enabled,
            )
        }

        if (state.initGit && state.gitIdentityMissing) {
            Text(
                stringResource(CoreR.string.pw_git_identity_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { onEvent(ProjectWizardUiEvent.CreateClicked) },
            enabled = state.canCreate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                Text(stringResource(CoreR.string.pw_creating), modifier = Modifier.padding(start = 8.dp))
            } else {
                Text(stringResource(CoreR.string.pw_create))
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    error: Error?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.message())) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> Dropdown(
    label: Int,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

private fun Error.message(): Int = when (this) {
    Error.INVALID_NAME -> CoreR.string.pw_err_name
    Error.INVALID_PACKAGE -> CoreR.string.pw_err_package
    Error.RESERVED_PACKAGE_SEGMENT -> CoreR.string.pw_err_package_reserved
    Error.DIRECTORY_EXISTS -> CoreR.string.pw_err_dir_exists
    Error.TARGET_BLANK -> CoreR.string.pw_err_target_blank
    Error.COMPOSE_REQUIRES_KOTLIN -> CoreR.string.pw_err_compose_kotlin
}
