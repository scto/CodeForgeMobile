/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.model

/** Art des Editors für ein Attribut in der Eigenschaftenliste. */
enum class AttrKind {
    TEXT, NUMBER, DIMENSION, SIZE, COLOR, ENUM,
    BOOLEAN, FLAG, REFERENCE, FLOAT, INTEGER, ID
}

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

enum class WidgetCategory { LAYOUT, TEXT, BUTTON, INPUT, MEDIA, OTHER, PROJECT }

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
        WidgetDef("include", false, WidgetCategory.LAYOUT, listOf(AttrSpec("layout", AttrKind.TEXT))),
        WidgetDef("merge", true, WidgetCategory.LAYOUT, listOf(AttrSpec("tools:parentTag", AttrKind.TEXT))),
        WidgetDef("ViewStub", false, WidgetCategory.LAYOUT, listOf(AttrSpec("android:layout", AttrKind.TEXT), AttrSpec("android:inflatedId", AttrKind.TEXT))),
        WidgetDef("fragment", false, WidgetCategory.LAYOUT, listOf(AttrSpec("android:name", AttrKind.TEXT), AttrSpec("tools:layout", AttrKind.TEXT))),
        WidgetDef("androidx.fragment.app.FragmentContainerView", false, WidgetCategory.LAYOUT, listOf(AttrSpec("android:name", AttrKind.TEXT), AttrSpec("tools:layout", AttrKind.TEXT))),

        // Erweiterte Layouts (41.7)
        WidgetDef("androidx.core.widget.NestedScrollView", true, WidgetCategory.LAYOUT),
        WidgetDef("GridLayout", true, WidgetCategory.LAYOUT, listOf(
            AttrSpec("android:rowCount", AttrKind.INTEGER),
            AttrSpec("android:columnCount", AttrKind.INTEGER),
            AttrSpec("android:orientation", AttrKind.ENUM, listOf("horizontal", "vertical")),
        )),
        WidgetDef("TableLayout", true, WidgetCategory.LAYOUT),
        WidgetDef("TableRow", true, WidgetCategory.LAYOUT),
        WidgetDef("RadioGroup", true, WidgetCategory.INPUT, listOf(
            AttrSpec("android:orientation", AttrKind.ENUM, listOf("vertical", "horizontal")),
            AttrSpec("android:checkedButton", AttrKind.ID),
        )),
        WidgetDef("androidx.coordinatorlayout.widget.CoordinatorLayout", true, WidgetCategory.LAYOUT),
        WidgetDef("com.google.android.material.appbar.AppBarLayout", true, WidgetCategory.LAYOUT),
        WidgetDef("com.google.android.material.appbar.CollapsingToolbarLayout", true, WidgetCategory.LAYOUT),
        WidgetDef("androidx.cardview.widget.CardView", true, WidgetCategory.LAYOUT, listOf(
            AttrSpec("app:cardCornerRadius", AttrKind.DIMENSION),
            AttrSpec("app:cardElevation", AttrKind.DIMENSION),
        )),
        WidgetDef("com.google.android.material.card.MaterialCardView", true, WidgetCategory.LAYOUT, listOf(
            AttrSpec("app:cardCornerRadius", AttrKind.DIMENSION),
            AttrSpec("app:cardElevation", AttrKind.DIMENSION),
            AttrSpec("app:strokeWidth", AttrKind.DIMENSION),
            AttrSpec("app:strokeColor", AttrKind.COLOR),
        )),
        WidgetDef("com.google.android.material.chip.ChipGroup", true, WidgetCategory.INPUT),
        WidgetDef("com.google.android.flexbox.FlexboxLayout", true, WidgetCategory.LAYOUT),

        // Erweiterte Widgets (41.7)
        WidgetDef("com.google.android.material.button.MaterialButton", false, WidgetCategory.BUTTON, textAttrs.take(3) + listOf(
            AttrSpec("app:icon", AttrKind.REFERENCE),
            AttrSpec("app:cornerRadius", AttrKind.DIMENSION),
        )),
        WidgetDef("com.google.android.material.textfield.TextInputLayout", true, WidgetCategory.INPUT, listOf(
            AttrSpec("android:hint", AttrKind.TEXT),
            AttrSpec("app:errorEnabled", AttrKind.BOOLEAN, listOf("true", "false")),
            AttrSpec("app:helperText", AttrKind.TEXT),
        )),
        WidgetDef("com.google.android.material.textfield.TextInputEditText", false, WidgetCategory.INPUT, textAttrs),
        WidgetDef("com.google.android.material.chip.Chip", false, WidgetCategory.BUTTON, textAttrs.take(3) + listOf(
            AttrSpec("app:chipIcon", AttrKind.REFERENCE),
        )),
        WidgetDef("com.google.android.material.floatingactionbutton.FloatingActionButton", false, WidgetCategory.BUTTON, listOf(
            AttrSpec("app:srcCompat", AttrKind.REFERENCE),
            AttrSpec("app:fabSize", AttrKind.ENUM, listOf("normal", "mini", "auto")),
        )),
        WidgetDef("com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton", false, WidgetCategory.BUTTON, textAttrs.take(3)),
        WidgetDef("com.google.android.material.appbar.MaterialToolbar", true, WidgetCategory.LAYOUT, listOf(
            AttrSpec("app:title", AttrKind.TEXT),
            AttrSpec("app:subtitle", AttrKind.TEXT),
        )),
        WidgetDef("androidx.appcompat.widget.Toolbar", true, WidgetCategory.LAYOUT, listOf(
            AttrSpec("app:title", AttrKind.TEXT),
        )),
        WidgetDef("com.google.android.material.bottomnavigation.BottomNavigationView", false, WidgetCategory.OTHER, listOf(
            AttrSpec("app:menu", AttrKind.REFERENCE),
        )),
        WidgetDef("com.google.android.material.navigationrail.NavigationRailView", false, WidgetCategory.OTHER, listOf(
            AttrSpec("app:menu", AttrKind.REFERENCE),
        )),
        WidgetDef("com.google.android.material.tabs.TabLayout", false, WidgetCategory.OTHER),
        WidgetDef("androidx.viewpager2.widget.ViewPager2", false, WidgetCategory.LAYOUT),
        WidgetDef("androidx.recyclerview.widget.RecyclerView", false, WidgetCategory.LAYOUT, listOf(
            AttrSpec("tools:listitem", AttrKind.REFERENCE),
            AttrSpec("tools:itemCount", AttrKind.INTEGER),
        )),
        WidgetDef("ListView", false, WidgetCategory.LAYOUT, listOf(
            AttrSpec("tools:listitem", AttrKind.REFERENCE),
        )),
        WidgetDef("GridView", false, WidgetCategory.LAYOUT, listOf(
            AttrSpec("android:numColumns", AttrKind.INTEGER),
        )),
        WidgetDef("Spinner", false, WidgetCategory.INPUT),
        WidgetDef("SeekBar", false, WidgetCategory.INPUT, listOf(
            AttrSpec("android:max", AttrKind.INTEGER),
            AttrSpec("android:progress", AttrKind.INTEGER),
        )),
        WidgetDef("com.google.android.material.slider.Slider", false, WidgetCategory.INPUT, listOf(
            AttrSpec("android:valueFrom", AttrKind.FLOAT),
            AttrSpec("android:valueTo", AttrKind.FLOAT),
            AttrSpec("android:value", AttrKind.FLOAT),
        )),
        WidgetDef("RatingBar", false, WidgetCategory.INPUT, listOf(
            AttrSpec("android:numStars", AttrKind.INTEGER),
            AttrSpec("android:rating", AttrKind.FLOAT),
            AttrSpec("android:stepSize", AttrKind.FLOAT),
        )),
        WidgetDef("ToggleButton", false, WidgetCategory.BUTTON, textAttrs.take(3) + listOf(
            AttrSpec("android:textOn", AttrKind.TEXT),
            AttrSpec("android:textOff", AttrKind.TEXT),
        )),
        WidgetDef("com.google.android.material.materialswitch.MaterialSwitch", false, WidgetCategory.INPUT, textAttrs.take(3)),
        WidgetDef("com.google.android.material.divider.MaterialDivider", false, WidgetCategory.OTHER),
        WidgetDef("com.google.android.material.imageview.ShapeableImageView", false, WidgetCategory.MEDIA, listOf(
            AttrSpec("android:src", AttrKind.REFERENCE),
            AttrSpec("app:shapeAppearanceOverlay", AttrKind.REFERENCE),
        )),
        WidgetDef("com.google.android.material.progressindicator.LinearProgressIndicator", false, WidgetCategory.OTHER, listOf(
            AttrSpec("android:indeterminate", AttrKind.BOOLEAN, listOf("true", "false")),
        )),
        WidgetDef("com.google.android.material.progressindicator.CircularProgressIndicator", false, WidgetCategory.OTHER, listOf(
            AttrSpec("android:indeterminate", AttrKind.BOOLEAN, listOf("true", "false")),
        )),
        WidgetDef("WebView", false, WidgetCategory.OTHER),
        WidgetDef("VideoView", false, WidgetCategory.MEDIA),
        WidgetDef("SurfaceView", false, WidgetCategory.MEDIA),
        WidgetDef("TextureView", false, WidgetCategory.MEDIA),
        WidgetDef("AutoCompleteTextView", false, WidgetCategory.INPUT, textAttrs),
        WidgetDef("androidx.compose.ui.platform.ComposeView", false, WidgetCategory.OTHER),
    )

    private val byTag: Map<String, WidgetDef> = widgets.associateBy { it.tag }

    fun find(tag: String): WidgetDef? = byTag[tag]

    /** Attribute für die Eigenschaftenliste: allgemeine + widget-spezifische + vom Elternteil abhängige. */
    fun attrsFor(tag: String, parentTag: String?): List<AttrSpec> = buildList {
        addAll(commonAttrs)
        byTag[tag]?.attrs?.let { addAll(it) }
        if (tag !in byTag && tag.contains('.')) {
            val simple = tag.substringAfterLast('.')
            byTag[simple]?.attrs?.let { addAll(it) }
        }
        when {
            parentTag == "LinearLayout" -> {
                add(AttrSpec("android:layout_weight", AttrKind.NUMBER))
                add(AttrSpec("android:layout_gravity", AttrKind.ENUM, gravityOptions))
            }
            parentTag == "FrameLayout" -> {
                add(AttrSpec("android:layout_gravity", AttrKind.ENUM, gravityOptions))
            }
            parentTag == "ConstraintLayout" || parentTag?.endsWith("ConstraintLayout") == true -> {
                add(AttrSpec("app:layout_constraintTop_toTopOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintTop_toBottomOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintBottom_toTopOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintBottom_toBottomOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintStart_toStartOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintStart_toEndOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintEnd_toStartOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintEnd_toEndOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintBaseline_toBaselineOf", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintHorizontal_bias", AttrKind.NUMBER))
                add(AttrSpec("app:layout_constraintVertical_bias", AttrKind.NUMBER))
                add(AttrSpec("app:layout_constraintDimensionRatio", AttrKind.TEXT))
                add(AttrSpec("app:layout_constraintHorizontal_chainStyle", AttrKind.ENUM, listOf("spread", "spread_inside", "packed")))
                add(AttrSpec("app:layout_constraintVertical_chainStyle", AttrKind.ENUM, listOf("spread", "spread_inside", "packed")))
                add(AttrSpec("app:layout_constraintHorizontal_weight", AttrKind.NUMBER))
                add(AttrSpec("app:layout_constraintVertical_weight", AttrKind.NUMBER))
                add(AttrSpec("app:layout_goneMarginStart", AttrKind.DIMENSION))
                add(AttrSpec("app:layout_goneMarginEnd", AttrKind.DIMENSION))
                add(AttrSpec("app:layout_goneMarginTop", AttrKind.DIMENSION))
                add(AttrSpec("app:layout_goneMarginBottom", AttrKind.DIMENSION))
            }
            parentTag == "RelativeLayout" -> {
                add(AttrSpec("android:layout_alignParentStart", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_alignParentEnd", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_alignParentTop", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_alignParentBottom", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_centerInParent", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_centerHorizontal", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_centerVertical", AttrKind.ENUM, listOf("true", "false")))
                add(AttrSpec("android:layout_below", AttrKind.TEXT))
                add(AttrSpec("android:layout_above", AttrKind.TEXT))
                add(AttrSpec("android:layout_toStartOf", AttrKind.TEXT))
                add(AttrSpec("android:layout_toEndOf", AttrKind.TEXT))
                add(AttrSpec("android:layout_alignStart", AttrKind.TEXT))
                add(AttrSpec("android:layout_alignEnd", AttrKind.TEXT))
                add(AttrSpec("android:layout_alignTop", AttrKind.TEXT))
                add(AttrSpec("android:layout_alignBottom", AttrKind.TEXT))
            }
            parentTag == "GridLayout" || parentTag?.endsWith("GridLayout") == true -> {
                add(AttrSpec("android:layout_row", AttrKind.NUMBER))
                add(AttrSpec("android:layout_column", AttrKind.NUMBER))
                add(AttrSpec("android:layout_rowSpan", AttrKind.NUMBER))
                add(AttrSpec("android:layout_columnSpan", AttrKind.NUMBER))
                add(AttrSpec("android:layout_columnWeight", AttrKind.NUMBER))
                add(AttrSpec("android:layout_gravity", AttrKind.ENUM, gravityOptions))
            }
            parentTag == "TableLayout" || parentTag == "TableRow" -> {
                add(AttrSpec("android:layout_column", AttrKind.NUMBER))
                add(AttrSpec("android:layout_span", AttrKind.NUMBER))
            }
            parentTag == "CoordinatorLayout" || parentTag?.endsWith("CoordinatorLayout") == true -> {
                add(AttrSpec("app:layout_anchor", AttrKind.TEXT))
                add(AttrSpec("app:layout_anchorGravity", AttrKind.ENUM, gravityOptions))
                add(AttrSpec("app:layout_behavior", AttrKind.TEXT))
            }
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
        PaletteEntry("include", "include", WidgetCategory.LAYOUT, linkedMapOf("layout" to "@layout/...", "android:layout_width" to MATCH, "android:layout_height" to WRAP)),
        PaletteEntry("card_view", "androidx.cardview.widget.CardView", WidgetCategory.LAYOUT, fillDefaults()),
        PaletteEntry("material_button", "com.google.android.material.button.MaterialButton", WidgetCategory.BUTTON, wrapDefaults("android:text" to "MaterialButton")),
        PaletteEntry("chip", "com.google.android.material.chip.Chip", WidgetCategory.BUTTON, wrapDefaults("android:text" to "Chip")),
        PaletteEntry("fab", "com.google.android.material.floatingactionbutton.FloatingActionButton", WidgetCategory.BUTTON, wrapDefaults()),
        PaletteEntry("recycler_view", "androidx.recyclerview.widget.RecyclerView", WidgetCategory.LAYOUT, fillDefaults()),
        PaletteEntry("text_input_layout", "com.google.android.material.textfield.TextInputLayout", WidgetCategory.INPUT, fillDefaults()),
        PaletteEntry("slider", "com.google.android.material.slider.Slider", WidgetCategory.INPUT, fillDefaults()),
        PaletteEntry("material_divider", "com.google.android.material.divider.MaterialDivider", WidgetCategory.OTHER, fillDefaults("android:layout_height" to "1dp")),
        PaletteEntry("compose_view", "androidx.compose.ui.platform.ComposeView", WidgetCategory.OTHER, fillDefaults()),
    )
}
