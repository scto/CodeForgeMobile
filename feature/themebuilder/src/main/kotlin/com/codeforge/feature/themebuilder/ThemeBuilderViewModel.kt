// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.dispatchers.IoDispatcher
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.model.PluginInstallEvent
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.libs.theme_kit.ColorHex
import com.codeforge.libs.theme_kit.FileChange
import com.codeforge.libs.theme_kit.ProjectThemeScanner
import com.codeforge.libs.theme_kit.ProjectThemeWriter
import com.codeforge.libs.theme_kit.SeedExtractor
import com.codeforge.libs.theme_kit.ThemeEngine
import com.codeforge.libs.theme_kit.ThemeNote
import com.codeforge.libs.theme_kit.ThemePlan
import com.codeforge.libs.theme_kit.ThemePluginExporter
import com.codeforge.libs.theme_kit.ThemeSpec
import com.codeforge.libs.theme_kit.ThemeTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel des Theme-Builders: Theme aus einer Grundfarbe gestalten, live ansehen und ins Projekt übernehmen
 * (Compose `ui/theme`, XML `themes.xml`) oder als App-Farbschema installieren.
 */
@HiltViewModel
class ThemeBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginRepository: PluginRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val root: File? = savedStateHandle.get<String>(ARG_ROOT)?.takeIf { it.isNotBlank() }?.let(::File)
    private val writer = root?.let(::ProjectThemeWriter)
    private var scannedTargets: List<ThemeTarget> = emptyList()
    private var currentPlan: ThemePlan? = null
    private var planJob: Job? = null

    private val _uiState = MutableStateFlow(ThemeBuilderUiState(projectRoot = root?.path, projectName = root?.name.orEmpty()))

    /** UI-Zustand. */
    val uiState: StateFlow<ThemeBuilderUiState> = _uiState.asStateFlow()

    private val effects = Channel<ThemeBuilderEffect>(Channel.BUFFERED)

    /** Einmalige Rückmeldungen. */
    val effect: Flow<ThemeBuilderEffect> = effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(io) { writer?.loadSpec() }
            if (saved != null) {
                setSpec(saved)
                effects.send(ThemeBuilderEffect.LoadedFromProject)
            }
        }
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: ThemeBuilderUiEvent) {
        when (event) {
            is ThemeBuilderUiEvent.SeedTextChanged -> {
                _uiState.update { it.copy(seedText = event.text) }
                ColorHex.parse(event.text)?.let { setSpec(_uiState.value.spec.copy(seed = ColorHex.format(it)), keepText = true) }
            }
            is ThemeBuilderUiEvent.SeedPicked -> ColorHex.parse(event.hex)?.let { setSpec(_uiState.value.spec.copy(seed = ColorHex.format(it))) }
            is ThemeBuilderUiEvent.StyleSelected -> setSpec(_uiState.value.spec.copy(style = event.style))
            is ThemeBuilderUiEvent.ContrastSelected -> setSpec(_uiState.value.spec.copy(contrast = event.contrast))
            is ThemeBuilderUiEvent.OverrideChanged -> setOverride(event.slot, event.hex)
            is ThemeBuilderUiEvent.PreviewModeChanged -> _uiState.update { it.copy(previewDark = event.dark) }
            is ThemeBuilderUiEvent.ImagePixels -> extractSeeds(event.pixels)
            ThemeBuilderUiEvent.Reset -> viewModelScope.launch {
                setSpec(withContext(io) { writer?.loadSpec() } ?: ThemeSpec.CODEFORGE)
                _uiState.update { it.copy(imageSeeds = emptyList()) }
            }
            ThemeBuilderUiEvent.OpenPlan -> openPlan()
            is ThemeBuilderUiEvent.TargetToggled -> {
                _uiState.update { s -> s.copy(targets = s.targets.map { if (it.id == event.id) it.copy(selected = !it.selected) else it }) }
                recomputePlan()
            }
            ThemeBuilderUiEvent.ConfirmApply -> confirmApply()
            ThemeBuilderUiEvent.DismissPlan -> _uiState.update { it.copy(plan = null) }
            is ThemeBuilderUiEvent.InstallAsAppTheme -> install(event.name)
        }
    }

    private fun setOverride(slot: OverrideSlot, hex: String?) {
        val value = hex?.let(ColorHex::parse)?.let(ColorHex::format)
        if (hex != null && value == null) return
        val s = _uiState.value.spec
        setSpec(
            when (slot) {
                OverrideSlot.SECONDARY -> s.copy(secondary = value)
                OverrideSlot.TERTIARY -> s.copy(tertiary = value)
                OverrideSlot.NEUTRAL -> s.copy(neutral = value)
                OverrideSlot.ERROR -> s.copy(error = value)
            },
        )
    }

    private fun setSpec(spec: ThemeSpec, keepText: Boolean = false) {
        _uiState.update {
            it.copy(
                spec = spec,
                seedText = if (keepText) it.seedText else spec.seed,
                light = ThemeEngine.roles(spec, isDark = false),
                dark = ThemeEngine.roles(spec, isDark = true),
            )
        }
    }

    private fun extractSeeds(pixels: IntArray) {
        viewModelScope.launch {
            val seeds = withContext(io) { SeedExtractor.extract(pixels) }
            _uiState.update { it.copy(imageSeeds = seeds) }
            seeds.firstOrNull()?.let { setSpec(_uiState.value.spec.copy(seed = ColorHex.format(it))) }
        }
    }

    private fun openPlan() {
        val projectRoot = root ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true, plan = PlanUi(emptyList(), emptyList(), computing = true)) }
            scannedTargets = runCatching { withContext(io) { ProjectThemeScanner.scan(projectRoot) } }
                .getOrElse {
                    effects.send(ThemeBuilderEffect.Failed(it.message.orEmpty()))
                    emptyList()
                }
            _uiState.update { s ->
                s.copy(
                    scanning = false,
                    targets = scannedTargets.map { t -> t.toItem(projectRoot, selected = t.module == scannedTargets.first().module) },
                )
            }
            recomputePlan()
        }
    }

    private fun recomputePlan() {
        val w = writer ?: return
        val projectRoot = root ?: return
        val selectedIds = _uiState.value.targets.filter { it.selected }.map { it.id }.toSet()
        val chosen = scannedTargets.filter { it.idIn(projectRoot) in selectedIds }
        val spec = _uiState.value.spec
        planJob?.cancel()
        planJob = viewModelScope.launch {
            _uiState.update { it.copy(plan = (it.plan ?: PlanUi(emptyList(), emptyList())).copy(computing = true)) }
            val plan = runCatching { withContext(io) { w.plan(spec, chosen) } }.getOrElse {
                effects.send(ThemeBuilderEffect.Failed(it.message.orEmpty()))
                null
            }
            currentPlan = plan
            _uiState.update { s ->
                s.copy(plan = PlanUi(plan?.changes.orEmpty().map { it.toItem(projectRoot) }, plan?.notes.orEmpty().map { it.toItem(projectRoot) }))
            }
        }
    }

    private fun confirmApply() {
        val w = writer ?: return
        val plan = currentPlan ?: return
        if (_uiState.value.plan?.computing == true) return
        viewModelScope.launch {
            if (plan.changes.isEmpty()) {
                _uiState.update { it.copy(plan = null) }
                effects.send(ThemeBuilderEffect.NothingToApply)
                return@launch
            }
            _uiState.update { it.copy(busy = true) }
            runCatching { withContext(io) { w.apply(plan) } }
                .onSuccess { backup ->
                    _uiState.update { it.copy(busy = false, plan = null) }
                    effects.send(ThemeBuilderEffect.Applied(plan.changes.size, backup?.relativeTo(root!!)?.path))
                }
                .onFailure { e ->
                    _uiState.update { it.copy(busy = false) }
                    effects.send(ThemeBuilderEffect.Failed(e.message.orEmpty()))
                }
        }
    }

    private fun install(name: String) {
        val clean = name.trim().ifEmpty { return }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val spec = _uiState.value.spec.copy(name = clean)
            val archive = File(context.cacheDir, "theme-builder/${ThemePluginExporter.pluginIdFor(clean)}.zip")
            val pluginId = withContext(io) {
                ThemePluginExporter.export(spec, clean, archive, version = "1.0.${System.currentTimeMillis() / MILLIS_PER_SECOND}")
            }
            val result = pluginRepository.installFromFile(archive.path, declarativeOnly = true)
                .first { it !is PluginInstallEvent.Progress }
            archive.delete()
            _uiState.update { it.copy(busy = false) }
            if (result is PluginInstallEvent.Failed) {
                effects.send(ThemeBuilderEffect.Failed(result.message))
            } else {
                // Variante egal: die App wählt hell/dunkel passend zum Modus (findExtensionTheme).
                val wantDark = settingsRepository.appSettings.first().theme.mode == ThemeMode.DARK
                val key = ExtensionColorTheme.keyOf(pluginId, ThemePluginExporter.themeIdFor(pluginId, wantDark))
                settingsRepository.updateTheme { it.toBuilder().setColorSchemeId(key).setUseDynamicColor(false).build() }
                effects.send(ThemeBuilderEffect.Installed(clean))
            }
        }
    }

    private fun ThemeTarget.idIn(projectRoot: File): String = when (this) {
        is ThemeTarget.Compose -> "compose:" + themeDir.relativeTo(projectRoot).path
        is ThemeTarget.Views -> "views:" + resDir.relativeTo(projectRoot).path
    }

    private fun ThemeTarget.toItem(projectRoot: File, selected: Boolean): TargetItem = when (this) {
        is ThemeTarget.Compose -> TargetItem(idIn(projectRoot), true, module, themeFunction ?: packageName, selected)
        is ThemeTarget.Views -> TargetItem(idIn(projectRoot), false, module, styleName, selected)
    }

    private fun FileChange.toItem(projectRoot: File): ChangeItem {
        val beforeLines = before?.lines().orEmpty()
        val afterLines = after.lines()
        val beforeSet = beforeLines.toSet()
        val afterSet = afterLines.toSet()
        val removed = beforeLines.count { it !in afterSet }
        val addedLines = afterLines.filter { it !in beforeSet }
        val preview = addedLines.take(PREVIEW_LINES).joinToString("\n")
        return ChangeItem(file.relativeTo(projectRoot).invariantSeparatorsPath, isNew, addedLines.size, removed, preview)
    }

    private fun ThemeNote.toItem(projectRoot: File): NoteItem = when (this) {
        is ThemeNote.ComposeSchemesNotFound -> NoteItem.SchemesNotFound(themeFile.relativeTo(projectRoot).invariantSeparatorsPath)
        is ThemeNote.DynamicColorDisabled -> NoteItem.DynamicColorOff(themeFile.relativeTo(projectRoot).invariantSeparatorsPath)
        is ThemeNote.StyleNotFound -> NoteItem.StyleMissing(styleName)
        is ThemeNote.LimitedXmlFamily -> NoteItem.LimitedFamily(styleName)
    }

    companion object {
        /** Navigationsargument: Projektordner (leer = ohne Projekt). */
        const val ARG_ROOT = "rootPath"
        private const val PREVIEW_LINES = 40
        private const val MILLIS_PER_SECOND = 1000
    }
}
