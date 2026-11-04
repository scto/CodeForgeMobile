package com.codeforge.feature.assetstudio

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.assetstudio.logic.AssetFilePlanner
import com.codeforge.feature.assetstudio.logic.ForegroundSource
import com.codeforge.feature.assetstudio.logic.IconNames
import com.codeforge.feature.assetstudio.logic.IconRenderer
import com.codeforge.feature.assetstudio.logic.M3IconCatalog
import com.codeforge.feature.assetstudio.logic.PlannedFile
import com.codeforge.feature.assetstudio.logic.VectorXmlReader
import com.codeforge.feature.assetstudio.logic.VectorXmlWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/** ViewModel des Asset-Studios. */
@HiltViewModel
class AssetStudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val rootPath: String = savedState.get<String>("rootPath").orEmpty()
    private val action: StudioAction =
        savedState.get<String>("action")?.let { runCatching { StudioAction.valueOf(it) }.getOrNull() } ?: StudioAction.STUDIO

    private val _state = MutableStateFlow(
        AssetStudioUiState(
            mode = if (action == StudioAction.DRAWABLE) StudioMode.DRAWABLE else StudioMode.ICON,
            showM3Picker = action == StudioAction.M3,
        )
    )
    /** UI-Zustand. */
    val state: StateFlow<AssetStudioUiState> = _state.asStateFlow()

    private val _effects = Channel<AssetStudioEffect>(Channel.BUFFERED)
    /** Einmalige Effekte. */
    val effects = _effects.receiveAsFlow()

    /** Name wurde vom Nutzer geändert → Vorschläge (M3-Auswahl) überschreiben ihn nicht mehr. */
    private var nameEdited = false

    /** Einmalige Start-Aktion für Import (Bildauswahl) – von der UI abgefragt. */
    val launchPickerOnStart: Boolean = action == StudioAction.IMPORT

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onIntent(intent: AssetStudioIntent) {
        when (intent) {
            is AssetStudioIntent.SetMode -> _state.update { it.copy(mode = intent.mode) }
            is AssetStudioIntent.SetName -> { nameEdited = true; _state.update { it.copy(name = intent.name.trim()) } }
            is AssetStudioIntent.SetShape -> _state.update { it.copy(shape = intent.shape) }
            is AssetStudioIntent.SetBackground -> _state.update { it.copy(backgroundArgb = intent.argb) }
            is AssetStudioIntent.SetTint -> _state.update { it.copy(tintArgb = intent.argb) }
            is AssetStudioIntent.SetScale -> _state.update { it.copy(scale = intent.scale.coerceIn(0.3f, 1f)) }
            is AssetStudioIntent.ShowM3Picker -> _state.update { it.copy(showM3Picker = intent.show) }
            is AssetStudioIntent.ImageChosen -> loadImage(intent.uriString)
            is AssetStudioIntent.XmlChosen -> loadXml(intent.uriString)
            is AssetStudioIntent.M3Chosen -> chooseM3(intent.className)
            AssetStudioIntent.Generate -> generate()
        }
    }

    private fun message(text: String) { _effects.trySend(AssetStudioEffect.Message(text)) }

    private fun loadImage(uriString: String) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("empty")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: error("decode")
                    val png = ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    StudioSource.Raster(uri.lastPathSegment.orEmpty().substringAfterLast('/'), png, bmp.asImageBitmap())
                }
            }
            source.onSuccess { s -> _state.update { it.copy(source = s) } }
                .onFailure { message(Res.string(R.string.assetstudio_error, it.message ?: "image")) }
        }
    }

    private fun loadXml(uriString: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val xml = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("empty")
                    val vector = VectorXmlReader.parse(xml) ?: error(Res.string(R.string.assetstudio_xml_unsupported))
                    StudioSource.Vector(uri.lastPathSegment.orEmpty().substringAfterLast('/'), vector, xml)
                }
            }
            result.onSuccess { s -> _state.update { it.copy(source = s, tintArgb = null) } }
                .onFailure { message(Res.string(R.string.assetstudio_error, it.message ?: "xml")) }
        }
    }

    private fun chooseM3(className: String) {
        val vector = M3IconCatalog.load(className)
        if (vector == null) {
            message(Res.string(R.string.assetstudio_error, className))
            return
        }
        _state.update {
            val tint = if (it.mode == StudioMode.ICON) Color.White else Color.Black
            it.copy(
                source = StudioSource.Vector(IconNames.displayName(className), vector, originalXml = null),
                tintArgb = tint.toArgb(),
                name = if (nameEdited) it.name else IconNames.suggestResourceName(className),
                showM3Picker = false,
            )
        }
    }

    private fun Color.toArgb(): Int =
        ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or
            ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()

    private fun generate() {
        val s = _state.value
        val source = s.source
        when {
            source == null -> return message(Res.string(R.string.assetstudio_no_source))
            !s.nameValid -> return message(Res.string(R.string.assetstudio_name_invalid))
        }
        source!!
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resDir = AssetFilePlanner.findResDir(File(rootPath)) ?: error(Res.string(R.string.assetstudio_no_res))
                    val foreground: ForegroundSource = when (source) {
                        is StudioSource.Raster -> ForegroundSource.Png(source.png)
                        is StudioSource.Vector -> ForegroundSource.VectorXml(
                            if (s.tintArgb == null && source.originalXml != null) source.originalXml
                            else VectorXmlWriter.toXml(source.vector, s.tintArgb?.let { Color(it) })
                        )
                    }
                    val files: List<PlannedFile> = when (s.mode) {
                        StudioMode.DRAWABLE -> AssetFilePlanner.planDrawable(s.name, foreground)
                        StudioMode.ICON -> {
                            val motif = when (source) {
                                is StudioSource.Raster -> IconRenderer.Motif.Raster(source.image)
                                is StudioSource.Vector -> IconRenderer.Motif.Vector(source.vector, s.tintArgb?.let { Color(it) })
                            }
                            AssetFilePlanner.planAdaptiveIcon(s.name, foreground, s.backgroundArgb, s.scale) +
                                IconRenderer.planLegacyPngs(s.name, motif, s.shape, s.backgroundArgb, s.scale)
                        }
                    }
                    AssetFilePlanner.write(resDir, files).size to resDir.path
                }
            }
            _state.update { it.copy(isWorking = false) }
            result.onSuccess { (count, dir) -> message(Res.string(R.string.assetstudio_done, count, dir)) }
                .onFailure { message(Res.string(R.string.assetstudio_error, it.message ?: "error")) }
        }
    }
}
