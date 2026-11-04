package com.codeforge.feature.assetstudio

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.codeforge.feature.assetstudio.logic.IconShape

/** Einstiegsaktionen (Schnellaktionen im Drawer-Panel). */
enum class StudioAction { STUDIO, DRAWABLE, ICON, IMPORT, M3 }

/** Modus: Symbol oder Drawable. */
enum class StudioMode { ICON, DRAWABLE }

/** Quelle des Motivs. Identitäts-Gleichheit genügt (wird nur ersetzt, nie verändert). */
@Immutable
sealed interface StudioSource {
    /** Anzeigename der Quelle. */
    val label: String

    /** Bildquelle als PNG. */
    class Raster(override val label: String, val png: ByteArray, val image: ImageBitmap) : StudioSource

    /** [originalXml] ist gesetzt, wenn der Vektor aus einer XML-Datei kam (verlustfreier Export ohne Einfärben). */
    class Vector(override val label: String, val vector: ImageVector, val originalXml: String?) : StudioSource
}

/** Zustand des Asset-Studios. */
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
    /** Ob der Name ein gültiger Ressourcenname ist. */
    val nameValid: Boolean get() = com.codeforge.feature.assetstudio.logic.IconNames.isValidResourceName(name)
}

/** Aktionen im Asset-Studio. */
sealed interface AssetStudioIntent {
    /** Modus wählen. */
    data class SetMode(val mode: StudioMode) : AssetStudioIntent
    /** Namen setzen. */
    data class SetName(val name: String) : AssetStudioIntent
    /** Form wählen. */
    data class SetShape(val shape: IconShape) : AssetStudioIntent
    /** Hintergrundfarbe setzen. */
    data class SetBackground(val argb: Int) : AssetStudioIntent
    /** Einfärbung setzen (`null` = keine). */
    data class SetTint(val argb: Int?) : AssetStudioIntent
    /** Skalierung setzen. */
    data class SetScale(val scale: Float) : AssetStudioIntent
    /** Bild gewählt. */
    data class ImageChosen(val uriString: String) : AssetStudioIntent
    /** Vektor-XML gewählt. */
    data class XmlChosen(val uriString: String) : AssetStudioIntent
    /** Material-Symbol gewählt. */
    data class M3Chosen(val className: String) : AssetStudioIntent
    /** Symbolauswahl ein-/ausblenden. */
    data class ShowM3Picker(val show: Boolean) : AssetStudioIntent
    /** Dateien erzeugen. */
    data object Generate : AssetStudioIntent
}

/** Einmalige Effekte. */
sealed interface AssetStudioEffect {
    /** Meldung anzeigen. */
    data class Message(val text: String) : AssetStudioEffect
}
