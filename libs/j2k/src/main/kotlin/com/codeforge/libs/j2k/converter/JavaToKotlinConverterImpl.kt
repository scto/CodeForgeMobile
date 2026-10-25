package com.codeforge.libs.j2k.converter

import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.repository.J2kFileResultWithContent
import com.codeforge.core.domain.repository.JavaToKotlinConverter
import java.util.regex.Pattern

class JavaToKotlinConverterImpl : JavaToKotlinConverter {

    override suspend fun convertFile(javaSource: String, filePath: String): J2kFileResultWithContent {
        val notes = mutableListOf<String>()
        val originalLines = javaSource.lines().size

        try {
            var kt = javaSource

            // 1. Javadoc zu KDoc: {@link Foo} -> [Foo]
            kt = kt.replace(Regex("""\{@link\s+([^}]+)\}"""), "[$1]")
            kt = kt.replace(Regex("""\{@code\s+([^}]+)\}"""), "`$1`")

            // 2. Package-Deklaration bereinigen (falls nötig)
            // bleibt erhalten

            // 3. Imports bereinigen / anpassen
            kt = kt.replace("import java.util.Objects;\n", "")
            kt = kt.replace("import java.util.List;", "// import java.util.List")
            kt = kt.replace("import java.util.Map;", "// import java.util.Map")
            kt = kt.replace("import java.util.Set;", "// import java.util.Set")

            // 4. Annotationen
            kt = kt.replace("@Nullable", "/* @Nullable */")
            kt = kt.replace("@NonNull", "")
            kt = kt.replace("@NotNull", "")
            kt = kt.replace(Regex("""public\s+@interface\s+(\w+)"""), "annotation class $1")
            kt = kt.replace(Regex("""@interface\s+(\w+)"""), "annotation class $1")

            // 5. Records (Java 14+) -> data class
            val recordPattern = Pattern.compile("""public\s+record\s+(\w+)\s*\(([^)]*)\)\s*\{([^}]*)\}""")
            val recordMatcher = recordPattern.matcher(kt)
            if (recordMatcher.find()) {
                val recordName = recordMatcher.group(1)
                val params = recordMatcher.group(2).split(",").mapNotNull { p ->
                    val trimmed = p.trim()
                    if (trimmed.isEmpty()) null
                    else {
                        val tokens = trimmed.split(Regex("""\s+"""))
                        if (tokens.size >= 2) {
                            val type = mapType(tokens[0])
                            val name = tokens[1]
                            "val $name: $type"
                        } else trimmed
                    }
                }.joinToString(", ")
                val body = recordMatcher.group(3).trim()
                val replacement = if (body.isEmpty()) {
                    "data class $recordName($params)"
                } else {
                    "data class $recordName($params) {\n    $body\n}"
                }
                kt = kt.replace(recordMatcher.group(0), replacement)
            }

            // 6. Enums
            kt = kt.replace(Regex("""(public\s+)?enum\s+(?!class\b)(\w+)"""), "enum class $2")

            // 7. Interfaces
            kt = kt.replace(Regex("""public\s+interface\s+(\w+)"""), "interface $1")

            // 8. Klassen
            kt = kt.replace(Regex("""public\s+abstract\s+class\s+(\w+)"""), "abstract class $1")
            kt = kt.replace(Regex("""public\s+class\s+(\w+)\s+extends\s+(\w+)\s+implements\s+([^\{]+)""")) { m ->
                val name = m.groupValues[1]
                val parent = m.groupValues[2]
                val ifaces = m.groupValues[3].trim()
                "class $name : $parent(), $ifaces"
            }
            kt = kt.replace(Regex("""public\s+class\s+(\w+)\s+extends\s+(\w+)""")) { m ->
                "class ${m.groupValues[1]} : ${m.groupValues[2]}()"
            }
            kt = kt.replace(Regex("""public\s+class\s+(\w+)\s+implements\s+([^\{]+)""")) { m ->
                "class ${m.groupValues[1]} : ${m.groupValues[2].trim()}"
            }
            kt = kt.replace(Regex("""public\s+class\s+(\w+)"""), "class $1")

            // 9. Primitive Typen & Sammlungen in Deklarationen
            kt = replaceJavaTypesAndFields(kt, notes)

            // 10. Methoden & Funktionen
            kt = replaceMethods(kt, notes)

            // 11. Kontrollstrukturen & Operatoren
            kt = replaceControlStructures(kt, notes)

            // 12. Try-with-resources: try (Resource r = new Resource()) -> Resource().use { r ->
            kt = kt.replace(Regex("""try\s*\(\s*(\w+)\s+(\w+)\s*=\s*(.+?)\s*;?\s*\)\s*\{""")) { m ->
                val varName = m.groupValues[2]
                val initExpr = m.groupValues[3].trim().removePrefix("new ").trim()
                "$initExpr.use { $varName ->"
            }

            // 13. equals / ==
            kt = kt.replace(".equals(", " == (")
            kt = kt.replace(Regex("""Objects\.equals\(([^,]+),\s*([^)]+)\)"""), "$1 == $2")

            // 14. Modifikatoren
            kt = kt.replace("synchronized ", "@Synchronized ")
            kt = kt.replace("volatile ", "@Volatile ")
            kt = kt.replace("transient ", "@Transient ")
            kt = kt.replace("native ", "external ")

            // 15. Generics ? extends T -> out T, ? super T -> in T
            kt = kt.replace("? extends ", "out ")
            kt = kt.replace("? super ", "in ")

            // 16. Bitweise Operatoren
            kt = kt.replace(" & ", " and ")
            kt = kt.replace(" | ", " or ")
            kt = kt.replace(" ^ ", " xor ")
            kt = kt.replace(" << ", " shl ")
            kt = kt.replace(" >> ", " shr ")
            kt = kt.replace(" >>> ", " ushr ")

            val convertedLines = kt.lines().size
            val status = if (notes.isNotEmpty() || kt.contains("// TODO(j2k)")) {
                J2kFileStatus.CONVERTED_WITH_NOTES
            } else {
                J2kFileStatus.CONVERTED
            }

            return J2kFileResultWithContent(
                result = J2kFileResult(
                    filePath = filePath,
                    status = status,
                    originalLines = originalLines,
                    convertedLines = convertedLines,
                    notes = notes
                ),
                convertedContent = kt
            )
        } catch (e: Exception) {
            return J2kFileResultWithContent(
                result = J2kFileResult(
                    filePath = filePath,
                    status = J2kFileStatus.FAILED,
                    originalLines = originalLines,
                    convertedLines = 0,
                    errorMessage = e.message
                ),
                convertedContent = null
            )
        }
    }

    private fun replaceJavaTypesAndFields(source: String, notes: MutableList<String>): String {
        var res = source

        // Statische Konstanten: public static final int MAX = 100; -> const val MAX: Int = 100
        res = res.replace(Regex("""(public|private|protected)?\s*static\s+final\s+(\w+)\s+(\w+)\s*=\s*([^;]+);""")) { m ->
            val vis = m.groupValues[1].let { if (it.isEmpty() || it == "public") "" else "$it " }
            val type = mapType(m.groupValues[2])
            val name = m.groupValues[3]
            val value = m.groupValues[4].trim()
            "${vis}const val $name: $type = $value"
        }

        // final Felder: private final String name; -> private val name: String
        res = res.replace(Regex("""(public|private|protected)?\s*final\s+(\w+)\s+(\w+)\s*=\s*([^;]+);""")) { m ->
            val vis = m.groupValues[1].let { if (it.isEmpty() || it == "public") "" else "$it " }
            val type = mapType(m.groupValues[2])
            val name = m.groupValues[3]
            val value = m.groupValues[4].trim()
            "${vis}val $name: $type = $value"
        }

        // normale Felder: private int count = 0; -> private var count: Int = 0
        res = res.replace(Regex("""(public|private|protected)\s+(\w+)\s+(\w+)\s*=\s*([^;]+);""")) { m ->
            val vis = m.groupValues[1]
            val type = mapType(m.groupValues[2])
            val name = m.groupValues[3]
            val value = m.groupValues[4].trim()
            "$vis var $name: $type = $value"
        }

        // new Keyword entfernen
        res = res.replace(Regex("""new\s+([A-Z]\w*)"""), "$1")

        // instanceof -> is
        res = res.replace(" instanceof ", " is ")

        // Casts: (String) x -> x as String, (int) x -> x.toInt()
        res = res.replace(Regex("""\((int)\)\s*(\w+)"""), "$2.toInt()")
        res = res.replace(Regex("""\((long)\)\s*(\w+)"""), "$2.toLong()")
        res = res.replace(Regex("""\((double)\)\s*(\w+)"""), "$2.toDouble()")
        res = res.replace(Regex("""\((float)\)\s*(\w+)"""), "$2.toFloat()")
        res = res.replace(Regex("""\(([A-Z]\w*)\)\s*(\w+)"""), "$2 as $1")

        return res
    }

    private fun replaceMethods(source: String, notes: MutableList<String>): String {
        var res = source

        // @Override void method() -> override fun method()
        res = res.replace(Regex("""@Override\s+(public|protected|private)?\s*(void|[A-Z]\w*|\w+)\s+(\w+)\s*\(([^)]*)\)\s*\{""")) { m ->
            val returnType = m.groupValues[2]
            val name = m.groupValues[3]
            val rawParams = m.groupValues[4]
            val params = convertParams(rawParams)
            val ret = if (returnType == "void") "" else ": ${mapType(returnType)}"
            "override fun $name($params)$ret {"
        }

        // public static void main(String[] args) -> fun main(args: Array<String>)
        res = res.replace(Regex("""public\s+static\s+void\s+main\s*\(\s*String\[\]\s+(\w+)\s*\)""")) { m ->
            "fun main(${m.groupValues[1]}: Array<String>)"
        }

        // Abstrakte / Interface-Methoden ohne Rumpf: void foo(int a); -> fun foo(a: Int)
        res = res.replace(Regex("""(public|protected|private|abstract)?\s*(abstract\s+)?(void|[A-Z]\w*|\w+)\s+(\w+)\s*\(([^)]*)\)\s*(throws\s+[\w,\s]+)?\s*;""")) { m ->
            val returnType = m.groupValues[3]
            val name = m.groupValues[4]
            val rawParams = m.groupValues[5]
            val throwsClause = m.groupValues[6]

            if (name in setOf("if", "for", "while", "catch", "switch", "synchronized", "return", "throw", "super", "this", "new")) {
                m.groupValues[0]
            } else {
                val params = convertParams(rawParams)
                val ret = if (returnType == "void") "" else ": ${mapType(returnType)}"
                val throwsAnnot = if (throwsClause.isNotBlank()) {
                    val excs = throwsClause.removePrefix("throws").trim().split(",").map { "${it.trim()}::class" }.joinToString(", ")
                    "@Throws($excs)\n"
                } else ""
                "$throwsAnnot fun $name($params)$ret"
            }
        }

        // Normale Methoden: public void foo(int a) -> fun foo(a: Int)
        res = res.replace(Regex("""(public|protected|private)?\s*(void|[A-Z]\w*|\w+)\s+(\w+)\s*\(([^)]*)\)\s*(throws\s+[\w,\s]+)?\s*\{""")) { m ->
            val vis = m.groupValues[1].let { if (it.isEmpty() || it == "public") "" else "$it " }
            val returnType = m.groupValues[2]
            val name = m.groupValues[3]
            val rawParams = m.groupValues[4]
            val throwsClause = m.groupValues[5]

            // Schlüsselwörter ausschließen (if, for, while, catch)
            if (name in setOf("if", "for", "while", "catch", "switch", "synchronized")) {
                m.groupValues[0]
            } else {
                val params = convertParams(rawParams)
                val ret = if (returnType == "void") "" else ": ${mapType(returnType)}"
                val throwsAnnot = if (throwsClause.isNotBlank()) {
                    val excs = throwsClause.removePrefix("throws").trim().split(",").map { "${it.trim()}::class" }.joinToString(", ")
                    "@Throws($excs)\n"
                } else ""
                "$throwsAnnot${vis}fun $name($params)$ret {"
            }
        }

        return res
    }

    private fun replaceControlStructures(source: String, notes: MutableList<String>): String {
        var res = source

        // for (int i = 0; i < n; i++) -> for (i in 0 until n)
        res = res.replace(Regex("""for\s*\(\s*int\s+(\w+)\s*=\s*0\s*;\s*\1\s*<\s*([^;]+);\s*\1\+\+\s*\)""")) { m ->
            "for (${m.groupValues[1]} in 0 until ${m.groupValues[2].trim()})"
        }

        // for (T item : list) -> for (item in list)
        res = res.replace(Regex("""for\s*\(\s*(\w+)\s+(\w+)\s*:\s*([^)]+)\)""")) { m ->
            "for (${m.groupValues[2]} in ${m.groupValues[3].trim()})"
        }

        // switch (x) { case A: ... break; default: ... } -> when (x) { A -> ... else -> ... }
        res = res.replace(Regex("""switch\s*\(([^)]+)\)\s*\{""")) { m ->
            "when (${m.groupValues[1].trim()}) {"
        }
        res = res.replace(Regex("""case\s+([^:]+):""")) { m ->
            "${m.groupValues[1].trim()} ->"
        }
        res = res.replace("default:", "else ->")
        res = res.replace(Regex("""break;\s*"""), "")

        // Null-Vergleich Heuristik für TODOs
        if (res.contains(" == null") || res.contains(" != null")) {
            notes.add("Mögliche Nullbarkeit in Vergleichen erkannt; Typen überprüfen.")
        }

        return res
    }

    private fun convertParams(rawParams: String): String {
        if (rawParams.isBlank()) return ""
        return rawParams.split(",").mapNotNull { p ->
            val trimmed = p.trim()
            if (trimmed.isEmpty()) null
            else {
                val parts = trimmed.split(Regex("""\s+"""))
                if (parts.size >= 2) {
                    val type = mapType(parts[parts.size - 2])
                    val name = parts[parts.size - 1]
                    "$name: $type"
                } else trimmed
            }
        }.joinToString(", ")
    }

    private fun mapType(javaType: String): String {
        return when (javaType) {
            "int" -> "Int"
            "long" -> "Long"
            "double" -> "Double"
            "float" -> "Float"
            "boolean" -> "Boolean"
            "byte" -> "Byte"
            "short" -> "Short"
            "char" -> "Char"
            "void" -> "Unit"
            "Object" -> "Any"
            "String[]" -> "Array<String>"
            "int[]" -> "IntArray"
            "long[]" -> "LongArray"
            else -> {
                if (javaType.startsWith("List<")) javaType
                else if (javaType.startsWith("Map<")) javaType
                else if (javaType.startsWith("Set<")) javaType
                else javaType
            }
        }
    }
}
