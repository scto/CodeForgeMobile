#!/usr/bin/env python3
"""TODO-Bericht: sucht TODO/FIXME/HACK in Quelltexten. Aufruf: tool.py <projektordner> [all|todo|fixme|hack]"""
import os, re, sys

SKIP_DIRS = {"build", ".git", ".gradle", ".idea", "node_modules", ".kotlin", ".cxx", ".cfm", "__pycache__"}
TEXT_EXTS = {"kt", "kts", "java", "xml", "py", "js", "ts", "rs", "go", "c", "cpp", "h", "md", "sh", "zig", "toml", "yaml", "yml"}
MARKERS = ("TODO", "FIXME", "HACK")


def main():
    project = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.getcwd()
    mode = (sys.argv[2].strip().lower() if len(sys.argv) > 2 and sys.argv[2].strip() else "all")
    wanted = MARKERS if mode == "all" else tuple(m for m in MARKERS if m.lower() == mode)
    if not wanted:
        raise SystemExit(f"Unbekannter Modus '{mode}'. Erlaubt: all, todo, fixme, hack")
    pattern = re.compile(r"\b(" + "|".join(wanted) + r")\b[:\s-]*(.*)")
    hits = 0
    for d, dirs, files in os.walk(project):
        dirs[:] = sorted(x for x in dirs if x not in SKIP_DIRS)
        for f in sorted(files):
            if f.rsplit(".", 1)[-1].lower() not in TEXT_EXTS:
                continue
            path = os.path.join(d, f)
            try:
                with open(path, encoding="utf-8", errors="replace") as fh:
                    for no, line in enumerate(fh, 1):
                        m = pattern.search(line)
                        if m:
                            hits += 1
                            print(f"{os.path.relpath(path, project)}:{no}  [{m.group(1)}] {m.group(2).strip()[:100]}")
            except OSError as e:
                print(f"{path}: {e}", file=sys.stderr)
    print(f"\n{hits} Treffer ({', '.join(wanted)})")


main()
