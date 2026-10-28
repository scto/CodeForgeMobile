#!/usr/bin/env python3
"""Ergänzt .gitignore um fehlende Einträge. Eingabe: android (Standard), kotlin, node, python, rust, go (mehrere mit Leerzeichen)."""
import os
from common import args, read, write

BLOCKS = {
    "android": ["*.iml", ".gradle/", "/local.properties", "/.idea/", ".DS_Store", "/build/", "/captures/", ".externalNativeBuild/", ".cxx/", "*.apk", "*.aab", "*.keystore", "*.jks", "/.kotlin/", "/.cfm/"],
    "kotlin": [".gradle/", "build/", "out/", "*.class", "*.jar", "/.kotlin/", ".idea/", "*.iml"],
    "node": ["node_modules/", "npm-debug.log*", "dist/", ".env", ".env.local", "coverage/"],
    "python": ["__pycache__/", "*.py[cod]", ".venv/", "venv/", ".env", "dist/", "build/", "*.egg-info/", ".pytest_cache/", ".ruff_cache/"],
    "rust": ["/target/", "**/*.rs.bk", "Cargo.lock"],
    "go": ["/bin/", "*.exe", "*.test", "*.out", "vendor/"],
}


def main():
    project, inp = args()
    kinds = [k for k in (inp.lower().split() or ["android"])]
    unknown = [k for k in kinds if k not in BLOCKS]
    if unknown:
        raise SystemExit(f"Unbekannt: {', '.join(unknown)}. Erlaubt: {', '.join(BLOCKS)}")
    path = os.path.join(project, ".gitignore")
    text = read(path) if os.path.isfile(path) else ""
    have = {l.strip() for l in text.splitlines()}
    out = text if text.endswith("\n") or not text else text + "\n"
    added = []
    for k in kinds:
        new = [e for e in BLOCKS[k] if e not in have]
        if new:
            out += f"\n# {k}\n" + "\n".join(new) + "\n"
            have.update(new)
            added += new
    write(path, out)
    print(f".gitignore: {len(added)} Einträge ergänzt" + (": " + ", ".join(added) if added else " (alles schon vorhanden)"))


main()
