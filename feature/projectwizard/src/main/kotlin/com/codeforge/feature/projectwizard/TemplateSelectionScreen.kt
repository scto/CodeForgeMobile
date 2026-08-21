// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.TemplateCategory

@Composable
fun TemplateSelectionScreen(
    isLoading: Boolean,
    templates: List<ProjectTemplateDescriptor>,
    onTemplateClick: (ProjectTemplateDescriptor) -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Neues Projekt", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Wähle eine Vorlage",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        val grouped = templates.groupBy { it.category }
        TemplateCategory.entries.forEach { category ->
            val categoryTemplates = grouped[category].orEmpty()
            if (categoryTemplates.isNotEmpty()) {
                item {
                    Text(
                        categoryLabel(category),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(categoryTemplates, key = { it.id }) { template ->
                    TemplateCard(template = template, onClick = { onTemplateClick(template) })
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: ProjectTemplateDescriptor, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text(
                template.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun categoryLabel(category: TemplateCategory): String = when (category) {
    TemplateCategory.COMPOSE -> "Jetpack Compose"
    TemplateCategory.JAVA -> "Java"
    TemplateCategory.KOTLIN_CLI -> "Kotlin CLI"
    TemplateCategory.MULTI_MODULE -> "Multi-Module"
}
