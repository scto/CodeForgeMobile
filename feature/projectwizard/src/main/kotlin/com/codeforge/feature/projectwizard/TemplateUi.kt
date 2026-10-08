// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.codeforge.core.domain.model.ProjectTemplateKind
import com.codeforge.core.resources.R as CoreR

/** UI-Darstellung einer Vorlage (Titel, Beschreibung, Vorschaubild). */
internal data class TemplateUi(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val image: Int,
)

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
}
