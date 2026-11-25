// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.descriptionFor
import com.codeforge.core.domain.model.labelFor

@Composable
private fun uiLanguage(): String = LocalConfiguration.current.locales[0].language

/** Titel der Vorlage in der Sprache der Oberfläche (`l10n` des Plugins), sonst der Standardtext. */
@Composable
internal fun ProjectTemplateDescriptor.title(): String = plugin.labelFor(uiLanguage())

/** Beschreibung der Vorlage in der Sprache der Oberfläche, sonst der Standardtext. */
@Composable
internal fun ProjectTemplateDescriptor.description(): String = plugin.descriptionFor(uiLanguage())

/** Vorschaubild aus dem Plugin; ohne Bild ein neutrales Symbol. */
@Composable
internal fun TemplateImage(template: ProjectTemplateDescriptor, modifier: Modifier = Modifier) {
    val iconFile = template.plugin.iconFile
    val bitmap = remember(iconFile) { iconFile?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}
