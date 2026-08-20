// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.domain.model.ParamType
import com.codeforge.core.domain.model.ProjectTemplate
import com.codeforge.core.domain.model.TemplateCategory

@Composable
fun ProjectWizardRoute(
    onNavigateToEditor: (projectPath: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProjectWizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProjectWizardUiEffect.NavigateToEditor -> onNavigateToEditor(effect.projectPath)
                ProjectWizardUiEffect.NavigateBack -> onNavigateBack()
                is ProjectWizardUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ProjectWizardScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWizardScreen(
    uiState: ProjectWizardUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ProjectWizardUiEvent) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WizardTopBar(
                currentStep = uiState.currentStep,
                onBackClicked = { onEvent(ProjectWizardUiEvent.BackClicked) }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            },
            label = "wizard_step_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { step ->
            when (step) {
                WizardStep.TEMPLATE_SELECTION -> TemplateSelectionStep(
                    templates = uiState.templates,
                    selectedCategory = uiState.selectedCategory,
                    selectedTemplate = uiState.selectedTemplate,
                    isLoading = uiState.isLoading,
                    onEvent = onEvent
                )
                WizardStep.CONFIGURE_PARAMS -> ConfigureParamsStep(
                    template = uiState.selectedTemplate,
                    paramValues = uiState.paramValues,
                    paramErrors = uiState.paramErrors,
                    onEvent = onEvent
                )
                WizardStep.GENERATING -> GeneratingStep(
                    progress = uiState.generationProgress
                )
                WizardStep.SUCCESS -> SuccessStep(
                    projectHandle = uiState.generatedProject,
                    onEvent = onEvent
                )
                WizardStep.ERROR -> ErrorStep(
                    errorMessage = uiState.generationError.orEmpty(),
                    onEvent = onEvent
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardTopBar(
    currentStep: WizardStep,
    onBackClicked: () -> Unit
) {
    val title = when (currentStep) {
        WizardStep.TEMPLATE_SELECTION -> "Template auswählen"
        WizardStep.CONFIGURE_PARAMS -> "Projekt konfigurieren"
        WizardStep.GENERATING -> "Projekt wird erstellt..."
        WizardStep.SUCCESS -> "Projekt erstellt!"
        WizardStep.ERROR -> "Fehler aufgetreten"
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (currentStep != WizardStep.GENERATING) {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück"
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSelectionStep(
    templates: List<ProjectTemplate>,
    selectedCategory: TemplateCategory?,
    selectedTemplate: ProjectTemplate?,
    isLoading: Boolean,
    onEvent: (ProjectWizardUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Category Filter Chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onEvent(ProjectWizardUiEvent.CategorySelected(null)) },
                label = { Text("Alle") }
            )
            TemplateCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onEvent(ProjectWizardUiEvent.CategorySelected(category)) },
                    label = { Text(category.displayName()) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = if (selectedCategory == null) templates
            else templates.filter { it.category == selectedCategory }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        isSelected = template.id == selectedTemplate?.id,
                        onClick = { onEvent(ProjectWizardUiEvent.TemplateSelected(template)) }
                    )
                }
            }

            // Next Button
            if (selectedTemplate != null) {
                Button(
                    onClick = { onEvent(ProjectWizardUiEvent.NextStepClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text("Weiter mit \"${selectedTemplate.name}\"")
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ProjectTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = template.category.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Ausgewählt",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigureParamsStep(
    template: ProjectTemplate?,
    paramValues: Map<String, String>,
    paramErrors: Map<String, String>,
    onEvent: (ProjectWizardUiEvent) -> Unit
) {
    if (template == null) return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = template.name,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = template.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        template.requiredParams.forEach { param ->
            OutlinedTextField(
                value = paramValues[param.key].orEmpty(),
                onValueChange = { onEvent(ProjectWizardUiEvent.ParamValueChanged(param.key, it)) },
                label = { Text(param.label) },
                placeholder = { Text(param.hint) },
                isError = paramErrors.containsKey(param.key),
                supportingText = paramErrors[param.key]?.let { { Text(it) } },
                singleLine = param.type != ParamType.DIRECTORY_PATH,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onEvent(ProjectWizardUiEvent.GenerateProjectClicked) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Projekt erstellen")
        }
    }
}

@Composable
private fun GeneratingStep(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Text(
                text = "Projekt wird generiert...",
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SuccessStep(
    projectHandle: com.codeforge.core.domain.model.ProjectHandle?,
    onEvent: (ProjectWizardUiEvent) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Projekt erfolgreich erstellt!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            projectHandle?.let {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = it.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onEvent(ProjectWizardUiEvent.OpenGeneratedProjectClicked) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Im Editor öffnen")
            }
            OutlinedButton(
                onClick = { onEvent(ProjectWizardUiEvent.CloseWizard) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zur Startseite")
            }
        }
    }
}

@Composable
private fun ErrorStep(
    errorMessage: String,
    onEvent: (ProjectWizardUiEvent) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Fehler bei der Projekterstellung",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onEvent(ProjectWizardUiEvent.BackClicked) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Zurück")
                }
                Button(
                    onClick = { onEvent(ProjectWizardUiEvent.GenerateProjectClicked) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Erneut versuchen")
                }
            }
        }
    }
}

private fun TemplateCategory.displayName(): String = when (this) {
    TemplateCategory.COMPOSE -> "Compose"
    TemplateCategory.MULTIMODULE -> "Multi-Module"
    TemplateCategory.JAVA -> "Java"
    TemplateCategory.KOTLIN_CLI -> "Kotlin CLI"
}
