/**
 * Modul: :app
 * @author Thomas Schmid
 */
package com.codeforge.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Route des Projektimports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectImportRoute(
    modifier: Modifier = Modifier,
    onImported: (rootPath: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ProjectImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // true = direkt am Speicherort öffnen, false = in den Projektstamm kopieren
    var openInPlace by rememberSaveable { mutableStateOf(true) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        when {
            uri == null -> viewModel.retry()
            openInPlace -> viewModel.onLocalTreeSelected(uri.toString())
            else -> viewModel.onTreeSelected(uri.toString())
        }
    }

    fun pick(inPlace: Boolean) {
        openInPlace = inPlace
        viewModel.onPickerOpened()
        pickerLauncher.launch(null)
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is ProjectImportUiState.Done) {
            onImported(state.rootPath)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.app_projekt_importieren)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringRes(R.string.common_abbrechen))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = uiState) {
                is ProjectImportUiState.ChooseMode -> {
                    Text(stringRes(R.string.app_import_modus_titel), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { pick(inPlace = true) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) { Text(stringRes(R.string.app_import_lokal_oeffnen)) }
                    Text(
                        stringRes(R.string.app_import_lokal_hinweis),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    OutlinedButton(
                        onClick = { pick(inPlace = false) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringRes(R.string.app_import_kopieren)) }
                    Text(
                        stringRes(R.string.app_import_kopieren_hinweis),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                is ProjectImportUiState.AwaitingPicker -> {
                    CircularProgressIndicator()
                    Text(stringRes(R.string.app_verzeichnisauswahl_wird_geoeffnet), modifier = Modifier.padding(top = 16.dp))
                }

                is ProjectImportUiState.Copying -> {
                    CircularProgressIndicator()
                    Text(
                        stringRes(R.string.app_importiere_projekt_dateien_kopiert, state.filesCopiedSoFar),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        state.currentPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is ProjectImportUiState.Done -> {
                    CircularProgressIndicator()
                    Text(stringRes(R.string.app_projekt_importiert_oeffne_arbeitsberei), modifier = Modifier.padding(top = 16.dp))
                }

                is ProjectImportUiState.Failed -> {
                    Text(stringRes(R.string.app_import_fehlgeschlagen), style = MaterialTheme.typography.titleMedium)
                    Text(state.message, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    Button(onClick = { viewModel.retry() }) {
                        Text(stringRes(R.string.common_erneut_versuchen))
                    }
                }
            }
        }
    }
}
