/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Zuordnung von Palette-Schlüsseln, Kategorien und Geräten zu Ressourcen und Icons.
 */
package com.codeforge.feature.layoutdesigner.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.codeforge.core.resources.R
import com.codeforge.feature.layoutdesigner.DevicePreset
import com.codeforge.feature.layoutdesigner.DesignerTab
import com.codeforge.feature.layoutdesigner.model.WidgetCategory

@StringRes
internal fun paletteLabel(key: String): Int = when (key) {
    "linear_vertical" -> R.string.layout_w_linear_vertical
    "linear_horizontal" -> R.string.layout_w_linear_horizontal
    "frame" -> R.string.layout_w_frame
    "scroll" -> R.string.layout_w_scroll
    "constraint" -> R.string.layout_w_constraint
    "text" -> R.string.layout_w_text
    "button" -> R.string.layout_w_button
    "edit" -> R.string.layout_w_edit
    "checkbox" -> R.string.layout_w_checkbox
    "switch" -> R.string.layout_w_switch
    "image" -> R.string.layout_w_image
    "progress" -> R.string.layout_w_progress
    "view" -> R.string.layout_w_view
    else -> R.string.layout_w_space
}

internal fun paletteIcon(key: String): ImageVector = when (key) {
    "linear_vertical" -> Icons.Filled.ViewAgenda
    "linear_horizontal" -> Icons.Filled.ViewColumn
    "frame" -> Icons.Filled.Layers
    "scroll" -> Icons.Filled.UnfoldMore
    "constraint" -> Icons.Filled.GridView
    "text" -> Icons.Filled.TextFields
    "button" -> Icons.Filled.TouchApp
    "edit" -> Icons.Filled.Edit
    "checkbox" -> Icons.Filled.CheckBox
    "switch" -> Icons.Filled.ToggleOn
    "image" -> Icons.Filled.Image
    "progress" -> Icons.Filled.HourglassEmpty
    "view" -> Icons.Filled.HorizontalRule
    "space" -> Icons.Filled.SpaceBar
    else -> Icons.Filled.Widgets
}

@StringRes
internal fun categoryLabel(category: WidgetCategory): Int = when (category) {
    WidgetCategory.LAYOUT -> R.string.layout_cat_layout
    WidgetCategory.TEXT -> R.string.layout_cat_text
    WidgetCategory.BUTTON -> R.string.layout_cat_button
    WidgetCategory.INPUT -> R.string.layout_cat_input
    WidgetCategory.MEDIA -> R.string.layout_cat_media
    WidgetCategory.OTHER -> R.string.layout_cat_other
}

@StringRes
internal fun deviceLabel(device: DevicePreset): Int = when (device) {
    DevicePreset.PHONE -> R.string.layout_device_phone
    DevicePreset.PHONE_LARGE -> R.string.layout_device_phone_large
    DevicePreset.TABLET_7 -> R.string.layout_device_tablet7
    DevicePreset.TABLET_10 -> R.string.layout_device_tablet10
}

@StringRes
internal fun tabLabel(tab: DesignerTab): Int = when (tab) {
    DesignerTab.PREVIEW -> R.string.layout_tab_preview
    DesignerTab.TREE -> R.string.layout_tab_tree
    DesignerTab.PROPERTIES -> R.string.layout_tab_props
    DesignerTab.XML -> R.string.layout_tab_xml
}
