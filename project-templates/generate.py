#!/usr/bin/env python3
"""Packt die Projektvorlagen-Plugins (`contributes.projectTemplates`) als ZIP.

ID: com.codeforge.plugin.<name>.template   Publisher: codeforge   Store-Kategorie: template

Aufruf: python3 project-templates/generate.py [ausgabeordner]   (Standard: project-templates/build)
Quellen liegen fertig unter project-templates/src/<id>/ (plugin.json, icon.png, template/<Ebene>/…).
Vorschaubilder neu zeichnen: python3 project-templates/make_icons.py (Pillow).
"""
import hashlib, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def pack(out, folder):
    target = out / f"{folder.name}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file() and "__pycache__" not in f.parts:
                info = zipfile.ZipInfo(f.relative_to(folder).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (f.stat().st_mode & 0o777) << 16
                z.writestr(info, f.read_bytes())
    print(f"{target.name}  sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for folder in sorted((ROOT / "src").iterdir()):
        if (folder / "plugin.json").is_file():
            pack(out, folder)


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
