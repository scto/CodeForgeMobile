/**
 * Modul: :core:designsystem
 * @author Thomas Schmid
 */
package com.codeforge.core.designsystem

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Fassungen der Pixelfigur Basti: erwachsen (heute) oder als Welpe (Foto von 2019). */
enum class BastiVariant(@DrawableRes internal val sheetRes: Int) {
    /** Basti heute: sitzend, helles Fell, schwarz-blaues Geschirr, wedelnde Rute. */
    ADULT(R.drawable.basti_adult_sheet),

    /** Basti als Welpe (2019): Kopf-Büste mit Zunge. */
    PUPPY(R.drawable.basti_puppy_sheet),
}

/**
 * Zeichnet die animierte Pixelfigur Basti aus dem Sprite-Sheet (Nearest-Neighbor, die Pixel bleiben scharf).
 *
 * Die Animation folgt [BastiAnimation.TIMELINE]. Sie steht still (ruhiger Frame), wenn [animated] `false` ist oder
 * Animationen in den Systemeinstellungen abgeschaltet sind. Gezeichnet wird in der Draw-Phase, die Komposition
 * wird pro Frame nicht neu ausgeführt.
 *
 * @param modifier Modifier.
 * @param variant Fassung der Figur.
 * @param size Kantenlänge der quadratischen Darstellung.
 * @param animated ob die Idle-Animation laufen soll.
 * @param contentDescription Beschreibung für Bedienungshilfen; `null` = rein dekorativ.
 */
@Composable
fun BastiSprite(
    modifier: Modifier = Modifier,
    variant: BastiVariant = BastiVariant.ADULT,
    size: Dp = 96.dp,
    animated: Boolean = true,
    contentDescription: String? = null,
) {
    val sheet: ImageBitmap = ImageBitmap.imageResource(variant.sheetRes)
    val context = LocalContext.current
    val animate = animated && remember(context) { animationsEnabled(context) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(animate) {
        elapsedMs = 0L
        if (animate) {
            val start = androidx.compose.runtime.withFrameMillis { it }
            while (true) androidx.compose.runtime.withFrameMillis { elapsedMs = it - start }
        }
    }

    val description = contentDescription
    val semanticsModifier = if (description != null) {
        Modifier.semantics {
            this.contentDescription = description
            role = Role.Image
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.size(size).then(semanticsModifier)) {
        val frame = BastiAnimation.frameAt(elapsedMs)
        val edge = this.size.minDimension.roundToInt()
        drawImage(
            image = sheet,
            srcOffset = IntOffset(frame * BastiAnimation.FRAME_SIZE_PX, 0),
            srcSize = IntSize(BastiAnimation.FRAME_SIZE_PX, BastiAnimation.FRAME_SIZE_PX),
            dstSize = IntSize(edge, edge),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun animationsEnabled(context: android.content.Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}.getOrDefault(true)
