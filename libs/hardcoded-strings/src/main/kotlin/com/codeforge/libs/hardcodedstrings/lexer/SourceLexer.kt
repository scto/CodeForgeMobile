package com.codeforge.libs.hardcodedstrings.lexer

enum class TokenType {
    STRING_LITERAL,
    RAW_STRING_LITERAL,
    COMMENT,
    IDENTIFIER,
    SYMBOL,
    ANNOTATION,
    OTHER
}

data class Token(
    val type: TokenType,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int
)

/**
 * Pure Kotlin JVM lexer for Kotlin and Java source code.
 * Precisely tracks character offsets, lines, and columns without external native dependencies.
 */
class SourceLexer(private val content: String) {

    private var pos = 0
    private var line = 1
    private var col = 1

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < content.length) {
            val c = content[pos]

            // Whitespace
            if (c.isWhitespace()) {
                advance()
                continue
            }

            val startPos = pos
            val startLine = line
            val startCol = col

            when {
                // Line comment
                c == '/' && peek() == '/' -> {
                    val sb = StringBuilder()
                    while (pos < content.length && content[pos] != '\n') {
                        sb.append(advance())
                    }
                    tokens.add(
                        Token(TokenType.COMMENT, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Block comment
                c == '/' && peek() == '*' -> {
                    val sb = StringBuilder()
                    sb.append(advance()) // /
                    sb.append(advance()) // *
                    while (pos < content.length) {
                        if (content[pos] == '*' && peek() == '/') {
                            sb.append(advance())
                            sb.append(advance())
                            break
                        } else {
                            sb.append(advance())
                        }
                    }
                    tokens.add(
                        Token(TokenType.COMMENT, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Raw string literal """...""" (Kotlin)
                c == '"' && peek() == '"' && peek(2) == '"' -> {
                    val sb = StringBuilder()
                    sb.append(advance()) // "
                    sb.append(advance()) // "
                    sb.append(advance()) // "
                    while (pos < content.length) {
                        if (content[pos] == '"' && peek() == '"' && peek(2) == '"') {
                            sb.append(advance())
                            sb.append(advance())
                            sb.append(advance())
                            break
                        } else {
                            sb.append(advance())
                        }
                    }
                    tokens.add(
                        Token(TokenType.RAW_STRING_LITERAL, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Single-quoted character literal
                c == '\'' -> {
                    val sb = StringBuilder()
                    sb.append(advance())
                    while (pos < content.length) {
                        val ch = advance()
                        sb.append(ch)
                        if (ch == '\\' && pos < content.length) {
                            sb.append(advance())
                        } else if (ch == '\'') {
                            break
                        }
                    }
                    tokens.add(
                        Token(TokenType.OTHER, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Regular string literal "..."
                c == '"' -> {
                    val sb = StringBuilder()
                    sb.append(advance())
                    var escaped = false
                    while (pos < content.length) {
                        val ch = advance()
                        sb.append(ch)
                        if (escaped) {
                            escaped = false
                        } else if (ch == '\\') {
                            escaped = true
                        } else if (ch == '"') {
                            break
                        } else if (ch == '\n') {
                            // Unterminated string on single line
                            break
                        }
                    }
                    tokens.add(
                        Token(TokenType.STRING_LITERAL, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Annotation (@Composable, @Preview, etc.)
                c == '@' -> {
                    val sb = StringBuilder()
                    sb.append(advance())
                    while (pos < content.length && (content[pos].isJavaIdentifierPart() || content[pos] == '.')) {
                        sb.append(advance())
                    }
                    tokens.add(
                        Token(TokenType.ANNOTATION, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Identifier
                c.isJavaIdentifierStart() -> {
                    val sb = StringBuilder()
                    while (pos < content.length && content[pos].isJavaIdentifierPart()) {
                        sb.append(advance())
                    }
                    tokens.add(
                        Token(TokenType.IDENTIFIER, sb.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }

                // Symbols & Punctuation
                else -> {
                    val ch = advance()
                    tokens.add(
                        Token(TokenType.SYMBOL, ch.toString(), startPos, pos, startLine, startCol, line, col)
                    )
                }
            }
        }
        return tokens
    }

    private fun peek(offset: Int = 1): Char? {
        val target = pos + offset
        return if (target < content.length) content[target] else null
    }

    private fun advance(): Char {
        val c = content[pos++]
        if (c == '\n') {
            line++
            col = 1
        } else {
            col++
        }
        return c
    }
}
