/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.model

/** Art des Editors für ein Attribut in der Eigenschaftenliste. */
enum class AttrKind { TEXT, NUMBER, DIMENSION, SIZE, COLOR, ENUM }

data class AttrSpec(
    val name: String,
    val kind: AttrKind,
    val options: List<String> = emptyList(),
)

/** Bekanntes Widget: bestimmt Container-Eigenschaft und die angebotenen Attribute. */
data class WidgetDef(
    val tag: String,
    val isContainer: Boolean,
    val category: WidgetCategory,
    val attrs: List<AttrSpec> = emptyList(),
)

enum class WidgetCategory { LAYOUT, TEXT, BUTTON, INPUT, MEDIA, OTHER }

/** Eintrag der Palette; [key] dient der UI zur Zuordnung des lokalisierten Namens. */
data class PaletteEntry(
    val key: String,
    val tag: String,
    val category: WidgetCategory,
    val defaults: Map<String, String>,
)

object WidgetCatalog {

    private const val WRAP = "wrap_content"
    private const val MATCH = "match_parent"

    private val sizeOptions = listOf(MATCH, WRAP)
    private val gravityOptions = listOf(
        "start", "end", "top", "bottom", "center", "center_horizontal", "center_vertical",
        "top|start", "top|end", "bottom|start", "bottom|end",
    )

    /** Attribute, die jedes View besitzt. */
    val commonAttrs: List<AttrSpec> = listOf(
        AttrSpec("android:id", AttrKind.TEXT),
        AttrSpec("android:layout_width", AttrKind.SIZE, sizeOptions),
        AttrSpec("android:layout_height", AttrKind.SIZE, sizeOptions),
        AttrSpec("android:layout_margin", AttrKind.DIMENSION),
        AttrSpec("android:padding", AttrKind.DIMENSION),
        AttrSpec("android:background", AttrKind.COLOR),
        AttrSpec("android:visibility", AttrKind.ENUM, listOf("visible", "invisible", "gone")),
        AttrSpec("android:contentDescription", AttrKind.TEXT),
    )

    private val textAttrs = listOf(
        AttrSpec("android:text", AttrKind.TEXT),
        AttrSpec("android:textSize", AttrKind.DIMENSION),
        AttrSpec("android:textColor", AttrKind.COLOR),
        AttrSpec("android:textStyle", AttrKind.ENUM, listOf("normal", "bold", "italic", "bold|italic")),
        AttrSpec("android:gravity", AttrKind.ENUM, gravityOptions),
    )

    private val widgets: List<WidgetDef> = listOf(
        WidgetDef(
            "LinearLayout", true, WidgetCategory.LAYOUT,
            listOf(
                AttrSpec("android:orientation", AttrKind.ENUM, listOf("vertical", "horizontal")),
                AttrSpec("android:gravity", AttrKind.ENUM, gravityOptions),
                AttrSpec("android:weightSum", AttrKind.NUMBER),
            ),
        ),
        WidgetDef("FrameLayout", true, WidgetCategory.LAYOUT),
        WidgetDef("ScrollView", true, WidgetCategory.LAYOUT),
        WidgetDef("HorizontalScrollView", true, WidgetCategory.LAYOUT),
        WidgetDef("RelativeLayout", true, WidgetCategory.LAYOUT, listOf(AttrSpec("android:gravity", AttrKind.ENUM, gravityOptions))),
        WidgetDef("androidx.constraintlayout.widget.ConstraintLayout", true, WidgetCategory.LAYOUT),
        WidgetDef("TextView", false, WidgetCategory.TEXT, textAttrs),
        WidgetDef("Button", false, WidgetCategory.BUTTON, textAttrs.take(3)),
        WidgetDef(
            "EditText", false, WidgetCategory.INPUT,
            textAttrs + listOf(
                AttrSpec("android:hint", AttrKind.TEXT),
                AttrSpec(
                    "android:inputType", AttrKind.ENUM,
                    listOf("text", "textPassword", "textEmailAddress", "number", "phone", "textMultiLine"),
                ),
            ),
        ),
        WidgetDef("CheckBox", false, WidgetCategory.INPUT, textAttrs.take(3) + AttrSpec("android:checked", AttrKind.ENUM, listOf("true", "false"))),
        WidgetDef("RadioButton", false, WidgetCategory.INPUT, textAttrs.take(3) + AttrSpec("android:checked", AttrKind.ENUM, listOf("true", "false"))),
        WidgetDef("Switch", false, WidgetCategory.INPUT, textAttrs.take(3) + AttrSpec("android:checked", AttrKind.ENUM, listOf("true", "false"))),
        WidgetDef(
            "ImageView", false, WidgetCategory.MEDIA,
            listOf(
                AttrSpec("android:src", AttrKind.TEXT),
                AttrSpec("android:scaleType", AttrKind.ENUM, listOf("fitCenter", "centerCrop", "centerInside", "fitXY", "center")),
            ),
        ),
        WidgetDef("ImageButton", false, WidgetCategory.MEDIA, listOf(AttrSpec("android:src", AttrKind.TEXT))),
        WidgetDef("ProgressBar", false, WidgetCategory.OTHER),
        WidgetDef("View", false, WidgetCategory.OTHER),
        WidgetDef("Space", false, WidgetCategory.OTHER),
    )

    private val byTag: Map<String, WidgetDef> = widgets.associateBy { it.tag }

    fun find(tag: String): WidgetDef? = byTag[tag]

    /** Attribute für die Eigenschaftenliste: allgemeine + widget-spezifische + vom Elternteil abhängige. */
    fun attrsFor(tag: String, parentTag: String?): List<AttrSpec> = buildList {
        addAll(commonAttrs)
        byTag[tag]?.attrs?.let { addAll(it) }
        when (parentTag) {
            "LinearLayout" -> {
                add(AttrSpec("android:layout_weight", AttrKind.NUMBER))
                add(AttrSpec("android:layout_gravity", AttrKind.ENUM, gravityOptions))
            }
            "FrameLayout" -> add(AttrSpec("android:layout_gravity", AttrKind.ENUM, gravityOptions))
        }
    }.distinctBy { it.name }

    private fun wrapDefaults(vararg more: Pair<String, String>): Map<String, String> = linkedMapOf(
        "android:layout_width" to WRAP,
        "android:layout_height" to WRAP,
    ).apply { putAll(more) }

    private fun fillDefaults(vararg more: Pair<String, String>): Map<String, String> = linkedMapOf(
        "android:layout_width" to MATCH,
        "android:layout_height" to MATCH,
    ).apply { putAll(more) }

    val palette: List<PaletteEntry> = listOf(
        PaletteEntry("linear_vertical", "LinearLayout", WidgetCategory.LAYOUT, fillDefaults("android:orientation" to "vertical")),
        PaletteEntry("linear_horizontal", "LinearLayout", WidgetCategory.LAYOUT, linkedMapOf("android:layout_width" to MATCH, "android:layout_height" to WRAP, "android:orientation" to "horizontal")),
        PaletteEntry("frame", "FrameLayout", WidgetCategory.LAYOUT, fillDefaults()),
        PaletteEntry("scroll", "ScrollView", WidgetCategory.LAYOUT, fillDefaults()),
        PaletteEntry("constraint", "androidx.constraintlayout.widget.ConstraintLayout", WidgetCategory.LAYOUT, fillDefaults()),
        PaletteEntry("text", "TextView", WidgetCategory.TEXT, wrapDefaults("android:text" to "Text")),
        PaletteEntry("button", "Button", WidgetCategory.BUTTON, wrapDefaults("android:text" to "Button")),
        PaletteEntry("edit", "EditText", WidgetCategory.INPUT, linkedMapOf("android:layout_width" to MATCH, "android:layout_height" to WRAP, "android:hint" to "Hint", "android:inputType" to "text")),
        PaletteEntry("checkbox", "CheckBox", WidgetCategory.INPUT, wrapDefaults("android:text" to "CheckBox")),
        PaletteEntry("switch", "Switch", WidgetCategory.INPUT, wrapDefaults("android:text" to "Switch")),
        PaletteEntry("image", "ImageView", WidgetCategory.MEDIA, linkedMapOf("android:layout_width" to "64dp", "android:layout_height" to "64dp")),
        PaletteEntry("progress", "ProgressBar", WidgetCategory.OTHER, wrapDefaults()),
        PaletteEntry("view", "View", WidgetCategory.OTHER, linkedMapOf("android:layout_width" to MATCH, "android:layout_height" to "1dp", "android:background" to "#CCCCCC")),
        PaletteEntry("space", "Space", WidgetCategory.OTHER, linkedMapOf("android:layout_width" to "16dp", "android:layout_height" to "16dp")),
    )
}
