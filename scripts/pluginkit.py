"""Gemeinsames Packen deklarativer CodeForge-Plugins für alle Generatoren.

Genutzt von theme-plugins/, textmate-plugins/, icon-plugins/, content-plugins/, tool-plugins/, project-templates/,
templates/build.py und store-site/publish.py. Jedes Paket bekommt

* `build-info.json` (Herkunft aus dem letzten Commit des Quellordners, siehe build_info.py),
* `signature.json`, wenn `CF_PLUGIN_SIGNING_KEY` auf einen EC-Schlüssel (PEM) zeigt (scripts/plugin-sign.py),
* ein reproduzierbares ZIP: feste Zeitstempel, sortierte Einträge. Unveränderte Quellen ergeben dieselbe SHA-256,
  so meldet der Store nur echte Änderungen.

Code-Plugins (classes.dex) packt Gradle: gradle/code-plugin.gradle.kts.
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_info import build_info  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
FIXED_DATE = (2026, 1, 1, 0, 0, 0)
SKIP_PARTS = {"__pycache__"}
SKIP_SUFFIXES = {".pyc", ".pyo"}


def dump_json(path, obj):
    """Schreibt JSON (UTF-8, eingerückt, mit Zeilenende) und legt Ordner an."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def manifest(pid, name, description, contributes, version="1.0.0", api_version=2, permissions=None, **extra):
    """Standard-Manifest der CodeForge-Pakete (Publisher `codeforge`)."""
    m = {"id": pid, "name": name, "version": version, "apiVersion": api_version, "publisher": "codeforge",
         "description": description, "permissions": list(permissions or []), "contributes": contributes}
    m.update(extra)
    return m


def _files(folder):
    for f in sorted(Path(folder).rglob("*")):
        if f.is_file() and not (SKIP_PARTS & set(f.parts)) and f.suffix not in SKIP_SUFFIXES:
            yield f


def write_zip(target, entries):
    """Reproduzierbares ZIP aus [(name, bytes, mode)], nach Namen sortiert."""
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data, mode in sorted(entries, key=lambda e: e[0]):
            info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (mode & 0o777) << 16
            z.writestr(info, data)
    return hashlib.sha256(target.read_bytes()).hexdigest()


def pack(folder, target, generator, source=None, quiet=False):
    """Packt [folder] nach [target] samt build-info.json (Herkunft von [source], Standard: [folder]) und optionaler
    Signatur. Gibt die SHA-256 zurück und meldet sie, außer [quiet]."""
    folder, target = Path(folder), Path(target)
    with tempfile.TemporaryDirectory() as tmp:
        stage = Path(tmp) / "plugin"
        shutil.copytree(folder, stage, ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.pyo"))
        if not (stage / "build-info.json").is_file():
            (stage / "build-info.json").write_text(build_info(source or folder, generator), encoding="utf-8")
        key = os.environ.get("CF_PLUGIN_SIGNING_KEY")
        if key and Path(key).is_file():
            subprocess.run([sys.executable, str(REPO / "scripts" / "plugin-sign.py"), str(stage), key], check=True)
        entries = [(f.relative_to(stage).as_posix(), f.read_bytes(), f.stat().st_mode) for f in _files(stage)]
        digest = write_zip(target, entries)
    if not quiet:
        try:
            shown = target.resolve().relative_to(REPO)
        except ValueError:
            shown = target
        print(f"{shown}  sha256={digest}")
    return digest


def pack_manifest_only(obj, target, generator, source):
    """Paket nur aus plugin.json (z. B. Bundles)."""
    with tempfile.TemporaryDirectory() as tmp:
        dump_json(Path(tmp) / "plugin.json", obj)
        return pack(tmp, target, generator, source=source)
