// Modul: :feature:editor
package com.codeforge.feature.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

/** Sprache, die eine andere umhüllt (LSP-Vorschläge, Wortlisten, Tab-Regel). */
interface LanguageWrapper {
    /** Die umhüllte Sprache. */
    val inner: Language
}

/** Innerste Sprache hinter allen [LanguageWrapper]n. */
fun Language.innermost(): Language {
    var current = this
    while (current is LanguageWrapper) current = current.inner
    return current
}

/**
 * Äußerste Hülle jeder Editor-Sprache: entscheidet, ob die Tab-Taste ein `\t` ([hardTabs]) oder Leerzeichen in
 * Tab-Breite einfügt – auch für Sprachen ohne eigene Einstellung ([EmptyLanguage], Tree-sitter).
 */
class TabPolicyLanguage(override val inner: Language, var hardTabs: Boolean = false) : Language by inner, LanguageWrapper {
    override fun useTab(): Boolean = hardTabs
}

/**
 * Überträgt Soft-Tab und Tab-Breite auf die Sprache des Editors. TextMate rückt neue Zeilen selbst ein und braucht
 * deshalb dieselben Werte wie der Editor.
 */
fun applyTabPolicy(language: Language, hardTabs: Boolean, tabSize: Int) {
    (language as? TabPolicyLanguage)?.hardTabs = hardTabs
    (language.innermost() as? TextMateLanguage)?.let {
        it.useTab(hardTabs)
        it.tabSize = tabSize
    }
}
