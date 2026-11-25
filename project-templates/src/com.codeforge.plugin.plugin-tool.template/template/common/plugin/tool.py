#!/usr/bin/env python3
"""Zählt Dateien je Endung unter <projekt>/<unterordner> (ohne build/, .git/, .gradle/).

Aufruf durch die App: python3 tool.py <projectDir> <input>. Ausgabe (stdout) erscheint im Werkzeug-Dialog,
Fehler (stderr, Exit-Code ≠ 0) als Fehlermeldung.
"""
import collections
import os
import sys

SKIP = {"build", ".git", ".gradle"}


def main(project, sub=""):
    root = os.path.join(project, sub) if sub else project
    if not os.path.isdir(root):
        sys.exit(f"Ordner nicht gefunden: {root}")
    counts = collections.Counter()
    for folder, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP]
        for name in files:
            counts[os.path.splitext(name)[1].lstrip(".") or "(ohne)"] += 1
    for ext, n in counts.most_common(20):
        print(f"{n:6d}  {ext}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2].strip() if len(sys.argv) > 2 else "")
