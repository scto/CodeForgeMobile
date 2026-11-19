"""Herkunftsangabe (build-info.json) für Plugin-Pakete.

Stabil je Quellstand: Commit und Datum stammen aus dem letzten Commit, der den Quellordner geändert hat – ein
erneutes Packen ohne Änderungen ergibt dasselbe ZIP (gleiche SHA-256 im Store). Format: siehe
core/domain …/extension/PluginBuildInfo.kt.
"""
import json, subprocess
from pathlib import Path

API_VERSION = 4


def _git(*args, cwd):
    try:
        return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        return ""


def build_info(source, builder):
    """JSON-Text für [source] (Ordner oder Datei, aus dem das Plugin entsteht)."""
    src = Path(source).resolve()
    cwd = src if src.is_dir() else src.parent
    line = _git("log", "-1", "--format=%h|%cI", "--", str(src), cwd=cwd)
    commit, _, date = line.partition("|")
    dirty = bool(_git("status", "--porcelain", "--", str(src), cwd=cwd))
    info = {"commit": commit or "unknown", "dirty": dirty, "builtAt": date or "", "builder": builder, "apiVersion": API_VERSION}
    return json.dumps(info, indent=2) + "\n"
