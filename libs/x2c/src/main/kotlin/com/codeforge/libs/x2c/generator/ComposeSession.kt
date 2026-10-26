package com.codeforge.libs.x2c.generator

import com.codeforge.core.domain.model.GeneratorContext
import com.codeforge.core.domain.repository.GeneratedComposeFile
import com.codeforge.libs.x2c.generator.LayoutValues.camel
import com.codeforge.libs.x2c.generator.LayoutValues.colorHex
import com.codeforge.libs.x2c.generator.LayoutValues.flags
import com.codeforge.libs.x2c.generator.LayoutValues.floatLiteral
import com.codeforge.libs.x2c.generator.LayoutValues.idName
import com.codeforge.libs.x2c.generator.LayoutValues.number
import com.codeforge.libs.x2c.generator.LayoutValues.numberLiteral
import com.codeforge.libs.x2c.generator.LayoutValues.pascal
import com.codeforge.libs.x2c.generator.LayoutValues.stringLiteral
import java.util.IdentityHashMap

private enum class PK { COLUMN, ROW, BOX, CONSTRAINT, OTHER }

private data class Parent(val kind: PK, val isRoot: Boolean = false)

private data class Param(val name: String, val type: String, val default: String, val doc: String, val preview: String? = null)

/**
 * Eine Generierung (ein Layout -> eine Datei). Deterministisch: gleiche Eingabe, gleiche Ausgabe.
 *
 * Nichts wird still verworfen: Nicht Abbildbares wird zu `// TODO(x2c): … (Quelle: datei:zeile)` und zählt in
 * [GeneratedComposeFile.todoCount]; reine Hinweise (z. B. harte Texte) stehen in [GeneratedComposeFile.notes].
 */
internal class ComposeSession(
    private val root: XmlElement,
    private val layoutName: String,
    private val targetPackage: String,
    touchedViewIds: Collection<String>,
    private val ctx: GeneratorContext
) {
    private val touched: Set<String> = touchedViewIds.toSet()
    private val imports = sortedSetOf("androidx.compose.runtime.Composable", "androidx.compose.ui.Modifier")
    private val notes = linkedSetOf<String>()
    private var todoCount = 0
    private val params = LinkedHashMap<String, Param>()
    private val source = ctx.sourcePath ?: "res/layout/$layoutName.xml"
    private val constrainBlocks = IdentityHashMap<XmlElement, String>()
    private val counters = HashMap<String, Int>()
    private var needsR = false

    // ---------------------------------------------------------------- Ausgabe

    fun generate(composableName: String): GeneratedComposeFile {
        val body = if (root.shortTag == "merge") {
            notes += "merge: Inhalt wird ohne eigenen Container ausgegeben; der Parameter `modifier` ist ungenutzt"
            root.children.flatMap { emit(it, Parent(PK.OTHER)) }
        } else {
            emit(root, Parent(PK.OTHER, isRoot = true))
        }
        if (needsR) {
            val rp = ctx.rPackage
            if (rp != null) imports += "$rp.R" else notes += "R-Paket unbekannt: `import <namespace>.R` muss ergänzt werden"
        }

        val out = StringBuilder()
        out.appendLine("package $targetPackage")
        out.appendLine()
        imports.forEach { out.appendLine("import $it") }
        out.appendLine()
        out.appendLine("/**")
        out.appendLine(" * Generiert aus `$source` von CodeForge x2c, bitte prüfen.")
        out.appendLine(" *")
        out.appendLine(" * Layout und Ressourcen wurden übertragen. Verhalten (Klick-Listener, Adapter, Datenbindung,")
        out.appendLine(" * Animationen) wird nicht übernommen.")
        out.appendLine(" *")
        out.appendLine(" * @param modifier Modifier für das Wurzelelement.")
        params.values.forEach { out.appendLine(" * @param ${it.name} ${it.doc}") }
        out.appendLine(" */")
        out.appendLine("@Composable")
        out.appendLine("fun $composableName(")
        out.appendLine("    modifier: Modifier = Modifier,")
        params.values.forEach { out.appendLine("    ${it.name}: ${it.type} = ${it.default},") }
        out.appendLine(") {")
        body.forEach { out.appendLine(if (it.isEmpty()) "" else "    $it") }
        out.appendLine("}")
        out.appendLine()

        imports += "androidx.compose.material3.MaterialTheme"
        imports += "androidx.compose.ui.tooling.preview.Preview"
        val previewArgs = params.values.filter { it.preview != null }.map { "${it.name} = ${it.preview}" }
        val call = call(composableName, previewArgs)
        out.appendLine("@Preview(showBackground = true)")
        out.appendLine("@Composable")
        out.appendLine("private fun ${composableName}Preview() {")
        out.appendLine("    MaterialTheme {")
        call.forEach { out.appendLine("        $it") }
        out.appendLine("    }")
        out.appendLine("}")

        // Imports nach den Previews nachtragen (MaterialTheme/Preview kamen erst hier dazu)
        val text = out.toString().replaceFirst(
            Regex("(?s)(package [^\n]*\n\n).*?(\n/\\*\\*)"),
            "$1" + imports.joinToString("\n") { "import $it" } + "\n$2"
        )
        val relPath = "${ctx.sourceRoot}/${targetPackage.replace('.', '/')}/$composableName.kt"
        return GeneratedComposeFile(
            packageName = targetPackage,
            composableName = composableName,
            targetRelativePath = relPath,
            sourceContent = text,
            todoCount = todoCount,
            notes = notes.toList()
        )
    }

    // ---------------------------------------------------------------- Hilfen

    private fun use(vararg imp: String) {
        imports += imp
    }

    private fun todo(what: String, el: XmlElement): String {
        todoCount++
        return "// TODO(x2c): $what manuell umsetzen (Quelle: $source:${el.line})"
    }

    private fun addParam(base: String, type: String, default: String, doc: String, preview: String? = null): String {
        var name = base
        var n = 2
        while (params.containsKey(name)) name = base + n++
        params[name] = Param(name, type, default, doc, preview)
        return name
    }

    private fun auto(base: String): String {
        val n = (counters[base] ?: 0) + 1
        counters[base] = n
        return "$base$n"
    }

    /** Name für Parameter/Refs: ID als camelCase, sonst fortlaufend. */
    private fun nameOf(el: XmlElement, base: String): String {
        val id = idName(el.peek("android:id"))
        return if (id != null) camel(id) else auto(base)
    }

    private fun isTouched(el: XmlElement): Boolean = idName(el.peek("android:id"))?.let { it in touched } == true

    private fun call(name: String, args: List<String>, body: List<String>? = null): List<String> {
        val oneLine = "$name(${args.joinToString(", ")})"
        val single = args.none { it.contains('\n') } && oneLine.length <= 90
        val head: List<String> = when {
            args.isEmpty() && body != null -> listOf(name)
            single -> listOf(oneLine)
            else -> buildList {
                add("$name(")
                args.forEach { a ->
                    val ls = a.lines()
                    ls.forEachIndexed { i, l -> add("    $l" + if (i == ls.lastIndex) "," else "") }
                }
                add(")")
            }
        }
        if (body == null) return head
        if (body.isEmpty()) return head.dropLast(1) + (head.last() + " {}")
        return head.dropLast(1) + (head.last() + " {") + body.map { if (it.isEmpty()) "" else "    $it" } + "}"
    }

    private fun indent(lines: List<String>) = lines.map { if (it.isEmpty()) "" else "    $it" }

    // ---------------------------------------------------------------- Werte

    private fun dim(value: String?, el: XmlElement): String? {
        if (value == null) return null
        val v = value.trim()
        if (v.startsWith("@dimen/")) {
            needsR = true
            use("androidx.compose.ui.res.dimensionResource")
            return "dimensionResource(R.dimen.${v.removePrefix("@dimen/")})"
        }
        val n = number(v) ?: return null
        return when (n.second) {
            "", "dp", "dip" -> { use("androidx.compose.ui.unit.dp"); "${numberLiteral(n.first)}.dp" }
            "sp" -> { use("androidx.compose.ui.unit.sp"); "${numberLiteral(n.first)}.sp" }
            else -> {
                use("androidx.compose.ui.unit.dp")
                notes += "Einheit ${n.second} (Zeile ${el.line}) als dp übernommen, bitte prüfen"
                "${numberLiteral(n.first)}.dp"
            }
        }
    }

    private fun color(value: String?): String? {
        if (value == null) return null
        val v = value.trim()
        if (v.startsWith("@color/")) {
            needsR = true
            use("androidx.compose.ui.res.colorResource")
            return "colorResource(R.color.${v.removePrefix("@color/")})"
        }
        if (v.startsWith("@android:color/")) {
            val m = LayoutValues.ANDROID_COLORS[v.removePrefix("@android:color/")] ?: return null
            use("androidx.compose.ui.graphics.Color")
            return m
        }
        val hex = colorHex(v) ?: return null
        use("androidx.compose.ui.graphics.Color")
        return hex
    }

    /** `@string/x` -> stringResource, Literal -> String (mit Hinweis auf harte Texte). */
    private fun str(value: String?, el: XmlElement): String? {
        if (value == null) return null
        val v = value.trim()
        return when {
            v.startsWith("@string/") -> {
                needsR = true
                use("androidx.compose.ui.res.stringResource")
                "stringResource(R.string.${v.removePrefix("@string/")})"
            }
            v.startsWith("@android:string/") -> {
                use("androidx.compose.ui.res.stringResource")
                "stringResource(android.R.string.${v.removePrefix("@android:string/")})"
            }
            v.startsWith("?") || v.startsWith("@") -> null
            else -> {
                notes += "Harter Text \"${v.take(30)}\" (Zeile ${el.line}) bleibt im Code; bitte auslagern (Task 42/17)"
                stringLiteral(v)
            }
        }
    }

    private fun bool(value: String?): Boolean? = when (value?.trim()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    // ---------------------------------------------------------------- Modifier

    private fun sidesCall(prefix: String, el: XmlElement, parentIsConstraint: Boolean = false): String? {
        fun side(vararg names: String): String? {
            for (n in names) {
                val v = el.attr("$prefix$n")
                if (v != null) return v
            }
            return null
        }
        val all = el.attr(prefix)
        val h = side("Horizontal")
        val v = side("Vertical")
        val start = side("Start", "Left") ?: h ?: all
        val end = side("End", "Right") ?: h ?: all
        val top = side("Top") ?: v ?: all
        val bottom = side("Bottom") ?: v ?: all
        if (parentIsConstraint) return null
        val e = listOf(start, top, end, bottom).map { dim(it, el) }
        if (e.all { it == null }) return null
        if (e.any { it != null && it.startsWith("-") }) {
            // negatives Padding ist in Compose nicht erlaubt
            pendingTodos += todo("negativer Abstand (${prefix.removePrefix("android:")})", el)
            return null
        }
        val (s, t, en, b) = e
        return when {
            s != null && s == t && t == en && en == b -> "padding($s)"
            s == en && t == b && (s != null || t != null) -> {
                val args = listOfNotNull(s?.let { "horizontal = $it" }, t?.let { "vertical = $it" })
                "padding(${args.joinToString(", ")})"
            }
            else -> {
                val args = listOfNotNull(
                    s?.let { "start = $it" }, t?.let { "top = $it" }, en?.let { "end = $it" }, b?.let { "bottom = $it" }
                )
                "padding(${args.joinToString(", ")})"
            }
        }.also { use("androidx.compose.foundation.layout.padding") }
    }

    /** TODO-Kommentare, die beim Aufbau des Modifiers entstehen und vor das Element gesetzt werden. */
    private val pendingTodos = mutableListOf<String>()

    private fun alignment(el: XmlElement, parent: Parent): String? {
        val g = flags(el.attr("android:layout_gravity"))
        if (g.isEmpty()) return null
        use("androidx.compose.ui.Alignment")
        return when (parent.kind) {
            PK.COLUMN -> when {
                "center_horizontal" in g || "center" in g -> "align(Alignment.CenterHorizontally)"
                "end" in g || "right" in g -> "align(Alignment.End)"
                "start" in g || "left" in g -> "align(Alignment.Start)"
                else -> null
            }
            PK.ROW -> when {
                "center_vertical" in g || "center" in g -> "align(Alignment.CenterVertically)"
                "bottom" in g -> "align(Alignment.Bottom)"
                "top" in g -> "align(Alignment.Top)"
                else -> null
            }
            PK.BOX -> boxAlignment(g)?.let { "align($it)" }
            else -> null
        }
    }

    private fun boxAlignment(g: Set<String>): String? {
        val h = when {
            "center_horizontal" in g -> "Center"
            "end" in g || "right" in g -> "End"
            "start" in g || "left" in g -> "Start"
            "center" in g -> "Center"
            else -> null
        }
        val v = when {
            "center_vertical" in g -> "Center"
            "bottom" in g -> "Bottom"
            "top" in g -> "Top"
            "center" in g -> "Center"
            else -> null
        }
        if (h == null && v == null) return null
        val name = when {
            h == "Center" && v == "Center" -> "Center"
            v == null && h != null -> if (h == "Center") "TopCenter" else "Top$h"
            v == "Center" && h == null -> "CenterStart"
            h == null -> "Top${"Start"}"
            v == "Center" -> "Center$h"
            else -> "$v$h"
        }
        use("androidx.compose.ui.Alignment")
        return "Alignment.$name"
    }

    private fun modifierFor(
        el: XmlElement,
        parent: Parent,
        scrollCalls: List<String> = emptyList(),
        isImageLike: Boolean = false,
        clickParam: String? = null
    ): String? {
        val calls = mutableListOf<String>()
        val inConstraint = parent.kind == PK.CONSTRAINT

        // 1. Eltern-Scope: weight / align / constrainAs
        val w = el.attr("android:layout_width")
        val h = el.attr("android:layout_height")
        val weight = el.attr("android:layout_weight")?.let { number(it)?.first }?.takeIf { it > 0 }
        var widthCall: String? = null
        var heightCall: String? = null
        when {
            weight != null && parent.kind == PK.COLUMN -> {
                calls += if (h?.trim() == "0dp") "weight(${floatLiteral(weight)})" else "weight(${floatLiteral(weight)}, fill = false)"
                if (h?.trim() != "0dp") heightCall = axis(h, false, el)
                widthCall = axis(w, true, el)
            }
            weight != null && parent.kind == PK.ROW -> {
                calls += if (w?.trim() == "0dp") "weight(${floatLiteral(weight)})" else "weight(${floatLiteral(weight)}, fill = false)"
                if (w?.trim() != "0dp") widthCall = axis(w, true, el)
                heightCall = axis(h, false, el)
            }
            else -> {
                if (weight != null) pendingTodos += todo("layout_weight außerhalb von LinearLayout", el)
                widthCall = if (inConstraint && w?.trim() == "0dp") null else axis(w, true, el)
                heightCall = if (inConstraint && h?.trim() == "0dp") null else axis(h, false, el)
            }
        }
        alignment(el, parent)?.let { calls += it }
        constrainBlocks[el]?.let { calls += it }

        // 2. Außenabstand (Margin); in ConstraintLayout sitzt er an den Links
        sidesCall("android:layout_margin", el, inConstraint)?.let { calls += it }

        // 3. Größe
        val sizeCalls = when {
            widthCall == "fillMaxWidth()" && heightCall == "fillMaxHeight()" -> listOf("fillMaxSize()")
            widthCall != null && heightCall != null && widthCall.removePrefix("width") == heightCall.removePrefix("height") &&
                widthCall.startsWith("width(") && heightCall.startsWith("height(") ->
                listOf("size${widthCall.removePrefix("width")}")
            else -> listOfNotNull(widthCall, heightCall)
        }
        calls += sizeCalls
        sizeCalls.forEach { use("androidx.compose.foundation.layout.${it.substringBefore('(')}") }
        ratioCalls[el]?.let { calls += it }
        el.attr("android:minWidth")?.let { dim(it, el) }?.let { calls += "widthIn(min = $it)"; use("androidx.compose.foundation.layout.widthIn") }
        el.attr("android:minHeight")?.let { dim(it, el) }?.let { calls += "heightIn(min = $it)"; use("androidx.compose.foundation.layout.heightIn") }

        // 4. Transparenz/Drehung, Hintergrund
        el.attr("android:alpha")?.let { number(it) }?.let { calls += "alpha(${floatLiteral(it.first)})"; use("androidx.compose.ui.draw.alpha") }
        el.attr("android:rotation")?.let { number(it) }?.let { calls += "rotate(${floatLiteral(it.first)})"; use("androidx.compose.ui.draw.rotate") }
        if (el.peek("android:visibility") == "invisible") {
            calls += "alpha(0f)"
            use("androidx.compose.ui.draw.alpha")
            notes += "invisible (Zeile ${el.line}): Platz bleibt erhalten, Element ist per alpha(0f) unsichtbar; Klicks kommen weiterhin an"
        }
        el.attr("android:background")?.let { bg ->
            val c = color(bg)
            if (c != null) {
                calls += "background($c)"
                use("androidx.compose.foundation.background")
            } else {
                pendingTodos += todo("android:background=\"$bg\"", el)
            }
        }

        // 5. Klick, Semantik
        if (clickParam != null) {
            calls += "clickable(onClick = $clickParam)"
            use("androidx.compose.foundation.clickable")
        }
        val cd = if (isImageLike) null else el.attr("android:contentDescription")
        if (cd != null) {
            str(cd, el)?.let {
                calls += "semantics { contentDescription = $it }"
                use("androidx.compose.ui.semantics.semantics", "androidx.compose.ui.semantics.contentDescription")
            }
        }
        when (el.attr("android:importantForAccessibility")) {
            "no", "noHideDescendants" -> {
                calls += "clearAndSetSemantics {}"
                use("androidx.compose.ui.semantics.clearAndSetSemantics")
            }
        }
        el.attr("android:labelFor")?.let { notes += "labelFor (Zeile ${el.line}): Beschriftung bitte als label/semantics des Zielelements prüfen" }

        // 6. Scroll, danach Innenabstand
        calls += scrollCalls
        sidesCall("android:padding", el)?.let { calls += it }

        val base = if (parent.isRoot) "modifier" else "Modifier"
        val shaped = calls.map { it.replace("\n", "\n    ") }
        return when {
            calls.isEmpty() -> if (parent.isRoot) "modifier" else null
            calls.size == 1 -> "$base.${calls[0]}"
            else -> base + shaped.joinToString("") { "\n    .$it" }
        }
    }

    private fun axis(value: String?, width: Boolean, el: XmlElement): String? {
        val v = value?.trim() ?: return null
        return when (v) {
            "match_parent", "fill_parent" -> {
                if (width) "fillMaxWidth()" else "fillMaxHeight()"
            }
            "wrap_content" -> null
            else -> {
                val d = dim(v, el)
                if (d == null) {
                    pendingTodos += todo("${if (width) "layout_width" else "layout_height"}=\"$v\"", el)
                    null
                } else if (v == "0dp") {
                    pendingTodos += todo("0dp ohne weight/Constraints (${if (width) "Breite" else "Höhe"})", el)
                    null
                } else {
                    "${if (width) "width" else "height"}($d)"
                }
            }
        }.also { if (it != null && it.startsWith("width(") || it != null && it.startsWith("height(")) Unit }
    }

    private fun modifierArg(el: XmlElement, parent: Parent, scroll: List<String> = emptyList(), image: Boolean = false, click: String? = null): String? =
        modifierFor(el, parent, scroll, image, click)?.let { "modifier = $it" }

    // ---------------------------------------------------------------- Elemente

    private fun emit(el: XmlElement, parent: Parent): List<String> {
        val id = idName(el.peek("android:id"))
        val vis = el.peek("android:visibility")
        el.markConsumed("android:visibility")
        el.markConsumed("android:id")
        val gone = vis == "gone"

        if (id != null && id in ctx.visibilityIds) {
            val p = addParam("${camel(id)}Visible", "Boolean", (!gone).toString(), "Sichtbarkeit von `$id` (Code ruft setVisibility auf).")
            val inner = emitBody(el, parent)
            return listOf("if ($p) {") + indent(inner) + "}"
        }
        if (gone) {
            notes += "${el.shortTag} (Zeile ${el.line}) hat visibility=\"gone\" und wurde weggelassen"
            return listOf("// Hinweis(x2c): ${el.shortTag} (Zeile ${el.line}) hat visibility=\"gone\" und wurde weggelassen")
        }
        return emitBody(el, parent)
    }

    private fun emitBody(el: XmlElement, parent: Parent): List<String> {
        pendingTodos.clear()
        val lines = dispatch(el, parent)
        val todos = ArrayList(pendingTodos)
        pendingTodos.clear()
        val leftovers = el.unconsumed().filter { key ->
            !key.startsWith("tools:") && !key.startsWith("xmlns") && key != "android:id"
        }
        val extra = leftovers.mapNotNull { key ->
            when (key) {
                in NOTE_ONLY -> { notes += "Attribut $key (Zeile ${el.line}) wird nicht übernommen"; null }
                "style" -> todo("style=\"${el.peek(key)}\" (Style in Parameter auflösen)", el)
                else -> todo("$key=\"${el.peek(key)}\"", el)
            }
        }
        return todos + extra + lines
    }

    private fun isFramework(tag: String) =
        !tag.contains('.') || tag.startsWith("androidx.") || tag.startsWith("com.google.android.material.") || tag.startsWith("android.widget.")

    private fun dispatch(el: XmlElement, parent: Parent): List<String> {
        if (!isFramework(el.tag)) return interop(el, parent)
        return when (el.shortTag) {
            "LinearLayout" -> linear(el, parent)
            "FrameLayout" -> box(el, parent, "FrameLayout")
            "RelativeLayout" -> relative(el, parent)
            "ConstraintLayout" -> constraint(el, parent)
            "ScrollView", "NestedScrollView" -> scroll(el, parent, vertical = true)
            "HorizontalScrollView" -> scroll(el, parent, vertical = false)
            "TextView", "MaterialTextView", "AppCompatTextView" -> text(el, parent)
            "Button", "MaterialButton", "AppCompatButton" -> button(el, parent)
            "EditText", "TextInputEditText", "AppCompatEditText" -> textField(el, null, parent)
            "TextInputLayout" -> textInputLayout(el, parent)
            "ImageView", "ShapeableImageView", "AppCompatImageView" -> image(el, parent)
            "ImageButton", "AppCompatImageButton" -> imageButton(el, parent)
            "CheckBox", "AppCompatCheckBox", "MaterialCheckBox" -> toggle(el, parent, "Checkbox")
            "Switch", "SwitchCompat", "SwitchMaterial", "MaterialSwitch" -> toggle(el, parent, "Switch")
            "RadioGroup" -> radioGroup(el, parent)
            "RadioButton", "AppCompatRadioButton", "MaterialRadioButton" -> radioButton(el, parent)
            "ProgressBar", "LinearProgressIndicator", "CircularProgressIndicator" -> progress(el, parent)
            "Slider", "SeekBar", "AppCompatSeekBar" -> slider(el, parent)
            "CardView", "MaterialCardView" -> card(el, parent)
            "View" -> plainView(el, parent)
            "Space" -> space(el, parent)
            "RecyclerView" -> recycler(el, parent)
            "include" -> include(el, parent)
            else -> interop(el, parent)
        }
    }

    private fun children(el: XmlElement, kind: PK): List<String> =
        el.children.flatMap { emit(it, Parent(kind)) }

    // ---- Container

    private fun linear(el: XmlElement, parent: Parent): List<String> {
        val vertical = el.attr("android:orientation")?.trim() == "vertical"
        val g = flags(el.attr("android:gravity"))
        val args = mutableListOf<String>()
        modifierArg(el, parent)?.let { args += it }
        if (vertical) {
            use("androidx.compose.foundation.layout.Column")
            when {
                "center_horizontal" in g || "center" in g -> { use("androidx.compose.ui.Alignment"); args += "horizontalAlignment = Alignment.CenterHorizontally" }
                "end" in g || "right" in g -> { use("androidx.compose.ui.Alignment"); args += "horizontalAlignment = Alignment.End" }
            }
            val va = when {
                "center_vertical" in g || "center" in g -> "Arrangement.Center"
                "bottom" in g -> "Arrangement.Bottom"
                else -> null
            }
            if (va != null) { use("androidx.compose.foundation.layout.Arrangement"); args += "verticalArrangement = $va" }
        } else {
            use("androidx.compose.foundation.layout.Row")
            val va = when {
                "center_vertical" in g || "center" in g -> "Alignment.CenterVertically"
                "bottom" in g -> "Alignment.Bottom"
                else -> null
            }
            if (va != null) { use("androidx.compose.ui.Alignment"); args += "verticalAlignment = $va" }
            val ha = when {
                "center_horizontal" in g || "center" in g -> "Arrangement.Center"
                "end" in g || "right" in g -> "Arrangement.End"
                else -> null
            }
            if (ha != null) { use("androidx.compose.foundation.layout.Arrangement"); args += "horizontalArrangement = $ha" }
        }
        if (g.any { it == "fill" || it == "fill_vertical" || it == "fill_horizontal" }) notes += "gravity=fill (Zeile ${el.line}) wird nicht abgebildet"
        el.attr("android:weightSum")?.let { notes += "weightSum (Zeile ${el.line}) ignoriert: Compose verteilt relativ zur Summe der Gewichte" }
        el.attr("android:baselineAligned")
        if (el.attr("android:showDividers") != null || el.attr("android:divider") != null || el.attr("android:dividerPadding") != null) {
            notes += "Trenner (divider/showDividers, Zeile ${el.line}) nicht übernommen: bitte HorizontalDivider/VerticalDivider ergänzen"
        }
        val kids = children(el, if (vertical) PK.COLUMN else PK.ROW)
        return call(if (vertical) "Column" else "Row", args, kids)
    }

    private fun box(el: XmlElement, parent: Parent, what: String): List<String> {
        use("androidx.compose.foundation.layout.Box")
        val args = listOfNotNull(modifierArg(el, parent)).toMutableList()
        el.attr("android:foregroundGravity")
        val kids = children(el, PK.BOX)
        if (what == "FrameLayout") el.attr("android:measureAllChildren")
        return call("Box", args, kids)
    }

    private fun relative(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.foundation.layout.Box")
        notes += "RelativeLayout (Zeile ${el.line}) als Box umgesetzt; Regeln wie layout_below sind nur teilweise abbildbar"
        el.attr("android:gravity")
        val args = listOfNotNull(modifierArg(el, parent)).toMutableList()
        val kids = el.children.flatMap { c ->
            val g = mutableSetOf<String>()
            if (c.attr("android:layout_centerInParent") == "true") g += "center"
            if (c.attr("android:layout_centerHorizontal") == "true") g += "center_horizontal"
            if (c.attr("android:layout_centerVertical") == "true") g += "center_vertical"
            if (c.attr("android:layout_alignParentTop") == "true") g += "top"
            if (c.attr("android:layout_alignParentBottom") == "true") g += "bottom"
            if (c.attr("android:layout_alignParentStart") == "true" || c.attr("android:layout_alignParentLeft") == "true") g += "start"
            if (c.attr("android:layout_alignParentEnd") == "true" || c.attr("android:layout_alignParentRight") == "true") g += "end"
            if (g.isNotEmpty() && c.peek("android:layout_gravity") == null) c.attrs["android:layout_gravity"] = g.joinToString("|")
            val rules = c.attrs.keys.filter {
                it.startsWith("android:layout_") && (it.contains("below") || it.contains("above") || it.contains("toStartOf") ||
                    it.contains("toEndOf") || it.contains("toLeftOf") || it.contains("toRightOf") || it.contains("alignTop") ||
                    it.contains("alignBottom") || it.contains("alignStart") || it.contains("alignEnd") || it.contains("alignLeft") ||
                    it.contains("alignRight") || it.contains("alignBaseline") || it.contains("alignWithParentIfMissing"))
            }
            val ruleTodos = rules.map { c.markConsumed(it); todo("${it.removePrefix("android:")}=\"${c.peek(it)}\"", c) }
            ruleTodos + emit(c, Parent(PK.BOX))
        }
        return call("Box", args, kids)
    }

    private fun scroll(el: XmlElement, parent: Parent, vertical: Boolean): List<String> {
        use("androidx.compose.foundation.layout.Box", "androidx.compose.foundation.rememberScrollState")
        use(if (vertical) "androidx.compose.foundation.verticalScroll" else "androidx.compose.foundation.horizontalScroll")
        el.attr("android:fillViewport")?.let { notes += "fillViewport (Zeile ${el.line}) nicht übernommen" }
        el.attr("android:scrollbars")
        val scrollCall = if (vertical) "verticalScroll(rememberScrollState())" else "horizontalScroll(rememberScrollState())"
        val args = listOfNotNull(modifierArg(el, parent, listOf(scrollCall))).toMutableList()
        val kids = children(el, PK.BOX)
        return call("Box", args, kids)
    }

    private fun card(el: XmlElement, parent: Parent): List<String> {
        val style = el.attr("style").orEmpty()
        val elevated = style.contains("Elevated")
        val name = if (elevated) "ElevatedCard" else "Card"
        use("androidx.compose.material3.$name")
        val args = listOfNotNull(modifierArg(el, parent)).toMutableList()
        (el.attr("app:cardCornerRadius") ?: el.attr("cardCornerRadius"))?.let { dim(it, el) }?.let {
            use("androidx.compose.foundation.shape.RoundedCornerShape")
            args += "shape = RoundedCornerShape($it)"
        }
        (el.attr("app:cardElevation"))?.let { dim(it, el) }?.let {
            use("androidx.compose.material3.CardDefaults")
            args += "elevation = CardDefaults.${if (elevated) "elevatedCardElevation" else "cardElevation"}(defaultElevation = $it)"
        }
        el.attr("app:cardBackgroundColor")?.let { color(it) }?.let {
            use("androidx.compose.material3.CardDefaults")
            args += "colors = CardDefaults.${if (elevated) "elevatedCardColors" else "cardColors"}(containerColor = $it)"
        }
        // Compose-Card legt Kinder untereinander; CardView ist ein FrameLayout -> bei mehreren Kindern Box
        val kids = if (el.children.size <= 1) children(el, PK.COLUMN) else {
            notes += "${el.shortTag} (Zeile ${el.line}) hat mehrere Kinder: in Box gelegt (FrameLayout-Verhalten)"
            use("androidx.compose.foundation.layout.Box")
            call("Box", emptyList(), children(el, PK.BOX))
        }
        return call(name, args, kids)
    }

    // ---- ConstraintLayout

    private data class Anchor(val side: String, val target: String, val targetAnchor: String)

    private fun constraint(el: XmlElement, parent: Parent): List<String> {
        use(
            "androidx.constraintlayout.compose.ConstraintLayout",
            "androidx.constraintlayout.compose.Dimension"
        )
        val kids = el.children
        val isGuideline = { c: XmlElement -> c.shortTag == "Guideline" }
        val isBarrier = { c: XmlElement -> c.shortTag == "Barrier" }
        val refName = IdentityHashMap<XmlElement, String>()
        val pre = mutableListOf<String>()
        val used = mutableSetOf<String>()
        fun unique(base: String): String {
            var n = base
            var i = 2
            while (!used.add(n)) n = base + i++
            return n
        }

        kids.forEach { c ->
            val base = idName(c.peek("android:id"))?.let { camel(it) } ?: when {
                isGuideline(c) -> auto("guideline")
                isBarrier(c) -> auto("barrier")
                else -> auto("view")
            }
            refName[c] = unique(base)
        }
        val byId = HashMap<String, XmlElement>()
        kids.forEach { c -> idName(c.peek("android:id"))?.let { byId[it] = c } }

        val plain = kids.filter { !isGuideline(it) && !isBarrier(it) }
        if (plain.isNotEmpty()) {
            if (plain.size <= 16) pre += "val (${plain.joinToString(", ") { refName[it]!! }}) = createRefs()"
            else plain.forEach { pre += "val ${refName[it]} = createRef()" }
        }
        kids.filter(isGuideline).forEach { g ->
            val vertical = g.attr("android:orientation")?.trim() == "vertical"
            val percent = g.attr("app:layout_constraintGuide_percent")?.let { number(it) }
            val begin = g.attr("app:layout_constraintGuide_begin")?.let { dim(it, g) }
            val end = g.attr("app:layout_constraintGuide_end")?.let { dim(it, g) }
            val name = refName[g]!!
            val ctor = when {
                percent != null -> "${if (vertical) "createGuidelineFromStart" else "createGuidelineFromTop"}(${floatLiteral(percent.first)})"
                begin != null -> "${if (vertical) "createGuidelineFromStart" else "createGuidelineFromTop"}($begin)"
                end != null -> "${if (vertical) "createGuidelineFromEnd" else "createGuidelineFromBottom"}($end)"
                else -> null
            }
            if (ctor != null) pre += "val $name = $ctor" else pre += todo("Guideline ohne begin/end/percent", g)
        }
        kids.filter(isBarrier).forEach { b ->
            val dir = b.attr("app:barrierDirection")?.trim()
            val ids = b.attr("app:constraint_referenced_ids")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val members = ids.mapNotNull { byId[it]?.let { m -> refName[m] } }
            val fn = when (dir) {
                "start", "left" -> "createStartBarrier"
                "end", "right" -> "createEndBarrier"
                "top" -> "createTopBarrier"
                "bottom" -> "createBottomBarrier"
                else -> null
            }
            if (fn != null && members.isNotEmpty() && members.size == ids.size) pre += "val ${refName[b]} = $fn(${members.joinToString(", ")})"
            else pre += todo("Barrier (Richtung/Referenzen nicht auflösbar)", b)
            b.attr("app:barrierAllowsGoneWidgets")
        }

        // Ketten: Kopf hat chainStyle; Mitglieder über end->start (bzw. bottom->top)
        val skip = HashSet<String>() // "<id>:<attr>"
        fun chain(axisH: Boolean) {
            val styleAttr = if (axisH) "app:layout_constraintHorizontal_chainStyle" else "app:layout_constraintVertical_chainStyle"
            val fwd = if (axisH) "app:layout_constraintEnd_toStartOf" else "app:layout_constraintBottom_toTopOf"
            val back = if (axisH) "app:layout_constraintStart_toEndOf" else "app:layout_constraintTop_toBottomOf"
            plain.filter { it.peek(styleAttr) != null }.forEach { head ->
                val members = mutableListOf(head)
                var cur = head
                while (true) {
                    val nextId = idName(cur.peek(fwd)?.trim())
                    val next = nextId?.let { byId[it] } ?: break
                    if (next in members || next.peek(back)?.let { idName(it) } != idName(cur.peek("android:id")) ) break
                    members += next
                    skip += "${refName[cur]}:$fwd"
                    skip += "${refName[next]}:$back"
                    cur = next
                }
                val style = when (head.attr(styleAttr)?.trim()) {
                    "packed" -> "ChainStyle.Packed"
                    "spread_inside" -> "ChainStyle.SpreadInside"
                    else -> "ChainStyle.Spread"
                }
                if (members.size >= 2) {
                    use("androidx.constraintlayout.compose.ChainStyle")
                    pre += "${if (axisH) "createHorizontalChain" else "createVerticalChain"}(${members.joinToString(", ") { refName[it]!! }}, chainStyle = $style)"
                } else {
                    pre += todo("Kette ohne Nachbarn (${styleAttr.removePrefix("app:")})", head)
                }
            }
        }
        chain(true)
        chain(false)

        // constrainAs je Element
        plain.forEach { c ->
            val me = refName[c]!!
            val lines = mutableListOf<String>()
            val sideMap = listOf(
                "Top" to "top", "Bottom" to "bottom", "Start" to "start", "End" to "end",
                "Left" to "start", "Right" to "end", "Baseline" to "baseline"
            )
            val horizontal = setOf("start", "end")
            val vertical = setOf("top", "bottom")
            var hLinks = 0
            var vLinks = 0
            for ((xmlSide, side) in sideMap) {
                for ((xmlTarget, targetAnchor) in sideMap) {
                    val key = "app:layout_constraint${xmlSide}_to${xmlTarget}Of"
                    val v = c.peek(key) ?: continue
                    c.markConsumed(key)
                    if ("$me:$key" in skip) continue
                    val compatible = (side in horizontal && targetAnchor in horizontal) ||
                        (side in vertical && targetAnchor in vertical) || (side == "baseline" && targetAnchor == "baseline")
                    if (!compatible) { pendingTodos += todo("$key=\"$v\" (Achsen passen nicht)", c); continue }
                    val tid = v.trim()
                    val target = if (tid == "parent") "parent.$targetAnchor" else {
                        val te = idName(tid)?.let { byId[it] }
                        val tn = te?.let { refName[it] }
                        when {
                            tn == null -> { pendingTodos += todo("$key=\"$v\" (Ziel nicht gefunden)", c); continue }
                            isGuideline(te) || isBarrier(te) -> tn
                            else -> "$tn.$targetAnchor"
                        }
                    }
                    val margin = marginFor(c, side)
                    lines += "$side.linkTo($target${if (margin != null) ", margin = $margin" else ""})"
                    if (side in horizontal) hLinks++ else if (side in vertical) vLinks++
                }
            }
            c.attr("app:layout_constraintHorizontal_bias")?.let { number(it) }?.let { lines += "horizontalBias = ${floatLiteral(it.first)}" }
            c.attr("app:layout_constraintVertical_bias")?.let { number(it) }?.let { lines += "verticalBias = ${floatLiteral(it.first)}" }
            val w = c.peek("android:layout_width")?.trim()
            val h = c.peek("android:layout_height")?.trim()
            if (w == "0dp") {
                if (hLinks >= 2 || c.peek("app:layout_constraintHorizontal_chainStyle") != null || skip.any { it.startsWith("$me:") }) lines += "width = Dimension.fillToConstraints"
                else pendingTodos += todo("Breite 0dp ohne beidseitige Constraints", c)
            }
            if (h == "0dp") {
                if (vLinks >= 2 || c.peek("app:layout_constraintVertical_chainStyle") != null || skip.any { it.startsWith("$me:") }) lines += "height = Dimension.fillToConstraints"
                else pendingTodos += todo("Höhe 0dp ohne beidseitige Constraints", c)
            }
            c.attr("app:layout_constraintVertical_chainStyle")
            c.attr("app:layout_constraintHorizontal_chainStyle")
            c.attr("app:layout_constraintDimensionRatio")?.let { r ->
                val m = Regex("""^(?:([WH]),)?(\d+(?:\.\d+)?):(\d+(?:\.\d+)?)$""").matchEntire(r.trim())
                if (m != null) {
                    val ratio = "${floatLiteral(m.groupValues[2].toDouble())} / ${floatLiteral(m.groupValues[3].toDouble())}"
                    use("androidx.compose.foundation.layout.aspectRatio")
                    ratioCalls[c] = "aspectRatio($ratio${if (m.groupValues[1] == "W") ", matchHeightConstraintsFirst = true" else ""})"
                } else pendingTodos += todo("layout_constraintDimensionRatio=\"$r\"", c)
            }
            if (lines.isNotEmpty()) {
                val block = "constrainAs($me) {\n" + lines.joinToString("\n") { "    $it" } + "\n}"
                constrainBlocks[c] = block
            } else {
                constrainBlocks[c] = "constrainAs($me) {}"
            }
        }

        el.attr("android:orientation")
        val args = listOfNotNull(modifierArg(el, parent)).toMutableList()
        val body = mutableListOf<String>()
        body += pre
        if (pre.isNotEmpty()) body += ""
        plain.forEach { c -> body += emitConstraintChild(c) }
        kids.filter { isGuideline(it) || isBarrier(it) }.forEach { g ->
            g.attrs.keys.filter { !it.startsWith("tools:") }.forEach { g.markConsumed(it) }
        }
        return call("ConstraintLayout", args, body)
    }

    private val ratioCalls = IdentityHashMap<XmlElement, String>()

    private fun emitConstraintChild(c: XmlElement): List<String> {
        // constrainAs-Block mehrzeilig in eine Zeile mit eingebettetem Lambda bringen
        val ratio = ratioCalls[c]
        if (ratio != null) {
            val existing = constrainBlocks[c]!!
            constrainBlocks[c] = existing
        }
        val extraAfter = ratioCalls[c]
        val lines = emit(c, Parent(PK.CONSTRAINT))
        return if (extraAfter == null) lines else lines
    }

    private fun marginFor(c: XmlElement, side: String): String? {
        val names = when (side) {
            "top" -> listOf("Top", "Vertical")
            "bottom" -> listOf("Bottom", "Vertical")
            "start" -> listOf("Start", "Left", "Horizontal")
            "end" -> listOf("End", "Right", "Horizontal")
            else -> return null
        }
        for (n in names) c.peek("android:layout_margin$n")?.let { return dim(it, c) }
        c.peek("android:layout_margin")?.let { return dim(it, c) }
        return null
    }

    // ---- Text

    private fun text(el: XmlElement, parent: Parent): List<String> {
        val raw = el.attr("android:text")
        val base = str(raw, el)
        val tools = el.peek("tools:text")
        val name = if (isTouched(el)) addParam(
            "${nameOf(el, "text")}Text", "String", base ?: "\"\"", "Text von `${idName(el.peek("android:id"))}`.",
            preview = tools?.let { stringLiteral(it) }
        ) else null
        val args = mutableListOf("text = ${name ?: base ?: "\"\""}")
        if (raw != null && base == null) pendingTodos += todo("android:text=\"$raw\"", el)
        modifierArg(el, parent)?.let { args += it }
        el.attr("android:textColor")?.let { c ->
            color(c)?.let { args += "color = $it" } ?: run { pendingTodos += todo("android:textColor=\"$c\"", el) }
        }
        el.attr("android:textSize")?.let { s ->
            val n = number(s)
            if (n != null && n.second == "sp") { use("androidx.compose.ui.unit.sp"); args += "fontSize = ${numberLiteral(n.first)}.sp" }
            else pendingTodos += todo("android:textSize=\"$s\" (nur sp wird übernommen)", el)
        }
        val style = flags(el.attr("android:textStyle"))
        if ("bold" in style) { use("androidx.compose.ui.text.font.FontWeight"); args += "fontWeight = FontWeight.Bold" }
        if ("italic" in style) { use("androidx.compose.ui.text.font.FontStyle"); args += "fontStyle = FontStyle.Italic" }
        val lines = el.attr("android:maxLines")?.trim()?.toIntOrNull() ?: el.attr("android:lines")?.trim()?.toIntOrNull()
            ?: if (el.attr("android:singleLine") == "true") 1 else null
        if (lines != null) args += "maxLines = $lines"
        if (el.attr("android:ellipsize")?.trim() == "end") {
            use("androidx.compose.ui.text.style.TextOverflow")
            args += "overflow = TextOverflow.Ellipsis"
        }
        val g = flags(el.attr("android:gravity")) + flags(el.attr("android:textAlignment"))
        val align = when {
            "center" in g || "center_horizontal" in g -> "TextAlign.Center"
            "end" in g || "right" in g || "viewEnd".lowercase() in g -> "TextAlign.End"
            else -> null
        }
        if (align != null) { use("androidx.compose.ui.text.style.TextAlign"); args += "textAlign = $align" }
        use("androidx.compose.material3.Text")
        return call("Text", args)
    }

    // ---- Button

    private fun button(el: XmlElement, parent: Parent): List<String> {
        val style = el.attr("style").orEmpty()
        val comp = when {
            style.contains("OutlinedButton") -> "OutlinedButton"
            style.contains("TextButton") -> "TextButton"
            style.contains("Elevated") -> "ElevatedButton"
            style.contains("Tonal") -> "FilledTonalButton"
            else -> "Button"
        }
        if (style.isNotEmpty() && comp == "Button" && !style.contains("Widget.Material")) {
            pendingTodos += todo("style=\"$style\" (Button-Stil)", el)
        }
        use("androidx.compose.material3.$comp", "androidx.compose.material3.Text")
        val id = idName(el.peek("android:id"))
        val onClickAttr = el.attr("android:onClick")
        val clickExpr = when {
            isTouched(el) -> {
                val n = addParam("on${pascal(nameOf(el, "button"))}Click", "() -> Unit", "{}", "Klick auf `$id`.")
                n
            }
            onClickAttr != null -> "{ ${todo("android:onClick=\"$onClickAttr\" (Methode aufrufen)", el).replace("// ", "/* ")} */ }"
            else -> "{}"
        }
        val args = mutableListOf("onClick = $clickExpr")
        modifierArg(el, parent)?.let { args += it }
        if (el.attr("android:enabled")?.trim() == "false") args += "enabled = false"
        else if (isTouched(el)) {
            val n = addParam("${nameOf(el, "button")}Enabled", "Boolean", "true", "Aktiv-Zustand von `$id`.")
            args += "enabled = $n"
        }
        val raw = el.attr("android:text")
        val label = str(raw, el)
        val textName = if (isTouched(el)) addParam("${nameOf(el, "button")}Text", "String", label ?: "\"\"", "Beschriftung von `$id`.") else null
        if (raw != null && label == null) pendingTodos += todo("android:text=\"$raw\"", el)
        val content = listOf("Text(text = ${textName ?: label ?: "\"\""})")
        return call(comp, args, content)
    }

    // ---- Eingabe

    private fun textInputLayout(el: XmlElement, parent: Parent): List<String> {
        val inner = findDescendant(el) { it.shortTag == "TextInputEditText" || it.shortTag == "EditText" }
        if (inner == null) {
            pendingTodos += todo("TextInputLayout ohne EditText", el)
            return interop(el, parent)
        }
        return textField(inner, el, parent)
    }

    private fun findDescendant(el: XmlElement, p: (XmlElement) -> Boolean): XmlElement? {
        for (c in el.children) {
            if (p(c)) return c
            findDescendant(c, p)?.let { return it }
        }
        return null
    }

    private fun textField(edit: XmlElement, til: XmlElement?, parent: Parent): List<String> {
        val owner = til ?: edit
        val style = (til?.attr("style") ?: "") + " " + (til?.attr("app:boxBackgroundMode") ?: "")
        val outlined = til != null && (style.contains("OutlinedBox") || style.contains("outline"))
        val comp = if (outlined || til == null && false) "OutlinedTextField" else "TextField"
        use("androidx.compose.material3.$comp")
        val id = idName(edit.peek("android:id")) ?: idName(til?.peek("android:id"))
        val name = if (id != null) camel(id) else auto("field")
        val textRaw = edit.attr("android:text")
        val textExpr = str(textRaw, edit) ?: "\"\""
        val vName = addParam("${name}Value", "String", textExpr, "Eingabewert von `${id ?: name}` (State-Hochziehen).", preview = edit.peek("tools:text")?.let { stringLiteral(it) })
        val cbName = addParam("on${pascal(name)}ValueChange", "(String) -> Unit", "{}", "Änderung des Eingabewerts von `${id ?: name}`.")
        if (!touched.contains(id ?: "")) notes += "EditText ${id ?: "(ohne ID)"} (Zeile ${edit.line}) wird nicht vom Code angefasst, bekommt aber Zustandsparameter, sonst wäre das Feld nicht editierbar"

        val args = mutableListOf("value = $vName", "onValueChange = $cbName")
        modifierArg(owner, parent)?.let { args += it }
        if (til != null) {
            // Maße des inneren EditText sind in TextInputLayout bedeutungslos
            listOf("android:layout_width", "android:layout_height", "android:layout_margin").forEach { edit.markConsumed(it) }
        }
        val hint = til?.attr("android:hint") ?: edit.attr("android:hint")
        val hintExpr = str(hint, edit)
        use("androidx.compose.material3.Text")
        if (hintExpr != null) {
            args += if (til != null) "label = { Text($hintExpr) }" else "placeholder = { Text($hintExpr) }"
        } else if (hint != null) pendingTodos += todo("android:hint=\"$hint\"", edit)
        til?.attr("app:placeholderText")?.let { str(it, til) }?.let { args += "placeholder = { Text($it) }" }
        if (edit.attr("android:enabled")?.trim() == "false") args += "enabled = false"
        val input = flags(edit.attr("android:inputType"))
        val kt = when {
            "textemailaddress" in input -> "KeyboardType.Email"
            "numberdecimal" in input -> "KeyboardType.Decimal"
            "number" in input || "numbersigned" in input -> "KeyboardType.Number"
            "phone" in input -> "KeyboardType.Phone"
            "textpassword" in input || "numberpassword" in input -> "KeyboardType.Password"
            "texturi" in input -> "KeyboardType.Uri"
            else -> null
        }
        if (kt != null) {
            use("androidx.compose.foundation.text.KeyboardOptions", "androidx.compose.ui.text.input.KeyboardType")
            args += "keyboardOptions = KeyboardOptions(keyboardType = $kt)"
        }
        if ("textpassword" in input || "numberpassword" in input) {
            use("androidx.compose.ui.text.input.PasswordVisualTransformation")
            args += "visualTransformation = PasswordVisualTransformation()"
        }
        val multiline = "textmultiline" in input
        val maxLines = edit.attr("android:maxLines")?.trim()?.toIntOrNull()
        val minLines = edit.attr("android:minLines")?.trim()?.toIntOrNull()
        val lines = edit.attr("android:lines")?.trim()?.toIntOrNull()
        val single = edit.attr("android:singleLine") == "true" || (kt != null && !multiline)
        if (single) args += "singleLine = true"
        else if (!multiline && lines != null) { args += "minLines = $lines"; args += "maxLines = $lines" }
        else {
            if (maxLines != null) args += "maxLines = $maxLines"
            if (minLines != null) args += "minLines = $minLines"
        }
        return call(comp, args)
    }

    // ---- Bilder

    private fun painter(el: XmlElement): String? {
        val v = (el.attr("android:src") ?: el.attr("app:srcCompat"))?.trim() ?: return null
        val m = Regex("""^@(drawable|mipmap)/(.+)$""").matchEntire(v)
        if (m == null) {
            pendingTodos += todo("Bildquelle \"$v\"", el)
            return null
        }
        needsR = true
        use("androidx.compose.ui.res.painterResource")
        return "painterResource(R.${m.groupValues[1]}.${m.groupValues[2]})"
    }

    private fun contentScale(el: XmlElement): String? {
        val st = el.attr("android:scaleType")?.trim() ?: return null
        val c = when (st) {
            "centerCrop" -> "Crop"
            "fitCenter", "fitStart", "fitEnd" -> "Fit"
            "centerInside" -> "Inside"
            "fitXY" -> "FillBounds"
            "center" -> "None"
            else -> { pendingTodos += todo("android:scaleType=\"$st\"", el); return null }
        }
        use("androidx.compose.ui.layout.ContentScale")
        return "ContentScale.$c"
    }

    private fun description(el: XmlElement): String {
        val raw = el.attr("android:contentDescription")
        return str(raw, el) ?: "null"
    }

    private fun image(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.foundation.Image")
        val src = painter(el)
        val args = mutableListOf<String>()
        if (src == null) {
            pendingTodos += todo("Bild ohne darstellbare Quelle", el)
            use("androidx.compose.foundation.layout.Box")
            return call("Box", listOfNotNull(modifierArg(el, parent, image = true)))
        }
        val pName = if (isTouched(el)) {
            use("androidx.compose.ui.graphics.painter.Painter")
            addParam("${nameOf(el, "image")}Painter", "Painter", src, "Bild von `${idName(el.peek("android:id"))}`.")
        } else null
        args += "painter = ${pName ?: src}"
        args += "contentDescription = ${description(el)}"
        modifierArg(el, parent, image = true)?.let { args += it }
        contentScale(el)?.let { args += "contentScale = $it" }
        if (el.tag.endsWith("ShapeableImageView")) notes += "ShapeableImageView (Zeile ${el.line}): Form (shapeAppearance) nicht übernommen"
        el.attr("app:tint")?.let { pendingTodos += todo("app:tint=\"$it\"", el) }
        return call("Image", args)
    }

    private fun imageButton(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.material3.IconButton", "androidx.compose.material3.Icon")
        val id = idName(el.peek("android:id"))
        val click = if (isTouched(el)) addParam("on${pascal(nameOf(el, "button"))}Click", "() -> Unit", "{}", "Klick auf `$id`.") else {
            el.attr("android:onClick")?.let { "{ ${todo("android:onClick=\"$it\" (Methode aufrufen)", el).replace("// ", "/* ")} */ }" } ?: "{}"
        }
        val args = mutableListOf("onClick = $click")
        modifierArg(el, parent, image = true)?.let { args += it }
        val src = painter(el)
        val icon = if (src == null) {
            pendingTodos += todo("ImageButton ohne darstellbare Quelle", el)
            emptyList()
        } else call("Icon", listOf("painter = $src", "contentDescription = ${description(el)}"))
        return call("IconButton", args, icon)
    }

    // ---- Auswahl und Anzeige

    private fun labelText(el: XmlElement): String? {
        val raw = el.attr("android:text") ?: return null
        val s = str(raw, el)
        if (s == null) { pendingTodos += todo("android:text=\"$raw\"", el); return null }
        use("androidx.compose.material3.Text")
        return "Text(text = $s)"
    }

    private fun toggle(el: XmlElement, parent: Parent, comp: String): List<String> {
        use("androidx.compose.material3.$comp", "androidx.compose.foundation.layout.Row", "androidx.compose.ui.Alignment")
        val id = idName(el.peek("android:id"))
        val checkedDefault = (el.attr("android:checked")?.trim() == "true").toString()
        val inner = mutableListOf<String>()
        val nm = nameOf(el, comp.lowercase())
        val args: List<String>
        if (isTouched(el)) {
            val c = addParam("${nm}Checked", "Boolean", checkedDefault, "Zustand von `$id`.")
            val cb = addParam("on${pascal(nm)}CheckedChange", "(Boolean) -> Unit", "{}", "Änderung des Zustands von `$id`.")
            args = listOf("checked = $c", "onCheckedChange = $cb")
        } else args = listOf("checked = $checkedDefault", "onCheckedChange = null")
        val en = el.attr("android:enabled")?.trim() == "false"
        inner += call(comp, args + listOfNotNull("enabled = false".takeIf { en }))
        labelText(el)?.let { inner += it }
        val rowArgs = mutableListOf("verticalAlignment = Alignment.CenterVertically")
        modifierArg(el, parent)?.let { rowArgs.add(0, it) }
        return call("Row", rowArgs, inner)
    }

    private fun radioGroup(el: XmlElement, parent: Parent): List<String> {
        val horizontal = el.attr("android:orientation")?.trim() == "horizontal"
        el.attr("android:checkedButton")?.let { notes += "RadioGroup (Zeile ${el.line}): Auswahlzustand (checkedButton) manuell koordinieren" }
        notes += "RadioGroup (Zeile ${el.line}): Einzelauswahl muss im ViewModel/State von Hand abgebildet werden"
        val kind = if (horizontal) "Row" else "Column"
        use("androidx.compose.foundation.layout.$kind")
        val args = listOfNotNull(modifierArg(el, parent)).toMutableList()
        return call(kind, args, children(el, if (horizontal) PK.ROW else PK.COLUMN))
    }

    private fun radioButton(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.material3.RadioButton", "androidx.compose.foundation.layout.Row", "androidx.compose.ui.Alignment")
        val id = idName(el.peek("android:id"))
        val selected = (el.attr("android:checked")?.trim() == "true").toString()
        val nm = nameOf(el, "radio")
        val args = if (isTouched(el)) {
            val s = addParam("${nm}Selected", "Boolean", selected, "Auswahl von `$id`.")
            val cb = addParam("on${pascal(nm)}Click", "() -> Unit", "{}", "Klick auf `$id`.")
            listOf("selected = $s", "onClick = $cb")
        } else listOf("selected = $selected", "onClick = null")
        val inner = mutableListOf<String>()
        inner += call("RadioButton", args)
        labelText(el)?.let { inner += it }
        val rowArgs = mutableListOf("verticalAlignment = Alignment.CenterVertically")
        modifierArg(el, parent)?.let { rowArgs.add(0, it) }
        return call("Row", rowArgs, inner)
    }

    private fun progress(el: XmlElement, parent: Parent): List<String> {
        val style = (el.attr("style") ?: "") + (el.peek("style") ?: "")
        val linear = el.tag.endsWith("LinearProgressIndicator") || style.contains("Horizontal")
        val indeterminate = el.attr("android:indeterminate")?.trim() == "true"
        val comp = if (linear) "LinearProgressIndicator" else "CircularProgressIndicator"
        use("androidx.compose.material3.$comp")
        val args = mutableListOf<String>()
        val progress = el.attr("android:progress")?.let { number(it)?.first }
        val max = el.attr("android:max")?.let { number(it)?.first } ?: 100.0
        if (linear && !indeterminate && progress != null) {
            val frac = floatLiteral(progress / max)
            val expr = if (isTouched(el)) addParam("${nameOf(el, "progress")}Progress", "Float", frac, "Fortschritt (0..1) von `${idName(el.peek("android:id"))}`.") else frac
            args += if (isTouched(el)) "progress = { $expr }" else "progress = { $expr }"
        } else if (!linear && !indeterminate && progress != null) {
            val frac = floatLiteral(progress / max)
            args += "progress = { $frac }"
        }
        modifierArg(el, parent)?.let { args += it }
        el.attr("android:progressTint")?.let { pendingTodos += todo("android:progressTint=\"$it\"", el) }
        return call(comp, args)
    }

    private fun slider(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.material3.Slider")
        val seek = el.shortTag.endsWith("SeekBar")
        val from = (if (seek) 0.0 else el.attr("android:valueFrom")?.let { number(it)?.first }) ?: 0.0
        val to = (if (seek) el.attr("android:max")?.let { number(it)?.first } else el.attr("android:valueTo")?.let { number(it)?.first }) ?: if (seek) 100.0 else 1.0
        val value = (if (seek) el.attr("android:progress") else el.attr("android:value"))?.let { number(it)?.first } ?: from
        val args = mutableListOf<String>()
        if (isTouched(el)) {
            val nm = nameOf(el, "slider")
            val v = addParam("${nm}Value", "Float", floatLiteral(value), "Wert von `${idName(el.peek("android:id"))}`.")
            val cb = addParam("on${pascal(nm)}ValueChange", "(Float) -> Unit", "{}", "Änderung des Werts von `${idName(el.peek("android:id"))}`.")
            args += "value = $v"; args += "onValueChange = $cb"
        } else { args += "value = ${floatLiteral(value)}"; args += "onValueChange = {}" }
        modifierArg(el, parent)?.let { args += it }
        args += "valueRange = ${floatLiteral(from)}..${floatLiteral(to)}"
        el.attr("android:stepSize")?.let { pendingTodos += todo("android:stepSize=\"$it\" (steps = …)", el) }
        return call("Slider", args)
    }

    // ---- Sonstiges

    private fun plainView(el: XmlElement, parent: Parent): List<String> {
        val w = el.peek("android:layout_width")?.trim()
        val h = el.peek("android:layout_height")?.trim()
        val bg = el.peek("android:background")?.let { color(it) }
        val hNum = h?.let { number(it) }
        val wNum = w?.let { number(it) }
        if (bg != null && w == "match_parent" && hNum != null && hNum.first <= 2.0) {
            use("androidx.compose.material3.HorizontalDivider")
            el.markConsumed("android:background")
            val args = mutableListOf<String>()
            val m = modifierFor(el, parent)?.let { mod ->
                // Höhe steckt in `thickness`
                mod.replace(Regex("""\n?\s*\.height\([^)]*\)"""), "").replace(Regex("""Modifier\.height\([^)]*\)"""), "Modifier")
            }
            val cleaned = m?.takeIf { it != "Modifier" }
            cleaned?.let { args += "modifier = $it" }
            args += "thickness = ${dim(h, el)}"
            args += "color = $bg"
            return call("HorizontalDivider", args)
        }
        if (bg == null && el.peek("android:background") == null && (wNum != null || h?.let { number(it) } != null || w == "0dp" || h == "0dp")) {
            return space(el, parent)
        }
        use("androidx.compose.foundation.layout.Box")
        notes += "View (Zeile ${el.line}) als leere Box umgesetzt, bitte prüfen"
        return call("Box", listOfNotNull(modifierArg(el, parent)))
    }

    private fun space(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.foundation.layout.Spacer")
        val args = listOfNotNull(modifierArg(el, parent)).ifEmpty { listOf("modifier = Modifier") }
        return call("Spacer", args)
    }

    private fun recycler(el: XmlElement, parent: Parent): List<String> {
        val id = idName(el.peek("android:id"))
        val lm = (el.attr("app:layoutManager") ?: "")
        val orientation = el.attr("android:orientation")?.trim()
        val horizontal = orientation == "horizontal" || lm.contains("Horizontal")
        val grid = lm.contains("GridLayoutManager")
        val spans = el.attr("app:spanCount")?.trim()?.toIntOrNull()
        val listitem = el.peek("tools:listitem")?.removePrefix("@layout/")
        val itemName = listitem?.let { ctx.knownLayouts[it] ?: LayoutNames.composableName(it) }
        val itemsName = addParam("${nameOf(el, "list")}Items".replace("list1Items", "items").let { if (id == null) "items" else it }, "List<Any>", "emptyList()",
            "Daten der Liste `${id ?: "RecyclerView"}`; Typ durch die eigene Datenklasse ersetzen.")
        use("androidx.compose.foundation.lazy.items")
        val container = when {
            grid && spans != null -> { use("androidx.compose.foundation.lazy.grid.LazyVerticalGrid", "androidx.compose.foundation.lazy.grid.GridCells"); "LazyVerticalGrid" }
            horizontal -> { use("androidx.compose.foundation.lazy.LazyRow"); "LazyRow" }
            else -> { use("androidx.compose.foundation.lazy.LazyColumn"); "LazyColumn" }
        }
        val itemsImport = if (container == "LazyVerticalGrid") "androidx.compose.foundation.lazy.grid.items" else "androidx.compose.foundation.lazy.items"
        imports.remove("androidx.compose.foundation.lazy.items")
        use(itemsImport)
        val args = mutableListOf<String>()
        if (container == "LazyVerticalGrid") args += "columns = GridCells.Fixed($spans)"
        modifierArg(el, parent)?.let { args += it }
        if (grid && spans == null) pendingTodos += todo("GridLayoutManager ohne spanCount", el)
        pendingTodos += todo("Adapter/ViewHolder-Logik von ${id ?: "RecyclerView"} (wird nicht umgeschrieben)", el)
        val itemBody = if (itemName != null) {
            if (listitem != null && ctx.knownLayouts[listitem] == null) notes += "Item-Layout $listitem (Zeile ${el.line}): Composable `$itemName` muss ebenfalls erzeugt werden"
            listOf("$itemName()")
        } else listOf(todo("Item-Layout (tools:listitem fehlt)", el))
        val body = listOf("items($itemsName) { _ ->") + indent(itemBody) + "}"
        return call(container, args, body)
    }

    private fun include(el: XmlElement, parent: Parent): List<String> {
        val ref = el.attr("layout")?.trim()?.removePrefix("@layout/")
        if (ref == null) {
            pendingTodos += todo("<include> ohne layout-Attribut", el)
            return emptyList()
        }
        val callee = ctx.knownLayouts[ref] ?: LayoutNames.composableName(ref)
        if (ctx.knownLayouts[ref] == null) notes += "include $ref (Zeile ${el.line}): Composable `$callee` muss ebenfalls erzeugt werden"
        return call(callee, listOfNotNull(modifierArg(el, parent)))
    }

    private fun interop(el: XmlElement, parent: Parent): List<String> {
        use("androidx.compose.ui.viewinterop.AndroidView")
        val cls = when {
            el.tag == "WebView" -> "android.webkit.WebView"
            el.tag == "ViewStub" || el.tag == "fragment" || el.shortTag == "FragmentContainerView" -> "android.view.View"
            el.tag.contains('.') -> el.tag
            else -> "android.widget.${el.tag}"
        }
        val reason = when {
            el.tag == "ViewStub" -> "ViewStub"
            el.tag == "fragment" || el.shortTag == "FragmentContainerView" -> "Fragment-Container"
            else -> "View ${el.tag}"
        }
        pendingTodos += todo("$reason (Interop-Rahmen, kein Compose-Pendant)", el)
        // Alle weiteren Attribute der View sind Sache des Interop-Teils
        val args = mutableListOf("factory = { context -> $cls(context) }")
        modifierArg(el, parent)?.let { args += it }
        return call("AndroidView", args)
    }

    companion object {
        private val NOTE_ONLY = setOf(
            "android:focusable", "android:focusableInTouchMode", "android:clipToPadding", "android:clipChildren",
            "android:importantForAutofill", "android:saveEnabled", "android:filterTouchesWhenObscured"
        )
    }
}

/** Namenskonventionen für erzeugte Composables. */
object LayoutNames {
    fun composableName(layoutName: String): String {
        val n = layoutName
        val name = when {
            n.startsWith("activity_") -> pascal(n.removePrefix("activity_")) + "Screen"
            n.endsWith("_activity") -> pascal(n.removeSuffix("_activity")) + "Screen"
            n.startsWith("fragment_") -> pascal(n.removePrefix("fragment_")) + "Screen"
            n.endsWith("_fragment") -> pascal(n.removeSuffix("_fragment")) + "Screen"
            n.startsWith("dialog_") -> pascal(n.removePrefix("dialog_")) + "DialogContent"
            n.startsWith("list_item_") -> pascal(n.removePrefix("list_item_")) + "Item"
            n.startsWith("item_") -> pascal(n.removePrefix("item_")) + "Item"
            n.startsWith("row_") -> pascal(n.removePrefix("row_")) + "Item"
            n.endsWith("_item") -> pascal(n.removeSuffix("_item")) + "Item"
            else -> pascal(n)
        }
        val safe = name.ifEmpty { pascal(n) }
        return if (safe.isNotEmpty() && safe.first().isDigit()) "Layout$safe" else safe
    }

    /** Layoutname -> eindeutiger Composable-Name; Kollisionen bekommen `Layout`/Zähler und werden gemeldet. */
    fun resolve(layoutNames: Collection<String>): Pair<Map<String, String>, List<String>> {
        val result = LinkedHashMap<String, String>()
        val used = HashSet<String>()
        val notes = mutableListOf<String>()
        for (n in layoutNames.sorted()) {
            var cand = composableName(n)
            if (!used.add(cand)) {
                val alt = pascal(n).let { if (it.first().isDigit()) "Layout$it" else it } + "Layout"
                var final = alt
                var i = 2
                while (!used.add(final)) final = alt + i++
                notes += "Namenskollision: $n -> $final (statt $cand)"
                cand = final
            }
            result[n] = cand
        }
        return result to notes
    }

    private fun pascal(s: String) = LayoutValues.pascal(s)
}
