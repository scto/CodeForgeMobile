package com.codeforge.feature.assetstudio

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.codeforge.feature.assetstudio.logic.IconShape

/** Einstiegsaktionen (Schnellaktionen im Drawer-Panel). */
enum class StudioAction { STUDIO, DRAWABLE, ICON, IMPORT, M3 }

enum class StudioMode { ICON, DRAWABLE }

/** Quelle des Motivs. Identitäts-Gleichheit genügt (wird nur ersetzt, nie verändert). */
@Immutable
sealed interface StudioSource {
    val label: String

    class Raster(override val label: String, val png: ByteArray, val image: ImageBitmap) : StudioSource

    /** [originalXml] ist gesetzt, wenn der Vektor aus einer XML-Datei kam (verlustfreier Export ohne Einfärben). */
    class Vector(override val label: String, val vector: ImageVector, val originalXml: String?) : StudioSource
}

@Immutable
data class AssetStudioUiState(
    val mode: StudioMode = StudioMode.ICON,
    val source: StudioSource? = null,
    val name: String = "ic_my_icon",
    val shape: IconShape = IconShape.CIRCLE,
    val backgroundArgb: Int = 0xFF6200EE.toInt(),
    /** `null` = Originalfarben des Vektors. */
    val tintArgb: Int? = null,
    val scale: Float = 0.6f,
    val showM3Picker: Boolean = false,
    val isWorking: Boolean = false,
) {
    val nameValid: Boolean get() = com.codeforge.feature.assetstudio.logic.IconNames.isValidResourceName(name)
}

sealed interface AssetStudioIntent {
    data class SetMode(val mode: StudioMode) : AssetStudioIntent
    data class SetName(val name: String) : AssetStudioIntent
    data class SetShape(val shape: IconShape) : AssetStudioIntent
    data class SetBackground(val argb: Int) : AssetStudioIntent
    data class SetTint(val argb: Int?) : AssetStudioIntent
    data class SetScale(val scale: Float) : AssetStudioIntent
    data class ImageChosen(val uriString: String) : AssetStudioIntent
    data class XmlChosen(val uriString: String) : AssetStudioIntent
    data class M3Chosen(val className: String) : AssetStudioIntent
    data class ShowM3Picker(val show: Boolean) : AssetStudioIntent
    data object Generate : AssetStudioIntent
}

sealed interface AssetStudioEffect {
    data class Message(val text: String) : AssetStudioEffect
}
