#!/usr/bin/env python3
"""Erzeugt die Terminal-Farbschemata des Stores (`contributes.terminalThemes`, Termux-`colors.properties`).

ID:        com.codeforge.plugin.<name>.terminal   (+ Bundle com.codeforge.plugin.all-terminal-themes.bundle)
Publisher: codeforge
Aufruf:    python3 terminal-plugins/generate.py [ausgabeordner]   (Standard: terminal-plugins/build)
Schreibt Quellordner nach terminal-plugins/src/<id>/ und ZIPs nach <ausgabeordner>/<id>.zip.
Die App bringt nur „Termux-Standard“ und „CodeForge“ mit; alle anderen kommen von hier.
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
import pluginkit  # noqa: E402

ROOT = Path(__file__).resolve().parent

# (slug, Name, hell?, Vordergrund, Hintergrund, Cursor, 16 ANSI-Farben: color0…color15)
THEMES = [
    ("dracula", "Dracula", False, "F8F8F2", "282A36", "F8F8F2",
     "21222C FF5555 50FA7B F1FA8C BD93F9 FF79C6 8BE9FD F8F8F2 6272A4 FF6E6E 69FF94 FFFFA5 D6ACFF FF92DF A4FFFF FFFFFF"),
    ("nord", "Nord", False, "D8DEE9", "2E3440", "D8DEE9",
     "3B4252 BF616A A3BE8C EBCB8B 81A1C1 B48EAD 88C0D0 E5E9F0 4C566A BF616A A3BE8C EBCB8B 81A1C1 B48EAD 8FBCBB ECEFF4"),
    ("solarized-dark", "Solarized Dark", False, "839496", "002B36", "93A1A1",
     "073642 DC322F 859900 B58900 268BD2 D33682 2AA198 EEE8D5 002B36 CB4B16 586E75 657B83 839496 6C71C4 93A1A1 FDF6E3"),
    ("solarized-light", "Solarized Light", True, "657B83", "FDF6E3", "586E75",
     "073642 DC322F 859900 B58900 268BD2 D33682 2AA198 EEE8D5 002B36 CB4B16 586E75 657B83 839496 6C71C4 93A1A1 FDF6E3"),
    ("gruvbox-dark", "Gruvbox Dark", False, "EBDBB2", "282828", "EBDBB2",
     "282828 CC241D 98971A D79921 458588 B16286 689D6A A89984 928374 FB4934 B8BB26 FABD2F 83A598 D3869B 8EC07C EBDBB2"),
    ("one-dark", "One Dark", False, "ABB2BF", "282C34", "528BFF",
     "282C34 E06C75 98C379 E5C07B 61AFEF C678DD 56B6C2 ABB2BF 5C6370 E06C75 98C379 E5C07B 61AFEF C678DD 56B6C2 FFFFFF"),
    ("monokai", "Monokai", False, "F8F8F2", "272822", "F8F8F0",
     "272822 F92672 A6E22E F4BF75 66D9EF AE81FF A1EFE4 F8F8F2 75715E F92672 A6E22E F4BF75 66D9EF AE81FF A1EFE4 F9F8F5"),
    ("tokyo-night", "Tokyo Night", False, "C0CAF5", "1A1B26", "C0CAF5",
     "15161E F7768E 9ECE6A E0AF68 7AA2F7 BB9AF7 7DCFFF A9B1D6 414868 F7768E 9ECE6A E0AF68 7AA2F7 BB9AF7 7DCFFF C0CAF5"),
    ("catppuccin-mocha", "Catppuccin Mocha", False, "CDD6F4", "1E1E2E", "F5E0DC",
     "45475A F38BA8 A6E3A1 F9E2AF 89B4FA F5C2E7 94E2D5 BAC2DE 585B70 F38BA8 A6E3A1 F9E2AF 89B4FA F5C2E7 94E2D5 A6ADC8"),
    ("github-light", "GitHub Light", True, "24292F", "FFFFFF", "0969DA",
     "24292F CF222E 116329 4D2D00 0969DA 8250DF 1B7C83 6E7781 57606A A40E26 1A7F37 633C01 218BFF A475F9 3192AA 8C959F"),
]


def properties(name, fg, bg, cursor, ansi):
    lines = [f"# {name} – Terminal-Farbschema für CodeForge (Termux colors.properties)",
             f"foreground=#{fg}", f"background=#{bg}", f"cursor=#{cursor}"]
    lines += [f"color{i}=#{c}" for i, c in enumerate(ansi.split())]
    return "\n".join(lines) + "\n"


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    ids = []
    for slug, name, light, fg, bg, cursor, ansi in THEMES:
        assert len(ansi.split()) == 16, slug
        pid = f"com.codeforge.plugin.{slug}.terminal"
        ids.append(pid)
        folder = ROOT / "src" / pid
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "colors.properties").write_text(properties(name, fg, bg, cursor, ansi), encoding="utf-8")
        pluginkit.dump_json(folder / "plugin.json", {
            "id": pid,
            "name": f"{name} (Terminal)",
            "version": "1.0.0",
            "apiVersion": 2,
            "publisher": "codeforge",
            "description": f"Terminal-Farbschema „{name}“ ({'hell' if light else 'dunkel'}). "
                           "Auswahl unter Einstellungen → Terminal → Farbschema.",
            "permissions": [],
            "contributes": {"terminalThemes": [
                {"id": slug, "label": name, "uiTheme": "light" if light else "dark", "path": "colors.properties"},
            ]},
        })
        pluginkit.pack(folder, out / f"{pid}.zip", "terminal-plugins/generate.py")
    bundle = ROOT / "src" / "com.codeforge.plugin.all-terminal-themes.bundle"
    pluginkit.dump_json(bundle / "plugin.json", {
        "id": bundle.name,
        "name": "Alle Terminal-Farbschemata (Bundle)",
        "version": "1.0.0",
        "apiVersion": 2,
        "publisher": "codeforge",
        "description": f"Installiert alle {len(ids)} Terminal-Farbschemata des Stores.",
        "permissions": [],
        "contributes": {"extensionPack": ids},
    })
    pluginkit.pack(bundle, out / f"{bundle.name}.zip", "terminal-plugins/generate.py")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
