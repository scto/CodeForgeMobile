/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

/**
 * Rekursiver Abstiegsparser für die Kotlin-Teilmenge typischer Compose-Screens: Funktionen und Properties auf
 * Dateiebene, Ausdrücke mit Lambdas, Named Arguments, `if`/`when`/`try`, Schleifen, String-Templates, Generics
 * (nur mitgelesen). Klassen/Objekte/Enums in der Zieldatei werden als [UnsupportedDecl] vermerkt.
 */
internal class KtParser(private val source: String, private val base: Int = 0) {
    private val t: List<Token> = KtLexer(source, base).tokenize()
    private var p = 0

    // --- Token-Hilfen ------------------------------------------------------------------------------------------

    private val cur get() = t[p]
    private fun peek(n: Int = 1) = t[minOf(p + n, t.size - 1)]
    private fun next(): Token = t[p].also { if (p < t.size - 1) p++ }
    private fun fail(msg: String, at: Int = cur.offset): Nothing = throw UnsupportedKotlinException(msg, at)

    private fun skipNl() { while (cur.kind == Tok.NEWLINE) p++ }
    private fun skipNlSemi() { while (cur.kind == Tok.NEWLINE || cur.isOp(";")) p++ }

    /** Nächstes Token, das kein Zeilenumbruch ist (ohne zu verbrauchen). */
    private fun peekNl(): Token { var i = p; while (t[i].kind == Tok.NEWLINE) i++; return t[i] }

    private fun expectOp(op: String): Token { skipNlIfClosing(op); if (!cur.isOp(op)) fail("'$op' erwartet, gefunden '${cur.text}'"); return next() }
    private fun skipNlIfClosing(op: String) { if (op == ")" || op == "]" || op == "}" || op == "->" || op == "=" || op == ":" || op == "{") skipNl() }
    private fun acceptOp(op: String): Boolean = if (cur.isOp(op)) { next(); true } else false
    private fun ident(): String { if (cur.kind != Tok.IDENT) fail("Bezeichner erwartet, gefunden '${cur.text}'"); return next().text }

    // --- Datei -------------------------------------------------------------------------------------------------

    fun parseFile(): KtFile {
        var pkg = ""
        val imports = ArrayList<Import>()
        val funs = ArrayList<FunDecl>()
        val props = ArrayList<PropDecl>()
        val unsupported = ArrayList<UnsupportedDecl>()
        skipNlSemi()
        while (cur.isOp("@") && peek().isIdent("file")) { skipAnnotation(); skipNlSemi() }
        if (cur.isIdent("package")) { next(); pkg = qualifiedName(); skipNlSemi() }
        while (cur.isIdent("import")) {
            next()
            val sb = StringBuilder(ident())
            var star = false
            while (cur.isOp(".")) {
                next()
                if (cur.isOp("*")) { next(); star = true; break }
                sb.append('.').append(ident())
            }
            val alias = if (cur.isIdent("as")) { next(); ident() } else null
            imports += Import(sb.toString(), alias, star)
            skipNlSemi()
        }
        while (cur.kind != Tok.EOF) {
            val annotations = annotations()
            val modifiers = modifiers()
            when {
                cur.isIdent("fun") -> funs += funDecl(annotations, modifiers)
                cur.isIdent("val") || cur.isIdent("var") -> props += propDecl()
                cur.isIdent("class") || cur.isIdent("interface") || cur.isIdent("object") || cur.isIdent("typealias") -> {
                    val at = cur.offset
                    val kind = next().text
                    val name = if (cur.kind == Tok.IDENT) cur.text else "?"
                    unsupported += UnsupportedDecl(kind, name, at)
                    skipDeclaration()
                }
                else -> fail("Unerwartetes Token auf Dateiebene: '${cur.text}'")
            }
            skipNlSemi()
        }
        return KtFile(pkg, imports, funs, props, unsupported)
    }

    private fun qualifiedName(): String {
        val sb = StringBuilder(ident())
        while (cur.isOp(".") && peek().kind == Tok.IDENT) { next(); sb.append('.').append(ident()) }
        return sb.toString()
    }

    /** Überspringt eine Klassen-/Objektdeklaration bis zum Ende ihres Rumpfs bzw. ihrer Zeile. */
    private fun skipDeclaration() {
        var depth = 0
        while (cur.kind != Tok.EOF) {
            if (cur.isOp("(") || cur.isOp("{") || cur.isOp("[")) depth++
            if (cur.isOp(")") || cur.isOp("]")) depth--
            if (cur.isOp("}")) { depth--; if (depth == 0) { next(); return } }
            if (cur.kind == Tok.NEWLINE && depth == 0) {
                val n = peekNl()
                if (!n.isOp("{") && !n.isOp(":") && !n.isOp(",") && !n.isIdent("where")) return
            }
            next()
        }
    }

    private fun annotations(): List<String> {
        val result = ArrayList<String>()
        skipNlSemi()
        while (cur.isOp("@")) {
            result += skipAnnotation()
            skipNlSemi()
        }
        return result
    }

    /** Liest eine Annotation (inkl. Argumente) und liefert ihren Namen. */
    private fun skipAnnotation(): String {
        expectOp("@")
        if (cur.kind == Tok.IDENT && peek().isOp(":")) { next(); next() } // Use-site target (file:, param:, …)
        if (cur.isOp("[")) { skipBalanced("[", "]"); return "" }
        val name = qualifiedName()
        if (cur.isOp("<")) skipBalanced("<", ">")
        if (cur.isOp("(")) skipBalanced("(", ")")
        return name
    }

    private fun skipBalanced(open: String, close: String) {
        var depth = 0
        do {
            if (cur.isOp(open)) depth++ else if (cur.isOp(close)) depth--
            if (cur.kind == Tok.EOF) fail("'$close' fehlt")
            next()
        } while (depth > 0)
    }

    private val modifierWords = setOf(
        "private", "internal", "public", "protected", "inline", "noinline", "crossinline", "suspend", "override", "operator",
        "infix", "tailrec", "const", "data", "enum", "sealed", "open", "abstract", "final", "annotation", "value", "inner",
        "lateinit", "external", "expect", "actual", "reified", "fun"
    )

    private fun modifiers(): Set<String> {
        val result = HashSet<String>()
        // Modifikatoren sind weiche Schlüsselwörter: nur, wenn ein weiterer Bezeichner folgt (`value: String` ist ein Name).
        while (cur.kind == Tok.IDENT && cur.text in modifierWords && peek().kind == Tok.IDENT && !(cur.text == "fun" && !peek().isIdent("interface"))) {
            // `enum class` / `data class` / `fun interface`
            result += next().text
            skipNl()
        }
        return result
    }

    private fun typeParams() { if (cur.isOp("<")) skipBalanced("<", ">") }

    private fun funDecl(annotations: List<String>, modifiers: Set<String>): FunDecl {
        val at = cur.offset
        next() // fun
        typeParams()
        // Empfänger: alles bis zum letzten Bezeichner vor '('
        var receiver: TypeRef? = null
        val name: String
        if (cur.kind == Tok.IDENT && peek().isOp("(")) {
            name = ident()
        } else {
            receiver = typeRef(stopBeforeCallName = true)
            expectOp(".")
            name = ident()
        }
        val params = valueParams()
        val returnType = if (cur.isOp(":")) { next(); typeRef() } else null
        if (cur.isIdent("where")) { while (cur.kind != Tok.EOF && !cur.isOp("{") && !cur.isOp("=")) next() }
        val n = peekNl()
        return when {
            n.isOp("{") -> { skipNl(); FunDecl(name, receiver, params, returnType, block(), false, annotations, modifiers, at) }
            n.isOp("=") -> {
                skipNl(); next(); skipNl()
                val e = expression()
                FunDecl(name, receiver, params, returnType, Block(listOf(ExprStmt(e)), e.at), true, annotations, modifiers, at)
            }
            else -> FunDecl(name, receiver, params, returnType, null, false, annotations, modifiers, at)
        }
    }

    private fun valueParams(): List<Param> {
        expectOp("(")
        val result = ArrayList<Param>()
        skipNl()
        while (!cur.isOp(")")) {
            annotations()
            val mods = modifiers()
            var vararg = "vararg" in mods
            if (cur.isIdent("vararg")) { next(); vararg = true }
            if (cur.isIdent("val") || cur.isIdent("var")) next()
            val name = ident()
            val type = if (cur.isOp(":")) { next(); skipNl(); typeRef() } else null
            skipNl()
            val default = if (cur.isOp("=")) { next(); skipNl(); expression() } else null
            result += Param(name, type, default, vararg)
            skipNl()
            if (!acceptOp(",")) break
            skipNl()
        }
        expectOp(")")
        return result
    }

    private fun propDecl(): PropDecl {
        val at = cur.offset
        val isVar = next().text == "var"
        typeParams()
        var receiver: TypeRef? = null
        val name: String
        if (cur.kind == Tok.IDENT && (peek().isOp(":") || peek().isOp("=") || peek().kind == Tok.NEWLINE || peek().isIdent("by") || peek().kind == Tok.EOF)) {
            name = ident()
        } else {
            receiver = typeRef(stopBeforeCallName = false, stopBeforeLastSegment = true)
            expectOp(".")
            name = ident()
        }
        if (cur.isOp(":")) { next(); typeRef() }
        var init: Expr? = null
        var delegate: Expr? = null
        if (cur.isOp("=")) { next(); skipNl(); init = expression() }
        else if (cur.isIdent("by")) { next(); skipNl(); delegate = expression() }
        // Getter auf der nächsten Zeile
        var getter: Block? = null
        var getterExpr = false
        val n = peekNl()
        if (n.isIdent("get") ) {
            skipNl(); next()
            expectOp("("); expectOp(")")
            if (cur.isOp(":")) { next(); typeRef() }
            if (peekNl().isOp("=")) { skipNl(); next(); skipNl(); val e = expression(); getter = Block(listOf(ExprStmt(e)), e.at); getterExpr = true }
            else { skipNl(); getter = block() }
        }
        return PropDecl(name, receiver, isVar, init, getter, getterExpr, delegate, at)
    }

    // --- Typen -------------------------------------------------------------------------------------------------

    /**
     * Liest einen Typ. [stopBeforeCallName]: bei Funktionsempfängern endet der Typ vor dem letzten Bezeichner,
     * auf den `(` folgt; [stopBeforeLastSegment]: bei Property-Empfängern vor dem letzten Segment.
     */
    fun typeRef(stopBeforeCallName: Boolean = false, stopBeforeLastSegment: Boolean = false): TypeRef {
        val annotations = ArrayList<String>()
        while (cur.isOp("@")) annotations += skipAnnotation()
        var suspend = false
        if (cur.isIdent("suspend")) { next(); suspend = true }
        if (cur.isOp("(")) {
            // Funktionstyp oder geklammerter Typ
            val save = p
            val params = ArrayList<TypeRef>()
            next(); skipNl()
            while (!cur.isOp(")")) {
                if (cur.kind == Tok.IDENT && peek().isOp(":")) { next(); next() } // benannte Parameter im Funktionstyp
                params += typeRef(); skipNl()
                if (!acceptOp(",")) break
                skipNl()
            }
            expectOp(")")
            if (cur.isOp("->")) {
                next(); skipNl()
                val result = typeRef()
                return TypeRef("kotlin.Function${params.size}", nullable = false, function = FunctionTypeRef(null, params, result, suspend), annotations = annotations)
            }
            if (params.size == 1 && !suspend) {
                val inner = params[0]
                return if (cur.isOp("?")) { next(); inner.copy(nullable = true, annotations = inner.annotations + annotations) } else inner.copy(annotations = inner.annotations + annotations)
            }
            p = save
            fail("Unbekannte Typ-Syntax")
        }
        val simple = simpleType(stopBeforeCallName, stopBeforeLastSegment)
        // Empfänger-Funktionstyp: A.(B) -> C
        if (cur.isOp(".") && peek().isOp("(")) {
            next()
            val fn = typeRef()
            val f = fn.function ?: fail("Funktionstyp erwartet")
            return fn.copy(
                name = "kotlin.Function${f.params.size + 1}",
                function = f.copy(receiver = simple, suspend = suspend || f.suspend),
                annotations = annotations + fn.annotations
            )
        }
        return simple.copy(annotations = annotations)
    }

    private fun simpleType(stopBeforeCallName: Boolean, stopBeforeLastSegment: Boolean): TypeRef {
        if (cur.isOp("*")) { next(); return TypeRef("*") }
        val sb = StringBuilder(ident())
        var args: List<TypeRef> = emptyList()
        while (true) {
            if (cur.isOp("<")) { args = typeArgs() ?: fail("Typargumente erwartet") }
            if (cur.isOp(".") && peek().kind == Tok.IDENT) {
                if (stopBeforeCallName && peek(2).isOp("(")) break
                if (stopBeforeCallName && peek(2).isOp("<")) break
                if (stopBeforeLastSegment && !peek(2).isOp(".") && !peek(2).isOp("<")) break
                next(); sb.append('.').append(ident())
                continue
            }
            break
        }
        val nullable = acceptOp("?")
        return TypeRef(sb.toString(), args, nullable)
    }

    /** Liest `<…>`-Typargumente; `null`, wenn es keine sind (z. B. Vergleich `a < b`). Position bleibt dann. */
    private fun typeArgs(): List<TypeRef>? {
        val save = p
        return try {
            next()
            val result = ArrayList<TypeRef>()
            while (true) {
                skipNl()
                if (cur.isIdent("out") || cur.isIdent("in")) { if (peek().kind == Tok.IDENT || peek().isOp("(")) next() }
                result += typeRef()
                skipNl()
                if (acceptOp(",")) continue
                if (cur.isOp(">")) { next(); return result }
                p = save; return null
            }
            @Suppress("UNREACHABLE_CODE") null
        } catch (_: UnsupportedKotlinException) { p = save; null }
    }

    // --- Blöcke und Anweisungen ---------------------------------------------------------------------------------

    private fun block(): Block {
        val at = expectOp("{").offset
        val stmts = statementsUntil("}")
        expectOp("}")
        return Block(stmts, at)
    }

    private fun statementsUntil(close: String): List<Stmt> {
        val result = ArrayList<Stmt>()
        skipNlSemi()
        while (!cur.isOp(close) && cur.kind != Tok.EOF) {
            result += statement()
            if (!cur.isOp(close) && cur.kind != Tok.NEWLINE && !cur.isOp(";") && cur.kind != Tok.EOF) fail("Zeilenende erwartet, gefunden '${cur.text}'")
            skipNlSemi()
        }
        return result
    }

    private fun statement(): Stmt {
        while (cur.isOp("@")) { skipAnnotation(); skipNl() }
        val at = cur.offset
        if (cur.kind == Tok.IDENT && (cur.text in setOf("private", "inline", "suspend")) && peek().isIdent("fun")) next()
        return when {
            cur.isIdent("val") || cur.isIdent("var") -> localVar()
            cur.isIdent("fun") -> LocalFun(funDecl(emptyList(), emptySet()))
            cur.isIdent("for") -> {
                next(); expectOp("(")
                val names = if (cur.isOp("(")) { next(); val ns = ArrayList<String>(); while (!cur.isOp(")")) { ns += ident(); if (cur.isOp(":")) { next(); typeRef() }; acceptOp(",") }; next(); ns } else listOf(ident()).also { if (cur.isOp(":")) { next(); typeRef() } }
                if (!cur.isIdent("in")) fail("'in' erwartet")
                next()
                val iterable = expression()
                expectOp(")")
                ForLoop(names, iterable, branchBlock(), at)
            }
            cur.isIdent("while") -> { next(); expectOp("("); val c = expression(); expectOp(")"); WhileLoop(c, branchBlock(), false, at) }
            cur.isIdent("do") -> {
                next(); val body = branchBlock(); skipNl()
                if (!cur.isIdent("while")) fail("'while' erwartet")
                next(); expectOp("("); val c = expression(); expectOp(")")
                WhileLoop(c, body, true, at)
            }
            cur.isIdent("class") || cur.isIdent("object") || cur.isIdent("interface") -> fail("Lokale Klassen werden nicht unterstützt")
            else -> {
                val e = expression()
                val opTok = cur
                if (opTok.kind == Tok.OP && opTok.text in setOf("=", "+=", "-=", "*=", "/=", "%=")) {
                    next(); skipNl()
                    Assign(e, opTok.text, expression(), at)
                } else ExprStmt(e)
            }
        }
    }

    private fun localVar(): Stmt {
        val at = cur.offset
        val isVar = next().text == "var"
        val names = ArrayList<String>()
        if (cur.isOp("(")) {
            next()
            while (!cur.isOp(")")) { names += ident(); if (cur.isOp(":")) { next(); typeRef() }; skipNl(); acceptOp(","); skipNl() }
            next()
        } else names += ident()
        val type = if (cur.isOp(":")) { next(); typeRef() } else null
        var init: Expr? = null
        var delegate: Expr? = null
        if (cur.isOp("=")) { next(); skipNl(); init = expression() }
        else if (cur.isIdent("by")) { next(); skipNl(); delegate = expression() }
        return LocalVar(names, isVar, type, init, delegate, at)
    }

    /** Rumpf von if/else/for/while: Block in `{}` oder eine einzelne Anweisung. */
    private fun branchBlock(): Block {
        skipNl()
        if (cur.isOp("{")) return block()
        val s = statement()
        return Block(listOf(s), s.at)
    }

    // --- Ausdrücke ---------------------------------------------------------------------------------------------

    fun expression(): Expr = disjunction()

    private fun disjunction(): Expr {
        var left = conjunction()
        while (peekNl().isOp("||")) { skipNl(); val at = next().offset; skipNl(); left = Binary("||", left, conjunction(), at) }
        return left
    }

    private fun conjunction(): Expr {
        var left = equality()
        while (peekNl().isOp("&&")) { skipNl(); val at = next().offset; skipNl(); left = Binary("&&", left, equality(), at) }
        return left
    }

    private fun equality(): Expr {
        var left = comparison()
        while (cur.kind == Tok.OP && cur.text in setOf("==", "!=", "===", "!==")) { val op = next(); skipNl(); left = Binary(op.text, left, comparison(), op.offset) }
        return left
    }

    private fun comparison(): Expr {
        var left = namedChecks()
        while (cur.kind == Tok.OP && cur.text in setOf("<", ">", "<=", ">=")) { val op = next(); skipNl(); left = Binary(op.text, left, namedChecks(), op.offset) }
        return left
    }

    private fun namedChecks(): Expr {
        var left = elvis()
        while (true) {
            val negated = cur.isOp("!") && (peek().isIdent("is") || peek().isIdent("in"))
            val kw = if (negated) peek() else cur
            if (!kw.isIdent("is") && !kw.isIdent("in")) break
            val at = cur.offset
            if (negated) next()
            next(); skipNl()
            left = if (kw.text == "is") IsCheck(left, typeRef(), negated, at)
            else Binary(if (negated) "!in" else "in", left, elvis(), at)
        }
        return left
    }

    private fun elvis(): Expr {
        var left = infix()
        while (peekNl().isOp("?:")) { skipNl(); val at = next().offset; skipNl(); left = Binary("?:", left, infix(), at) }
        return left
    }

    private val notInfix = setOf("is", "in", "as", "else", "by", "get", "set", "catch", "finally", "where")

    private fun infix(): Expr {
        var left = range()
        while (cur.kind == Tok.IDENT && cur.text !in notInfix && !peek().isOp(".") ) {
            val name = next()
            skipNl()
            val right = range()
            left = Call(Navigation(left, name.text, false, name.offset), emptyList(), listOf(Arg(null, right)), name.offset)
        }
        return left
    }

    private fun range(): Expr {
        var left = additive()
        while (cur.isOp("..") || cur.isOp("..<")) { val op = next(); skipNl(); left = Binary(op.text, left, additive(), op.offset) }
        return left
    }

    private fun additive(): Expr {
        var left = multiplicative()
        while (cur.isOp("+") || cur.isOp("-")) { val op = next(); skipNl(); left = Binary(op.text, left, multiplicative(), op.offset) }
        return left
    }

    private fun multiplicative(): Expr {
        var left = asExpr()
        while (cur.isOp("*") || cur.isOp("/") || cur.isOp("%")) { val op = next(); skipNl(); left = Binary(op.text, left, asExpr(), op.offset) }
        return left
    }

    private fun asExpr(): Expr {
        var left = prefix()
        while (cur.isIdent("as")) {
            val at = next().offset
            val safe = acceptOp("?")
            left = Cast(left, typeRef(), safe, at)
        }
        return left
    }

    private fun prefix(): Expr {
        if (cur.kind == Tok.OP && cur.text in setOf("-", "+", "!", "++", "--")) {
            val op = next()
            return Unary(op.text, prefix(), op.offset)
        }
        if (cur.isOp("@")) { skipAnnotation(); return prefix() }
        // Label vor Lambda: name@{ … }
        if (cur.kind == Tok.IDENT && peek().isOp("@") && peek(2).isOp("{")) {
            val label = next().text; next()
            return postfix(lambda(label))
        }
        return postfix(primary())
    }

    private fun postfix(start: Expr): Expr {
        var e = start
        while (true) {
            when {
                cur.isOp("(") -> e = Call(e, emptyList(), valueArgs(), e.at).withTrailingLambdas()
                cur.isOp("<") && (e is NameRef || e is Navigation) -> {
                    val save = p
                    val ta = typeArgs()
                    if (ta != null && (cur.isOp("(") || cur.isOp("{") || cur.isOp("::"))) {
                        e = if (cur.isOp("(")) Call(e, ta, valueArgs(), e.at).withTrailingLambdas()
                        else if (cur.isOp("{")) Call(e, ta, emptyList(), e.at).withTrailingLambdas()
                        else e
                    } else { p = save; return e }
                }
                cur.isOp("{") && (e is NameRef || e is Navigation || (e is Call)) && e !is LambdaExpr -> {
                    e = (if (e is Call) e else Call(e, emptyList(), emptyList(), e.at)).withTrailingLambdas()
                }
                cur.isOp(".") || cur.isOp("?.") || (cur.kind == Tok.NEWLINE && (peekNl().isOp(".") || peekNl().isOp("?.")) && !peekNlIsRange()) -> {
                    skipNl()
                    val safe = next().text == "?."
                    skipNl()
                    val nameTok = cur
                    if (nameTok.kind != Tok.IDENT) fail("Name nach '.' erwartet")
                    next()
                    e = Navigation(e, nameTok.text, safe, nameTok.offset)
                }
                cur.isOp("::") -> {
                    next()
                    val name = if (cur.isIdent("class")) next().text else ident()
                    e = CallableRef(e, name, e.at)
                }
                cur.isOp("[") -> {
                    val at = next().offset
                    val idx = ArrayList<Expr>()
                    skipNl()
                    while (!cur.isOp("]")) { idx += expression(); skipNl(); if (!acceptOp(",")) break; skipNl() }
                    expectOp("]")
                    e = IndexExpr(e, idx, at)
                }
                cur.isOp("!!") -> e = Postfix("!!", e, next().offset)
                cur.isOp("++") || cur.isOp("--") -> e = Postfix(cur.text, e, next().offset)
                else -> return e
            }
        }
    }

    private fun peekNlIsRange(): Boolean = false

    /** Hängt nachgestellte Lambdas (`foo(a) { … }`) als letzte Argumente an. */
    private fun Call.withTrailingLambdas(): Call {
        var args = this.args
        while (cur.isOp("{") || (cur.kind == Tok.IDENT && peek().isOp("@") && peek(2).isOp("{"))) {
            val label = if (cur.kind == Tok.IDENT) next().text.also { next() } else null
            args = args + Arg(TRAILING_LAMBDA, lambda(label))
        }
        return copy(args = args)
    }

    private fun valueArgs(): List<Arg> {
        expectOp("(")
        val result = ArrayList<Arg>()
        skipNl()
        while (!cur.isOp(")")) {
            val spread = acceptOp("*")
            val name = if (cur.kind == Tok.IDENT && peek().isOp("=") ) { val n = next().text; next(); skipNl(); n } else null
            result += Arg(name, expression(), spread)
            skipNl()
            if (!acceptOp(",")) break
            skipNl()
        }
        expectOp(")")
        return result
    }

    private fun lambda(label: String?): LambdaExpr {
        val at = expectOp("{").offset
        skipNl()
        val save = p
        var params: List<LambdaParam>? = null
        try {
            val ps = ArrayList<LambdaParam>()
            if (!cur.isOp("->")) {
                while (true) {
                    if (cur.isOp("(")) { // Destrukturierung: als ein Parameter mit Komponenten
                        next(); val comps = ArrayList<String>()
                        while (!cur.isOp(")")) { comps += ident(); if (cur.isOp(":")) { next(); typeRef() }; acceptOp(",") }
                        next()
                        ps += LambdaParam("(" + comps.joinToString(",") + ")", null)
                    } else {
                        val n = ident()
                        val type = if (cur.isOp(":")) { next(); typeRef() } else null
                        ps += LambdaParam(n, type)
                    }
                    skipNl()
                    if (!acceptOp(",")) break
                    skipNl()
                }
            }
            if (cur.isOp("->")) { next(); params = ps } else p = save
        } catch (_: UnsupportedKotlinException) { p = save }
        val stmts = statementsUntil("}")
        expectOp("}")
        return LambdaExpr(params, Block(stmts, at), label, at)
    }

    private fun primary(): Expr {
        val tok = cur
        return when (tok.kind) {
            Tok.INT, Tok.LONG, Tok.FLOAT, Tok.DOUBLE, Tok.CHAR -> { next(); Literal(tok.value, tok.offset) }
            Tok.STRING -> { next(); stringTemplate(tok) }
            Tok.OP -> when (tok.text) {
                "(" -> { next(); skipNl(); val e = expression(); expectOp(")"); e }
                "{" -> lambda(null)
                "::" -> { next(); CallableRef(null, ident(), tok.offset) }
                else -> fail("Unerwartetes Zeichen '${tok.text}'")
            }
            Tok.IDENT -> when (tok.text) {
                "null" -> { next(); Literal(null, tok.offset) }
                "true" -> { next(); Literal(true, tok.offset) }
                "false" -> { next(); Literal(false, tok.offset) }
                "this" -> {
                    next()
                    val label = if (cur.isOp("@")) { next(); ident() } else null
                    ThisRef(label, tok.offset)
                }
                "if" -> ifExpr()
                "when" -> whenExpr()
                "try" -> tryExpr()
                "return" -> {
                    next()
                    val label = if (cur.isOp("@")) { next(); ident() } else null
                    val value = if (cur.kind == Tok.NEWLINE || cur.isOp("}") || cur.isOp(")") || cur.isOp(";") || cur.kind == Tok.EOF) null else expression()
                    ReturnExpr(value, label, tok.offset)
                }
                "break", "continue" -> { next(); if (cur.isOp("@")) { next(); ident() }; JumpExpr(tok.text == "break", tok.offset) }
                "throw" -> { next(); ThrowExpr(expression(), tok.offset) }
                "object" -> fail("Objektausdrücke werden nicht unterstützt")
                "super" -> fail("'super' wird nicht unterstützt")
                else -> { next(); NameRef(tok.text, tok.offset) }
            }
            else -> fail("Ausdruck erwartet, gefunden '${tok.text}'")
        }
    }

    private fun stringTemplate(tok: Token): Expr {
        val parts = tok.parts.map { part ->
            when (part) {
                is StrPart.Text -> part.text
                is StrPart.Expr -> KtParser(part.source, part.offset).expressionOnly()
            }
        }
        return StringTemplate(parts, tok.offset)
    }

    /** Liest genau einen Ausdruck (für String-Templates). */
    fun expressionOnly(): Expr {
        skipNl()
        val e = expression()
        skipNl()
        if (cur.kind != Tok.EOF) fail("Unerwartetes Token im Template: '${cur.text}'")
        return e
    }

    private fun ifExpr(): Expr {
        val at = next().offset
        expectOp("(")
        val cond = expression()
        expectOp(")")
        val then = branchBlock()
        val save = p
        skipNl()
        val otherwise = if (cur.isIdent("else")) { next(); branchBlock() } else { p = save; null }
        return IfExpr(cond, then, otherwise, at)
    }

    private fun whenExpr(): Expr {
        val at = next().offset
        var subject: Expr? = null
        var subjectName: String? = null
        if (cur.isOp("(")) {
            next(); skipNl()
            if (cur.isIdent("val")) { next(); subjectName = ident(); if (cur.isOp(":")) { next(); typeRef() }; expectOp("=") }
            subject = expression()
            expectOp(")")
        }
        skipNl()
        expectOp("{")
        val branches = ArrayList<WhenBranch>()
        skipNlSemi()
        while (!cur.isOp("}")) {
            if (cur.isIdent("else")) {
                next(); expectOp("->")
                branches += WhenBranch(emptyList(), branchBlock(), true)
            } else {
                val conds = ArrayList<WhenCondition>()
                while (true) {
                    skipNl()
                    val negated = cur.isOp("!") && (peek().isIdent("is") || peek().isIdent("in"))
                    if (negated) next()
                    conds += when {
                        cur.isIdent("is") -> { next(); WhenCondition.Is(typeRef(), negated) }
                        cur.isIdent("in") -> { next(); WhenCondition.In(expression(), negated) }
                        else -> WhenCondition.Value(expression())
                    }
                    skipNl()
                    if (!acceptOp(",")) break
                }
                expectOp("->")
                branches += WhenBranch(conds, branchBlock(), false)
            }
            skipNlSemi()
        }
        expectOp("}")
        return WhenExpr(subject, subjectName, branches, at)
    }

    private fun tryExpr(): Expr {
        val at = next().offset
        skipNl()
        val body = block()
        val catches = ArrayList<Pair<Pair<String, TypeRef>, Block>>()
        var fin: Block? = null
        while (true) {
            val save = p
            skipNl()
            if (cur.isIdent("catch")) {
                next(); expectOp("(")
                val n = ident(); expectOp(":"); val type = typeRef(); expectOp(")")
                skipNl()
                catches += (n to type) to block()
            } else if (cur.isIdent("finally")) {
                next(); skipNl(); fin = block()
            } else { p = save; break }
        }
        return TryExpr(body, catches, fin, at)
    }

    companion object {
        /** Name-Markierung für nachgestellte Lambdas (gehören immer zum letzten Parameter). */
        const val TRAILING_LAMBDA = "\u0000trailing"

        /** Zeilennummer (1-basiert) eines Offsets für Fehlermeldungen. */
        fun lineOf(source: String, offset: Int): Int = if (offset < 0) 0 else source.take(offset).count { it == '\n' } + 1
    }
}
