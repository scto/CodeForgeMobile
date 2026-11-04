/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Zuordnung Dateiname/-endung -> TextMate-Scope der mitgelieferten Grammatiken
 * (siehe `assets/textmate/languages.json`). Kotlin/Java/XML/JSON laufen weiterhin über
 * [com.codeforge.feature.editor.EditorLanguageType].
 */
package com.codeforge.feature.editor.textmate

/** Mitgelieferte TextMate-Grammatiken. */
object BundledGrammars {

    private val byExtension: Map<String, String> = buildMap {
        fun add(scope: String, vararg ext: String) = ext.forEach { put(it, scope) }
        add("source.dart", "dart")
        add("source.asm", "asm", "s", "nasm")
        add("source.pascal", "pas", "pp", "dpr")
        add("source.toml", "toml")
        add("source.zig", "zig")
        add("text.tex.latex", "tex", "sty", "cls")
        add("source.nim", "nim", "nims")
        add("source.coq", "v")
        add("source.lua", "lua")
        add("source.smali", "smali")
        add("source.python", "py", "pyw", "pyi")
        add("text.html.basic", "html", "htm", "xhtml")
        add("text.html.htmx", "htmx")
        add("source.ts", "ts", "mts", "cts")
        add("source.tsx", "tsx")
        add("source.java-properties", "properties")
        add("text.html.php", "phtml")
        add("source.php", "php")
        add("source.js", "js", "mjs", "cjs")
        add("source.js.jsx", "jsx")
        add("source.cpp", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "h")
        add("source.c", "c")
        add("source.ini", "ini", "cfg", "conf")
        add("source.css", "css")
        add("source.cs", "cs")
        add("source.go", "go")
        add("source.yaml", "yml", "yaml")
        add("source.shell", "sh", "bash", "zsh")
        add("source.rust", "rs")
        add("source.groovy", "groovy", "gradle")
        add("text.html.markdown", "md", "markdown")
        add("source.batchfile", "bat", "cmd")
        add("source.lisp", "lisp", "lsp", "cl", "el")
        add("source.sql", "sql")
        add("text.plain", "txt")
    }

    /** Scope-Name für den Dateipfad oder `null`. */
    fun scopeForPath(path: String): String? {
        val name = path.substringAfterLast('/').lowercase()
        if (name == "gradlew" || name == ".bashrc" || name == ".profile") return "source.shell"
        val ext = name.substringAfterLast('.', "")
        return byExtension[ext]
    }
}
