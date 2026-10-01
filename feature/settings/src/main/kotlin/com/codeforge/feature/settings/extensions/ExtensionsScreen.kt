@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.codeforge.feature.settings.extensions

import com.codeforge.core.resources.ResGetter

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ExtensionEntry(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val sha256: String,
    val version: String,
    val size: Long = 0,
    val type: String? = null,
    val checkPaths: List<String>? = null
)

@Composable
fun ExtensionsRoute(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { ExtensionsManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var extensions by remember { mutableStateOf<List<ExtensionEntry>>(emptyList()) }
    var installedExtensions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var installingExtensionId by remember { mutableStateOf<String?>(null) }

    val defaultList = remember {
        listOf(
            ExtensionEntry(
                id = "kotlin-language-server",
                name = "Kotlin Language Server",
                description = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_lsp_kotlin),
                url = "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.11/kotlin-language-server.zip",
                sha256 = "",
                version = "1.3.11"
            ),
            ExtensionEntry(
                id = "java-language-server",
                name = "Java Language Server",
                description = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_jdt_java),
                url = "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz",
                sha256 = "",
                version = "1.26.0"
            ),
            ExtensionEntry(
                id = "clangd",
                name = "C/C++ Clangd Language Server",
                description = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_clangd_cpp),
                url = "https://github.com/clangd/clangd/releases/download/17.0.3/clangd-linux-17.0.3.zip",
                sha256 = "",
                version = "17.0.3"
            )
        )
    }

    fun refreshInstalled() {
        installedExtensions = defaultList.union(extensions)
            .filter { manager.isExtensionInstalled(it) }
            .map { it.id }
            .toSet()
    }

    fun loadExtensions() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val fetched = withContext(Dispatchers.IO) {
                    runCatching {
                        val connection = URL("https://raw.githubusercontent.com/AndroidStudio-App/NeonIDE-Extension/main/extensions.json").openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                        connection.disconnect()
                        val jsonObj = JSONObject(jsonText)
                        val array = jsonObj.optJSONArray("extensions") ?: return@runCatching emptyList<ExtensionEntry>()
                        val result = mutableListOf<ExtensionEntry>()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val pathsArray = item.optJSONArray("checkPaths")
                            val pathsList = if (pathsArray != null) {
                                (0 until pathsArray.length()).map { pathsArray.getString(it) }
                            } else null

                            result.add(
                                ExtensionEntry(
                                    id = item.getString("id"),
                                    name = item.getString("name"),
                                    description = item.optString("description", ""),
                                    url = item.getString("url"),
                                    sha256 = item.optString("sha256", ""),
                                    version = item.optString("version", "1.0"),
                                    size = item.optLong("size", 0L),
                                    type = if (item.has("type") && !item.isNull("type")) item.getString("type") else null,
                                    checkPaths = pathsList
                                )
                            )
                        }
                        result
                    }.getOrDefault(emptyList())
                }
                extensions = if (fetched.isNotEmpty()) fetched else defaultList
                refreshInstalled()
            } catch (e: Exception) {
                error = e.message
                extensions = defaultList
                refreshInstalled()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadExtensions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back)
                        )
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_title)) },
                actions = {
                    IconButton(onClick = { loadExtensions() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_refresh)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && extensions.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Language Server & Tooling Extensions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Lade Sprach-Server und Erweiterungen herunter, um Autovervollständigung und Code-Analyse zu aktivieren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(extensions, key = { it.id }) { item ->
                        val isInstalled = installedExtensions.contains(item.id)
                        val isBusy = installingExtensionId == item.id

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Extension,
                                        contentDescription = null,
                                        tint = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "v${item.version}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_installing), style = MaterialTheme.typography.bodySmall)
                                    } else if (isInstalled) {
                                        Text(
                                            text = "Installiert",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    installingExtensionId = item.id
                                                    manager.uninstallExtension(item)
                                                    refreshInstalled()
                                                    installingExtensionId = null
                                                    snackbarHostState.showSnackbar("${item.name} deinstalliert")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_uninstall))
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    installingExtensionId = item.id
                                                    val res = manager.installExtension(item)
                                                    installingExtensionId = null
                                                    if (res.isSuccess) {
                                                        refreshInstalled()
                                                        snackbarHostState.showSnackbar("${item.name} erfolgreich installiert")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Fehler beim Installieren: ${res.exceptionOrNull()?.message}")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_ext_install))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
