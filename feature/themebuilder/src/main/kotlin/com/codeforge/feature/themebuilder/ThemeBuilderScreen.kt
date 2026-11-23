// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.codeforge.core.designsystem.toColorScheme
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.libs.theme_kit.ColorHex
import com.codeforge.libs.theme_kit.ColorRole
import com.codeforge.libs.theme_kit.ColorRoles
import com.codeforge.libs.theme_kit.ThemeContrast
import com.codeforge.libs.theme_kit.ThemeEngine
import com.codeforge.libs.theme_kit.ThemeSpec
import com.codeforge.libs.theme_kit.ThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AA_RATIO = 4.5

/**
 * Theme-Builder für das geöffnete Projekt (wie der Layout-Designer für Layouts): Material-3-Theme aus einer
 * Grundfarbe gestalten, live ansehen und ins Projekt übernehmen oder als App-Farbschema installieren.
 */
@Composable
fun ThemeBuilderRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ThemeBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            val text = when (effect) {
                is ThemeBuilderEffect.Applied -> Res.string(R.string.themebuilder_applied, effect.files, effect.backup ?: "–")
                ThemeBuilderEffect.NothingToApply -> Res.string(R.string.themebuilder_nothing)
                ThemeBuilderEffect.LoadedFromProject -> Res.string(R.string.themebuilder_loaded)
                is ThemeBuilderEffect.Installed -> Res.string(R.string.themebuilder_install_done, effect.name)
                is ThemeBuilderEffect.Failed -> Res.string(R.string.themebuilder_failed, effect.message)
            }
            snackbar.showSnackbar(text)
        }
    }
    ThemeBuilderContent(state = state, snackbar = snackbar, onBack = onBack, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Zustandsloser Inhalt des Theme-Builders (auch für Screenshots). */
@Composable
fun ThemeBuilderContent(
    state: ThemeBuilderUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (ThemeBuilderUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerFor by remember { mutableStateOf<PickerTarget?>(null) }
    var showInstall by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val pixels = withContext(Dispatchers.IO) { runCatching { loadSamplePixels(context, uri) }.getOrNull() }
                pixels?.let { onEvent(ThemeBuilderUiEvent.ImagePixels(it)) }
            }
        }
    }
    val previewScheme = remember(state.shown) { state.shown.toColorScheme() }

    Scaffold(
        modifier = modifier,
        topBar = {
            SettingsTopBar(
                title = if (state.projectName.isBlank()) {
                    stringRes(R.string.themebuilder_title)
                } else {
                    stringRes(R.string.themebuilder_title_project, state.projectName)
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEvent(ThemeBuilderUiEvent.Reset) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.themebuilder_reset))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ActionBar(
                hasProject = state.projectRoot != null,
                busy = state.busy,
                onInstall = { showInstall = true },
                onApply = { onEvent(ThemeBuilderUiEvent.OpenPlan) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (state.projectRoot == null) {
                item { InfoRow(stringRes(R.string.themebuilder_no_project)) }
            }
            item { PreviewModeSwitch(state.previewDark) { onEvent(ThemeBuilderUiEvent.PreviewModeChanged(it)) } }
            item { ThemePreview(previewScheme) }
            item {
                SeedSection(
                    state = state,
                    onEvent = onEvent,
                    onPick = { pickerFor = PickerTarget.Seed },
                    onImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
            item { StyleSection(state.spec.style) { onEvent(ThemeBuilderUiEvent.StyleSelected(it)) } }
            item { ContrastSection(state.spec.contrast) { onEvent(ThemeBuilderUiEvent.ContrastSelected(it)) } }
            item { OverrideSection(state.spec, state.shown, onEvent) { pickerFor = PickerTarget.Override(it) } }
            item { RolesSection(state.shown) }
        }
    }

    pickerFor?.let { target ->
        val initial = when (target) {
            PickerTarget.Seed -> state.spec.seed
            is PickerTarget.Override -> overrideValue(state.spec, target.slot) ?: ColorHex.format(state.shown[target.slot.role])
        }
        ColorPickerDialog(
            title = stringRes(R.string.themebuilder_farbauswahl),
            initialHex = initial,
            onConfirm = { hex ->
                when (target) {
                    PickerTarget.Seed -> onEvent(ThemeBuilderUiEvent.SeedPicked(hex))
                    is PickerTarget.Override -> onEvent(ThemeBuilderUiEvent.OverrideChanged(target.slot, hex))
                }
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }
    if (showInstall) {
        InstallDialog(
            onConfirm = { name ->
                showInstall = false
                onEvent(ThemeBuilderUiEvent.InstallAsAppTheme(name))
            },
            onDismiss = { showInstall = false },
        )
    }
    state.plan?.let { plan -> PlanSheet(plan = plan, targets = state.targets, scanning = state.scanning, busy = state.busy, onEvent = onEvent) }
}

private sealed interface PickerTarget {
    data object Seed : PickerTarget

    data class Override(val slot: OverrideSlot) : PickerTarget
}

private val OverrideSlot.role: ColorRole
    get() = when (this) {
        OverrideSlot.SECONDARY -> ColorRole.SECONDARY
        OverrideSlot.TERTIARY -> ColorRole.TERTIARY
        OverrideSlot.NEUTRAL -> ColorRole.SURFACE_VARIANT
        OverrideSlot.ERROR -> ColorRole.ERROR
    }

private fun overrideValue(spec: ThemeSpec, slot: OverrideSlot): String? = when (slot) {
    OverrideSlot.SECONDARY -> spec.secondary
    OverrideSlot.TERTIARY -> spec.tertiary
    OverrideSlot.NEUTRAL -> spec.neutral
    OverrideSlot.ERROR -> spec.error
}

@Composable
private fun ActionBar(hasProject: Boolean, busy: Boolean, onInstall: () -> Unit, onApply: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onInstall, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringRes(R.string.themebuilder_install_app), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onApply, enabled = hasProject && !busy, modifier = Modifier.weight(1f)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringRes(R.string.themebuilder_apply), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun PreviewModeSwitch(dark: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false to R.string.themebuilder_hell, true to R.string.themebuilder_dunkel).forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = dark == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(stringRes(label)) }
        }
    }
}

@Composable
private fun Swatch(argb: Int, size: Int = 40, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .let { m -> onClick?.let { m.clickable(onClick = it) } ?: m },
    )
}

@Composable
private fun SeedSection(state: ThemeBuilderUiState, onEvent: (ThemeBuilderUiEvent) -> Unit, onPick: () -> Unit, onImage: () -> Unit) {
    Column {
        SectionTitle(stringRes(R.string.themebuilder_seed))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(ColorHex.parse(state.spec.seed) ?: 0, size = 48, onClick = onPick)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = state.seedText,
                onValueChange = { onEvent(ThemeBuilderUiEvent.SeedTextChanged(it)) },
                label = { Text(stringRes(R.string.themebuilder_hex_hint)) },
                isError = ColorHex.parse(state.seedText) == null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPick) { Icon(Icons.Filled.Colorize, contentDescription = stringRes(R.string.themebuilder_pick_color)) }
            IconButton(onClick = onImage) { Icon(Icons.Filled.Image, contentDescription = stringRes(R.string.themebuilder_from_image)) }
        }
        Spacer(Modifier.height(10.dp))
        val seedArgb = ColorHex.parse(state.spec.seed)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(RgbColorUtils.QUICK_PRESETS) { hex ->
                val argb = ColorHex.parse(hex) ?: return@items
                Swatch(argb, size = 34, selected = argb == seedArgb) { onEvent(ThemeBuilderUiEvent.SeedPicked(hex)) }
            }
        }
        if (state.imageSeeds.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringRes(R.string.themebuilder_image_seeds), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.imageSeeds.forEach { argb ->
                    Swatch(argb, size = 34, selected = argb == seedArgb) { onEvent(ThemeBuilderUiEvent.SeedPicked(ColorHex.format(argb))) }
                }
            }
        }
    }
}

private val ThemeStyle.labelRes: Int
    get() = when (this) {
        ThemeStyle.TONAL_SPOT -> R.string.themebuilder_style_tonal_spot
        ThemeStyle.NEUTRAL -> R.string.themebuilder_style_neutral
        ThemeStyle.VIBRANT -> R.string.themebuilder_style_vibrant
        ThemeStyle.EXPRESSIVE -> R.string.themebuilder_style_expressive
        ThemeStyle.FIDELITY -> R.string.themebuilder_style_fidelity
        ThemeStyle.CONTENT -> R.string.themebuilder_style_content
        ThemeStyle.RAINBOW -> R.string.themebuilder_style_rainbow
        ThemeStyle.FRUIT_SALAD -> R.string.themebuilder_style_fruit_salad
        ThemeStyle.MONOCHROME -> R.string.themebuilder_style_monochrome
    }

@Composable
private fun StyleSection(selected: ThemeStyle, onSelect: (ThemeStyle) -> Unit) {
    Column {
        SectionTitle(stringRes(R.string.themebuilder_style))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ThemeStyle.entries) { style ->
                FilterChip(selected = style == selected, onClick = { onSelect(style) }, label = { Text(stringRes(style.labelRes)) })
            }
        }
    }
}

@Composable
private fun ContrastSection(selected: ThemeContrast, onSelect: (ThemeContrast) -> Unit) {
    Column {
        SectionTitle(stringRes(R.string.themebuilder_contrast))
        val options = listOf(
            ThemeContrast.STANDARD to R.string.themebuilder_contrast_standard,
            ThemeContrast.MEDIUM to R.string.themebuilder_contrast_medium,
            ThemeContrast.HIGH to R.string.themebuilder_contrast_high,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(stringRes(label)) }
            }
        }
    }
}

@Composable
private fun OverrideSection(spec: ThemeSpec, shown: ColorRoles, onEvent: (ThemeBuilderUiEvent) -> Unit, onPick: (OverrideSlot) -> Unit) {
    Column {
        SectionTitle(stringRes(R.string.themebuilder_overrides))
        Text(stringRes(R.string.themebuilder_override_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val labels = mapOf(
            OverrideSlot.SECONDARY to R.string.themebuilder_sekundaer,
            OverrideSlot.TERTIARY to R.string.themebuilder_tertiaer,
            OverrideSlot.NEUTRAL to R.string.themebuilder_neutral,
            OverrideSlot.ERROR to R.string.themebuilder_error,
        )
        OverrideSlot.entries.forEach { slot ->
            val value = overrideValue(spec, slot)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(ColorHex.parse(value) ?: shown[slot.role], size = 32, onClick = { onPick(slot) })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringRes(labels.getValue(slot)), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        value ?: stringRes(R.string.themebuilder_override_auto),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = value?.let { FontFamily.Monospace }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = value != null,
                    onCheckedChange = { on ->
                        if (on) onPick(slot) else onEvent(ThemeBuilderUiEvent.OverrideChanged(slot, null))
                    },
                )
            }
        }
    }
}

@Composable
private fun RolesSection(roles: ColorRoles) {
    Column {
        SectionTitle(stringRes(R.string.themebuilder_roles))
        ColorRole.CONTRAST_PAIRS.forEach { (fg, bg) ->
            val ratio = ThemeEngine.contrastRatio(roles[fg], roles[bg])
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(roles[bg]))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(bg.key, color = Color(roles[fg]), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    ColorHex.format(roles[bg]),
                    color = Color(roles[fg]),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringRes(R.string.themebuilder_ratio, "%.1f".format(ratio)) + if (ratio >= AA_RATIO) " ✓" else " !",
                    color = Color(roles[fg]),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val surfaces = listOf(
            ColorRole.BACKGROUND, ColorRole.SURFACE_DIM, ColorRole.SURFACE, ColorRole.SURFACE_BRIGHT,
            ColorRole.SURFACE_CONTAINER_LOWEST, ColorRole.SURFACE_CONTAINER_LOW, ColorRole.SURFACE_CONTAINER,
            ColorRole.SURFACE_CONTAINER_HIGH, ColorRole.SURFACE_CONTAINER_HIGHEST, ColorRole.OUTLINE, ColorRole.OUTLINE_VARIANT,
            ColorRole.INVERSE_PRIMARY,
        )
        surfaces.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                pair.forEach { role ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(roles[role]))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(role.key, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                ColorHex.format(roles[role]),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.themebuilder_install_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(R.string.themebuilder_install_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringRes(R.string.themebuilder_install_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringRes(R.string.themebuilder_install_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanSheet(plan: PlanUi, targets: List<TargetItem>, scanning: Boolean, busy: Boolean, onEvent: (ThemeBuilderUiEvent) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { onEvent(ThemeBuilderUiEvent.DismissPlan) }, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringRes(R.string.themebuilder_plan_title), style = MaterialTheme.typography.titleLarge) }
            if (scanning) {
                item { CircularProgressIndicator() }
            } else if (targets.isEmpty()) {
                item { InfoRow(stringRes(R.string.themebuilder_no_targets)) }
            } else {
                planContent(plan, targets, busy, onEvent)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.planContent(
    plan: PlanUi,
    targets: List<TargetItem>,
    busy: Boolean,
    onEvent: (ThemeBuilderUiEvent) -> Unit,
) {
    item { Text(stringRes(R.string.themebuilder_targets), style = MaterialTheme.typography.titleSmall) }
    items(targets, key = { it.id }) { t -> TargetRow(t) { onEvent(ThemeBuilderUiEvent.TargetToggled(t.id)) } }
    item { HorizontalDivider() }
    items(plan.notes) { note -> InfoRow(noteText(note)) }
    if (plan.computing) {
        item { CircularProgressIndicator(Modifier.size(24.dp)) }
    } else {
        items(plan.changes, key = { it.path }) { change -> ChangeRow(change) }
    }
    item {
        Button(
            onClick = { onEvent(ThemeBuilderUiEvent.ConfirmApply) },
            enabled = !plan.computing && !busy && targets.any { it.selected },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringRes(R.string.themebuilder_confirm, plan.changes.size)) }
    }
}

@Composable
private fun TargetRow(t: TargetItem, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = t.selected, onCheckedChange = { onToggle() })
        Column {
            Text(
                stringRes(if (t.isCompose) R.string.themebuilder_target_compose else R.string.themebuilder_target_views, t.module),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(t.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun noteText(note: NoteItem): String = when (note) {
    is NoteItem.SchemesNotFound -> stringRes(R.string.themebuilder_note_schemes, note.path)
    is NoteItem.DynamicColorOff -> stringRes(R.string.themebuilder_note_dynamic, note.path)
    is NoteItem.StyleMissing -> stringRes(R.string.themebuilder_note_style, note.style)
    is NoteItem.LimitedFamily -> stringRes(R.string.themebuilder_note_family, note.style)
}

@Composable
private fun ChangeRow(change: ChangeItem) {
    var expanded by rememberSaveable(change.path) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { expanded = !expanded }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(change.path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(
                if (change.isNew) stringRes(R.string.themebuilder_file_new) else stringRes(R.string.themebuilder_file_changed, change.added, change.removed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded && change.preview.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                change.preview,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
