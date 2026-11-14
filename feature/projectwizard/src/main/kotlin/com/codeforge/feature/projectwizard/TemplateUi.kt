// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.ProjectTemplateKind
import com.codeforge.core.resources.stringRes
import com.codeforge.core.resources.R as CoreR

/** UI-Darstellung einer eingebauten Vorlage (Titel, Beschreibung, Vorschaubild). */
internal data class TemplateUi(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val image: Int,
)

/** Anzeigedaten (Symbol, Texte) zur Vorlagenart. */
internal fun ProjectTemplateKind.ui(): TemplateUi = when (this) {
    ProjectTemplateKind.NO_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_no_activity, CoreR.string.pw_tpl_no_activity_desc, R.drawable.pw_no_activity)
    ProjectTemplateKind.EMPTY_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_empty, CoreR.string.pw_tpl_empty_desc, R.drawable.pw_empty_activity)
    ProjectTemplateKind.CPP_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_cpp, CoreR.string.pw_tpl_cpp_desc, R.drawable.pw_cpp_activity)
    ProjectTemplateKind.BASIC_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_basic, CoreR.string.pw_tpl_basic_desc, R.drawable.pw_basic_activity)
    ProjectTemplateKind.NAV_DRAWER_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_drawer, CoreR.string.pw_tpl_drawer_desc, R.drawable.pw_blank_activity_drawer)
    ProjectTemplateKind.BOTTOM_NAV_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_bottom_nav, CoreR.string.pw_tpl_bottom_nav_desc, R.drawable.pw_bottom_navigation_activity)
    ProjectTemplateKind.TABBED_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_tabbed, CoreR.string.pw_tpl_tabbed_desc, R.drawable.pw_blank_activity_tabs)
    ProjectTemplateKind.NO_ANDROIDX_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_no_androidx, CoreR.string.pw_tpl_no_androidx_desc, R.drawable.pw_empty_noandroidx)
    ProjectTemplateKind.COMPOSE_ACTIVITY -> TemplateUi(CoreR.string.pw_tpl_compose, CoreR.string.pw_tpl_compose_desc, R.drawable.pw_compose_empty_activity)
    // Platzhalter; Texte und Bild einer Plugin-Vorlage kommen aus dem Plugin.
    ProjectTemplateKind.PLUGIN -> TemplateUi(CoreR.string.pw_tpl_plugin, CoreR.string.pw_tpl_plugin, R.drawable.pw_no_activity)
}

/** Titel der Vorlage (Plugin-Text oder Ressource). */
@Composable
internal fun ProjectTemplateDescriptor.title(): String = plugin?.label ?: stringRes(kind.ui().title)

/** Beschreibung der Vorlage (Plugin-Text oder Ressource). */
@Composable
internal fun ProjectTemplateDescriptor.description(): String = plugin?.description ?: stringRes(kind.ui().description)

/** Vorschaubild: Bild aus dem Plugin, sonst das der Vorlagenart. */
@Composable
internal fun TemplateImage(template: ProjectTemplateDescriptor, modifier: Modifier = Modifier) {
    val iconFile = template.plugin?.iconFile
    val bitmap = remember(iconFile) { iconFile?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Image(painterResource(template.kind.ui().image), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}
