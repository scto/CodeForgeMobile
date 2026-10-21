// Modul: :feature:build
package com.codeforge.feature.build.testrunner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.domain.model.TestCaseResult
import com.codeforge.core.domain.model.TestClassSuite
import com.codeforge.core.domain.model.TestFilterMode
import com.codeforge.core.domain.model.TestModuleSuite
import com.codeforge.core.domain.model.TestStatus
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.build.BuildTab
import com.codeforge.feature.build.BuildUiIntent
import com.codeforge.feature.build.BuildUiState
import java.util.Locale

@Composable
fun TestsTab(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedModules = remember { mutableStateMapOf<String, Boolean>() }
    val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Summary & Counters Header
        TestsHeader(
            state = state,
            onIntent = onIntent
        )

        HorizontalDivider()

        // Notice: Build failed before tests could be executed
        if (state.didBuildFailBeforeTests) {
            PreTestFailureNotice(
                onViewProblems = {
                    onIntent(BuildUiIntent.DismissPreTestBuildError)
                    onIntent(BuildUiIntent.SelectTab(BuildTab.PROBLEMS))
                }
            )
        }

        // Running Indicator
        if (state.isTesting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringRes(R.string.test_runner_laeuft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Search & Filter Row
        TestsFilterBar(
            state = state,
            onIntent = onIntent
        )

        // Main Tree List or Empty States
        when {
            state.testRunResult == null && !state.isTesting -> {
                NoTestsExecutedCard(
                    onRunAll = { onIntent(BuildUiIntent.RunTests()) }
                )
            }
            state.filteredModuleSuites.isEmpty() && !state.isTesting -> {
                NoFilterMatchesCard(
                    onResetFilter = {
                        onIntent(BuildUiIntent.SetTestSearchQuery(""))
                        onIntent(BuildUiIntent.SetTestFilterMode(TestFilterMode.ALL))
                        onIntent(BuildUiIntent.SelectTestModule(null))
                    }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (moduleSuite in state.filteredModuleSuites) {
                        val isModuleExpanded = expandedModules.getOrPut(moduleSuite.moduleGradlePath) { true }

                        item(key = "module_${moduleSuite.moduleGradlePath}") {
                            ModuleTreeRow(
                                moduleSuite = moduleSuite,
                                isExpanded = isModuleExpanded,
                                onToggle = {
                                    expandedModules[moduleSuite.moduleGradlePath] = !isModuleExpanded
                                },
                                onRunModule = {
                                    onIntent(BuildUiIntent.RunTests(moduleGradlePath = moduleSuite.moduleGradlePath))
                                }
                            )
                        }

                        if (isModuleExpanded) {
                            for (classSuite in moduleSuite.classes) {
                                val classKey = "${moduleSuite.moduleGradlePath}:${classSuite.className}"
                                val isClassExpanded = expandedClasses.getOrPut(classKey) { true }

                                item(key = "class_$classKey") {
                                    ClassTreeRow(
                                        classSuite = classSuite,
                                        isExpanded = isClassExpanded,
                                        onToggle = {
                                            expandedClasses[classKey] = !isClassExpanded
                                        },
                                        onRunClass = {
                                            onIntent(
                                                BuildUiIntent.RunTests(
                                                    moduleGradlePath = moduleSuite.moduleGradlePath,
                                                    testFilter = classSuite.className
                                                )
                                            )
                                        }
                                    )
                                }

                                if (isClassExpanded) {
                                    items(
                                        items = classSuite.cases,
                                        key = { case -> "$classKey:${case.name}" }
                                    ) { testCase ->
                                        MethodTreeRow(
                                            testCase = testCase,
                                            onClick = { onIntent(BuildUiIntent.SelectTestCase(testCase)) },
                                            onRunMethod = {
                                                onIntent(
                                                    BuildUiIntent.RunTests(
                                                        moduleGradlePath = moduleSuite.moduleGradlePath,
                                                        testFilter = "${testCase.className}.${testCase.name}"
                                                    )
                                                )
                                            }
                                        )
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

@Composable
private fun TestsHeader(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Counter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Passed
            CounterChip(
                icon = Icons.Default.CheckCircle,
                text = stringRes(R.string.test_runner_zaehler_bestanden, state.testPassedCount),
                color = Color(0xFF2E7D32),
                containerColor = Color(0xFFE8F5E9)
            )

            // Failed
            CounterChip(
                icon = Icons.Default.Cancel,
                text = stringRes(R.string.test_runner_zaehler_fehlgeschlagen, state.testFailedCount),
                color = MaterialTheme.colorScheme.error,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            )

            // Skipped
            if (state.testSkippedCount > 0) {
                CounterChip(
                    icon = Icons.Default.Help,
                    text = stringRes(R.string.test_runner_zaehler_uebersprungen, state.testSkippedCount),
                    color = Color(0xFF757575),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Total & Duration
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ) {
                val durationFormatted = String.format(Locale.US, "%.2fs", state.testDurationSeconds)
                Text(
                    text = "${stringRes(R.string.test_runner_zaehler_gesamt, state.testTotalCount)} · ${stringRes(R.string.test_runner_dauer, durationFormatted)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onIntent(BuildUiIntent.RunTests(moduleGradlePath = state.selectedModuleGradlePath)) },
                enabled = !state.isBuilding && !state.isTesting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringRes(R.string.test_runner_alle_ausfuehren))
            }

            OutlinedButton(
                onClick = { onIntent(BuildUiIntent.RerunFailedTests) },
                enabled = state.testFailedCount > 0 && !state.isBuilding && !state.isTesting,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringRes(R.string.test_runner_fehlgeschlagene_wiederholen))
            }
        }
    }
}

@Composable
private fun CounterChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    containerColor: Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TestsFilterBar(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search textfield
        OutlinedTextField(
            value = state.testSearchQuery,
            onValueChange = { onIntent(BuildUiIntent.SetTestSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringRes(R.string.test_runner_suche_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (state.testSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onIntent(BuildUiIntent.SetTestSearchQuery("")) }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips (Only failures + module selector)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.testFilterMode == TestFilterMode.FAILURES_ONLY,
                onClick = {
                    val next = if (state.testFilterMode == TestFilterMode.FAILURES_ONLY) {
                        TestFilterMode.ALL
                    } else {
                        TestFilterMode.FAILURES_ONLY
                    }
                    onIntent(BuildUiIntent.SetTestFilterMode(next))
                },
                label = { Text(stringRes(R.string.test_runner_nur_fehler_filter)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            // Available modules
            if (state.availableModules.size > 1) {
                for (module in state.availableModules) {
                    val isSelected = state.selectedModuleGradlePath == module
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val next = if (isSelected) null else module
                            onIntent(BuildUiIntent.SelectTestModule(next))
                        },
                        label = {
                            val label = if (module == ":") "All Modules" else module
                            Text(label)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreTestFailureNotice(
    onViewProblems: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringRes(R.string.test_runner_build_abgebrochen_vor_tests),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onViewProblems,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringRes(R.string.test_runner_zu_problemen_wechseln))
            }
        }
    }
}

@Composable
private fun ModuleTreeRow(
    moduleSuite: TestModuleSuite,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRunModule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = moduleSuite.moduleGradlePath,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Pass/fail counts
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (moduleSuite.failedCount > 0) {
                    Text(
                        text = "${moduleSuite.failedCount} ✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${moduleSuite.passedCount} ✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onRunModule,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringRes(R.string.test_runner_ausfuehren_tooltip),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassTreeRow(
    classSuite: TestClassSuite,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRunClass: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
            .clickable { onToggle() },
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = classSuite.simpleClassName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (classSuite.packageName.isNotBlank()) {
                    Text(
                        text = classSuite.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status counts for class
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (classSuite.failedCount > 0) {
                    Text(
                        text = "${classSuite.failedCount} ✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${classSuite.passedCount} ✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )

                IconButton(
                    onClick = onRunClass,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringRes(R.string.test_runner_klasse_ausfuehren_tooltip),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodTreeRow(
    testCase: TestCaseResult,
    onClick: () -> Unit,
    onRunMethod: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (testCase.status) {
            TestStatus.PASSED -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
            TestStatus.FAILED -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
            TestStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
            TestStatus.SKIPPED -> Icons.Default.Help to Color(0xFF757575)
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = testCase.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )

        val durationFormatted = String.format(Locale.US, "%.3fs", testCase.durationSeconds)
        Text(
            text = durationFormatted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onRunMethod,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringRes(R.string.test_runner_ausfuehren_tooltip),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun NoTestsExecutedCard(
    onRunAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = stringRes(R.string.test_runner_noch_keine_tests),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(onClick = onRunAll) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes(R.string.test_runner_alle_ausfuehren))
                }
            }
        }
    }
}

@Composable
private fun NoFilterMatchesCard(
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringRes(R.string.test_runner_keine_tests_gefunden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(onClick = onResetFilter) {
                Text(stringRes(R.string.test_runner_filter_zuruecksetzen))
            }
        }
    }
}
