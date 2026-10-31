"""Kompakte, selbst geschriebene TextMate-Grammatiken (vereinfacht: Kommentare, Strings, Zahlen, Schlüsselwörter, Typen, Aufrufe).

Keine vollständigen Sprachdefinitionen – sie färben die gängigen Bestandteile und fallen sonst auf Klartext zurück.
"""
NUM = r"\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?[fFlLuU]*)\b"
IDENT_CALL = r"\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"
TYPE_NAME = r"\b[A-Z][A-Za-z0-9_]*\b"
OPS = r"[-+*/%=<>!&|^~?:]+"
ESC = {"match": r"\\.", "name": "constant.character.escape"}


def words(lst):
    return r"\b(?:" + "|".join(lst) + r")\b"


def string(q, name, multiline=False, esc=True):
    end = q if multiline else "(?:" + q + ")|(?=$)"
    return {"begin": q, "end": end, "name": f"string.quoted.{name}", "patterns": [ESC] if esc else []}


def build(name, scope, line=(), block=None, strings=("dq", "sq"), control=(), storage=(), modifier=(), constants=(),
          builtin=(), extra=(), types=True, calls=True, numbers=True, ops=True, extra_after=()):
    pats = [dict(p) for p in extra]
    for lc in line:
        pats.append({"match": lc + r".*$", "name": "comment.line"})
    if block:
        pats.append({"begin": block[0], "end": block[1], "name": "comment.block"})
    table = {
        "dq": string('"', "double"), "sq": string("'", "single"), "bt": string("`", "template", multiline=True),
        "dq3": {"begin": '"""', "end": '"""', "name": "string.quoted.triple", "patterns": [ESC]},
        "sq3": {"begin": "'''", "end": "'''", "name": "string.quoted.triple", "patterns": [ESC]},
        "raw": {"begin": r'r(#*)"', "end": r'"\1', "name": "string.quoted.raw"},
        "dqraw": {"match": r'@"(?:[^"]|"")*"', "name": "string.quoted.verbatim"},
    }
    for s in strings:
        pats.append(table[s])
    if numbers:
        pats.append({"match": NUM, "name": "constant.numeric"})
    if control:
        pats.append({"match": words(control), "name": "keyword.control"})
    if storage:
        pats.append({"match": words(storage), "name": "storage.type"})
    if modifier:
        pats.append({"match": words(modifier), "name": "storage.modifier"})
    if constants:
        pats.append({"match": words(constants), "name": "constant.language"})
    if builtin:
        pats.append({"match": words(builtin), "name": "support.type"})
    pats.extend(dict(p) for p in extra_after)
    if calls:
        pats.append({"match": IDENT_CALL, "name": "entity.name.function"})
    if types:
        pats.append({"match": TYPE_NAME, "name": "entity.name.type"})
    if ops:
        pats.append({"match": OPS, "name": "keyword.operator"})
    return {"name": name, "scopeName": scope, "patterns": pats}


HASH = ["#"]
SLASH = ["//"]
CBLOCK = (r"/\*", r"\*/")
C_CONTROL = ["if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "goto"]
PREPROC = {"match": r"^\s*#\s*(?:include|define|undef|ifdef|ifndef|if|elif|else|endif|pragma|error|warning)\b.*$", "name": "meta.preprocessor"}
SHELL_VAR = {"match": r"\$(?:\{[^}]*\}|[A-Za-z_][A-Za-z0-9_]*|[0-9@#?$!*-])", "name": "variable.other"}

# id, Anzeigename, languageId, scope, Dateiendungen, Dateinamen, Grammatik
LANGS = [
    ("python", "Python", "python", "source.python", ["py", "pyi", "pyw"], [], build(
        "Python", "source.python", line=HASH, strings=("dq3", "sq3", "dq", "sq"),
        control=["if", "elif", "else", "for", "while", "try", "except", "finally", "with", "return", "yield", "raise", "break", "continue", "pass", "assert", "match", "case", "await", "async"],
        storage=["def", "class", "lambda", "import", "from", "as", "global", "nonlocal"],
        modifier=["and", "or", "not", "in", "is", "del"], constants=["True", "False", "None", "self", "cls"],
        builtin=["int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes", "object", "len", "range", "print"],
        extra=[{"match": r"^\s*@[A-Za-z_][\w.]*", "name": "meta.decorator"}])),
    ("javascript", "JavaScript", "javascript", "source.js", ["js", "mjs", "cjs", "jsx"], [], build(
        "JavaScript", "source.js", line=SLASH, block=CBLOCK, strings=("dq", "sq", "bt"),
        control=["if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "await", "yield", "of", "in"],
        storage=["var", "let", "const", "function", "class", "import", "export", "from", "as", "async", "new", "extends", "static", "get", "set"],
        modifier=["typeof", "instanceof", "delete", "void"], constants=["true", "false", "null", "undefined", "NaN", "Infinity", "this", "super"],
        builtin=["Array", "Object", "String", "Number", "Boolean", "Promise", "Map", "Set", "JSON", "Math", "console"])),
    ("typescript", "TypeScript", "typescript", "source.ts", ["ts", "tsx", "mts", "cts"], [], build(
        "TypeScript", "source.ts", line=SLASH, block=CBLOCK, strings=("dq", "sq", "bt"),
        control=["if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "await", "yield", "of", "in"],
        storage=["var", "let", "const", "function", "class", "interface", "type", "enum", "namespace", "import", "export", "from", "as", "async", "new", "extends", "implements", "declare", "abstract"],
        modifier=["public", "private", "protected", "readonly", "static", "typeof", "keyof", "instanceof", "satisfies"],
        constants=["true", "false", "null", "undefined", "this", "super"],
        builtin=["string", "number", "boolean", "any", "unknown", "never", "void", "object", "Array", "Record", "Promise", "Partial", "Readonly"],
        extra=[{"match": r"@[A-Za-z_]\w*", "name": "meta.decorator"}])),
    ("c", "C", "c", "source.c", ["c", "h"], [], build(
        "C", "source.c", line=SLASH, block=CBLOCK, control=C_CONTROL,
        storage=["struct", "union", "enum", "typedef", "sizeof"], modifier=["const", "static", "extern", "volatile", "register", "inline", "restrict", "unsigned", "signed"],
        constants=["NULL", "true", "false"], builtin=["int", "char", "short", "long", "float", "double", "void", "size_t", "bool", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t", "int32_t", "int64_t"],
        extra=[PREPROC])),
    ("cpp", "C++", "cpp", "source.cpp", ["cpp", "cc", "cxx", "hpp", "hh", "hxx"], [], build(
        "C++", "source.cpp", line=SLASH, block=CBLOCK, strings=("raw", "dq", "sq"),
        control=C_CONTROL + ["try", "catch", "throw", "co_await", "co_return", "co_yield"],
        storage=["class", "struct", "union", "enum", "typedef", "using", "namespace", "template", "typename", "new", "delete", "sizeof", "operator", "concept", "requires"],
        modifier=["const", "constexpr", "consteval", "static", "extern", "virtual", "override", "final", "explicit", "inline", "mutable", "noexcept", "public", "private", "protected", "friend", "auto", "unsigned", "signed"],
        constants=["nullptr", "NULL", "true", "false", "this"],
        builtin=["int", "char", "short", "long", "float", "double", "void", "bool", "size_t", "string", "vector", "map", "unique_ptr", "shared_ptr"],
        extra=[PREPROC])),
    ("rust", "Rust", "rust", "source.rust", ["rs"], [], build(
        "Rust", "source.rust", line=SLASH, block=CBLOCK, strings=("raw", "dq"),
        control=["if", "else", "for", "while", "loop", "match", "break", "continue", "return", "in", "await", "async", "move"],
        storage=["fn", "let", "struct", "enum", "trait", "impl", "type", "mod", "use", "crate", "pub", "mut", "ref", "const", "static", "unsafe", "extern", "where", "as", "dyn"],
        constants=["true", "false", "self", "Self", "None", "Some", "Ok", "Err"],
        builtin=["i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result", "Box"],
        extra=[{"match": r"#!?\[[^\]]*\]", "name": "meta.attribute"}, {"match": r"'[a-z_]+\b", "name": "storage.modifier.lifetime"},
               {"match": r"\b[A-Za-z_]\w*!", "name": "support.function.macro"}, {"match": r"'(?:\\.|[^'\\])'", "name": "string.quoted.char"}])),
    ("go", "Go", "go", "source.go", ["go"], [], build(
        "Go", "source.go", line=SLASH, block=CBLOCK, strings=("dq", "bt", "sq"),
        control=["if", "else", "for", "range", "switch", "case", "default", "break", "continue", "return", "goto", "fallthrough", "select", "defer", "go"],
        storage=["func", "var", "const", "type", "struct", "interface", "map", "chan", "package", "import"], constants=["true", "false", "nil", "iota"],
        builtin=["int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16", "uint32", "uint64", "float32", "float64", "string", "bool", "byte", "rune", "error", "any", "make", "len", "cap", "append", "new"])),
    ("shellscript", "Shell", "shellscript", "source.shell", ["sh", "bash", "zsh", "ksh"], [".bashrc", ".zshrc", ".profile"], build(
        "Shell", "source.shell", line=HASH, strings=("dq", "sq"),
        control=["if", "then", "elif", "else", "fi", "for", "while", "until", "do", "done", "case", "esac", "in", "select", "return", "break", "continue", "exit"],
        storage=["function", "local", "export", "readonly", "declare", "unset", "source", "alias"], constants=["true", "false"],
        builtin=["echo", "cd", "pwd", "test", "read", "printf", "eval", "exec", "set", "shift", "trap", "wait"],
        extra=[SHELL_VAR, {"match": r"^#!.*$", "name": "comment.line.shebang"}], types=False)),
    ("yaml", "YAML", "yaml", "source.yaml", ["yml", "yaml"], [], build(
        "YAML", "source.yaml", line=HASH, strings=("dq", "sq"), constants=["true", "false", "null", "yes", "no", "on", "off"],
        extra=[{"match": r"^\s*-?\s*[\w./-]+(?=\s*:(?:\s|$))", "name": "entity.name.tag"}, {"match": r"[&*][\w-]+", "name": "variable.other.anchor"},
               {"match": r"^---|^\.\.\.", "name": "meta.separator"}], calls=False, types=False, ops=False)),
    ("toml", "TOML", "toml", "source.toml", ["toml"], ["Cargo.lock"], build(
        "TOML", "source.toml", line=HASH, strings=("dq3", "sq3", "dq", "sq"), constants=["true", "false"],
        extra=[{"match": r"^\s*\[\[?[^\]]+\]\]?", "name": "entity.name.section"}, {"match": r"^\s*[\w.\"-]+(?=\s*=)", "name": "variable.other.key"},
               {"match": r"\b\d{4}-\d{2}-\d{2}(?:[Tt ][\d:.]+(?:Z|[+-]\d{2}:\d{2})?)?\b", "name": "constant.numeric.date"}], calls=False, types=False, ops=False)),
    ("html", "HTML", "html", "text.html", ["html", "htm", "xhtml"], [], {
        "name": "HTML", "scopeName": "text.html", "patterns": [
            {"begin": "<!--", "end": "-->", "name": "comment.block"},
            {"match": r"<!DOCTYPE[^>]*>", "name": "meta.doctype"},
            {"begin": r"(</?)([A-Za-z][\w:-]*)", "end": r"/?>", "beginCaptures": {"1": {"name": "punctuation.definition.tag"}, "2": {"name": "entity.name.tag"}},
             "patterns": [{"match": r"[\w:-]+(?=\s*=)", "name": "entity.other.attribute-name"}, string('"', "double"), string("'", "single")]},
            {"match": r"&[#\w]+;", "name": "constant.character.entity"}]}),
    ("css", "CSS", "css", "source.css", ["css", "scss", "less"], [], {
        "name": "CSS", "scopeName": "source.css", "patterns": [
            {"begin": r"/\*", "end": r"\*/", "name": "comment.block"},
            {"match": r"@[\w-]+", "name": "keyword.control.at-rule"},
            {"match": r"[.#][A-Za-z_][\w-]*", "name": "entity.name.tag.selector"},
            {"match": r"\b[a-z-]+(?=\s*:)", "name": "support.type.property-name"},
            {"match": r"#[0-9a-fA-F]{3,8}\b", "name": "constant.other.color"},
            {"match": r"-?\d*\.?\d+(?:px|em|rem|%|vh|vw|s|ms|deg|fr)?\b", "name": "constant.numeric"},
            string('"', "double"), string("'", "single"),
            {"match": r"\b[a-z-]+(?=\()", "name": "support.function"}]}),
    ("dart", "Dart", "dart", "source.dart", ["dart"], [], build(
        "Dart", "source.dart", line=SLASH, block=CBLOCK, strings=("dq3", "sq3", "dq", "sq"),
        control=["if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "rethrow", "await", "yield", "on", "in"],
        storage=["class", "mixin", "extension", "enum", "typedef", "import", "export", "part", "library", "var", "final", "const", "late", "new", "extends", "implements", "with", "abstract", "async", "sync", "required", "factory", "get", "set"],
        modifier=["static", "external", "covariant", "is", "as"], constants=["true", "false", "null", "this", "super"],
        builtin=["int", "double", "num", "bool", "String", "List", "Map", "Set", "Future", "Stream", "void", "dynamic", "Object"],
        extra=[{"match": r"@[A-Za-z_]\w*", "name": "meta.annotation"}])),
    ("cmake", "CMake", "cmake", "source.cmake", ["cmake"], ["CMakeLists.txt"], build(
        "CMake", "source.cmake", line=HASH, strings=("dq",), constants=["ON", "OFF", "TRUE", "FALSE"],
        control=["if", "elseif", "else", "endif", "foreach", "endforeach", "while", "endwhile", "function", "endfunction", "macro", "endmacro", "return", "break", "continue"],
        extra=[{"match": r"\$\{[^}]*\}", "name": "variable.other"}, {"match": r"\b(?:cmake_minimum_required|project|add_executable|add_library|target_link_libraries|target_include_directories|set|find_package|include|option|install)(?=\s*\()", "name": "support.function"}],
        types=False, ops=False)),
    ("markdown", "Markdown", "markdown", "text.html.markdown", ["md", "markdown", "mdx"], [], {
        "name": "Markdown", "scopeName": "text.html.markdown", "patterns": [
            {"match": r"^#{1,6}\s.*$", "name": "markup.heading"},
            {"match": r"^\s*(?:[-*+]|\d+\.)\s", "name": "punctuation.definition.list"},
            {"begin": r"^```.*$", "end": r"^```\s*$", "name": "markup.fenced_code.block"},
            {"match": r"`[^`\n]+`", "name": "markup.inline.raw"},
            {"match": r"\*\*[^*\n]+\*\*|__[^_\n]+__", "name": "markup.bold"},
            {"match": r"(?<![*\w])\*[^*\n]+\*(?!\*)|(?<![_\w])_[^_\n]+_(?!_)", "name": "markup.italic"},
            {"match": r"!?\[[^\]\n]*\]\([^)\n]*\)", "name": "markup.underline.link"},
            {"match": r"^>.*$", "name": "markup.quote"}, {"match": r"^(?:---|\*\*\*|___)\s*$", "name": "meta.separator"}]}),
    ("sql", "SQL", "sql", "source.sql", ["sql"], [], build(
        "SQL", "source.sql", line=["--"], block=CBLOCK, strings=("sq", "dq"), constants=["NULL", "TRUE", "FALSE"],
        control=["(?i:select)", "(?i:from)", "(?i:where)", "(?i:join)", "(?i:left)", "(?i:right)", "(?i:inner)", "(?i:outer)", "(?i:on)", "(?i:group)", "(?i:by)", "(?i:order)", "(?i:having)", "(?i:limit)", "(?i:offset)", "(?i:union)", "(?i:insert)", "(?i:into)", "(?i:values)", "(?i:update)", "(?i:set)", "(?i:delete)", "(?i:and)", "(?i:or)", "(?i:not)", "(?i:in)", "(?i:as)", "(?i:distinct)", "(?i:case)", "(?i:when)", "(?i:then)", "(?i:else)", "(?i:end)"],
        storage=["(?i:create)", "(?i:alter)", "(?i:drop)", "(?i:table)", "(?i:index)", "(?i:view)", "(?i:primary)", "(?i:key)", "(?i:foreign)", "(?i:references)", "(?i:constraint)", "(?i:default)", "(?i:unique)"],
        builtin=["(?i:int)", "(?i:integer)", "(?i:bigint)", "(?i:text)", "(?i:varchar)", "(?i:boolean)", "(?i:real)", "(?i:blob)", "(?i:date)", "(?i:timestamp)"], types=False)),
    ("lua", "Lua", "lua", "source.lua", ["lua"], [], build(
        "Lua", "source.lua", line=["--"], strings=("dq", "sq"),
        control=["if", "then", "elseif", "else", "end", "for", "while", "do", "repeat", "until", "return", "break", "goto", "in"],
        storage=["function", "local"], modifier=["and", "or", "not"], constants=["true", "false", "nil", "self"],
        extra=[{"begin": r"--\[(=*)\[", "end": r"\]\1\]", "name": "comment.block"}])),
    ("ruby", "Ruby", "ruby", "source.ruby", ["rb", "rake", "gemspec"], ["Rakefile", "Gemfile"], build(
        "Ruby", "source.ruby", line=HASH, strings=("dq", "sq"),
        control=["if", "elsif", "else", "unless", "case", "when", "while", "until", "for", "do", "end", "begin", "rescue", "ensure", "raise", "return", "yield", "break", "next", "redo", "retry", "then", "in"],
        storage=["def", "class", "module", "require", "require_relative", "include", "extend", "attr_accessor", "attr_reader", "attr_writer", "alias", "undef"],
        modifier=["and", "or", "not", "public", "private", "protected"], constants=["true", "false", "nil", "self"],
        extra=[{"match": r":[A-Za-z_]\w*[?!]?", "name": "constant.other.symbol"}, {"match": r"@{1,2}[A-Za-z_]\w*", "name": "variable.other.instance"}])),
    ("php", "PHP", "php", "source.php", ["php", "phtml"], [], build(
        "PHP", "source.php", line=SLASH + HASH, block=CBLOCK, strings=("dq", "sq"),
        control=["if", "else", "elseif", "for", "foreach", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "match", "yield"],
        storage=["function", "class", "interface", "trait", "enum", "namespace", "use", "new", "extends", "implements", "const", "var", "echo", "print", "require", "require_once", "include", "include_once"],
        modifier=["public", "private", "protected", "static", "abstract", "final", "readonly"], constants=["true", "false", "null", "TRUE", "FALSE", "NULL", "self", "parent"],
        builtin=["int", "float", "string", "bool", "array", "object", "mixed", "void", "callable", "iterable"],
        extra=[{"match": r"\$[A-Za-z_]\w*", "name": "variable.other"}, {"match": r"<\?php|<\?=|\?>", "name": "punctuation.section.embedded"}])),
    ("swift", "Swift", "swift", "source.swift", ["swift"], [], build(
        "Swift", "source.swift", line=SLASH, block=CBLOCK, strings=("dq3", "dq"),
        control=["if", "else", "guard", "for", "while", "repeat", "switch", "case", "default", "break", "continue", "return", "defer", "do", "try", "catch", "throw", "in", "await"],
        storage=["func", "var", "let", "class", "struct", "enum", "protocol", "extension", "init", "deinit", "import", "typealias", "associatedtype", "actor", "subscript", "operator"],
        modifier=["public", "private", "fileprivate", "internal", "open", "static", "final", "override", "lazy", "weak", "unowned", "mutating", "async", "throws", "rethrows", "inout", "some", "any"],
        constants=["true", "false", "nil", "self", "Self", "super"], builtin=["Int", "Double", "Float", "Bool", "String", "Character", "Array", "Dictionary", "Set", "Optional", "Void", "Any"],
        extra=[{"match": r"@[A-Za-z_]\w*", "name": "meta.attribute"}, {"match": r"#[A-Za-z]+", "name": "meta.preprocessor"}])),
    ("groovy", "Groovy / Gradle", "groovy", "source.groovy", ["gradle", "groovy", "gvy"], [], build(
        "Groovy", "source.groovy", line=SLASH, block=CBLOCK, strings=("dq3", "sq3", "dq", "sq"),
        control=["if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "in"],
        storage=["def", "class", "interface", "enum", "trait", "package", "import", "new", "extends", "implements", "as", "var"],
        modifier=["public", "private", "protected", "static", "final", "abstract", "synchronized"], constants=["true", "false", "null", "this", "super", "it"],
        builtin=["int", "long", "double", "float", "boolean", "String", "List", "Map", "Object", "void"],
        extra=[{"match": r"@[A-Za-z_]\w*", "name": "meta.annotation"}, {"match": r"\b(?:plugins|android|dependencies|repositories|implementation|api|compileOnly|runtimeOnly|testImplementation|classpath|apply|id|task|buildscript|allprojects|subprojects|sourceSets)\b", "name": "support.function.gradle"}])),
    ("properties", "Properties / INI", "properties", "source.properties", ["properties", "ini", "cfg", "conf", "env"], ["local.properties", "gradle.properties", ".editorconfig"], {
        "name": "Properties", "scopeName": "source.properties", "patterns": [
            {"match": r"^\s*[#;!].*$", "name": "comment.line"}, {"match": r"^\s*\[[^\]]+\]", "name": "entity.name.section"},
            {"match": r"^\s*[\w.\-/]+(?=\s*[=:])", "name": "variable.other.key"}, {"match": r"(?<=[=:])\s*.*$", "name": "string.unquoted.value"}]}),
    ("dockerfile", "Dockerfile", "dockerfile", "source.dockerfile", ["dockerfile"], ["Dockerfile", "Containerfile"], build(
        "Dockerfile", "source.dockerfile", line=HASH, strings=("dq", "sq"),
        control=["FROM", "RUN", "CMD", "ENTRYPOINT", "COPY", "ADD", "ENV", "ARG", "EXPOSE", "VOLUME", "WORKDIR", "USER", "LABEL", "HEALTHCHECK", "SHELL", "STOPSIGNAL", "ONBUILD", "AS"],
        extra=[SHELL_VAR], types=False, calls=False, ops=False)),
    ("makefile", "Makefile", "makefile", "source.makefile", ["mk", "mak"], ["Makefile", "makefile", "GNUmakefile"], build(
        "Makefile", "source.makefile", line=HASH, strings=("dq", "sq"),
        control=["ifeq", "ifneq", "ifdef", "ifndef", "else", "endif", "include", "-include", "define", "endef", "export", "override", "vpath"],
        extra=[{"match": r"^[\w./%$()-]+(?=\s*:(?!=))", "name": "entity.name.function.target"}, {"match": r"\$[({][^)}]*[)}]|\$[@<^?*%]", "name": "variable.other"}, {"match": r"^\s*[\w.]+(?=\s*[:+?]?=)", "name": "variable.other.key"}],
        types=False, calls=False)),
    ("protobuf", "Protocol Buffers", "proto", "source.proto", ["proto"], [], build(
        "Protobuf", "source.proto", line=SLASH, block=CBLOCK,
        control=["syntax", "package", "import", "option", "message", "enum", "service", "rpc", "returns", "oneof", "map", "reserved", "extend", "stream"],
        modifier=["repeated", "optional", "required", "public", "weak"], constants=["true", "false"],
        builtin=["double", "float", "int32", "int64", "uint32", "uint64", "sint32", "sint64", "fixed32", "fixed64", "sfixed32", "sfixed64", "bool", "string", "bytes"], calls=False)),
]
