package com.codeforge.feature.projectwizard

import com.codeforge.core.resources.ResGetter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.common.logging.AppLogger
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.feature.projectwizard.R as WizardR
import com.codeforge.libs.template_engine.CreateTemplate
import com.codeforge.libs.template_engine.ProjectTemplate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WizardTemplateItem(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val kind: ProjectTemplate.Kind
)

private object WizardTemplateRegistry {
    val templates = listOf(
        WizardTemplateItem(
            id = "empty_activity",
            nameRes = R.string.tpl_empty_activity_name,
            descRes = R.string.tpl_empty_activity_desc,
            iconRes = WizardR.drawable.empty_activity,
            kind = ProjectTemplate.Kind.EMPTY_ACTIVITY
        ),
        WizardTemplateItem(
            id = "compose_activity",
            nameRes = R.string.tpl_compose_activity_name,
            descRes = R.string.tpl_compose_activity_desc,
            iconRes = WizardR.drawable.compose_empty_activity,
            kind = ProjectTemplate.Kind.COMPOSE_ACTIVITY
        ),
        WizardTemplateItem(
            id = "basic_activity",
            nameRes = R.string.tpl_basic_activity_name,
            descRes = R.string.tpl_basic_activity_desc,
            iconRes = WizardR.drawable.basic_activity,
            kind = ProjectTemplate.Kind.BASIC_ACTIVITY
        ),
        WizardTemplateItem(
            id = "bottom_nav_activity",
            nameRes = R.string.tpl_bottom_nav_activity_name,
            descRes = R.string.tpl_bottom_nav_activity_desc,
            iconRes = WizardR.drawable.bottom_navigation_activity,
            kind = ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY
        ),
        WizardTemplateItem(
            id = "nav_drawer_activity",
            nameRes = R.string.tpl_nav_drawer_activity_name,
            descRes = R.string.tpl_nav_drawer_activity_desc,
            iconRes = WizardR.drawable.blank_activity_drawer,
            kind = ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY
        ),
        WizardTemplateItem(
            id = "tabbed_activity",
            nameRes = R.string.tpl_tabbed_activity_name,
            descRes = R.string.tpl_tabbed_activity_desc,
            iconRes = WizardR.drawable.blank_activity_tabs,
            kind = ProjectTemplate.Kind.TABBED_ACTIVITY
        ),
        WizardTemplateItem(
            id = "cpp_activity",
            nameRes = R.string.tpl_cpp_activity_name,
            descRes = R.string.tpl_cpp_activity_desc,
            iconRes = WizardR.drawable.cpp_activity,
            kind = ProjectTemplate.Kind.CPP_ACTIVITY
        ),
        WizardTemplateItem(
            id = "no_androidx_activity",
            nameRes = R.string.tpl_no_androidx_activity_name,
            descRes = R.string.tpl_no_androidx_activity_desc,
            iconRes = WizardR.drawable.empty_noandroidx,
            kind = ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY
        ),
        WizardTemplateItem(
            id = "no_activity",
            nameRes = R.string.tpl_no_activity_name,
            descRes = R.string.tpl_no_activity_desc,
            iconRes = WizardR.drawable.empty_activity,
            kind = ProjectTemplate.Kind.NO_ACTIVITY
        )
    )
}

private object WizardValidators {
    private val PROJECT_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_\\-\\+]{1,50}$")
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z]+(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValidProjectName(name: String): Boolean = PROJECT_NAME_REGEX.matches(name)
    fun isValidPackageName(pkg: String): Boolean = PACKAGE_NAME_REGEX.matches(pkg)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWizardRoute(
    onNavigateToEditor: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ProjectWizardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wizardUiState by viewModel.uiState.collectAsState()

    var selectedTemplateItem by remember { mutableStateOf<WizardTemplateItem?>(null) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var isPackageNameManuallyEdited by remember { mutableStateOf(false) }

    val defaultSaveDir = remember(wizardUiState.targetDir) {
        if (wizardUiState.targetDir.isNotBlank()) {
            wizardUiState.targetDir
        } else {
            val prefs = context.getSharedPreferences("codeforge_wizard_prefs", Context.MODE_PRIVATE)
            prefs.getString("last_save_location", "/storage/emulated/0/CodeForgeMobileProjects")
                ?: "/storage/emulated/0/CodeForgeMobileProjects"
        }
    }
    var saveLocation by remember(defaultSaveDir) { mutableStateOf(defaultSaveDir) }
    var minSdk by remember { mutableStateOf("24") }
    var language by remember { mutableStateOf("Kotlin") }
    var useKts by remember { mutableStateOf(true) }

    var projectNameError by remember { mutableStateOf<String?>(null) }
    var packageNameError by remember { mutableStateOf<String?>(null) }
    var saveLocationError by remember { mutableStateOf<String?>(null) }
    var languageError by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                val decoded = android.net.Uri.decode(uri.toString())
                val posixPath = if (decoded.contains("primary:")) {
                    "/storage/emulated/0/" + decoded.substringAfter("primary:")
                } else {
                    decoded
                }
                saveLocation = posixPath
                saveLocationError = null
            } catch (e: Exception) {
                // Ignore SAF fallback errors
            }
        }
    }

    fun updatePackageName(name: String, templateItem: WizardTemplateItem) {
        val templateRaw = context.getString(templateItem.nameRes).lowercase()
            .replace("project", "")
            .replace("activity", "")
            .replace("views", "")
            .trim()

        val templateSanitized = templateRaw.replace(Regex("[^a-z0-9]"), "")
        val projectSanitized = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        val prefix = if (templateSanitized.isNotEmpty()) "com.$templateSanitized" else "com.example"

        packageName = if (projectSanitized.isNotEmpty()) "$prefix.$projectSanitized" else prefix
    }

    fun validate(): Boolean {
        var valid = true

        if (!WizardValidators.isValidProjectName(projectName)) {
            projectNameError = context.getString(R.string.create_project_error_invalid_name)
            valid = false
        } else {
            projectNameError = null
        }

        if (!WizardValidators.isValidPackageName(packageName)) {
            packageNameError = context.getString(R.string.create_project_error_invalid_package)
            valid = false
        } else {
            packageNameError = null
        }

        val base = File(saveLocation)
        val projectDir = File(base, projectName)
        if (projectDir.exists()) {
            saveLocationError = context.getString(R.string.create_project_error_dir_exists)
            valid = false
        } else {
            saveLocationError = null
        }

        if (selectedTemplateItem?.kind == ProjectTemplate.Kind.COMPOSE_ACTIVITY &&
            !language.equals("Kotlin", ignoreCase = true)
        ) {
            languageError = context.getString(R.string.compose_requires_kotlin)
            valid = false
        } else {
            languageError = null
        }

        return valid
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.title_new_project)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTemplateItem != null) {
                            selectedTemplateItem = null
                        } else {
                            onCancel()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                }
            )

            if (selectedTemplateItem == null) {
                // Step 1: Template Selection Grid
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.choose_template),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(id = R.string.pick_template_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(WizardTemplateRegistry.templates) { item ->
                            TemplateCard(
                                item = item,
                                onClick = {
                                    selectedTemplateItem = item
                                    val suggestedName = "My" + context.getString(item.nameRes)
                                        .replace(" ", "")
                                        .replace("Activity", "")
                                        .replace("Views", "")
                                    projectName = suggestedName
                                    isPackageNameManuallyEdited = false
                                    updatePackageName(suggestedName, item)
                                }
                            )
                        }
                    }
                }
            } else {
                // Step 2: Configuration Form
                val templateItem = selectedTemplateItem!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Selected Template Info Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = templateItem.iconRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = templateItem.nameRes),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(id = templateItem.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(id = R.string.project_configuration),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Project Name
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            projectNameError = null
                            if (!isPackageNameManuallyEdited) {
                                updatePackageName(it, templateItem)
                            }
                        },
                        label = { Text(stringResource(id = R.string.label_project_name)) },
                        isError = projectNameError != null,
                        supportingText = projectNameError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    // Package Name
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            packageNameError = null
                            isPackageNameManuallyEdited = true
                        },
                        label = { Text(stringResource(id = R.string.package_name)) },
                        isError = packageNameError != null,
                        supportingText = packageNameError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    // Save Location
                    OutlinedTextField(
                        value = saveLocation,
                        onValueChange = {
                            saveLocation = it
                            saveLocationError = null
                        },
                        label = { Text(stringResource(id = R.string.project_location)) },
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_projectwizard_select_folder)
                                )
                            }
                        },
                        isError = saveLocationError != null,
                        supportingText = saveLocationError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    // Language Selector Dropdown
                    var langExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = !langExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = language,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(id = R.string.language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            isError = languageError != null,
                            supportingText = languageError?.let { { Text(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false }
                        ) {
                            listOf("Kotlin", "Java").forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        language = item
                                        if (item == "Kotlin") useKts = true
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Minimum SDK Dropdown
                    var sdkExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = sdkExpanded,
                        onExpandedChange = { sdkExpanded = !sdkExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = "API $minSdk (Android ${getAndroidVersionName(minSdk)})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(id = R.string.minimum_sdk)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sdkExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = sdkExpanded,
                            onDismissRequest = { sdkExpanded = false }
                        ) {
                            listOf("21", "24", "26", "28", "29", "30", "33").forEach { sdk ->
                                DropdownMenuItem(
                                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_projectwizard_api_level, sdk, getAndroidVersionName(sdk)))) },
                                    onClick = {
                                        minSdk = sdk
                                        sdkExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Gradle KTS Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.use_gradle_kotlin_dsl),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = useKts,
                            onCheckedChange = { useKts = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCancel, enabled = !isCreating) {
                            Text(stringResource(id = R.string.btn_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = { selectedTemplateItem = null }, enabled = !isCreating) {
                            Text(stringResource(id = R.string.action_back))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (validate()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                        Toast.makeText(
                                            context,
                                            "Bitte erteile die Speicherberechtigung (Alle Dateien verwalten)",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            context.startActivity(intent)
                                        }
                                        return@Button
                                    }

                                    isCreating = true
                                    scope.launch {
                                        var base = File(saveLocation)
                                        if (!base.exists()) {
                                            val created = base.mkdirs()
                                            if (!created && !base.exists()) {
                                                // Fallback to external files directory if public root is restricted
                                                base = File(context.getExternalFilesDir(null), "CodeForgeMobileProjects").apply { mkdirs() }
                                            }
                                        }
                                        val projectDir = File(base, projectName)

                                        val success = withContext(Dispatchers.IO) {
                                            try {
                                                val tpl = ProjectTemplate(
                                                    id = templateItem.id,
                                                    name = context.getString(templateItem.nameRes),
                                                    description = context.getString(templateItem.descRes),
                                                    kind = templateItem.kind
                                                )
                                                CreateTemplate(
                                                    context = context,
                                                    template = tpl,
                                                    projectDir = projectDir,
                                                    appId = packageName,
                                                    minSdk = minSdk.toIntOrNull() ?: 24,
                                                    language = language,
                                                    useKts = useKts
                                                )
                                                context.getSharedPreferences("codeforge_wizard_prefs", Context.MODE_PRIVATE)
                                                    .edit().putString("last_save_location", base.absolutePath).apply()
                                                true
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                false
                                            }
                                        }

                                        isCreating = false
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.create_project_success, projectDir.absolutePath),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            onNavigateToEditor(projectDir.absolutePath)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Fehler beim Erstellen des Projekts in ${projectDir.absolutePath}!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isCreating
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(id = R.string.btn_create))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    item: WizardTemplateItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = item.nameRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = item.descRes),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

private fun getAndroidVersionName(api: String): String {
    return when (api) {
        "21" -> "5.0 Lollipop"
        "24" -> "7.0 Nougat"
        "26" -> "8.0 Oreo"
        "28" -> "9.0 Pie"
        "29" -> "10 Q"
        "30" -> "11 Red Velvet Cake"
        "33" -> "13 Tiramisu"
        else -> api
    }
}
