#!/usr/bin/env python3
"""Packt die Projektvorlagen-Plugins (`contributes.projectTemplates`) als ZIP.

ID: com.codeforge.plugin.<name>.template   Publisher: codeforge   Store-Kategorie: template

Aufruf: python3 project-templates/generate.py [ausgabeordner]   (Standard: project-templates/build)
Quellen liegen fertig unter project-templates/src/<id>/ (plugin.json, icon.png, template/<Ebene>/…).
Vorschaubilder neu zeichnen: python3 project-templates/make_icons.py (Pillow).
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / "scripts"))
import pluginkit  # noqa: E402


def pack(out, folder):
    pluginkit.pack(folder, out / f"{folder.name}.zip", "project-templates/generate.py")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for folder in sorted((ROOT / "src").iterdir()):
        if (folder / "plugin.json").is_file():
            pack(out, folder)


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
