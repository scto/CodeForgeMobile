/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.platform.LocalConfiguration
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.metadata.KmType

/**
 * Compose-spezifische Teile des Interpreters: Gruppen für stabile Slot-Positionen, `remember`/`key`,
 * Ressourcenfunktionen für das native `R` und Lambdas als echte `kotlin.jvm.functions.FunctionN`-Objekte.
 */
@OptIn(InternalComposeApi::class)
internal class ComposeSupport(private val interp: Interpreter) {

    /** Führt [body] in einer ersetzbaren Gruppe aus (Schlüssel = Aufrufstelle), wie es das Compose-Plugin täte. */
    fun <T> group(composer: Composer, at: Int, body: () -> T): T {
        composer.startReplaceGroup(KEY_BASE + at)
        val result = body()
        composer.endReplaceGroup()
        return result
    }

    class Found(val value: Any?)

    /** Sonderbehandlung einzelner Funktionen; `null` = normal auflösen. */
    fun intrinsic(name: String, call: Call, f: Frame): Found? {
        if (!importedFrom(name, RUNTIME_OR_DEFAULT[name] ?: return null)) return null
        return when (name) {
            "remember" -> {
                val c = f.composer ?: throw UnsupportedKotlinException("remember außerhalb einer Komposition")
                val calc = call.args.lastOrNull()?.value as? LambdaExpr ?: return null
                val keys = call.args.dropLast(1).map { interp.eval(it.value, f) }
                Found(group(c, call.at) {
                    var invalid = false
                    for (k in keys) invalid = c.changed(k) or invalid
                    val current = c.rememberedValue()
                    if (invalid || current === Composer.Empty) {
                        val v = interp.invokeLambda(InterpLambda(calc, f.env, f.receivers, null), emptyList(), null)
                        c.updateRememberedValue(v)
                        v
                    } else current
                })
            }
            "key" -> {
                val c = f.composer ?: return null
                val block = call.args.lastOrNull()?.value as? LambdaExpr ?: return null
                val keys = call.args.dropLast(1).map { interp.eval(it.value, f) }
                c.startMovableGroup(KEY_BASE + call.at, if (keys.size == 1) keys[0] else keys)
                val r = interp.invokeLambda(InterpLambda(block, f.env, f.receivers, null), emptyList(), c)
                c.endMovableGroup()
                Found(r)
            }
            "stringResource", "pluralStringResource", "stringArrayResource", "colorResource", "dimensionResource", "painterResource" -> resource(name, call, f)
            else -> null
        }
    }

    private fun importedFrom(name: String, pkg: String): Boolean {
        val imports = interp.file.imports
        val explicit = imports.firstOrNull { !it.star && (it.alias ?: it.path.substringAfterLast('.')) == name }
        if (explicit != null) return explicit.path.substringBeforeLast('.') == pkg || explicit.path.startsWith(PreviewResourceTable.STUB_PACKAGE)
        return imports.any { it.star && it.path == pkg } || pkg == "kotlin"
    }

    private fun resource(name: String, call: Call, f: Frame): Found? {
        val c = f.composer ?: return null
        val args = call.args.map { interp.eval(it.value, f) }
        val id = args.firstOrNull() as? Int ?: return null
        val table = interp.resources
        if (!table.isNative(id) && !table.isCompiledStub(id)) return null
        if (table.isCompiledStub(id)) return Found(table.callCompiledStub(name, args, c))
        val locale = c.consume(LocalConfiguration).locales[0]
        return Found(group(c, call.at) { table.resolve(name, id, args.drop(1), locale) })
    }

    // --- Lambdas als JVM-Funktionen ---------------------------------------------------------------------------

    /** Wandelt ein interpretiertes Lambda (oder eine Referenz) in ein Objekt des erwarteten Interface-Typs. */
    fun proxy(value: CallableValue, t: Class<*>, kmType: KmType?): Any {
        val iface = when {
            t.isInterface -> t
            else -> {
                val arity = interp.reflect.functionArity(kmType) ?: (value as? InterpLambda)?.expr?.params?.size ?: 1
                Class.forName("kotlin.jvm.functions.Function$arity")
            }
        }
        val kotlinArity = interp.reflect.functionArity(kmType)
        val suspend = interp.reflect.isSuspendType(kmType)
        val handler = InvocationHandler { proxy, method, rawArgs -> handle(value, proxy, method, rawArgs, kotlinArity, suspend) }
        return Proxy.newProxyInstance(iface.classLoader ?: javaClass.classLoader, arrayOf(iface), handler)
    }

    private fun handle(value: CallableValue, proxy: Any, method: Method, rawArgs: Array<Any?>?, kotlinArity: Int?, suspend: Boolean): Any? {
        if (method.declaringClass == Any::class.java) return when (method.name) {
            "equals" -> proxy === rawArgs?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            else -> "InterpretedLambda"
        }
        val args = rawArgs?.toList() ?: emptyList()
        // suspend-Lambdas (LaunchedEffect, pointerInput, …) laufen in der Vorschau nicht: dauerhaft „suspendiert“.
        if (suspend || args.lastOrNull() is kotlin.coroutines.Continuation<*>) return kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
        val composable = args.size >= 2 && args[args.size - 2] is Composer && args.last() is Int &&
            (kotlinArity == null || args.size == kotlinArity + 2)
        val composer = if (composable) args[args.size - 2] as Composer else null
        val actual = if (composable) args.dropLast(2) else args
        val frameArgs = actual.map { EvalArg(null, it, false) }
        val run = {
            when (value) {
                is InterpLambda -> interp.invokeLambda(value, actual, composer)
                else -> interp.invokeValue(value, frameArgs, Frame(Env(null), emptyList(), composer, null), -1)
            }
        }
        val result = if (composer != null && value is InterpLambda) group(composer, value.expr.at) { run() } else run()
        return when {
            method.returnType == Void.TYPE -> null
            method.returnType.isPrimitive -> interp.toJvm(result, method.returnType, null)
            result == null && method.returnType == Any::class.java && method.name == "invoke" -> if (isUnitLike(value)) Unit else null
            else -> result
        }
    }

    private fun isUnitLike(value: CallableValue): Boolean = value is InterpLambda

    companion object {
        const val KEY_BASE = 0x4CF00000
        val RUNTIME_OR_DEFAULT = mapOf(
            "remember" to "androidx.compose.runtime",
            "key" to "androidx.compose.runtime",
            "stringResource" to "androidx.compose.ui.res",
            "pluralStringResource" to "androidx.compose.ui.res",
            "stringArrayResource" to "androidx.compose.ui.res",
            "colorResource" to "androidx.compose.ui.res",
            "dimensionResource" to "androidx.compose.ui.res",
            "painterResource" to "androidx.compose.ui.res",
        )
    }
}
