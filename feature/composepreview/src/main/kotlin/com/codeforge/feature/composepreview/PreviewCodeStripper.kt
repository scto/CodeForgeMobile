/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

/**
 * Entfernt Code, den die Vorschau weder braucht noch kompilieren kann:
 *
 *  * Klassen, die von `ViewModel`, `Application` oder einer `Activity` erben oder mit Hilt-Annotationen versehen sind
 *    (`@HiltViewModel`, `@AndroidEntryPoint`, `@HiltAndroidApp`, `@Module`). Der Rest der Datei bleibt, z. B. UI-Zustand
 *    und Ereignisse, die neben dem ViewModel stehen.
 *  * Top-Level-Funktionen, die ein ViewModel beschaffen (`hiltViewModel()`, `koinViewModel()`, `viewModel()`) oder eine
 *    entfernte Klasse benutzen – typischerweise die „Route“ vor einem zustandslosen Screen.
 *  * DI-Annotationen (`@Inject`, `@Singleton`, …), deren Bibliotheken in der Vorschau-Laufzeit fehlen.
 */
internal object PreviewCodeStripper {

    /** Ergebnis: bereinigter Text und die Namen der entfernten Deklarationen. */
    data class Stripped(val text: String, val removedNames: Set<String>)

    private val UNAVAILABLE_ANNOTATIONS = setOf("HiltViewModel", "AndroidEntryPoint", "HiltAndroidApp", "Module", "InstallIn", "HiltWorker")
    private val UNAVAILABLE_SUPERTYPES =
        Regex("""\b(?:AndroidViewModel|ViewModel|Application|ComponentActivity|AppCompatActivity|FragmentActivity|Activity|Service|BroadcastReceiver)\s*\(""")
    private val CLASS_HEAD = Regex(
        """^((?:@[\w.]+(?:\([^)\n]*\))?[ \t]*\n?)*)(?:(?:public|private|internal|protected|open|abstract|final|data|sealed|inner|value|enum|annotation)[ \t]+)*(class|object)[ \t]+(\w+)""",
        RegexOption.MULTILINE
    )
    private val FUN_HEAD = Regex(
        """^(?:(?:public|private|internal|protected|inline|suspend|operator|infix|tailrec|override)[ \t]+)*fun\b""",
        RegexOption.MULTILINE
    )
    private val VIEW_MODEL_LOOKUP = Regex("""\b(?:hiltViewModel|koinViewModel|viewModel)\s*(?:<[^>]*>)?\s*\(""")
    private val DI_ANNOTATIONS = Regex(
        """@(?:param:|field:|get:)?(?:javax\.inject\.|dagger\.hilt\.android\.qualifiers\.)?(?:Inject|Singleton|Provides|Binds|IntoSet|IntoMap|ApplicationContext|ActivityContext|Assisted|AssistedInject|AssistedFactory)\b(?:\([^)]*\))?[ \t]*"""
    )
    private val IMPORT = Regex("""^[ \t]*import[ \t]+([\w.]+)(?:[ \t]+as[ \t]+(\w+))?[ \t]*;?[ \t]*\r?\n?""", RegexOption.MULTILINE)
    private val UNAVAILABLE_IMPORT_PREFIXES = listOf(
        "androidx.hilt.", "dagger.", "javax.inject.", "org.koin.", "androidx.lifecycle.viewmodel.compose.viewModel"
    )

    /** Klassen ohne Vorschau-Nutzen entfernen (siehe Klassenbeschreibung). */
    fun removeUnavailableClasses(text: String): Stripped {
        var result = text
        val removed = linkedSetOf<String>()
        var from = 0
        while (true) {
            val head = CLASS_HEAD.find(result, from) ?: break
            val annotations = head.groupValues[1]
            val name = head.groupValues[3]
            val bodyOpen = findBodyOpen(result, head.range.last + 1)
            val headerEnd = bodyOpen ?: endOfDeclarationHeader(result, head.range.last + 1)
            val header = result.substring(head.range.last + 1, headerEnd)
            val annotated = Regex("""@(?:[\w.]+\.)?(\w+)""").findAll(annotations).any { it.groupValues[1] in UNAVAILABLE_ANNOTATIONS }
            val supertypePart = supertypeList(header)
            if (annotated || (supertypePart != null && UNAVAILABLE_SUPERTYPES.containsMatchIn(supertypePart))) {
                val end = if (bodyOpen != null) PreviewSourcePreparer.matchingBrace(result, bodyOpen).let { if (it < 0) result.length - 1 else it } else headerEnd - 1
                val start = withLeadingKDoc(result, head.range.first)
                // Konstanten im companion object (z. B. `PlayerViewModel.SKIP_SEC`) bleiben als gleichnamiges object erhalten.
                val companion = bodyOpen?.let { publicCompanionBody(result.substring(it, end + 1)) }
                val replacement = if (companion != null) "object $name {$companion}\n" else ""
                result = result.substring(0, start) + replacement + result.substring(end + 1)
                if (companion == null) removed += name
                from = start + replacement.length
            } else {
                from = head.range.last + 1
            }
        }
        return Stripped(result, removed)
    }

    /** Top-Level-Funktionen entfernen, die ein ViewModel beschaffen oder [unavailable] benutzen. */
    fun removeFunctionsUsing(text: String, unavailable: Set<String>): Stripped {
        val names = unavailable.filter { it.isNotBlank() }
        val usesUnavailable = if (names.isEmpty()) null else Regex("""\b(?:${names.joinToString("|") { Regex.escape(it) }})\b""")
        var result = text
        val removed = linkedSetOf<String>()
        var from = 0
        while (true) {
            val head = FUN_HEAD.find(result, from) ?: break
            val end = functionEnd(result, head.range.last + 1)
            val start = withLeadingAnnotations(result, head.range.first)
            val declaration = result.substring(head.range.first, end)
            if (VIEW_MODEL_LOOKUP.containsMatchIn(declaration) || usesUnavailable?.containsMatchIn(declaration) == true) {
                Regex("""fun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)""").find(declaration)?.let { removed += it.groupValues[1] }
                result = result.removeRange(start, end)
                from = start
            } else {
                from = end.coerceAtLeast(head.range.last + 1)
            }
        }
        return Stripped(result, removed)
    }

    /** DI-Annotationen und Importe nicht verfügbarer Bibliotheken oder entfernter Deklarationen entfernen. */
    fun removeUnavailableReferences(text: String, removedNames: Set<String>): String {
        val withoutAnnotations = DI_ANNOTATIONS.replace(text, "")
        return IMPORT.replace(withoutAnnotations) { m ->
            val fqn = m.groupValues[1]
            val simple = m.groupValues[2].ifEmpty { fqn.substringAfterLast('.') }
            val drop = UNAVAILABLE_IMPORT_PREFIXES.any { fqn.startsWith(it) } || simple in removedNames
            if (drop) "" else m.value
        }
    }

    /** Alles zusammen: Klassen, dann Funktionen (mit allen bekannten [unavailable] Namen), dann Importe. */
    fun strip(text: String, unavailable: Set<String> = emptySet()): Stripped {
        val classes = removeUnavailableClasses(text)
        val allUnavailable = unavailable + classes.removedNames
        val functions = removeFunctionsUsing(classes.text, allUnavailable)
        val removed = classes.removedNames + functions.removedNames
        val cleaned = removeUnavailableReferences(functions.text, allUnavailable + removed)
        return Stripped(removeImportsOnlyUsedBy(text, cleaned), removed)
    }

    private val IDENTIFIER = Regex("""\b[A-Za-z_]\w*\b""")

    /**
     * Importe entfernen, die nur der entfernte Code brauchte (z. B. `ViewModel`, `viewModelScope`, Use Cases);
     * alle übrigen bleiben, auch implizit genutzte wie `getValue` für `by`.
     */
    private fun removeImportsOnlyUsedBy(original: String, cleaned: String): String {
        val before = IDENTIFIER.findAll(IMPORT.replace(original, "")).map { it.value }.toSet()
        val after = IDENTIFIER.findAll(IMPORT.replace(cleaned, "")).map { it.value }.toSet()
        val gone = before - after
        if (gone.isEmpty()) return cleaned
        return IMPORT.replace(cleaned) { m ->
            val simple = m.groupValues[2].ifEmpty { m.groupValues[1].substringAfterLast('.') }
            if (simple in gone) "" else m.value
        }
    }

    // --- Hilfsfunktionen -------------------------------------------------------------------------------------------

    private val COMPANION = Regex("""^([ \t]*)((?:public|internal|private|protected)[ \t]+)?companion[ \t]+object(?:[ \t]+\w+)?[ \t]*\{""", RegexOption.MULTILINE)

    /** Rumpf eines nicht privaten `companion object` im Klassenrumpf [body] (inkl. `{`…`}`), sonst `null`. */
    private fun publicCompanionBody(body: String): String? {
        val m = COMPANION.find(body) ?: return null
        if (m.groupValues[2].trim() in setOf("private", "protected")) return null
        val open = m.range.last
        val close = PreviewSourcePreparer.matchingBrace(body, open)
        if (close < 0) return null
        return body.substring(open + 1, close).trimIndent().prependIndent("    ").let { "\n$it\n" }
    }

    /** Erste `{` auf Klammertiefe 0 nach [from], solange der Kopf nicht endet; `null`, wenn die Klasse keinen Rumpf hat. */
    private fun findBodyOpen(s: String, from: Int): Int? {
        var depth = 0
        var i = from
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> i = skipString(s, i)
                c == '(' -> depth++
                c == ')' -> depth--
                c == '{' && depth <= 0 -> return i
                c == '\n' && depth <= 0 && startsNewDeclaration(s, i + 1) -> return null
            }
            i++
        }
        return null
    }

    /** Ende eines Klassenkopfs ohne Rumpf: Beginn der nächsten Top-Level-Deklaration oder Dateiende. */
    private fun endOfDeclarationHeader(s: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < s.length) {
            when (s[i]) {
                '"' -> i = skipString(s, i)
                '(' -> depth++
                ')' -> depth--
                '\n' -> if (depth <= 0) return i + 1
            }
            i++
        }
        return s.length
    }

    /** Teil nach dem `:` der Supertypen (hinter dem primären Konstruktor); `null`, wenn keine Supertypen. */
    private fun supertypeList(header: String): String? {
        var depth = 0
        for ((i, c) in header.withIndex()) {
            when {
                c == '(' || c == '<' -> depth++
                c == ')' || (c == '>' && header.getOrNull(i - 1) != '-') -> depth--
                c == ':' && depth == 0 -> return header.substring(i + 1)
            }
        }
        return null
    }

    /** Ende (exklusiv) einer Funktion ab [from] (hinter `fun`). */
    private fun functionEnd(s: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> i = skipString(s, i)
                s.startsWith("//", i) -> i = s.indexOf('\n', i).let { if (it < 0) s.length else it - 1 }
                s.startsWith("/*", i) -> i = s.indexOf("*/", i).let { if (it < 0) s.length else it + 1 }
                c == '(' -> depth++
                c == ')' -> depth--
                c == '{' && depth == 0 -> {
                    val close = PreviewSourcePreparer.matchingBrace(s, i)
                    return if (close < 0) s.length else lineEnd(s, close)
                }
                c == '=' && depth == 0 && s.getOrNull(i + 1) != '=' && s.getOrNull(i - 1) != '=' && s.getOrNull(i - 1) != '!' -> {
                    return expressionEnd(s, i + 1)
                }
                c == '\n' && depth == 0 && startsNewDeclaration(s, i + 1) -> return i + 1
            }
            i++
        }
        return s.length
    }

    /** Ende eines Ausdrucksrumpfs: nächste Zeile, die in Spalte 0 eine neue Deklaration beginnt. */
    private fun expressionEnd(s: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> i = skipString(s, i)
                c == '(' || c == '{' || c == '[' -> depth++
                c == ')' || c == '}' || c == ']' -> depth--
                c == '\n' && depth <= 0 && startsNewDeclaration(s, i + 1) -> return i + 1
            }
            i++
        }
        return s.length
    }

    private fun startsNewDeclaration(s: String, at: Int): Boolean {
        if (at >= s.length) return true
        val c = s[at]
        return c.isLetter() || c == '@' || c == '/'
    }

    private fun lineEnd(s: String, at: Int): Int = s.indexOf('\n', at).let { if (it < 0) s.length else it + 1 }

    private fun skipString(s: String, at: Int): Int {
        if (s.startsWith("\"\"\"", at)) return s.indexOf("\"\"\"", at + 3).let { if (it < 0) s.length else it + 2 }
        var i = at + 1
        while (i < s.length && s[i] != '"' && s[i] != '\n') {
            if (s[i] == '\\') i++
            i++
        }
        return i
    }

    /** Annotationszeilen und KDoc direkt oberhalb von [start] mit einbeziehen. */
    private fun withLeadingAnnotations(s: String, start: Int): Int {
        var begin = start
        while (true) {
            val prevLineEnd = begin - 1
            if (prevLineEnd <= 0) return withLeadingKDoc(s, begin)
            val prevLineStart = s.lastIndexOf('\n', prevLineEnd - 1) + 1
            val line = s.substring(prevLineStart, prevLineEnd)
            if (line.startsWith("@")) begin = prevLineStart else return withLeadingKDoc(s, begin)
        }
    }

    private fun withLeadingKDoc(s: String, start: Int): Int {
        val before = s.substring(0, start).trimEnd(' ', '\t', '\n', '\r')
        if (!before.endsWith("*/")) return start
        val open = before.lastIndexOf("/**")
        if (open < 0) return start
        val lineStart = s.lastIndexOf('\n', open - 1) + 1
        return if (s.substring(lineStart, open).isBlank()) lineStart else start
    }
}
