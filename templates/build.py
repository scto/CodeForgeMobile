#!/usr/bin/env python3
"""Baut alle deklarativen Vorlagen zu installierbaren ZIPs (templates/build/<ordner>.zip).

Aufruf:  python3 templates/build.py [ordner ...]
Prüft vorher, dass alle JSON-Dateien gültig sind und alle in plugin.json referenzierten Dateien existieren,
und gibt je ZIP die SHA-256 aus (für den Store-Katalog). Das Code-Plugin baut Gradle:
  ./gradlew :templates:code-plugin:packagePlugin
"""
import hashlib, json, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "build"


def referenced_paths(manifest):
    c = manifest.get("contributes", {})
    for kind in ("colorThemes", "iconThemes", "languagePacks", "grammars", "snippets"):
        for item in c.get(kind, []):
            yield item["path"]


def check(folder):
    manifest = json.loads((folder / "plugin.json").read_text(encoding="utf-8"))
    for f in folder.rglob("*.json"):
        json.loads(f.read_text(encoding="utf-8"))
    for rel in referenced_paths(manifest):
        if ".." in rel or rel.startswith("/") or not (folder / rel).is_file():
            raise SystemExit(f"{folder.name}: Datei fehlt/ungültig: {rel}")
    if "entryPointClass" in manifest:
        raise SystemExit(f"{folder.name}: Code-Plugins baut Gradle, nicht dieses Skript")
    return manifest


def build(folder):
    check(folder)
    OUT.mkdir(exist_ok=True)
    target = OUT / f"{folder.name}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(folder).as_posix())
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    print(f"{target.relative_to(ROOT.parent)}  sha256={digest}")


if __name__ == "__main__":
    names = sys.argv[1:]
    folders = [ROOT / n for n in names] if names else [
        p.parent for p in sorted(ROOT.glob("*/plugin.json")) if "entryPointClass" not in p.read_text(encoding="utf-8")
    ]
    for folder in folders:
        build(folder)
