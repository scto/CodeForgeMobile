package com.codeforge.feature.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R

@Composable
fun GitRoute(repoPath: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.title_git),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = repoPath,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun GitCloneRoute(
    onCloned: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var repoUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.btn_clone_repo),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = repoUrl,
            onValueChange = { repoUrl = it },
            label = { Text(stringResource(R.string.git_url)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onNavigateBack) {
                Text(stringResource(R.string.btn_cancel))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    val repoName = repoUrl.substringAfterLast("/").removeSuffix(".git")
                    val targetPath = "/storage/emulated/0/CodeForgeMobileProjects/${repoName.ifBlank { "ClonedRepo" }}"
                    onCloned(targetPath)
                },
            ) {
                Text(stringResource(R.string.btn_clone))
            }
        }
    }
}
