/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

/** Typangabe (für `is`/`as`, Parameter und Rückgabetypen); Generics werden nur mitgelesen. */
internal data class TypeRef(
    val name: String,
    val args: List<TypeRef> = emptyList(),
    val nullable: Boolean = false,
    /** Funktionstyp: Empfänger, Parameter, Rückgabe. */
    val function: FunctionTypeRef? = null,
    val annotations: List<String> = emptyList(),
) {
    val simpleName: String get() = name.substringAfterLast('.')
}

internal data class FunctionTypeRef(val receiver: TypeRef?, val params: List<TypeRef>, val result: TypeRef, val suspend: Boolean)

/** Ausdrücke. Jeder Knoten kennt seinen Offset (für Fehler und stabile Gruppen-Schlüssel). */
internal sealed interface Expr { val at: Int }

internal data class Literal(val value: Any?, override val at: Int) : Expr
internal data class StringTemplate(val parts: List<Any>, override val at: Int) : Expr // String oder Expr
internal data class NameRef(val name: String, override val at: Int) : Expr
internal data class ThisRef(val label: String?, override val at: Int) : Expr
internal data class Arg(val name: String?, val value: Expr, val spread: Boolean = false)
internal data class Call(
    val callee: Expr,
    val typeArgs: List<TypeRef>,
    val args: List<Arg>,
    override val at: Int,
) : Expr
internal data class Navigation(val receiver: Expr, val name: String, val safe: Boolean, override val at: Int) : Expr
internal data class IndexExpr(val target: Expr, val indices: List<Expr>, override val at: Int) : Expr
internal data class Binary(val op: String, val left: Expr, val right: Expr, override val at: Int) : Expr
internal data class Unary(val op: String, val operand: Expr, override val at: Int) : Expr
internal data class Postfix(val op: String, val operand: Expr, override val at: Int) : Expr
internal data class IsCheck(val operand: Expr, val type: TypeRef, val negated: Boolean, override val at: Int) : Expr
internal data class Cast(val operand: Expr, val type: TypeRef, val safe: Boolean, override val at: Int) : Expr
internal data class IfExpr(val cond: Expr, val then: Block, val otherwise: Block?, override val at: Int) : Expr
internal data class WhenBranch(val conditions: List<WhenCondition>, val body: Block, val isElse: Boolean)
internal sealed interface WhenCondition {
    data class Value(val expr: Expr) : WhenCondition
    data class Is(val type: TypeRef, val negated: Boolean) : WhenCondition
    data class In(val expr: Expr, val negated: Boolean) : WhenCondition
}
internal data class WhenExpr(val subject: Expr?, val subjectName: String?, val branches: List<WhenBranch>, override val at: Int) : Expr
internal data class LambdaParam(val name: String, val type: TypeRef?)
internal data class LambdaExpr(val params: List<LambdaParam>?, val body: Block, val label: String?, override val at: Int) : Expr
internal data class CallableRef(val receiver: Expr?, val name: String, override val at: Int) : Expr
internal data class ReturnExpr(val value: Expr?, val label: String?, override val at: Int) : Expr
internal data class JumpExpr(val isBreak: Boolean, override val at: Int) : Expr
internal data class ThrowExpr(val value: Expr, override val at: Int) : Expr
internal data class TryExpr(val body: Block, val catches: List<Pair<Pair<String, TypeRef>, Block>>, val finally: Block?, override val at: Int) : Expr

/** Anweisungen. */
internal sealed interface Stmt { val at: Int }
internal data class ExprStmt(val expr: Expr) : Stmt { override val at get() = expr.at }
internal data class LocalVar(
    val names: List<String>, // mehrere = Destrukturierung
    val isVar: Boolean,
    val type: TypeRef?,
    val init: Expr?,
    val delegate: Expr?,
    override val at: Int,
) : Stmt
internal data class Assign(val target: Expr, val op: String, val value: Expr, override val at: Int) : Stmt
internal data class ForLoop(val names: List<String>, val iterable: Expr, val body: Block, override val at: Int) : Stmt
internal data class WhileLoop(val cond: Expr, val body: Block, val doWhile: Boolean, override val at: Int) : Stmt
internal data class LocalFun(val decl: FunDecl) : Stmt { override val at get() = decl.at }

internal data class Block(val statements: List<Stmt>, val at: Int)

/** Deklarationen auf Dateiebene. */
internal data class Param(val name: String, val type: TypeRef?, val default: Expr?, val vararg: Boolean)

internal data class FunDecl(
    val name: String,
    val receiver: TypeRef?,
    val params: List<Param>,
    val returnType: TypeRef?,
    /** Blockrumpf oder Ausdrucksrumpf (als Block mit einem Ausdruck und [expressionBody] = true). */
    val body: Block?,
    val expressionBody: Boolean,
    val annotations: List<String>,
    val modifiers: Set<String>,
    val at: Int,
) {
    val isComposable: Boolean get() = annotations.any { it == "Composable" || it.endsWith(".Composable") }
}

internal data class PropDecl(
    val name: String,
    val receiver: TypeRef?,
    val isVar: Boolean,
    val init: Expr?,
    val getter: Block?,
    val getterIsExpression: Boolean,
    val delegate: Expr?,
    val at: Int,
)

/** Klassen, Objekte, Enums und Typaliase in der Zieldatei werden (noch) nicht interpretiert. */
internal data class UnsupportedDecl(val kind: String, val name: String, val at: Int)

internal data class KtFile(
    val packageName: String,
    val imports: List<Import>,
    val functions: List<FunDecl>,
    val properties: List<PropDecl>,
    val unsupported: List<UnsupportedDecl>,
)

internal data class Import(val path: String, val alias: String?, val star: Boolean)
