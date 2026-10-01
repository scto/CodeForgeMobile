package com.codeforge.libs.template_engine

data class ProjectTemplate(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val kind: Kind = Kind.EMPTY_ACTIVITY
) {
    enum class Kind {
        NO_ACTIVITY,
        EMPTY_ACTIVITY,
        BASIC_ACTIVITY,
        BOTTOM_NAV_ACTIVITY,
        BOTTOM_NAVIGATION_ACTIVITY,
        NAV_DRAWER_ACTIVITY,
        NAVIGATION_DRAWER_ACTIVITY,
        TABBED_ACTIVITY,
        NO_ANDROIDX_ACTIVITY,
        COMPOSE_ACTIVITY,
        CPP_ACTIVITY
    }
}
