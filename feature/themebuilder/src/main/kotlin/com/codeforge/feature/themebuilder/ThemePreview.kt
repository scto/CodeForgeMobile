// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.designsystem.CodeForgeTypography
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Beispiel-App im erzeugten Schema: App-Leiste, Karten, Schaltflächen, Eingabe, Chips, Fehler, Navigation. */
@Composable
internal fun ThemePreview(colorScheme: ColorScheme, modifier: Modifier = Modifier) {
    MaterialTheme(colorScheme = colorScheme, typography = CodeForgeTypography) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            Column {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(R.string.themebuilder_sample_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringRes(R.string.themebuilder_sample_card), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringRes(R.string.themebuilder_sample_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { 0.62f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text(stringRes(R.string.themebuilder_sample_primary)) }
                        FilledTonalButton(onClick = {}) { Text(stringRes(R.string.themebuilder_sample_tonal)) }
                        OutlinedButton(onClick = {}) { Text(stringRes(R.string.themebuilder_sample_outlined)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = true, onClick = {}, label = { Text(stringRes(R.string.themebuilder_sample_chip)) })
                        AssistChip(onClick = {}, label = { Text(stringRes(R.string.themebuilder_sample_assist)) })
                        Spacer(Modifier.weight(1f))
                        Switch(checked = true, onCheckedChange = {})
                    }
                    OutlinedTextField(
                        value = stringRes(R.string.themebuilder_sample_input_value),
                        onValueChange = {},
                        label = { Text(stringRes(R.string.themebuilder_sample_input)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        ) { Text(stringRes(R.string.themebuilder_sample_accent), Modifier.padding(12.dp)) }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringRes(R.string.themebuilder_sample_error))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        FloatingActionButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = null) }
                    }
                }
                NavigationBar(tonalElevation = 0.dp, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Filled.Home, null) }, label = { Text(stringRes(R.string.themebuilder_sample_nav_home)) })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Filled.Search, null) }, label = { Text(stringRes(R.string.themebuilder_sample_nav_search)) })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Filled.Person, null) }, label = { Text(stringRes(R.string.themebuilder_sample_nav_profile)) })
                }
            }
        }
    }
}
