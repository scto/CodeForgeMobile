// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.TemplateParam
import com.codeforge.core.domain.model.TemplateParamType

@Composable
fun ParameterFormScreen(
    template: ProjectTemplateDescriptor,
    paramValues: Map<String, String>,
    paramErrors: Map<String, String>,
    targetDir: String,
    onParamChanged: (key: String, value: String) -> Unit,
    onTargetDirChanged: (String) -> Unit,
    onBack: () -> Unit,
    onGenerate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(template.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                template.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        item {
            OutlinedTextField(
                value = targetDir,
                onValueChange = onTargetDirChanged,
                label = { Text("Zielverzeichnis") },
                isError = paramErrors.containsKey("__targetDir"),
                supportingText = paramErrors["__targetDir"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(template.requiredParams, key = { it.key }) { param ->
            ParamField(
                param = param,
                value = paramValues[param.key].orEmpty(),
                error = paramErrors[param.key],
                onValueChange = { value -> onParamChanged(param.key, value) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Zurück")
                }
                Button(onClick = onGenerate, modifier = Modifier.weight(1f)) {
                    Text("Erstellen")
                }
            }
        }
    }
}

@Composable
private fun ParamField(
    param: TemplateParam,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit
) {
    when (param.type) {
        TemplateParamType.BOOLEAN -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(param.label)
                Switch(
                    checked = value.toBooleanStrictOrNull() ?: false,
                    onCheckedChange = { checked -> onValueChange(checked.toString()) }
                )
            }
        }

        TemplateParamType.MIN_SDK -> {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> onValueChange(input.filter { it.isDigit() }) },
                label = { Text(param.label) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        TemplateParamType.PACKAGE_NAME -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(param.label) },
                isError = error != null || (value.isNotBlank() && !isValidPackageName(value)),
                supportingText = {
                    Text(
                        error ?: if (value.isNotBlank() && !isValidPackageName(value)) {
                            "Ungültiger Package-Name (z.B. com.example.app)"
                        } else {
                            ""
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        TemplateParamType.STRING, TemplateParamType.DIRECTORY -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(param.label) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun isValidPackageName(value: String): Boolean =
    Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$").matches(value)
