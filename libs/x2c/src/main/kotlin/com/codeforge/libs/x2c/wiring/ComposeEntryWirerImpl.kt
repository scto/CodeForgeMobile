package com.codeforge.libs.x2c.wiring

import com.codeforge.core.domain.model.ComposeWireResult
import com.codeforge.core.domain.model.WireReviewHint
import com.codeforge.core.domain.model.WireStatus
import com.codeforge.core.domain.model.WireTarget
import com.codeforge.core.domain.repository.ComposeEntryWirer

/** Kriterien der Stufe 2 an einer Stelle (Dialog und Bericht zeigen sie an). */
object Stage2Criteria {
    val RULES: List<String> = listOf(
        "Kotlin-Datei (Java: Task 50 zuerst, wird nie angefasst)",
        "genau eine Klasse mit bekannter Basisklasse (AppCompatActivity, ComponentActivity, FragmentActivity, Fragment)",
        "Activity: genau ein `setContentView(R.layout.<name>)` mit dem gewählten Layout",
        "Fragment: genau ein `inflater.inflate(R.layout.<name>, …)` in `onCreateView`",
        "kein `findViewById`, kein ViewBinding/Data Binding/Synthetics, keine weitere `R.layout`-Verwendung",
        "kein `LayoutInflater.from`, kein `Fragment(R.layout…)`-Konstruktor"
    )
}

class ComposeEntryWirerImpl : ComposeEntryWirer {

    override fun wire(fileName: String, content: String, target: WireTarget): ComposeWireResult {
        if (fileName.endsWith(".java")) return skip("Java-Datei: wird nicht angefasst (Task 50 zuerst)")
        if (!fileName.endsWith(".kt")) return skip("Keine Kotlin-Datei")
        val code = blankCommentsAndStrings(content)

        val classes = CLASS_DECL.findAll(code).map { m ->
            val base = Regex("""([\w.]+)\s*\(""").find(m.groupValues[2])?.groupValues?.get(1)?.substringAfterLast('.').orEmpty()
            m.groupValues[1] to base
        }.toList()
        val known = classes.filter { it.second in ACTIVITY_BASES || it.second in FRAGMENT_BASES }
        if (known.isEmpty()) {
            val other = classes.firstOrNull { it.second.isNotEmpty() }?.second
            return skip(if (other != null) "Basisklasse `$other` ist nicht bekannt (muss ComponentActivity/AppCompatActivity/Fragment sein)" else "Keine Activity/Fragment-Klasse gefunden")
        }
        if (known.size > 1) return skip("Mehrere Activity/Fragment-Klassen in einer Datei")
        val isActivity = known[0].second in ACTIVITY_BASES

        for ((re, why) in OBSTACLES) re.find(code)?.let { return skip("$why (Zeile ${lineOf(code, it.range.first)})") }
        if (FRAGMENT_LAYOUT_CTOR.containsMatchIn(code)) return skip("Fragment(R.layout…)-Konstruktor: bitte von Hand umstellen")

        val layoutRefs = Regex("""R\.layout\.(\w+)""").findAll(code).toList()
        if (layoutRefs.any { it.groupValues[1] != target.layoutName }) return skip("Weiteres Layout in dieser Datei verwendet")

        val screenCall = "${target.composableName}()"
        val wrapOpen = target.themeName ?: "MaterialTheme"
        val newImports = sortedSetOf<String>()
        val updated: String
        if (isActivity) {
            val m = Regex("""setContentView\(\s*R\.layout\.${Regex.escape(target.layoutName)}\s*\)""").findAll(code).toList()
            if (m.isEmpty()) return skip("`setContentView(R.layout.${target.layoutName})` nicht gefunden")
            if (m.size > 1) return skip("Mehrere setContentView-Aufrufe")
            if (Regex("""setContentView\(""").findAll(code).count() > 1) return skip("Mehrere setContentView-Aufrufe")
            val r = m[0].range
            val indent = lineIndent(content, r.first)
            val block = buildString {
                appendLine("setContent {")
                appendLine("$indent    $wrapOpen {")
                appendLine("$indent        $screenCall")
                appendLine("$indent    }")
                append("$indent}")
            }
            newImports += "androidx.activity.compose.setContent"
            updated = content.replaceRange(r, block)
        } else {
            val m = Regex("""inflater\s*\.\s*inflate\(\s*R\.layout\.${Regex.escape(target.layoutName)}\s*,[^)]*\)""").findAll(code).toList()
            if (m.isEmpty()) return skip("`inflater.inflate(R.layout.${target.layoutName}, …)` nicht gefunden")
            if (m.size > 1 || Regex("""\.inflate\(""").findAll(code).count() > 1) return skip("Mehrere inflate-Aufrufe")
            if (!code.contains("onCreateView")) return skip("inflate außerhalb von onCreateView")
            val r = m[0].range
            val indent = lineIndent(content, r.first)
            val block = buildString {
                appendLine("ComposeView(requireContext()).apply {")
                appendLine("$indent    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)")
                appendLine("$indent    setContent {")
                appendLine("$indent        $wrapOpen {")
                appendLine("$indent            $screenCall")
                appendLine("$indent        }")
                appendLine("$indent    }")
                append("$indent}")
            }
            newImports += "androidx.compose.ui.platform.ComposeView"
            newImports += "androidx.compose.ui.platform.ViewCompositionStrategy"
            updated = content.replaceRange(r, block)
        }
        if (target.themeName == null) newImports += "androidx.compose.material3.MaterialTheme"
        else if (target.themePackage.isNotEmpty() && target.themePackage != packageOf(content)) newImports += "${target.themePackage}.${target.themeName}"
        if (target.composablePackage.isNotEmpty() && target.composablePackage != packageOf(content)) {
            newImports += "${target.composablePackage}.${target.composableName}"
        }

        val withImports = addImports(updated, newImports)
        return ComposeWireResult(
            status = WireStatus.WIRED,
            newContent = withImports.first,
            reviewHints = reviewHints(code),
            addedImports = withImports.second
        )
    }

    private fun skip(reason: String) = ComposeWireResult(status = WireStatus.SKIPPED, reason = reason)

    private fun reviewHints(code: String): List<WireReviewHint> {
        val out = mutableListOf<WireReviewHint>()
        for ((re, msg) in REVIEW) re.findAll(code).forEach { out += WireReviewHint(lineOf(code, it.range.first), msg) }
        // XML-`android:onClick`-Methoden: fun name(view: View)
        Regex("""fun\s+(\w+)\s*\(\s*\w+\s*:\s*(?:android\.view\.)?View\s*\)""").findAll(code).forEach {
            out += WireReviewHint(lineOf(code, it.range.first), "Methode `${it.groupValues[1]}(View)` war evtl. per android:onClick angebunden; jetzt von Hand an die Composable verdrahten")
        }
        return out.sortedBy { it.line }
    }

    private fun addImports(src: String, imports: Set<String>): Pair<String, List<String>> {
        val existing = Regex("""(?m)^import\s+([\w.*]+)""").findAll(src).map { it.groupValues[1] }.toSet()
        val toAdd = imports.filter { it !in existing }
        if (toAdd.isEmpty()) return src to emptyList()
        val lines = toAdd.joinToString("\n") { "import $it" }
        val last = Regex("""(?m)^import\s+[^\n]*$""").findAll(src).lastOrNull()
        val out = if (last != null) {
            src.substring(0, last.range.last + 1) + "\n" + lines + src.substring(last.range.last + 1)
        } else {
            val pkg = Regex("""(?m)^package\s+[^\n]*$""").find(src)
            if (pkg != null) src.substring(0, pkg.range.last + 1) + "\n\n" + lines + src.substring(pkg.range.last + 1)
            else lines + "\n\n" + src
        }
        return out to toAdd
    }

    private fun packageOf(src: String) = Regex("""(?m)^package\s+([\w.]+)""").find(src)?.groupValues?.get(1).orEmpty()

    private fun lineOf(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1

    private fun lineIndent(text: String, index: Int): String {
        val start = text.lastIndexOf('\n', index - 1) + 1
        return text.substring(start, index).takeWhile { it == ' ' || it == '\t' }
    }

    /** Kommentare und String-Inhalte werden durch Leerzeichen ersetzt; Positionen und Zeilen bleiben gleich. */
    internal fun blankCommentsAndStrings(src: String): String {
        val sb = StringBuilder(src)
        var i = 0
        val n = src.length
        fun blank(from: Int, to: Int) { for (k in from until minOf(to, n)) if (sb[k] != '\n') sb[k] = ' ' }
        while (i < n) {
            when {
                src.startsWith("//", i) -> { val e = src.indexOf('\n', i).let { if (it < 0) n else it }; blank(i, e); i = e }
                src.startsWith("/*", i) -> { val e = src.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }; blank(i, e); i = e }
                src.startsWith("\"\"\"", i) -> { val e = src.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }; blank(i + 3, e - 3); i = e }
                src[i] == '"' -> {
                    var j = i + 1
                    while (j < n && src[j] != '"' && src[j] != '\n') { if (src[j] == '\\') j++; j++ }
                    blank(i + 1, j); i = j + 1
                }
                else -> i++
            }
        }
        return sb.toString()
    }

    companion object {
        private val ACTIVITY_BASES = setOf("AppCompatActivity", "ComponentActivity", "FragmentActivity")
        private val FRAGMENT_BASES = setOf("Fragment")
        private val CLASS_DECL = Regex("""(?m)^[ \t]*(?:(?:open|abstract|internal|public|private)\s+)*class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*([^{\n]*)""")
        private val FRAGMENT_LAYOUT_CTOR = Regex("""Fragment\(\s*R\.layout\.""")
        private val OBSTACLES = listOf(
            Regex("""\bfindViewById\b""") to "findViewById",
            Regex("""\w+Binding\b""") to "ViewBinding/Data Binding",
            Regex("""DataBindingUtil""") to "Data Binding",
            Regex("""kotlinx\.android\.synthetic""") to "Kotlin Synthetics",
            Regex("""LayoutInflater\.from""") to "LayoutInflater.from",
            Regex("""\bsetSupportActionBar\b""") to "Toolbar/ActionBar-Zugriff (setSupportActionBar)"
        )
        private val REVIEW = listOf(
            Regex("""\bonCreateOptionsMenu\b""") to "Optionsmenü: ActionBar/Menü gehört bisher zur View-Welt, bitte prüfen",
            Regex("""\bonOptionsItemSelected\b""") to "Optionsmenü-Auswahl bitte prüfen",
            Regex("""\bsupportActionBar\b""") to "ActionBar-Zugriff bitte prüfen",
            Regex("""\brequestWindowFeature\b""") to "Fensterfeature vor dem Inhalt bitte prüfen",
            Regex("""\bsetTheme\b""") to "setTheme: Theme-Wechsel greift nicht auf Compose-Inhalt, bitte prüfen"
        )
    }
}
