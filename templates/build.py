#!/usr/bin/env python3
"""Baut alle deklarativen Vorlagen zu installierbaren ZIPs (templates/build/<ordner>.zip).

Aufruf:  python3 templates/build.py [--out ORDNER] [--prefix P] [vorlage-oder-pfad ...]
  --out ORDNER  legt je Plugin ORDNER/<P><name>/<P><name>.zip samt plugin.json ab (z. B. examples/out)
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
    for tool in c.get("agentTools", []):  # Skripte unter ${pluginDir}
        for part in tool["command"]:
            if part.startswith("${pluginDir}/"):
                yield part[len("${pluginDir}/"):]


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


def build(folder, out=None, prefix=""):
    check(folder)
    if out is None:
        OUT.mkdir(exist_ok=True)
        target = OUT / f"{folder.name}.zip"
    else:
        name = prefix + folder.name
        (out / name).mkdir(parents=True, exist_ok=True)
        target = out / name / f"{name}.zip"
        (out / name / "plugin.json").write_bytes((folder / "plugin.json").read_bytes())
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(folder).as_posix())
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    try:
        shown = target.relative_to(ROOT.parent)
    except ValueError:
        shown = target
    print(f"{shown}  sha256={digest}")


if __name__ == "__main__":
    args = sys.argv[1:]
    out, prefix = None, ""
    while args and args[0] in ("--out", "--prefix"):
        if len(args) < 2:
            raise SystemExit(f"{args[0]} braucht einen Wert")
        if args[0] == "--out":
            out = Path(args[1]).resolve()
        else:
            prefix = args[1]
        args = args[2:]
    names = args
    # Name = Vorlage unter templates/, sonst Pfad zu einem beliebigen Plugin-Ordner (z. B. examples/guide/nord-night)
    folders = [(Path(n).resolve() if (Path(n) / "plugin.json").is_file() else ROOT / n) for n in names] if names else [
        p.parent for p in sorted(ROOT.glob("*/plugin.json")) if "entryPointClass" not in p.read_text(encoding="utf-8")
    ]
    for folder in folders:
        build(folder, out, prefix)
