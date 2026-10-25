// Modul: :feature:signing
package com.codeforge.feature.signing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.GradleSigningSnippet
import com.codeforge.core.domain.model.KeyAlgorithm
import com.codeforge.core.domain.model.KeystoreCertificateInfo
import com.codeforge.core.domain.model.KeystoreInfo
import com.codeforge.core.domain.model.PasswordStrength
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import java.io.File

@Composable
fun SigningRoute(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: SigningViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    SigningScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningScreen(
    state: SigningUiState,
    onIntent: (SigningUiIntent) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onIntent(SigningUiIntent.DismissUserMessage)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.signing_title),
                onBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { onIntent(SigningUiIntent.RefreshKeystores) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Warnung bei fehlenden Tools
            if (!state.isJdkAvailable) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringRes(R.string.signing_jdk_nicht_installiert),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (!state.isApksignerAvailable) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringRes(R.string.signing_apksigner_nicht_installiert),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Aktive Build-Signatur-Banner
            if (state.activeBuildSigning.isNotEmpty()) {
                val activeKsPath = state.activeBuildSigning[GradleSigningSnippet.ENV_KEYSTORE_PATH].orEmpty()
                val activeAlias = state.activeBuildSigning[GradleSigningSnippet.ENV_KEYSTORE_ALIAS].orEmpty()
                val ksName = activeKsPath.substringAfterLast("/")
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringRes(R.string.signing_gradle_active_banner, ksName, activeAlias),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = { onIntent(SigningUiIntent.DeactivateBuildSigning) }) {
                            Text(stringRes(R.string.signing_gradle_deactivate_btn), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                Tab(
                    selected = state.activeTab == SigningTab.KEYSTORES,
                    onClick = { onIntent(SigningUiIntent.SelectTab(SigningTab.KEYSTORES)) },
                    text = { Text(stringRes(R.string.signing_tab_keystores)) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) }
                )
                Tab(
                    selected = state.activeTab == SigningTab.SIGN_APK,
                    onClick = { onIntent(SigningUiIntent.SelectTab(SigningTab.SIGN_APK)) },
                    text = { Text(stringRes(R.string.signing_tab_sign_apk)) },
                    icon = { Icon(Icons.Default.Security, contentDescription = null) }
                )
                Tab(
                    selected = state.activeTab == SigningTab.GRADLE_CONFIG,
                    onClick = { onIntent(SigningUiIntent.SelectTab(SigningTab.GRADLE_CONFIG)) },
                    text = { Text(stringRes(R.string.signing_tab_gradle_config)) },
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
            }

            when (state.activeTab) {
                SigningTab.KEYSTORES -> KeystoresTabContent(state = state, onIntent = onIntent)
                SigningTab.SIGN_APK -> SignApkTabContent(state = state, onIntent = onIntent)
                SigningTab.GRADLE_CONFIG -> GradleConfigTabContent(state = state, onIntent = onIntent)
            }
        }
    }

    // Dialoge
    if (state.isCreateDialogVisible) {
        CreateKeystoreDialog(state = state.createForm, onIntent = onIntent)
    }

    if (state.isImportDialogVisible) {
        ImportKeystoreDialog(state = state.importForm, onIntent = onIntent)
    }

    if (state.isDetailsDialogVisible && state.selectedKeystore != null) {
        val ks = state.selectedKeystore
        val assignments = state.projectAssignments.filter { it.keystoreId == ks.id }
        KeystoreDetailsDialog(
            keystore = ks,
            assignments = assignments,
            onAssignClick = { onIntent(SigningUiIntent.OpenAssignProjectDialog(ks)) },
            onRemoveAssignment = { path -> onIntent(SigningUiIntent.RemoveProjectAssignment(path)) },
            onDismiss = { onIntent(SigningUiIntent.DismissDetails) }
        )
    }

    if (state.isAssignProjectDialogVisible && state.assignProjectKeystore != null) {
        AssignProjectDialog(
            keystore = state.assignProjectKeystore,
            recentProjects = state.recentProjects,
            selectedProjectPath = state.assignSelectedProjectPath,
            selectedAlias = state.assignSelectedAlias,
            onProjectChange = { onIntent(SigningUiIntent.SelectAssignProject(it)) },
            onAliasChange = { onIntent(SigningUiIntent.SelectAssignAlias(it)) },
            onConfirm = { onIntent(SigningUiIntent.SubmitAssignProject) },
            onDismiss = { onIntent(SigningUiIntent.DismissAssignProjectDialog) }
        )
    }

    if (state.isExportConfirmDialogVisible && state.keystoreToExport != null) {
        ExportConfirmDialog(
            keystore = state.keystoreToExport,
            targetPath = state.exportTargetPath,
            onTargetPathChange = { onIntent(SigningUiIntent.UpdateExportTargetPath(it)) },
            onConfirm = { onIntent(SigningUiIntent.ConfirmExport) },
            onDismiss = { onIntent(SigningUiIntent.DismissExportConfirm) }
        )
    }

    if (state.isDeleteConfirmDialogVisible && state.keystoreToDelete != null) {
        DeleteConfirmDialog(
            keystore = state.keystoreToDelete,
            warningProjects = state.deleteWarningProjects,
            onConfirm = { onIntent(SigningUiIntent.ConfirmDelete) },
            onDismiss = { onIntent(SigningUiIntent.DismissDeleteConfirm) }
        )
    }
}

@Composable
private fun KeystoresTabContent(
    state: SigningUiState,
    onIntent: (SigningUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onIntent(SigningUiIntent.OpenCreateDialog) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringRes(R.string.signing_new_keystore))
            }
            OutlinedButton(
                onClick = { onIntent(SigningUiIntent.OpenImportDialog) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringRes(R.string.signing_import_keystore))
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.keystores.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringRes(R.string.signing_keystores_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.signing_keystores_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.keystores, key = { it.id }) { ks ->
                    KeystoreCard(
                        keystore = ks,
                        isActiveForBuild = state.activeBuildSigning[GradleSigningSnippet.ENV_KEYSTORE_PATH] == ks.filePath,
                        onDetails = { onIntent(SigningUiIntent.OpenDetails(ks)) },
                        onExport = { onIntent(SigningUiIntent.OpenExportConfirm(ks)) },
                        onDelete = { onIntent(SigningUiIntent.OpenDeleteConfirm(ks)) },
                        onActivateBuild = {
                            ks.primaryAlias?.let { alias ->
                                onIntent(SigningUiIntent.ActivateBuildSigning(ks.id, alias))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeystoreCard(
    keystore: KeystoreInfo,
    isActiveForBuild: Boolean,
    onDetails: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onActivateBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveForBuild) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = keystore.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${keystore.fileSizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (keystore.isDebugKeystore) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringRes(R.string.signing_debug_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD32F2F)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            val aliasCount = keystore.entries.size
            val primaryEntry = keystore.entries.firstOrNull()
            Text(
                text = "${stringRes(R.string.signing_aliases)}: ${if (aliasCount > 0) keystore.entries.joinToString { it.alias } else "—"}",
                style = MaterialTheme.typography.bodyMedium
            )

            primaryEntry?.primaryCertificate?.let { cert ->
                if (cert.sha256Fingerprint.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "SHA-256: ${cert.sha256Fingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDetails) {
                        Text(stringRes(R.string.signing_details_title))
                    }
                    OutlinedButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringRes(R.string.signing_export_keystore))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }

                if (!isActiveForBuild && keystore.primaryAlias != null) {
                    FilledTonalButton(onClick = onActivateBuild) {
                        Text(stringRes(R.string.signing_gradle_activate_btn), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignApkTabContent(
    state: SigningUiState,
    onIntent: (SigningUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val form = state.signForm
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = form.inputApkPath,
            onValueChange = { onIntent(SigningUiIntent.UpdateSignInputPath(it)) },
            label = { Text(stringRes(R.string.signing_input_apk)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = form.outputApkPath,
            onValueChange = { onIntent(SigningUiIntent.UpdateSignOutputPath(it)) },
            label = { Text(stringRes(R.string.signing_output_apk)) },
            placeholder = { Text("app-release-signed.apk") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Keystore Auswahl
        if (state.keystores.isNotEmpty()) {
            Text(stringRes(R.string.signing_select_keystore), style = MaterialTheme.typography.titleSmall)
            var expandedKs by remember { mutableStateOf(false) }
            val selectedKs = state.keystores.firstOrNull { it.id == form.selectedKeystoreId }

            ExposedDropdownMenuBox(
                expanded = expandedKs,
                onExpandedChange = { expandedKs = !expandedKs }
            ) {
                OutlinedTextField(
                    value = selectedKs?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringRes(R.string.signing_select_keystore)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expandedKs,
                    onDismissRequest = { expandedKs = false }
                ) {
                    state.keystores.forEach { ks ->
                        DropdownMenuItem(
                            text = { Text(ks.name) },
                            onClick = {
                                onIntent(SigningUiIntent.SelectSignKeystore(ks.id))
                                expandedKs = false
                            }
                        )
                    }
                }
            }

            // Alias Auswahl
            val aliases = selectedKs?.entries?.map { it.alias }.orEmpty()
            if (aliases.isNotEmpty()) {
                var expandedAlias by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedAlias,
                    onExpandedChange = { expandedAlias = !expandedAlias }
                ) {
                    OutlinedTextField(
                        value = form.selectedAlias,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringRes(R.string.signing_select_alias)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAlias) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAlias,
                        onDismissRequest = { expandedAlias = false }
                    ) {
                        aliases.forEach { alias ->
                            DropdownMenuItem(
                                text = { Text(alias) },
                                onClick = {
                                    onIntent(SigningUiIntent.SelectSignAlias(alias))
                                    expandedAlias = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Schema-Optionen
        Column {
            Text("Signature Schemes", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.v1SigningEnabled, onCheckedChange = { onIntent(SigningUiIntent.SetV1Signing(it)) })
                Text(stringRes(R.string.signing_scheme_v1), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.v2SigningEnabled, onCheckedChange = { onIntent(SigningUiIntent.SetV2Signing(it)) })
                Text(stringRes(R.string.signing_scheme_v2), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.v3SigningEnabled, onCheckedChange = { onIntent(SigningUiIntent.SetV3Signing(it)) })
                Text(stringRes(R.string.signing_scheme_v3), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.v4SigningEnabled, onCheckedChange = { onIntent(SigningUiIntent.SetV4Signing(it)) })
                Text(stringRes(R.string.signing_scheme_v4), style = MaterialTheme.typography.bodyMedium)
            }
        }

        form.errorMessage?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = { onIntent(SigningUiIntent.SubmitSignApk) },
            enabled = form.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (form.isSigning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringRes(R.string.signing_btn_sign))
        }

        // Ergebnis
        form.signResult?.let { res ->
            if (res.success) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringRes(R.string.signing_verified_success),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringRes(
                                R.string.signing_signed_apk_created,
                                res.outputApkPath.orEmpty(),
                                "${(res.outputApkSizeBytes ?: 0L) / 1024} KB"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        res.verificationResult?.let { vRes ->
                            Spacer(Modifier.height(12.dp))
                            Text("Verifikation:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                if (vRes.v1SchemeVerified) Text("V1 ✓", style = MaterialTheme.typography.bodySmall)
                                if (vRes.v2SchemeVerified) Text("V2 ✓", style = MaterialTheme.typography.bodySmall)
                                if (vRes.v3SchemeVerified) Text("V3 ✓", style = MaterialTheme.typography.bodySmall)
                                if (vRes.v4SchemeVerified) Text("V4 ✓", style = MaterialTheme.typography.bodySmall)
                            }
                            vRes.certificates.firstOrNull()?.let { cert ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Zertifikat: ${cert.owner}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "SHA-256: ${cert.sha256Fingerprint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradleConfigTabContent(
    state: SigningUiState,
    onIntent: (SigningUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringRes(R.string.signing_gradle_snippet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringRes(R.string.signing_gradle_snippet_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("app/build.gradle.kts", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("signingConfigs", state.gradleSnippet))
                        copied = true
                    }) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = state.gradleSnippet,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateKeystoreDialog(
    state: CreateKeystoreFormState,
    onIntent: (SigningUiIntent) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onIntent(SigningUiIntent.DismissCreateDialog) },
        title = { Text(stringRes(R.string.signing_new_keystore)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.fileName,
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateFileName(it)) },
                    label = { Text(stringRes(R.string.signing_file_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.alias,
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateAlias(it)) },
                    label = { Text(stringRes(R.string.signing_alias)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.validityYears.toString(),
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateValidityYears(it.toIntOrNull() ?: 25)) },
                    label = { Text(stringRes(R.string.signing_validity_years)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.commonName,
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateCommonName(it)) },
                    label = { Text(stringRes(R.string.signing_common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.organization,
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateOrganization(it)) },
                    label = { Text(stringRes(R.string.signing_organization)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.country,
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateCountry(it)) },
                    label = { Text(stringRes(R.string.signing_country)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Algorithmus Auswahl
                Text(stringRes(R.string.signing_algorithm), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyAlgorithm.entries.forEach { alg ->
                        FilterChip(
                            selected = state.algorithm == alg,
                            onClick = { onIntent(SigningUiIntent.UpdateCreateAlgorithm(alg)) },
                            label = { Text(alg.name.replace("_", " ")) }
                        )
                    }
                }

                // Passwort
                OutlinedTextField(
                    value = state.storePassword,
                    onValueChange = { onIntent(SigningUiIntent.UpdateCreateStorePassword(it)) },
                    label = { Text(stringRes(R.string.signing_store_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Stärkeanzeige
                if (state.storePassword.isNotEmpty()) {
                    val strengthText = when (state.passwordStrength) {
                        PasswordStrength.VERY_WEAK -> stringRes(R.string.signing_strength_very_weak)
                        PasswordStrength.WEAK -> stringRes(R.string.signing_strength_weak)
                        PasswordStrength.MEDIUM -> stringRes(R.string.signing_strength_medium)
                        PasswordStrength.STRONG -> stringRes(R.string.signing_strength_strong)
                        PasswordStrength.VERY_STRONG -> stringRes(R.string.signing_strength_very_strong)
                    }
                    val progress = state.passwordStrength.score / 5f
                    val color = when (state.passwordStrength) {
                        PasswordStrength.VERY_WEAK, PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                        PasswordStrength.MEDIUM -> Color(0xFFFFA000)
                        PasswordStrength.STRONG, PasswordStrength.VERY_STRONG -> Color(0xFF388E3C)
                    }
                    Column {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = color,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringRes(R.string.signing_password_strength, strengthText),
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.useSamePassword,
                        onCheckedChange = { onIntent(SigningUiIntent.SetCreateUseSamePassword(it)) }
                    )
                    Text(stringRes(R.string.signing_same_password), style = MaterialTheme.typography.bodySmall)
                }

                if (!state.useSamePassword) {
                    OutlinedTextField(
                        value = state.keyPassword,
                        onValueChange = { onIntent(SigningUiIntent.UpdateCreateKeyPassword(it)) },
                        label = { Text(stringRes(R.string.signing_key_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                state.errorMessage?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntent(SigningUiIntent.SubmitCreateKeystore) },
                enabled = state.canSubmit
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringRes(R.string.signing_btn_create))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SigningUiIntent.DismissCreateDialog) }) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}

@Composable
private fun ImportKeystoreDialog(
    state: ImportKeystoreFormState,
    onIntent: (SigningUiIntent) -> Unit
) {
    val context = LocalContext.current
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "imported.keystore"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
                val cacheFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                onIntent(SigningUiIntent.UpdateImportFilePath(cacheFile.absolutePath))
                if (state.customName.isBlank()) {
                    onIntent(SigningUiIntent.UpdateImportCustomName(fileName))
                }
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = { onIntent(SigningUiIntent.DismissImportDialog) },
        title = { Text(stringRes(R.string.signing_import_keystore)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(R.string.signing_saf_pick_file))
                }

                Text(
                    text = stringRes(R.string.signing_supported_formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = state.filePath,
                    onValueChange = { onIntent(SigningUiIntent.UpdateImportFilePath(it)) },
                    label = { Text(stringRes(R.string.signing_file_name)) },
                    placeholder = { Text("/sdcard/Download/my-key.keystore") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.storePassword,
                    onValueChange = { onIntent(SigningUiIntent.UpdateImportPassword(it)) },
                    label = { Text(stringRes(R.string.signing_store_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.customName,
                    onValueChange = { onIntent(SigningUiIntent.UpdateImportCustomName(it)) },
                    label = { Text("Neuer Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                state.errorMessage?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntent(SigningUiIntent.SubmitImportKeystore) },
                enabled = state.canSubmit
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringRes(R.string.signing_btn_import))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SigningUiIntent.DismissImportDialog) }) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}

@Composable
private fun KeystoreDetailsDialog(
    keystore: KeystoreInfo,
    assignments: List<ProjectSigningAssignment>,
    onAssignClick: () -> Unit,
    onRemoveAssignment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(keystore.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Format: ${keystore.type}", style = MaterialTheme.typography.bodySmall)
                Text("Pfad: ${keystore.filePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                // 47.5: Verwendet in Projekten
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringRes(R.string.signing_used_in_projects),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = onAssignClick,
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringRes(R.string.signing_assign_project_btn), fontSize = 12.sp)
                            }
                        }

                        if (assignments.isEmpty()) {
                            Text(
                                text = stringRes(R.string.signing_no_projects_assigned),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            assignments.forEach { a ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = a.projectPath.substringAfterLast('/'),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${a.projectPath} (Alias: ${a.alias})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveAssignment(a.projectPath) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringRes(R.string.signing_remove_assignment),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                keystore.entries.forEach { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Alias: ${entry.alias}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            if (entry.creationDate.isNotBlank()) {
                                Text("Erstellt: ${entry.creationDate}", style = MaterialTheme.typography.bodySmall)
                            }

                            entry.primaryCertificate?.let { cert ->
                                if (cert.owner.isNotBlank()) {
                                    Text("Inhaber: ${cert.owner}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (cert.validFrom.isNotBlank()) {
                                    Text("Gültig: ${cert.validFrom} bis ${cert.validUntil}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (cert.publicKeyAlgorithm.isNotBlank()) {
                                    Text("Algorithmus: ${cert.publicKeyAlgorithm}", style = MaterialTheme.typography.bodySmall)
                                }

                                if (cert.sha256Fingerprint.isNotBlank()) {
                                    FingerprintRow(label = "SHA-256", fingerprint = cert.sha256Fingerprint, context = context)
                                }
                                if (cert.sha1Fingerprint.isNotBlank()) {
                                    FingerprintRow(label = "SHA-1", fingerprint = cert.sha1Fingerprint, context = context)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.pw_ok))
            }
        }
    )
}

@Composable
private fun FingerprintRow(
    label: String,
    fingerprint: String,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text(
                text = fingerprint,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, fingerprint))
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignProjectDialog(
    keystore: KeystoreInfo,
    recentProjects: List<RecentProject>,
    selectedProjectPath: String,
    selectedAlias: String,
    onProjectChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedProject by remember { mutableStateOf(false) }
    var expandedAlias by remember { mutableStateOf(false) }
    val availableAliases = remember(keystore) { keystore.entries.map { it.alias }.ifEmpty { listOf(keystore.primaryAlias.orEmpty()).filter { it.isNotBlank() } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.signing_assign_project_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Projekt-Auswahl
                if (recentProjects.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedProject,
                        onExpandedChange = { expandedProject = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProjectPath,
                            onValueChange = onProjectChange,
                            label = { Text(stringRes(R.string.signing_select_project)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProject) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProject,
                            onDismissRequest = { expandedProject = false }
                        ) {
                            recentProjects.forEach { project ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(project.name, fontWeight = FontWeight.Bold)
                                            Text(project.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    onClick = {
                                        onProjectChange(project.path)
                                        expandedProject = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedProjectPath,
                        onValueChange = onProjectChange,
                        label = { Text(stringRes(R.string.signing_select_project)) },
                        placeholder = { Text("/data/data/com.termux/files/home/MyProject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Alias-Auswahl
                if (availableAliases.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expandedAlias,
                        onExpandedChange = { expandedAlias = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAlias,
                            onValueChange = onAliasChange,
                            label = { Text(stringRes(R.string.signing_select_alias)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAlias) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            readOnly = true
                        )
                        ExposedDropdownMenu(
                            expanded = expandedAlias,
                            onDismissRequest = { expandedAlias = false }
                        ) {
                            availableAliases.forEach { alias ->
                                DropdownMenuItem(
                                    text = { Text(alias) },
                                    onClick = {
                                        onAliasChange(alias)
                                        expandedAlias = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedAlias,
                        onValueChange = onAliasChange,
                        label = { Text(stringRes(R.string.signing_alias)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedProjectPath.isNotBlank() && selectedAlias.isNotBlank()
            ) {
                Text(stringRes(R.string.pw_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}

@Composable
private fun ExportConfirmDialog(
    keystore: KeystoreInfo,
    targetPath: String,
    onTargetPathChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(R.string.signing_export_warning_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringRes(R.string.signing_export_warning_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringRes(R.string.signing_export_contains_private_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = targetPath,
                    onValueChange = onTargetPathChange,
                    label = { Text("Zielpfad") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringRes(R.string.signing_export_keystore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    keystore: KeystoreInfo,
    warningProjects: List<String> = emptyList(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.signing_delete_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringRes(R.string.signing_delete_confirm_text, keystore.name))

                if (warningProjects.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringRes(
                                R.string.signing_delete_warning_projects,
                                warningProjects.joinToString(", ") { it.substringAfterLast('/') }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringRes(R.string.signing_delete_keystore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}
