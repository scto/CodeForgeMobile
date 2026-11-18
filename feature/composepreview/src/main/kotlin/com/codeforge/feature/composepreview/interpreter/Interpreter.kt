/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import androidx.compose.runtime.Composer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmType
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isNullable

/** Kontrollfluss-Signale (ohne Stacktrace, billig). */
internal class ReturnSignal(val value: Any?, val label: String?) : RuntimeException(null, null, false, false)
internal class BreakSignal : RuntimeException(null, null, false, false)
internal class ContinueSignal : RuntimeException(null, null, false, false)

/** Variable im Gültigkeitsbereich; [delegate] für `by`-Properties (z. B. `by remember { mutableStateOf(…) }`). */
internal class Slot(var value: Any?, val mutable: Boolean, val delegate: Any? = null)

internal class Env(val parent: Env?) {
    private val vars = HashMap<String, Slot>()
    fun define(name: String, slot: Slot) { vars[name] = slot }
    fun lookup(name: String): Slot? = vars[name] ?: parent?.lookup(name)
}

/** Ausführungskontext: Variablen, implizite Empfänger (innerster zuerst) und der Composer (falls in einer Komposition). */
internal class Frame(val env: Env, val receivers: List<Any?>, val composer: Composer?, val returnLabel: String?)

/** Interpretierte Funktion der Zieldatei (auch lokal); aufrufbar als Wert. */
internal class InterpFn(val decl: FunDecl, val closure: Env?, val closureReceivers: List<Any?>) : CallableValue

/** Interpretiertes Lambda samt Umgebung. */
internal class InterpLambda(
    val expr: LambdaExpr,
    val env: Env,
    val receivers: List<Any?>,
    val name: String?,
    /** Composer der Stelle, an der das Lambda entstand: Inline-Funktionen (forEach, let, …) rufen es synchron dort auf. */
    val creationComposer: Composer? = null,
) : CallableValue

/** Gebundene Funktionsreferenz auf ein Objekt (`obj::method`) oder eine Bibliotheksfunktion. */
internal class BoundRef(val receiver: Any?, val name: String) : CallableValue

/** Nativ aufgelöstes `R` der Zieldatei (ohne Gradle-Build). */
internal data class NativeR(val pkg: String)

/** Paketpfad in voll qualifizierten Namen (`androidx.compose.ui.graphics.Color(…)`). */
internal data class PackageRef(val path: String)
internal data class NativeRType(val type: String)

/** Argument nach dem Auswerten. */
internal class EvalArg(val name: String?, val value: Any?, val spread: Boolean)

private object NotProvided

/**
 * Tree-walking-Interpreter für die Zieldatei einer Compose-Vorschau. Bibliotheken und andere Projektdateien laufen
 * als echter, kompilierter Code und werden per Reflection aufgerufen (inkl. Compose-Aufrufkonvention: `Composer`,
 * `$changed`, `$default`, Wertklassen). Nicht Unterstütztes wirft [UnsupportedKotlinException].
 */
internal class Interpreter(
    val file: KtFile,
    val reflect: KotlinReflect,
    val resources: PreviewResourceTable,
    /** `R` kommt als echte Klasse aus einem Gradle-Build (IDs passen zu den geladenen Projekt-Ressourcen). */
    private val compiledR: Boolean = false,
) {
    private val functions: Map<String, List<FunDecl>> = file.functions.groupBy { it.name }
    private val propertyDecls: Map<String, PropDecl> = file.properties.associateBy { it.name }
    private val propertyValues = HashMap<String, Any?>()
    private val unsupportedNames = file.unsupported.associateBy { it.name }
    internal val compose = ComposeSupport(this)

    // --- Einstieg ---------------------------------------------------------------------------------------------

    /** Ruft eine parameterlose (bzw. nur mit Defaults) Funktion der Datei auf. */
    fun invokeRoot(name: String, composer: Composer?): Any? {
        val fn = functions[name]?.firstOrNull() ?: throw UnsupportedKotlinException("Funktion '$name' nicht gefunden")
        return callInterpreted(InterpFn(fn, null, emptyList()), null, emptyList(), composer, fn.at)
    }

    // --- Anweisungen -------------------------------------------------------------------------------------------

    fun execBlock(block: Block, frame: Frame): Any? {
        var last: Any? = Unit
        for (s in block.statements) last = exec(s, frame)
        return last
    }

    private fun exec(s: Stmt, f: Frame): Any? = when (s) {
        is ExprStmt -> eval(s.expr, f)
        is LocalVar -> {
            if (s.delegate != null) {
                val d = eval(s.delegate, f)
                f.env.define(s.names[0], Slot(null, s.isVar, d))
            } else {
                val v = s.init?.let { eval(it, f) }
                if (s.names.size == 1) f.env.define(s.names[0], Slot(v, s.isVar))
                else s.names.forEachIndexed { i, n -> if (n != "_") f.env.define(n, Slot(component(v, i + 1), s.isVar)) }
            }
            Unit
        }
        is Assign -> { assign(s, f); Unit }
        is ForLoop -> { forLoop(s, f); Unit }
        is WhileLoop -> { whileLoop(s, f); Unit }
        is LocalFun -> { f.env.define(s.decl.name, Slot(InterpFn(s.decl, f.env, f.receivers), false)); Unit }
    }

    private fun forLoop(s: ForLoop, f: Frame) {
        val iterable = eval(s.iterable, f)
        val it: Iterator<*> = iteratorOf(iterable)
        while (it.hasNext()) {
            val item = it.next()
            val env = Env(f.env)
            if (s.names.size == 1) env.define(s.names[0], Slot(item, false))
            else s.names.forEachIndexed { i, n -> if (n != "_") env.define(n, Slot(component(item, i + 1), false)) }
            try { execBlock(s.body, Frame(env, f.receivers, f.composer, f.returnLabel)) } catch (_: BreakSignal) { break } catch (_: ContinueSignal) { continue }
        }
    }

    private fun whileLoop(s: WhileLoop, f: Frame) {
        var guard = 0
        if (s.doWhile) {
            do {
                if (++guard > LOOP_LIMIT) throw UnsupportedKotlinException("Schleife läuft zu lange")
                try { execBlock(s.body, Frame(Env(f.env), f.receivers, f.composer, f.returnLabel)) } catch (_: BreakSignal) { break } catch (_: ContinueSignal) { }
            } while (truthy(eval(s.cond, f)))
        } else {
            while (truthy(eval(s.cond, f))) {
                if (++guard > LOOP_LIMIT) throw UnsupportedKotlinException("Schleife läuft zu lange")
                try { execBlock(s.body, Frame(Env(f.env), f.receivers, f.composer, f.returnLabel)) } catch (_: BreakSignal) { break } catch (_: ContinueSignal) { continue }
            }
        }
    }

    private fun iteratorOf(v: Any?): Iterator<*> = when (v) {
        is Iterable<*> -> v.iterator()
        is Map<*, *> -> v.entries.iterator()
        is Array<*> -> v.iterator()
        is IntArray -> v.iterator()
        is CharSequence -> v.iterator()
        is Sequence<*> -> v.iterator()
        is Iterator<*> -> v
        else -> throw UnsupportedKotlinException("Nicht iterierbar: ${v?.javaClass?.name}")
    }

    private fun assign(s: Assign, f: Frame) {
        val newValue: Any? = if (s.op == "=") eval(s.value, f) else {
            val current = eval(s.target, f)
            val op = s.op.dropLast(1)
            // `list += x` auf MutableList/MutableSet
            if (op == "+" && current is MutableCollection<*> && s.target !is IndexExpr) {
                @Suppress("UNCHECKED_CAST") (current as MutableCollection<Any?>).add(eval(s.value, f)); return
            }
            binary(op, current, eval(s.value, f), s.at, f)
        }
        when (val t = s.target) {
            is NameRef -> {
                val slot = f.env.lookup(t.name)
                when {
                    slot != null && slot.delegate != null -> setDelegated(slot.delegate, newValue, f)
                    slot != null -> { if (!slot.mutable) throw UnsupportedKotlinException("'${t.name}' ist nicht veränderbar"); slot.value = newValue }
                    propertyDecls[t.name]?.isVar == true -> propertyValues[t.name] = newValue
                    else -> {
                        val r = f.receivers.firstOrNull { it != null && setterFor(it.javaClass, t.name) != null }
                            ?: throw UnsupportedKotlinException("Zuweisung an '${t.name}' nicht möglich")
                        setProperty(r, t.name, newValue)
                    }
                }
            }
            is Navigation -> {
                val recv = eval(t.receiver, f) ?: if (t.safe) return else throw NullPointerException("${t.name} auf null")
                setProperty(recv, t.name, newValue)
            }
            is IndexExpr -> {
                val target = eval(t.target, f)
                val idx = t.indices.map { eval(it, f) }
                @Suppress("UNCHECKED_CAST")
                when (target) {
                    is MutableList<*> -> (target as MutableList<Any?>)[(idx[0] as Number).toInt()] = newValue
                    is MutableMap<*, *> -> (target as MutableMap<Any?, Any?>)[idx[0]] = newValue
                    is Array<*> -> (target as Array<Any?>)[(idx[0] as Number).toInt()] = newValue
                    else -> callMember(target, "set", idx.map { EvalArg(null, it, false) } + EvalArg(null, newValue, false), f, s.at)
                }
            }
            else -> throw UnsupportedKotlinException("Zuweisungsziel nicht unterstützt")
        }
    }

    // --- Ausdrücke ---------------------------------------------------------------------------------------------

    fun eval(e: Expr, f: Frame): Any? = when (e) {
        is Literal -> e.value
        is StringTemplate -> buildString { e.parts.forEach { p -> if (p is String) append(p) else append(stringOf(eval(p as Expr, f))) } }
        is NameRef -> nameValue(e.name, f, e.at)
        is ThisRef -> f.receivers.firstOrNull() ?: throw UnsupportedKotlinException("'this' ohne Empfänger")
        is Call -> evalCall(e, f)
        is Navigation -> {
            val recv = eval(e.receiver, f)
            if (recv == null && e.safe) null else property(recv, e.name, f, e.at)
        }
        is IndexExpr -> index(eval(e.target, f), e.indices.map { eval(it, f) }, f, e.at)
        is Binary -> when (e.op) {
            "&&" -> truthy(eval(e.left, f)) && truthy(eval(e.right, f))
            "||" -> truthy(eval(e.left, f)) || truthy(eval(e.right, f))
            "?:" -> eval(e.left, f) ?: eval(e.right, f)
            else -> binary(e.op, eval(e.left, f), eval(e.right, f), e.at, f)
        }
        is Unary -> unary(e, f)
        is Postfix -> when (e.op) {
            "!!" -> eval(e.operand, f) ?: throw NullPointerException("!! auf null")
            else -> {
                val old = eval(e.operand, f)
                exec(Assign(e.operand, if (e.op == "++") "+=" else "-=", Literal(1, e.at), e.at), f)
                old
            }
        }
        is IsCheck -> {
            val v = eval(e.operand, f)
            isInstance(v, e.type) != e.negated
        }
        is Cast -> {
            val v = eval(e.operand, f)
            if (isInstance(v, e.type)) v else if (e.safe) null else throw ClassCastException("${v?.javaClass?.name} ist kein ${e.type.name}")
        }
        is IfExpr -> if (truthy(eval(e.cond, f))) execBlock(e.then, child(f)) else e.otherwise?.let { execBlock(it, child(f)) } ?: Unit
        is WhenExpr -> evalWhen(e, f)
        is LambdaExpr -> InterpLambda(e, f.env, f.receivers, e.label, f.composer)
        is CallableRef -> callableRef(e, f)
        is ReturnExpr -> throw ReturnSignal(e.value?.let { eval(it, f) } ?: Unit, e.label)
        is JumpExpr -> throw if (e.isBreak) BreakSignal() else ContinueSignal()
        is ThrowExpr -> throw (eval(e.value, f) as? Throwable ?: IllegalStateException("throw ohne Throwable"))
        is TryExpr -> evalTry(e, f)
    }

    private fun child(f: Frame) = Frame(Env(f.env), f.receivers, f.composer, f.returnLabel)

    private fun evalTry(e: TryExpr, f: Frame): Any? {
        try {
            return execBlock(e.body, child(f))
        } catch (t: Throwable) {
            if (t is ReturnSignal || t is BreakSignal || t is ContinueSignal) throw t
            for ((decl, block) in e.catches) {
                val cls = resolveType(decl.second) ?: Throwable::class.java
                if (cls.isInstance(t)) {
                    val env = Env(f.env); env.define(decl.first, Slot(t, false))
                    return execBlock(block, Frame(env, f.receivers, f.composer, f.returnLabel))
                }
            }
            throw t
        } finally {
            e.finally?.let { execBlock(it, child(f)) }
        }
    }

    private fun evalWhen(e: WhenExpr, f: Frame): Any? {
        val hasSubject = e.subject != null
        val subject = e.subject?.let { eval(it, f) }
        val frame = if (e.subjectName != null) { val env = Env(f.env); env.define(e.subjectName, Slot(subject, false)); Frame(env, f.receivers, f.composer, f.returnLabel) } else f
        for (b in e.branches) {
            val matches = b.isElse || b.conditions.any { c ->
                when (c) {
                    is WhenCondition.Value -> if (hasSubject) valueEquals(subject, eval(c.expr, frame)) else truthy(eval(c.expr, frame))
                    is WhenCondition.Is -> isInstance(subject, c.type) != c.negated
                    is WhenCondition.In -> contains(eval(c.expr, frame), subject, frame, c.expr.at) != c.negated
                }
            }
            if (matches) return execBlock(b.body, child(frame))
        }
        return Unit
    }

    private fun unary(e: Unary, f: Frame): Any? {
        if (e.op == "++" || e.op == "--") {
            exec(Assign(e.operand, if (e.op == "++") "+=" else "-=", Literal(1, e.at), e.at), f)
            return eval(e.operand, f)
        }
        val v = eval(e.operand, f)
        return when (e.op) {
            "!" -> !truthy(v)
            "-" -> when (v) {
                is Int -> -v; is Long -> -v; is Float -> -v; is Double -> -v
                else -> callMember(v, "unaryMinus", emptyList(), f, e.at)
            }
            "+" -> v
            else -> throw UnsupportedKotlinException("Operator ${e.op}")
        }
    }

    fun truthy(v: Any?): Boolean = v as? Boolean ?: throw UnsupportedKotlinException("Boolean erwartet, war ${v?.javaClass?.name}")

    // --- Namen ---------------------------------------------------------------------------------------------------

    private fun nameValue(name: String, f: Frame, at: Int): Any? {
        f.env.lookup(name)?.let { slot -> return if (slot.delegate != null) getDelegated(slot.delegate, f) else slot.value }
        // Mitglieder impliziter Empfänger (z. B. maxWidth in BoxWithConstraints)
        for (r in f.receivers) {
            if (r == null) continue
            if (r is ClassRefValue) continue
            reflect.memberProperty(r.javaClass, name)?.let { return readProperty(it, r, null, f) }
        }
        functions[name]?.let { return InterpFn(it.first(), null, emptyList()) }
        if (propertyDecls.containsKey(name)) return topLevelProperty(name, f)
        if (name == "R" && !compiledR) return NativeR(file.packageName)
        // Top-Level-Properties aus Importen/Paket
        for (pkg in candidatePackages(name)) {
            reflect.topLevelProperties(pkg, name).firstOrNull { !it.isExtension }?.let { return readProperty(it, null, null, f) }
        }
        resolveClassByName(name)?.let { return ClassRefValue(it) }
        unsupportedNames[name]?.let { throw UnsupportedKotlinException("${it.kind} '${it.name}' in der Datei wird nicht interpretiert") }
        if (name.first().isLowerCase()) return PackageRef(name)
        throw UnsupportedKotlinException("Unbekannter Name '$name'")
    }

    private fun topLevelProperty(name: String, f: Frame): Any? {
        if (propertyValues.containsKey(name)) return propertyValues[name]
        val d = propertyDecls.getValue(name)
        val root = Frame(Env(null), emptyList(), f.composer, null)
        return when {
            d.getter != null -> execBlock(d.getter, root)
            d.delegate != null -> getDelegated(eval(d.delegate, root), f).also { propertyValues[name] = it }
            else -> (d.init?.let { eval(it, root) }).also { propertyValues[name] = it }
        }
    }

    // Importe → Pakete, in denen ein Name gesucht wird (Reihenfolge = Priorität)
    private val explicitImports: Map<String, List<String>> = file.imports.filter { !it.star }
        .groupBy({ it.alias ?: it.path.substringAfterLast('.') }, { it.path })
    private val starPackages = file.imports.filter { it.star }.map { it.path }

    /** Pakete, die [name] als Top-Level-Funktion/-Property enthalten können. */
    fun candidatePackages(name: String): List<String> = buildList {
        explicitImports[name]?.forEach { add(it.substringBeforeLast('.')) }
        add(file.packageName)
        addAll(starPackages)
        addAll(DEFAULT_PACKAGES)
    }.distinct()

    /** Name, unter dem eine importierte Funktion wirklich heißt (bei `import a.b as c`). */
    private fun realName(name: String): String = file.imports.firstOrNull { it.alias == name }?.path?.substringAfterLast('.') ?: name

    fun resolveClassByName(name: String): Class<*>? {
        if (name.contains('.')) {
            val first = name.substringBefore('.')
            val rest = name.substringAfter('.')
            resolveClassByName(first)?.let { return reflect.classFor(it.name.replace('$', '.') + "." + rest) ?: reflect.classFor(it.name + "$" + rest.replace('.', '$')) }
            return reflect.classFor(name)
        }
        explicitImports[name]?.forEach { path -> reflect.classFor(path)?.let { return it } }
        reflect.classFor("${file.packageName}.$name".trimStart('.'))?.let { return it }
        starPackages.forEach { pkg -> reflect.classFor("$pkg.$name")?.let { return it } }
        KotlinReflect.KOTLIN_TO_JAVA["kotlin.$name"]?.let { return it }
        KotlinReflect.KOTLIN_TO_JAVA["kotlin.collections.$name"]?.let { return it }
        for (pkg in DEFAULT_PACKAGES) reflect.classFor("$pkg.$name")?.let { return it }
        return null
    }

    fun resolveType(t: TypeRef): Class<*>? = when (t.simpleName) {
        "Int" -> Int::class.javaObjectType
        "Long" -> Long::class.javaObjectType
        "Float" -> Float::class.javaObjectType
        "Double" -> Double::class.javaObjectType
        "Boolean" -> Boolean::class.javaObjectType
        "String" -> String::class.java
        "Char" -> Char::class.javaObjectType
        "Any" -> Any::class.java
        "Unit" -> Unit::class.java
        else -> if (t.function != null) kotlin.Function::class.java else resolveClassByName(t.name)
    }

    private fun isInstance(v: Any?, t: TypeRef): Boolean {
        if (v == null) return t.nullable
        val cls = resolveType(t) ?: throw UnsupportedKotlinException("Typ '${t.name}' nicht gefunden")
        return cls.isInstance(v) || (v is CallableValue && cls == kotlin.Function::class.java)
    }

    // --- Properties --------------------------------------------------------------------------------------------

    fun property(recv: Any?, name: String, f: Frame, at: Int): Any? {
        when (recv) {
            is NativeR -> return NativeRType(name)
            is NativeRType -> return resources.idFor(recv.type, name)
            is ClassRefValue -> return staticProperty(recv.cls, name, f, at)
            is PackageRef -> {
                val path = recv.path + "." + name
                reflect.classFor(path)?.let { return ClassRefValue(it) }
                reflect.topLevelProperties(recv.path, name).firstOrNull { !it.isExtension }?.let { return readProperty(it, null, null, f) }
                return PackageRef(path)
            }
            null -> throw NullPointerException("'$name' auf null")
        }
        // Interpretierte Extension-Properties der Datei
        propertyDecls[name]?.takeIf { it.receiver != null }?.let { d ->
            val root = Frame(Env(null), listOf(recv), f.composer, null)
            return d.getter?.let { execBlock(it, root) } ?: d.init?.let { eval(it, root) }
        }
        // Mitglied
        reflect.memberProperty(recv.javaClass, name)?.let { return readProperty(it, recv, null, f) }
        if (recv is Array<*> && name == "size") return recv.size
        if (recv is Map.Entry<*, *>) when (name) { "key" -> return recv.key; "value" -> return recv.value }
        if (recv is Pair<*, *>) when (name) { "first" -> return recv.first; "second" -> return recv.second }
        if (recv is Enum<*>) when (name) { "name" -> return recv.name; "ordinal" -> return recv.ordinal }
        // Extension-Property (Importe, Paket, Standard)
        extensionProperty(recv, name, f)?.let { return it.value }
        throw UnsupportedKotlinException("Property '$name' auf ${recv.javaClass.name} nicht gefunden")
    }

    private class Found(val value: Any?)

    private fun extensionProperty(recv: Any, name: String, f: Frame): Found? {
        for (r in f.receivers) {
            if (r == null || r is ClassRefValue) continue
            reflect.memberExtensionProperties(r.javaClass, name).firstOrNull { reflect.accepts(it.km?.receiverParameterType, recv) }
                ?.let { return Found(readProperty(it, r, recv, f)) }
        }
        for (pkg in candidatePackages(name)) {
            reflect.topLevelProperties(pkg, name).firstOrNull { it.isExtension && reflect.accepts(it.km?.receiverParameterType, recv) }
                ?.let { return Found(readProperty(it, null, recv, f)) }
        }
        return null
    }

    private fun staticProperty(cls: Class<*>, name: String, f: Frame, at: Int): Any? {
        // Verschachtelte Klasse/Objekt (Icons.Filled)
        reflect.classFor(cls.name + "$" + name)?.let { return ClassRefValue(it) }
        // Enum-Konstante oder statisches Feld
        runCatching { cls.getField(name) }.getOrNull()?.takeIf { Modifier.isStatic(it.modifiers) }?.let { return it.get(null) }
        if (cls.isEnum && name == "entries") return (cls.enumConstants?.toList() ?: emptyList<Any>())
        // Objekt selbst bzw. Companion
        (reflect.objectInstance(cls) ?: reflect.companionInstance(cls))?.let { obj ->
            reflect.memberProperty(obj.javaClass, name)?.let { return readProperty(it, obj, null, f) }
            extensionProperty(obj, name, f)?.let { return it.value }
        }
        // Java-Statik (getter)
        val cap = name.replaceFirstChar { it.uppercaseChar() }
        cls.methods.firstOrNull { Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && (it.name == "get$cap" || it.name == name) }?.let { return it.invoke(null) }
        throw UnsupportedKotlinException("'${cls.simpleName}.$name' nicht gefunden")
    }

    /** Liest eine Property; Composable-Getter erhalten den Composer, Wertklassen werden geboxt. */
    fun readProperty(p: PropSym, dispatch: Any?, extReceiver: Any?, f: Frame): Any? {
        p.field?.let { return it.get(if (Modifier.isStatic(it.modifiers)) null else dispatch) }
        val g = p.getter ?: throw UnsupportedKotlinException("Property '${p.name}' ohne Getter")
        val types = g.parameterTypes
        val args = ArrayList<Any?>()
        var i = 0
        val static = Modifier.isStatic(g.modifiers)
        if (static && p.isMember && dispatch != null) { args += toJvm(dispatch, types[i], null); i++ }
        if (extReceiver != null) { args += toJvm(extReceiver, types[i], p.km?.receiverParameterType); i++ }
        while (i < types.size) {
            when {
                types[i].name == KotlinReflect.COMPOSER -> args += (f.composer ?: throw UnsupportedKotlinException("'${p.name}' braucht eine Komposition"))
                types[i] == Int::class.javaPrimitiveType -> args += 0
                else -> throw UnsupportedKotlinException("Getter '${g.name}' mit unbekannter Signatur")
            }
            i++
        }
        val raw = invokeJvm(g, if (static) null else dispatch, args.toTypedArray())
        return fromJvm(raw, p.km?.returnType)
    }

    private fun setterFor(c: Class<*>, name: String): Method? {
        val cap = name.replaceFirstChar { it.uppercaseChar() }
        return c.methods.firstOrNull { it.name == "set$cap" && it.parameterCount == 1 }
    }

    private fun setProperty(recv: Any, name: String, value: Any?) {
        val setter = setterFor(recv.javaClass, name)
        if (setter != null) { invokeJvm(setter, recv, arrayOf(toJvm(value, setter.parameterTypes[0], null))); return }
        val field = runCatching { recv.javaClass.getField(name) }.getOrNull() ?: throw UnsupportedKotlinException("'$name' kann nicht gesetzt werden")
        field.set(recv, value)
    }

    // --- Delegates (State) --------------------------------------------------------------------------------------

    private fun getDelegated(d: Any?, f: Frame): Any? = when (d) {
        is androidx.compose.runtime.State<*> -> d.value
        is Lazy<*> -> d.value
        else -> callMember(d, "getValue", listOf(EvalArg(null, null, false), EvalArg(null, null, false)), f, -1)
    }

    private fun setDelegated(d: Any?, v: Any?, f: Frame) {
        @Suppress("UNCHECKED_CAST")
        when (d) {
            is androidx.compose.runtime.MutableState<*> -> (d as androidx.compose.runtime.MutableState<Any?>).value = v
            else -> callMember(d, "setValue", listOf(EvalArg(null, null, false), EvalArg(null, null, false), EvalArg(null, v, false)), f, -1)
        }
    }

    // --- Aufrufe -----------------------------------------------------------------------------------------------

    private fun evalArgs(args: List<Arg>, f: Frame): List<EvalArg> = args.map { EvalArg(it.name, eval(it.value, f), it.spread) }

    private fun evalCall(e: Call, f: Frame): Any? {
        val callee = e.callee
        return when (callee) {
            is NameRef -> callByName(callee.name, e, f)
            is Navigation -> {
                val recv = eval(callee.receiver, f)
                if (recv == null && callee.safe) return null
                callMember(recv, callee.name, evalArgs(e.args, f), f, e.at, e)
            }
            else -> invokeValue(eval(callee, f), evalArgs(e.args, f), f, e.at)
        }
    }

    private fun callByName(name: String, e: Call, f: Frame): Any? {
        // 1. Lokale Variable mit Funktionswert
        f.env.lookup(name)?.let { slot ->
            val v = if (slot.delegate != null) getDelegated(slot.delegate, f) else slot.value
            return invokeValue(v, evalArgs(e.args, f), f, e.at)
        }
        // 2. Compose-Sonderfälle (remember, key, Ressourcen)
        compose.intrinsic(name, e, f)?.let { return it.value }
        val args = evalArgs(e.args, f)
        // 3. Interpretierte Funktionen der Datei
        functions[name]?.let { decls ->
            val candidates = decls.filter { it.receiver == null }
            pickInterpreted(candidates, args)?.let { return callInterpreted(InterpFn(it, null, emptyList()), null, args, f.composer, e.at) }
            // Extension mit implizitem Empfänger
            decls.filter { it.receiver != null }.forEach { d ->
                f.receivers.firstOrNull { r -> r != null && (resolveType(d.receiver!!)?.isInstance(r) ?: true) }?.let { r ->
                    return callInterpreted(InterpFn(d, null, emptyList()), r, args, f.composer, e.at)
                }
            }
        }
        // 4. Mitglieder impliziter Empfänger (z. B. item { } in LazyListScope)
        for (r in f.receivers) {
            if (r == null || r is ClassRefValue) continue
            val members = reflect.memberFunctions(r.javaClass, name).filter { !it.isExtension }
            tryCall(members, r, null, args, f, e.at)?.let { return it.value }
        }
        // 5. Top-Level-Funktionen (Importe, Paket, Standard) – auch Extensions mit implizitem Empfänger
        val real = realName(name)
        for (pkg in candidatePackages(name)) {
            val fns = reflect.topLevelFunctions(pkg, real)
            if (fns.isEmpty()) continue
            tryCall(fns.filter { !it.isExtension }, null, null, args, f, e.at)?.let { return it.value }
            for (r in f.receivers) {
                if (r == null || r is ClassRefValue) continue
                tryCall(fns.filter { it.isExtension }, null, r, args, f, e.at)?.let { return it.value }
            }
        }
        // 6. Konstruktor
        resolveClassByName(name)?.let { cls -> return construct(cls, args, f, e.at) }
        unsupportedNames[name]?.let { throw UnsupportedKotlinException("${it.kind} '${it.name}' in der Datei wird nicht interpretiert") }
        throw UnsupportedKotlinException("Funktion '$name(${args.size} Arg.)' nicht gefunden")
    }

    /** Aufruf `recv.name(args)`. */
    fun callMember(recv: Any?, name: String, args: List<EvalArg>, f: Frame, at: Int, call: Call? = null): Any? {
        when (recv) {
            is ClassRefValue -> return callStatic(recv.cls, name, args, f, at)
            is PackageRef -> {
                tryCall(reflect.topLevelFunctions(recv.path, name).filter { !it.isExtension }, null, null, args, f, at)?.let { return it.value }
                reflect.classFor(recv.path + "." + name)?.let { return construct(it, args, f, at) }
                throw UnsupportedKotlinException("'${recv.path}.$name' nicht gefunden")
            }
            null -> {
                // Extension auf nullable Empfänger (z. B. orEmpty, isNullOrEmpty)
                for (pkg in candidatePackages(name)) {
                    val nullable = reflect.topLevelFunctions(pkg, name).filter { it.isExtension && it.km?.receiverParameterType?.isNullable == true }
                    tryCall(nullable, null, null, args, f, at, nullReceiver = true)?.let { return it.value }
                }
                throw NullPointerException("'$name' auf null")
            }
        }
        // Interpretierte Extensions der Datei
        functions[name]?.filter { it.receiver != null && (resolveType(it.receiver)?.isInstance(recv) ?: true) }?.let { decls ->
            pickInterpreted(decls, args)?.let { return callInterpreted(InterpFn(it, null, emptyList()), recv, args, f.composer, at) }
        }
        // lambda.invoke(…)
        if (recv is CallableValue && name == "invoke") return invokeValue(recv, args, f, at)
        // Mitglieder
        tryCall(reflect.memberFunctions(recv.javaClass, name).filter { !it.isExtension }, recv, null, args, f, at)?.let { return it.value }
        // Property mit Funktionstyp: obj.onClick()
        reflect.memberProperty(recv.javaClass, name)?.let { p ->
            val v = readProperty(p, recv, null, f)
            if (v != null && (v is CallableValue || v is kotlin.Function<*>)) return invokeValue(v, args, f, at)
        }
        // Mitglieds-Extensions impliziter Empfänger (Modifier.weight in RowScope)
        for (r in f.receivers) {
            if (r == null || r is ClassRefValue) continue
            tryCall(reflect.memberFunctions(r.javaClass, name).filter { it.isExtension }, r, recv, args, f, at)?.let { return it.value }
        }
        // Top-Level-Extensions
        val real = realName(name)
        for (pkg in candidatePackages(name)) {
            tryCall(reflect.topLevelFunctions(pkg, real).filter { it.isExtension }, null, recv, args, f, at)?.let { return it.value }
        }
        // Grundlegende Operationen auf Kotlin-Typen
        BuiltinCalls.call(this, recv, name, args, f)?.let { return it.value }
        throw UnsupportedKotlinException("Funktion '$name' auf ${recv.javaClass.name} nicht gefunden")
    }

    private fun callStatic(cls: Class<*>, name: String, args: List<EvalArg>, f: Frame, at: Int): Any? {
        reflect.classFor(cls.name + "$" + name)?.let { nested -> return construct(nested, args, f, at) }
        // Java-Statik / Enum.valueOf / values
        val statics = cls.methods.filter { Modifier.isStatic(it.modifiers) && it.name == name && it.parameterCount == args.size }
        statics.firstOrNull { m -> m.parameterTypes.zip(args).all { (t, a) -> compatibleJvm(t, a.value) } }?.let { m ->
            return invokeJvm(m, null, m.parameterTypes.zip(args).map { (t, a) -> toJvm(a.value, t, null) }.toTypedArray())
        }
        // Objekt bzw. Companion wie einen normalen Empfänger behandeln (Mitglieder, Extensions, Scope-Extensions)
        val instance = reflect.objectInstance(cls) ?: reflect.companionInstance(cls)
            ?: throw UnsupportedKotlinException("'${cls.simpleName}.$name' nicht gefunden")
        return callMember(instance, name, args, f, at)
    }

    private fun compatibleJvm(t: Class<*>, v: Any?): Boolean {
        if (v == null) return !t.isPrimitive
        val boxed = KotlinReflect.PRIMITIVE_BOXES[t] ?: t
        return boxed.isInstance(v) || (v is Number && Number::class.java.isAssignableFrom(boxed)) || (v is CallableValue && t.isInterface)
    }

    /** Ruft einen Funktionswert auf (Lambda, Funktionsreferenz, kotlin.Function). */
    fun invokeValue(v: Any?, args: List<EvalArg>, f: Frame, at: Int): Any? = when (v) {
        is InterpLambda -> invokeLambda(v, args.map { it.value }, f.composer)
        is InterpFn -> callInterpreted(v, null, args, f.composer, at)
        is BoundRef -> callMember(v.receiver, v.name, args, f, at)
        null -> throw NullPointerException("Aufruf von null")
        else -> {
            // kotlin.Function / SAM: invoke(); Composable-Lambdas aus Bibliotheken (z. B. innerTextField) mit Composer
            val plain = v.javaClass.methods.firstOrNull { it.name == "invoke" && it.parameterCount == args.size }?.let(reflect::publicVersion)
            val composer = f.composer
            val withComposer = if (composer != null) v.javaClass.methods.firstOrNull { it.name == "invoke" && it.parameterCount == args.size + 2 }?.let(reflect::publicVersion) else null
            val composableTarget = v is kotlin.jvm.functions.Function2<*, *, *> && args.isEmpty() && composer != null ||
                (withComposer != null && (plain == null || v.javaClass.name.contains("ComposableLambda")))
            when {
                composableTarget && withComposer != null -> compose.group(composer!!, at) {
                    invokeJvm(withComposer, v, (args.map { it.value } + listOf(composer, 0)).toTypedArray())
                }
                plain != null -> invokeJvm(plain, v, args.map { it.value }.toTypedArray())
                else -> throw UnsupportedKotlinException("Wert vom Typ ${v.javaClass.name} ist nicht aufrufbar")
            }
        }
    }

    // --- Interpretierte Funktionen und Lambdas --------------------------------------------------------------------

    private fun pickInterpreted(decls: List<FunDecl>, args: List<EvalArg>): FunDecl? = decls.firstOrNull { d ->
        val positional = args.count { it.name == null || it.name == KtParser.TRAILING_LAMBDA }
        val named = args.mapNotNull { it.name?.takeIf { n -> n != KtParser.TRAILING_LAMBDA } }
        val hasVararg = d.params.any { it.vararg }
        named.all { n -> d.params.any { it.name == n } } && (hasVararg || positional + named.size <= d.params.size) &&
            d.params.filter { it.default == null && !it.vararg }.all { p -> p.name in named || d.params.indexOf(p) < positional }
    } ?: decls.firstOrNull()

    fun callInterpreted(fn: InterpFn, receiver: Any?, args: List<EvalArg>, composer: Composer?, at: Int): Any? {
        val d = fn.decl
        val env = Env(fn.closure)
        val receivers = if (receiver != null) listOf(receiver) + fn.closureReceivers else fn.closureReceivers
        val frame = Frame(env, receivers, composer, d.name)
        bindInterpretedParams(d, args, frame)
        val body = d.body ?: throw UnsupportedKotlinException("Funktion '${d.name}' ohne Rumpf")
        val run = {
            try { execBlock(body, frame).let { if (d.expressionBody) it else Unit } } catch (r: ReturnSignal) {
                if (r.label == null || r.label == d.name) r.value else throw r
            }
        }
        if (d.isComposable) {
            val c = composer ?: throw UnsupportedKotlinException("@Composable '${d.name}' außerhalb einer Komposition")
            return compose.group(c, at) { run() }
        }
        return run()
    }

    private fun bindInterpretedParams(d: FunDecl, args: List<EvalArg>, frame: Frame) {
        val values = arrayOfNulls<Any?>(d.params.size)
        val provided = BooleanArray(d.params.size)
        var pos = 0
        val varargIdx = d.params.indexOfFirst { it.vararg }
        val varargValues = ArrayList<Any?>()
        for (a in args) {
            when {
                a.name == KtParser.TRAILING_LAMBDA -> { values[d.params.lastIndex] = a.value; provided[d.params.lastIndex] = true }
                a.name != null -> {
                    val i = d.params.indexOfFirst { it.name == a.name }
                    if (i < 0) throw UnsupportedKotlinException("Parameter '${a.name}' gibt es in '${d.name}' nicht")
                    values[i] = a.value; provided[i] = true
                }
                else -> {
                    while (pos < d.params.size && provided[pos] && pos != varargIdx) pos++
                    if (pos == varargIdx) {
                        if (a.spread) varargValues.addAll(iteratorOf(a.value).asSequence().toList()) else varargValues += a.value
                        provided[pos] = true
                    } else {
                        if (pos >= d.params.size) throw UnsupportedKotlinException("Zu viele Argumente für '${d.name}'")
                        values[pos] = a.value; provided[pos] = true; pos++
                    }
                }
            }
        }
        if (varargIdx >= 0) { values[varargIdx] = varargValues.toList(); provided[varargIdx] = true }
        d.params.forEachIndexed { i, p ->
            val v = if (provided[i]) values[i] else p.default?.let { eval(it, frame) } ?: if (p.type?.nullable == true) null else throw UnsupportedKotlinException("Argument '${p.name}' fehlt für '${d.name}'")
            frame.env.define(p.name, Slot(v, false))
        }
    }

    /** Führt ein Lambda mit Argumenten aus (Empfänger-Lambdas: erstes Argument wird `this`). */
    fun invokeLambda(l: InterpLambda, args: List<Any?>, composer: Composer?): Any? {
        val env = Env(l.env)
        val params = l.expr.params
        var receivers = l.receivers
        when {
            params == null -> when (args.size) {
                0 -> {}
                1 -> { env.define("it", Slot(args[0], false)); receivers = listOf(args[0]) + receivers }
                else -> { receivers = listOf(args[0]) + receivers; env.define("it", Slot(args[1], false)) }
            }
            params.size == args.size -> bindLambdaParams(params, args, env)
            params.size == args.size - 1 -> { receivers = listOf(args[0]) + receivers; bindLambdaParams(params, args.drop(1), env) }
            else -> throw UnsupportedKotlinException("Lambda erwartet ${params.size} Parameter, erhielt ${args.size}")
        }
        val frame = Frame(env, receivers, composer ?: l.creationComposer, l.name)
        return try { execBlock(l.expr.body, frame) } catch (r: ReturnSignal) {
            if (r.label != null && r.label == l.name) r.value else throw r
        }
    }

    private fun bindLambdaParams(params: List<LambdaParam>, args: List<Any?>, env: Env) {
        params.forEachIndexed { i, p ->
            if (p.name.startsWith("(")) {
                p.name.removeSurrounding("(", ")").split(",").forEachIndexed { k, n -> if (n != "_") env.define(n, Slot(component(args[i], k + 1), false)) }
            } else if (p.name != "_") env.define(p.name, Slot(args[i], false))
        }
    }

    private fun callableRef(e: CallableRef, f: Frame): Any? {
        if (e.receiver == null) {
            functions[e.name]?.let { return InterpFn(it.first(), null, emptyList()) }
            return BoundRef(null, e.name)
        }
        val recv = eval(e.receiver, f)
        if (e.name == "class") return recv?.javaClass
        return BoundRef(recv, e.name)
    }

    // --- Reflektierte Aufrufe ----------------------------------------------------------------------------------

    /** Ergebnis-Hülle, damit `null` als gültiges Ergebnis von „nicht gefunden“ unterscheidbar ist. */
    class Result(val value: Any?)

    /**
     * Versucht einen Aufruf über passende Kandidaten. [dispatch] = Objekt für Mitglieder, [ext] = Extension-Empfänger.
     * Liefert `null`, wenn kein Kandidat passt.
     */
    fun tryCall(candidates: List<FnSym>, dispatch: Any?, ext: Any?, args: List<EvalArg>, f: Frame, at: Int, nullReceiver: Boolean = false): Result? {
        if (candidates.isEmpty()) return null
        val ranked = candidates.mapNotNull { fn -> bind(fn, ext, args, nullReceiver)?.let { fn to it } }
            .sortedByDescending { it.second.score }
        for ((fn, bound) in ranked) {
            val layouts = reflect.layouts(fn)
            val layout = layouts.firstOrNull { l -> bound.missing.isEmpty() || l.composeDefaultMasks > 0 || l.kotlinDefaultMasks > 0 } ?: continue
            if (layout.composerIndex >= 0 && f.composer == null) throw UnsupportedKotlinException("@Composable '${fn.name}' außerhalb einer Komposition")
            val call = { Result(invokeLayout(fn, layout, dispatch, ext, bound, f)) }
            return if (layout.composerIndex >= 0) compose.group(f.composer!!, at) { call() } else call()
        }
        return null
    }

    class Bound(val values: Array<Any?>, val missing: Set<Int>, val score: Int)

    /** Ordnet Argumente den Parametern zu (Named, Positional, Trailing Lambda, vararg) und bewertet die Passung. */
    private fun bind(fn: FnSym, ext: Any?, args: List<EvalArg>, nullReceiver: Boolean): Bound? {
        if (fn.javaMethod != null) {
            val m = fn.javaMethod
            if (m.parameterCount != args.size || args.any { it.name != null && it.name != KtParser.TRAILING_LAMBDA }) return null
            if (!m.parameterTypes.zip(args).all { (t, a) -> compatibleJvm(t, a.value) }) return null
            return Bound(args.map { it.value }.toTypedArray(), emptySet(), 1)
        }
        val km = fn.km ?: return null
        if (fn.isExtension != (ext != null || nullReceiver)) return null
        if (ext != null && !reflect.accepts(km.receiverParameterType, ext)) return null
        val params = km.valueParameters
        val values = arrayOfNulls<Any?>(params.size)
        val provided = BooleanArray(params.size)
        var pos = 0
        val varargIdx = params.indexOfFirst { it.varargElementType != null }
        val varargValues = ArrayList<Any?>()
        for (a in args) {
            when {
                a.name == KtParser.TRAILING_LAMBDA -> {
                    if (params.isEmpty()) return null
                    values[params.lastIndex] = a.value; provided[params.lastIndex] = true
                }
                a.name != null -> {
                    val i = params.indexOfFirst { it.name == a.name }
                    if (i < 0) return null
                    if (i == varargIdx) { varargValues += a.value; provided[i] = true } else { values[i] = a.value; provided[i] = true }
                }
                else -> {
                    while (pos < params.size && provided[pos] && pos != varargIdx) pos++
                    if (pos >= params.size) return null
                    if (pos == varargIdx) {
                        if (a.spread) varargValues.addAll(iteratorOf(a.value).asSequence().toList()) else varargValues += a.value
                        provided[pos] = true
                    } else { values[pos] = a.value; provided[pos] = true; pos++ }
                }
            }
        }
        if (varargIdx >= 0) { values[varargIdx] = VarargList(varargValues); provided[varargIdx] = true }
        var score = 0
        val missing = HashSet<Int>()
        params.forEachIndexed { i, p ->
            if (!provided[i]) {
                if (!p.declaresDefaultValue) return null
                missing += i
            } else if (i != varargIdx) {
                if (!reflect.accepts(p.type, values[i])) return null
                score += exactness(p.type, values[i])
            } else {
                val el = p.varargElementType
                if (!(values[i] as VarargList).items.all { reflect.accepts(el, it) }) return null
            }
        }
        return Bound(values, missing, score * 10 - params.size)
    }

    private fun exactness(t: KmType, v: Any?): Int {
        val c = (t.classifier as? KmClassifier.Class)?.name ?: return 0
        val cls = reflect.classForKm(c) ?: return 0
        return if (v != null && (KotlinReflect.PRIMITIVE_BOXES[cls] ?: cls) == v.javaClass) 3 else 1
    }

    class VarargList(val items: List<Any?>)

    private fun invokeLayout(fn: FnSym, l: JvmLayout, dispatch: Any?, ext: Any?, b: Bound, f: Frame): Any? {
        val m = l.method
        val types = m.parameterTypes
        if (fn.javaMethod != null) {
            return fromJvm(invokeJvm(m, dispatch, b.values.mapIndexed { i, v -> toJvm(v, types[i], null) }.toTypedArray()), null)
        }
        val km = fn.km!!
        val jvmArgs = arrayOfNulls<Any?>(types.size)
        var i = 0
        if (l.staticWithDispatch) { jvmArgs[i] = toJvm(dispatch, types[i], null); i++ }
        if (km.receiverParameterType != null) { jvmArgs[i] = toJvm(ext, types[i], km.receiverParameterType); i++ }
        km.valueParameters.forEachIndexed { k, p ->
            val t = types[i]
            jvmArgs[i] = if (k in b.missing) placeholder(t) else {
                val v = b.values[k]
                if (v is VarargList) varargArray(v, t, p.varargElementType) else toJvm(v, t, p.type)
            }
            i++
        }
        if (l.composerIndex >= 0) {
            jvmArgs[l.composerIndex] = f.composer
            i = l.composerIndex + 1
            repeat(l.changedCount) { jvmArgs[i++] = 0 }
            repeat(l.composeDefaultMasks) { maskIdx -> jvmArgs[i++] = mask(b.missing, maskIdx, 31) }
        }
        if (l.kotlinDefaultMasks > 0) {
            repeat(l.kotlinDefaultMasks) { maskIdx -> jvmArgs[i++] = mask(b.missing, maskIdx, 32) }
            jvmArgs[i] = null
        }
        val target = if (Modifier.isStatic(m.modifiers)) null else dispatch
        val raw = invokeJvm(m, target, jvmArgs)
        return fromJvm(raw, km.returnType)
    }

    private fun mask(missing: Set<Int>, maskIdx: Int, per: Int): Int {
        var bits = 0
        for (k in missing) if (k / per == maskIdx) bits = bits or (1 shl (k % per))
        return bits
    }

    private fun varargArray(v: VarargList, t: Class<*>, elType: KmType?): Any {
        val comp = t.componentType ?: Any::class.java
        val arr = java.lang.reflect.Array.newInstance(comp, v.items.size)
        v.items.forEachIndexed { i, item -> java.lang.reflect.Array.set(arr, i, toJvm(item, comp, elType)) }
        return arr
    }

    private fun placeholder(t: Class<*>): Any? = when (t) {
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Boolean::class.javaPrimitiveType -> false
        Char::class.javaPrimitiveType -> '\u0000'
        Short::class.javaPrimitiveType -> 0.toShort()
        Byte::class.javaPrimitiveType -> 0.toByte()
        else -> null
    }

    private fun construct(cls: Class<*>, args: List<EvalArg>, f: Frame, at: Int): Any? {
        val km = reflect.kmClass(cls)
        if (km != null) {
            for (ctor in km.constructors) {
                val fake = FnSym("<init>", cls, kotlin.metadata.KmFunction("<init>").apply { valueParameters.addAll(ctor.valueParameters) }, isMember = false)
                val b = bind(fake, null, args, false) ?: continue
                val jvmCtors = cls.declaredConstructors.filter { c ->
                    val n = ctor.valueParameters.size
                    c.parameterCount == n || (b.missing.isNotEmpty() && c.parameterCount == n + (n + 31) / 32 + 1 && c.parameterTypes.last().name == "kotlin.jvm.internal.DefaultConstructorMarker")
                }.sortedBy { it.parameterCount }
                val jc = (if (b.missing.isEmpty()) jvmCtors.firstOrNull { it.parameterCount == ctor.valueParameters.size } else jvmCtors.firstOrNull { it.parameterCount > ctor.valueParameters.size })
                    ?: continue
                jc.isAccessible = true
                val types = jc.parameterTypes
                val jvmArgs = arrayOfNulls<Any?>(types.size)
                ctor.valueParameters.forEachIndexed { k, p ->
                    jvmArgs[k] = if (k in b.missing) placeholder(types[k]) else b.values[k].let { v -> if (v is VarargList) varargArray(v, types[k], p.varargElementType) else toJvm(v, types[k], p.type) }
                }
                if (jc.parameterCount > ctor.valueParameters.size) {
                    val masks = (ctor.valueParameters.size + 31) / 32
                    repeat(masks) { mi -> jvmArgs[ctor.valueParameters.size + mi] = mask(b.missing, mi, 32) }
                    jvmArgs[types.size - 1] = null
                }
                return try { jc.newInstance(*jvmArgs) } catch (ite: InvocationTargetException) { throw ite.targetException }
            }
            // Wertklasse: Konstruktor-Funktion gleichen Namens auf Paketebene wurde schon versucht
        }
        val c = cls.constructors.firstOrNull { it.parameterCount == args.size && it.parameterTypes.zip(args).all { (t, a) -> compatibleJvm(t, a.value) } }
            ?: throw UnsupportedKotlinException("Kein passender Konstruktor für ${cls.simpleName}")
        return try { c.newInstance(*c.parameterTypes.zip(args).map { (t, a) -> toJvm(a.value, t, null) }.toTypedArray()) } catch (ite: InvocationTargetException) { throw ite.targetException }
    }

    // --- Werte zwischen Interpreter und JVM -------------------------------------------------------------------

    fun invokeJvm(m: Method, target: Any?, args: Array<Any?>): Any? = try {
        try { m.invoke(target, *args) } catch (_: IllegalAccessException) { m.isAccessible = true; m.invoke(target, *args) }
    } catch (ite: InvocationTargetException) {
        throw ite.targetException
    } catch (iae: IllegalArgumentException) {
        throw UnsupportedKotlinException("Aufruf von ${m.declaringClass.simpleName}.${m.name} fehlgeschlagen: ${iae.message}")
    }

    /** Interpreter-Wert → JVM-Parameter (Lambdas → Proxy, Wertklassen auspacken, Zahlen umwandeln). */
    fun toJvm(v: Any?, t: Class<*>, kmType: KmType?): Any? {
        var value = v
        if (value is ClassRefValue) value = reflect.objectInstance(value.cls) ?: reflect.companionInstance(value.cls) ?: value.cls
        if (value is CallableValue) return compose.proxy(value, t, kmType)
        if (value == null) return if (t.isPrimitive) placeholder(t) else null
        if (!t.isPrimitive && t.isInstance(value)) return value
        if (reflect.isValueClass(value.javaClass) && !t.isInstance(value)) value = reflect.unbox(value)
        if (value == null) return null
        return when (t) {
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> (value as? Number)?.toInt() ?: (value as? Char)?.code ?: value
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> (value as? Number)?.toLong() ?: value
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> (value as? Number)?.toFloat() ?: value
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> (value as? Number)?.toDouble() ?: value
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> (value as? Number)?.toShort() ?: value
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> (value as? Number)?.toByte() ?: value
            else -> value
        }
    }

    /** JVM-Rückgabe → Interpreter-Wert (Wertklassen werden geboxt). */
    fun fromJvm(raw: Any?, kmType: KmType?): Any? {
        if (raw == null) return null
        val vc = reflect.valueClassOf(kmType) ?: return raw
        return if (vc.isInstance(raw)) raw else reflect.box(vc, raw)
    }

    // --- Operatoren ----------------------------------------------------------------------------------------------

    fun binary(op: String, l: Any?, r: Any?, at: Int, f: Frame): Any? {
        when (op) {
            "==" -> return valueEquals(l, r)
            "!=" -> return !valueEquals(l, r)
            "===" -> return l === r || (l is Number && r is Number && l == r)
            "!==" -> return !(l === r)
            "in" -> return contains(r, l, f, at)
            "!in" -> return !contains(r, l, f, at)
        }
        if (op == "+" && (l is String)) return l + stringOf(r)
        if (l is Number && r is Number) return Numbers.arithmetic(op, l, r) ?: throw UnsupportedKotlinException("Operator $op")
        if (l is Char && r is Int) return when (op) { "+" -> l + r; "-" -> l - r; else -> throw UnsupportedKotlinException("Operator $op") }
        if (l is Char && r is Char) when (op) {
            "-" -> return l - r
            "..", "..<" -> return if (op == "..") l..r else l until r
            "<", ">", "<=", ">=" -> return Numbers.compareResult(op, l.compareTo(r))
        }
        if ((op == "<" || op == ">" || op == "<=" || op == ">=")) {
            val cmp = if (l is Comparable<*> && l.javaClass == r?.javaClass) {
                @Suppress("UNCHECKED_CAST") (l as Comparable<Any?>).compareTo(r)
            } else (callMember(l, "compareTo", listOf(EvalArg(null, r, false)), f, at) as Number).toInt()
            return Numbers.compareResult(op, cmp)
        }
        val name = when (op) {
            "+" -> "plus"; "-" -> "minus"; "*" -> "times"; "/" -> "div"; "%" -> "rem"; ".." -> "rangeTo"; "..<" -> "rangeUntil"
            else -> throw UnsupportedKotlinException("Operator $op")
        }
        return callMember(l, name, listOf(EvalArg(null, r, false)), f, at)
    }

    fun valueEquals(a: Any?, b: Any?): Boolean {
        if (a is Number && b is Number && a.javaClass != b.javaClass) {
            return if (a is Double || a is Float || b is Double || b is Float) a.toDouble() == b.toDouble() else a.toLong() == b.toLong()
        }
        return a == b
    }

    private fun contains(container: Any?, item: Any?, f: Frame, at: Int): Boolean = when (container) {
        is IntRange -> item is Number && item.toInt() in container && item.toDouble() % 1.0 == 0.0
        is LongRange -> item is Number && item.toLong() in container
        is CharRange -> item is Char && item in container
        is ClosedRange<*> -> {
            @Suppress("UNCHECKED_CAST") val r = container as ClosedRange<Comparable<Any?>>
            item is Comparable<*> && r.contains(item as Comparable<Any?>)
        }
        is Collection<*> -> container.contains(item)
        is Map<*, *> -> container.containsKey(item)
        is CharSequence -> if (item is Char) container.contains(item) else container.contains(item.toString())
        is Array<*> -> container.contains(item)
        else -> truthy(callMember(container, "contains", listOf(EvalArg(null, item, false)), f, at))
    }

    private fun index(target: Any?, idx: List<Any?>, f: Frame, at: Int): Any? = when (target) {
        is List<*> -> target[(idx[0] as Number).toInt()]
        is Map<*, *> -> target[idx[0]]
        is Array<*> -> target[(idx[0] as Number).toInt()]
        is IntArray -> target[(idx[0] as Number).toInt()]
        is CharSequence -> target[(idx[0] as Number).toInt()]
        else -> callMember(target, "get", idx.map { EvalArg(null, it, false) }, f, at)
    }

    fun component(v: Any?, n: Int): Any? = when (v) {
        is Map.Entry<*, *> -> if (n == 1) v.key else v.value
        is Pair<*, *> -> if (n == 1) v.first else v.second
        is Triple<*, *, *> -> when (n) { 1 -> v.first; 2 -> v.second; else -> v.third }
        is List<*> -> v[n - 1]
        is Array<*> -> v[n - 1]
        null -> throw NullPointerException("Destrukturierung von null")
        else -> v.javaClass.methods.firstOrNull { it.name == "component$n" && it.parameterCount == 0 }?.let { invokeJvm(it, v, emptyArray()) }
            ?: throw UnsupportedKotlinException("component$n() auf ${v.javaClass.name} fehlt")
    }

    fun stringOf(v: Any?): String = when (v) {
        null -> "null"
        is Unit -> "kotlin.Unit"
        else -> v.toString()
    }

    companion object {
        const val LOOP_LIMIT = 100_000
        val DEFAULT_PACKAGES = listOf(
            "kotlin", "kotlin.collections", "kotlin.text", "kotlin.ranges", "kotlin.sequences", "kotlin.comparisons",
            "kotlin.io", "kotlin.annotation", "java.lang"
        )
    }
}

/** Arithmetik auf Zahlen nach Kotlin-Regeln (Double > Float > Long > Int). */
internal object Numbers {
    fun arithmetic(op: String, l: Number, r: Number): Any? {
        val kind = when {
            l is Double || r is Double -> 3
            l is Float || r is Float -> 2
            l is Long || r is Long -> 1
            else -> 0
        }
        if (op == "<" || op == ">" || op == "<=" || op == ">=") {
            val c = when (kind) { 3, 2 -> l.toDouble().compareTo(r.toDouble()); else -> l.toLong().compareTo(r.toLong()) }
            return compareResult(op, c)
        }
        return when (kind) {
            3 -> { val a = l.toDouble(); val b = r.toDouble(); when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; "%" -> a % b; ".." -> a..b; else -> null } }
            2 -> { val a = l.toFloat(); val b = r.toFloat(); when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; "%" -> a % b; ".." -> a..b; else -> null } }
            1 -> { val a = l.toLong(); val b = r.toLong(); when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; "%" -> a % b; ".." -> a..b; "..<" -> a until b; else -> null } }
            else -> { val a = l.toInt(); val b = r.toInt(); when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; "%" -> a % b; ".." -> a..b; "..<" -> a until b; else -> null } }
        }
    }

    fun compareResult(op: String, c: Int): Boolean = when (op) { "<" -> c < 0; ">" -> c > 0; "<=" -> c <= 0; else -> c >= 0 }
}
