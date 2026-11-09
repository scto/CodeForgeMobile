"""Gemeinsame Helfer der CodeForge-Tool-Skripte (wird beim Bauen an jedes Plugin als common.py angehängt)."""
import os, sys

SKIP_DIRS = {"build", ".git", ".gradle", ".idea", "node_modules", ".kotlin", ".cxx", ".cfm", "__pycache__"}


def args():
    """(projektordner, eingabe) aus argv."""
    project = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.getcwd()
    return project, (sys.argv[2].strip() if len(sys.argv) > 2 else "")


def walk(root, exts=None, names=None):
    """Alle Dateien unter root ohne Build-/Meta-Ordner; optional gefiltert nach Endung (ohne Punkt) oder Dateiname."""
    for d, dirs, files in os.walk(root):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for f in files:
            ext = f.rsplit(".", 1)[-1].lower() if "." in f else ""
            if (exts is None and names is None) or (exts and ext in exts) or (names and f in names):
                yield os.path.join(d, f)


def read(path):
    with open(path, "r", encoding="utf-8", errors="surrogateescape") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
        f.write(text)


def rel(path, root):
    return os.path.relpath(path, root)


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024
