#!/usr/bin/env python3
"""Erzeugt aus den 15 eingebauten Farbschemata (ThemePresets.kt) je ein deklaratives Theme-Plugin.

ID:        com.codeforge.plugin.<name>.theme   (name = Preset-ID, '_' → '-')
Publisher: codeforge
Themes:    <name>-dark und <name>-light (das Preset gilt in der App für beide Modi)

Aufruf: python3 theme-plugins/generate.py [ausgabeordner]   (Standard: theme-plugins/build)
Schreibt Quellordner nach theme-plugins/src/<id>/ und ZIPs nach <ausgabeordner>/<id>.zip.
Farben: nur primary/secondary/tertiary wie in der App; alle übrigen Rollen kommen vom Material-3-Standardschema.
"""
import hashlib, json, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
# (preset-id, Anzeigename, primary, secondary, tertiary) – synchron zu core/designsystem/ThemePresets.kt
PRESETS = [
    ("monokai", "Monokai", "#A6E22E", "#F92672", "#66D9EF"),
    ("darcula", "Darcula", "#CC7832", "#6897BB", "#9876AA"),
    ("quiet_light", "Quiet Light", "#7A3E9D", "#4B83CD", "#AA3731"),
    ("ayu_dark", "Ayu Dark", "#E6B450", "#39BAE6", "#FF3333"),
    ("solarized_dark", "Solarized Dark", "#268BD2", "#2AA198", "#859900"),
    ("github_light", "GitHub Light", "#0969DA", "#1A7F37", "#8250DF"),
    ("vscode_dark_plus", "VS Code Dark+", "#007ACC", "#4EC9B0", "#CE9178"),
    ("notepad_plus_plus", "Notepad++", "#1E5B99", "#008000", "#A00000"),
    ("eclipse", "Eclipse", "#2C2255", "#F7941E", "#7B58A3"),
    ("cyberpunk_neon", "Cyberpunk Neon", "#00F0FF", "#FF007F", "#FFE600"),
    ("forest", "Forest", "#2E7D32", "#558B2F", "#00695C"),
    ("ocean", "Ocean", "#0277BD", "#00838F", "#1565C0"),
    ("sunset", "Sunset", "#E64A19", "#F9A825", "#C2185B"),
    ("monochrome", "Monochrome", "#424242", "#616161", "#757575"),
    ("violet", "Violet", "#6A1B9A", "#8E24AA", "#5E35B1"),
]


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for pid, label, p, s, t in PRESETS:
        slug = pid.replace("_", "-")
        plugin_id = f"com.codeforge.plugin.{slug}.theme"
        folder = ROOT / "src" / plugin_id
        colors = {"colors": {"primary": p, "secondary": s, "tertiary": t}}
        dump(folder / "themes" / "dark.json", colors)
        dump(folder / "themes" / "light.json", colors)
        dump(folder / "plugin.json", {
            "id": plugin_id,
            "name": f"{label} (Farbschema)",
            "version": "1.0.0",
            "apiVersion": 2,
            "publisher": "codeforge",
            "description": f"Farbschema „{label}“ aus CodeForge, als Dunkel- und Hell-Variante.",
            "permissions": [],
            "contributes": {"colorThemes": [
                {"id": f"{slug}-dark", "label": f"{label} Dark", "uiTheme": "dark", "path": "themes/dark.json"},
                {"id": f"{slug}-light", "label": f"{label} Light", "uiTheme": "light", "path": "themes/light.json"},
            ]},
        })
        target = out / f"{plugin_id}.zip"
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
            for f in sorted(folder.rglob("*")):
                if f.is_file():
                    z.write(f, f.relative_to(folder).as_posix())
        print(f"{target.name}  sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
