#!/usr/bin/env python3
"""Findet Regex-Literale, die auf der JVM laufen, auf Android aber abstürzen.

Android kompiliert `java.util.regex.Pattern` mit ICU. ICU verlangt, dass `{` und `}` außerhalb von
Quantifiern (`{3}`, `{2,5}`) escapt sind; die JVM akzeptiert z. B. `\\$\\{[^}]*}` stillschweigend.
JVM-Unit-Tests bemerken das also nicht, die App wirft `PatternSyntaxException`.

Geprüft werden `Regex("…")`, `Regex(\"\"\"…\"\"\")`, `"…".toRegex()` und `Pattern.compile(…)` in `src/main`.
Kotlin-Templates (`${…}`, `$name`) werden vorher entfernt. Aufruf: `python3 scripts/check_android_regex.py`
(Exit-Code 1 bei Funden).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CALL = re.compile(
    r'(?:Regex\(\s*|Pattern\.compile\(\s*)(?:"""(?P<raw>.*?)"""|"(?P<str>(?:[^"\\]|\\.)*)")'
    r'|(?:"""(?P<raw2>.*?)"""|"(?P<str2>(?:[^"\\]|\\.)*)")\s*\.toRegex\(',
    re.S,
)
KOTLIN_TEMPLATE = re.compile(r"(?<!\\)\$\{(?:[^{}]|\{[^{}]*\})*\}|(?<!\\)\$[A-Za-z_]\w*")


def regex_text(match: re.Match) -> str:
    raw = match.group("raw") if match.group("raw") is not None else match.group("raw2")
    if raw is not None:
        return raw
    s = match.group("str") if match.group("str") is not None else match.group("str2")
    # In normalen Strings ist "\\{" im Regex ein "\{"; "\$" ist nur das Kotlin-Escape für "$".
    return s.replace("\\$", "$").replace("\\\\", "\\")


def bad_brace(pattern: str) -> bool:
    t = KOTLIN_TEMPLATE.sub("", pattern)
    t = re.sub(r"\\.", "", t)  # escapte Zeichen
    t = re.sub(r"\[\^?\]?(?:\\.|[^\]])*\]", "", t)  # Zeichenklassen: dort ist } erlaubt
    t = re.sub(r"\{\d+(,\d*)?\}", "", t)  # Quantifier
    return "{" in t or "}" in t


def main() -> int:
    found = []
    for path in sorted(ROOT.glob("**/src/main/**/*.kt")):
        if "build" in path.relative_to(ROOT).parts:
            continue
        source = path.read_text(encoding="utf-8", errors="ignore")
        for match in CALL.finditer(source):
            pattern = regex_text(match)
            if bad_brace(pattern):
                line = source.count("\n", 0, match.start()) + 1
                found.append(f"{path.relative_to(ROOT)}:{line}: {pattern[:100]}")
    for entry in found:
        print(entry)
    if found:
        print(f"\n{len(found)} Regex mit unescapter geschweifter Klammer – auf Android PatternSyntaxException.")
        return 1
    print("OK: keine Android-inkompatiblen Klammern in Regex-Literalen.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
