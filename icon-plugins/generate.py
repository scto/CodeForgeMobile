#!/usr/bin/env python3
"""Erzeugt deklarative Icon-Pakete (PNG, transparent, 96 px) für CodeForge.

11 Pakete: 3 klassisch hell, 3 klassisch dunkel, 3 modern hell, 2 modern dunkel.
ID: com.codeforge.plugin.<slug>.icons   Publisher: codeforge   Kategorie im Store: icons

Aufruf: python3 icon-plugins/generate.py [ausgabeordner]   (Standard: icon-plugins/build)
Schreibt Quellordner nach <ausgabe>/src/<id>/ und ZIPs nach <ausgabe>/<id>.zip.
Benötigt Pillow und DejaVu Sans Bold. Es werden keine Dateien im Repo erzeugt (build/ ist ignoriert).
"""
import hashlib, json, sys, zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SIZE, SS = 96, 4            # Zielgröße, Supersampling
S = SIZE * SS
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# key -> (Beschriftung, Basisfarbe). Ein Bild je Schlüssel.
KINDS = {
    "kotlin": ("Kt", "#7F52FF"), "java": ("Jv", "#E76F00"), "xml": ("<>", "#2E9E5B"), "json": ("{}", "#C9A400"),
    "markdown": ("Md", "#3B82C4"), "gradle": ("Gr", "#0B8F8F"), "python": ("Py", "#3776AB"), "js": ("JS", "#C9A400"),
    "ts": ("TS", "#2F74C0"), "c": ("C", "#3F6FB5"), "cpp": ("C+", "#5B5FC7"), "rust": ("Rs", "#B7410E"),
    "go": ("Go", "#00A7C4"), "shell": ("$_", "#4C8B4C"), "yaml": ("Ym", "#CB3C3C"), "toml": ("Tm", "#9A5B2F"),
    "html": ("Ht", "#E34F26"), "css": ("#", "#2965F1"), "text": ("Tx", "#6B7280"), "image": ("Im", "#D6409F"),
    "archive": ("Zp", "#8A6D3B"), "props": ("Pr", "#6B7280"), "proto": ("Pb", "#2F9E9E"), "sql": ("Sq", "#C2410C"),
    "android": ("An", "#3DDC84"), "git": ("Gi", "#F05033"), "license": ("Li", "#6B7280"), "readme": ("i", "#3B82C4"),
    "docker": ("Dk", "#2496ED"), "make": ("Mk", "#6B7280"), "cmake": ("Cm", "#2F7FBF"), "npm": ("Np", "#CB3837"),
    "lock": ("Lk", "#6B7280"), "proguard": ("Pg", "#6B7280"),
}
EXT = {
    "kt": "kotlin", "kts": "kotlin", "java": "java", "xml": "xml", "json": "json", "md": "markdown", "gradle": "gradle",
    "py": "python", "js": "js", "mjs": "js", "cjs": "js", "ts": "ts", "tsx": "ts", "jsx": "js", "c": "c", "h": "c",
    "cpp": "cpp", "cc": "cpp", "cxx": "cpp", "hpp": "cpp", "rs": "rust", "go": "go", "sh": "shell", "bash": "shell",
    "yml": "yaml", "yaml": "yaml", "toml": "toml", "html": "html", "htm": "html", "css": "css", "txt": "text",
    "png": "image", "jpg": "image", "jpeg": "image", "webp": "image", "gif": "image", "svg": "image",
    "zip": "archive", "tar": "archive", "gz": "archive", "tar.gz": "archive", "jar": "archive", "apk": "archive",
    "aab": "archive", "properties": "props", "proto": "proto", "sql": "sql", "lock": "lock", "pro": "proguard",
    "gitignore": "git", "gitattributes": "git", "cmake": "cmake",
}
NAMES = {
    "build.gradle": "gradle", "build.gradle.kts": "gradle", "settings.gradle": "gradle", "settings.gradle.kts": "gradle",
    "gradlew": "gradle", "gradle.properties": "gradle", "androidmanifest.xml": "android", "proguard-rules.pro": "proguard",
    "readme.md": "readme", "license": "license", "dockerfile": "docker", "makefile": "make", "cmakelists.txt": "cmake",
    "package.json": "npm", "libs.versions.toml": "gradle", "cargo.toml": "rust", "go.mod": "go",
}
# Ordnername -> (Beschriftung, Farbe)
FOLDERS = {
    "src": ("</>", "#3B82C4"), "app": ("A", "#3DDC84"), "build": ("B", "#8A8F98"), "res": ("R", "#D6409F"),
    "gradle": ("Gr", "#0B8F8F"), "docs": ("Md", "#3B82C4"), "test": ("T", "#2E9E5B"), "tests": ("T", "#2E9E5B"),
    "assets": ("As", "#C2410C"), "libs": ("Lb", "#7F52FF"), ".git": ("Gi", "#F05033"), ".github": ("Gh", "#6B7280"),
    ".idea": ("Id", "#8A8F98"), "node_modules": ("Np", "#CB3837"), "java": ("Jv", "#E76F00"), "kotlin": ("Kt", "#7F52FF"),
    "values": ("Vl", "#D6409F"), "layout": ("Ly", "#D6409F"), "drawable": ("Dr", "#D6409F"), "scripts": ("$_", "#4C8B4C"),
}

# slug, Name, Beschreibung, Stil, Modus, Ordnerfarbe, Seiten-/Kachelfarbe, Kontur, Tönung der Sprachfarben
THEMES = [
    ("paper", "Paper", "Klassisches helles Icon-Paket: warme Ordner, weiße Seiten.", "classic", "light", "#F2B84B", "#FFFFFF", "#9AA5B1", "#F2B84B"),
    ("sky", "Sky", "Klassisches helles Icon-Paket mit blauen Ordnern.", "classic", "light", "#5B9BD5", "#FFFFFF", "#8FA6BF", "#5B9BD5"),
    ("meadow", "Meadow", "Klassisches helles Icon-Paket mit grünen Ordnern.", "classic", "light", "#6AB04C", "#FFFFFF", "#93AA86", "#6AB04C"),
    ("slate", "Slate", "Klassisches dunkles Icon-Paket mit grauen Ordnern.", "classic", "dark", "#8FA3B8", "#363C46", "#8A93A0", "#8FA3B8"),
    ("ember", "Ember", "Klassisches dunkles Icon-Paket mit glutroten Ordnern.", "classic", "dark", "#E0894A", "#3B3430", "#A08C7C", "#E0894A"),
    ("frost", "Frost", "Klassisches dunkles Icon-Paket mit eisblauen Ordnern.", "classic", "dark", "#6FC3DF", "#2F3B44", "#7FA0B0", "#6FC3DF"),
    ("aurora", "Aurora", "Modernes helles Icon-Paket: runde Kacheln, violette Ordner.", "modern", "light", "#7C5CFF", "#FFFFFF", "#E5E7EB", "#7C5CFF"),
    ("coral", "Coral", "Modernes helles Icon-Paket: runde Kacheln, korallenrote Ordner.", "modern", "light", "#FF6B6B", "#FFFFFF", "#E5E7EB", "#FF6B6B"),
    ("lagoon", "Lagoon", "Modernes helles Icon-Paket: runde Kacheln, türkise Ordner.", "modern", "light", "#14B8A6", "#FFFFFF", "#E5E7EB", "#14B8A6"),
    ("neon", "Neon", "Modernes dunkles Icon-Paket: Neon-Konturen auf dunklen Kacheln.", "modern", "dark", "#A78BFA", "#1E2230", "#A78BFA", "#A78BFA"),
    ("obsidian", "Obsidian", "Modernes dunkles Icon-Paket: ruhige Graustufen mit Akzenten.", "modern", "dark", "#94A3B8", "#232833", "#475569", "#94A3B8"),
]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    a, b = rgb(a) if isinstance(a, str) else a, rgb(b) if isinstance(b, str) else b
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def kind_color(base, mode, tint):
    c = mix(base, tint, 0.15)
    return mix(c, (0, 0, 0), 0.18) if mode == "light" else mix(c, (255, 255, 255), 0.38)


def font(size):
    return ImageFont.truetype(FONT, size)


def text(d, xy, label, fill, size):
    d.text(xy, label, fill=fill, font=font(size), anchor="mm")


def finish(img):
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def label_size(label, base):
    return base if len(label) <= 1 else int(base * (0.78 if len(label) == 2 else 0.6))


# ---------- Dateien ----------
def classic_file(theme, label, color):
    _, _, _, _, mode, _, page, outline, _ = theme
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    x0, y0, x1, y1, fold, w = 64, 24, 320, 360, 84, 12
    pts = [(x0, y0), (x1 - fold, y0), (x1, y0 + fold), (x1, y1), (x0, y1)]
    d.polygon(pts, fill=rgb(page)); d.line(pts + [pts[0]], fill=rgb(outline), width=w, joint="curve")
    d.polygon([(x1 - fold, y0), (x1 - fold, y0 + fold), (x1, y0 + fold)], fill=mix(page, outline, 0.35))
    d.line([(x1 - fold, y0), (x1 - fold, y0 + fold), (x1, y0 + fold)], fill=rgb(outline), width=w // 2 + 2)
    if label:
        d.rectangle([x0 + w // 2, y1 - 62, x1 - w // 2, y1 - w // 2], fill=color)
        text(d, ((x0 + x1) // 2, 214), label, color, label_size(label, 118))
    else:
        for i, yy in enumerate((190, 236, 282)):
            d.rounded_rectangle([x0 + 44, yy, x1 - (44 if i < 2 else 100), yy + 16], 8, fill=mix(outline, page, 0.35))
    return finish(img)


def modern_file(theme, label, color):
    _, _, _, _, mode, _, tile, border, _ = theme
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    box = [32, 32, 352, 352]
    if mode == "light":
        d.rounded_rectangle([box[0], box[1] + 10, box[2], box[3] + 10], 84, fill=(0, 0, 0, 28))
        d.rounded_rectangle(box, 84, fill=color if label else rgb("#E5E7EB"))
        fg = (255, 255, 255)
    else:
        d.rounded_rectangle(box, 84, fill=rgb(tile), outline=color if label else rgb(border), width=14)
        fg = color
    if label:
        text(d, (192, 192), label, fg, label_size(label, 150))
    else:
        for i, yy in enumerate((140, 190, 240)):
            d.rounded_rectangle([96, yy, 288 - (0 if i < 2 else 80), yy + 20], 10, fill=(150, 156, 168))
    return finish(img)


# ---------- Ordner ----------
def classic_folder(theme, open_, label, color):
    _, _, _, _, mode, fcol, _, outline, _ = theme
    base = rgb(fcol)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    back = mix(base, (0, 0, 0), 0.22)
    d.polygon([(28, 88), (140, 88), (170, 128), (356, 128), (356, 332), (28, 332)], fill=back)
    if open_:
        d.polygon([(70, 168), (368, 168), (322, 340), (28, 340)], fill=base)
    else:
        d.rectangle([28, 150, 356, 332], fill=base)
    d.line([(28, 332), (356, 332)], fill=mix(base, (0, 0, 0), 0.35), width=6)
    if label:
        ink = mix(color, (0, 0, 0), 0.55)  # dunkle Schrift auf der Ordnerfläche (gut lesbar in hell und dunkel)
        text(d, (196 if not open_ else 190, 252), label, ink, label_size(label, 110))
    return finish(img)


def modern_folder(theme, open_, label, color):
    _, _, _, _, mode, fcol, tile, _, _ = theme
    base = rgb(fcol)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    back = mix(base, (0, 0, 0), 0.25) if mode == "light" else mix(base, (0, 0, 0), 0.45)
    d.rounded_rectangle([28, 70, 360, 330], 56, fill=back)
    d.rounded_rectangle([28, 70, 172, 140], 36, fill=back)
    front = base if mode == "light" else mix(base, (0, 0, 0), 0.15)
    if open_:
        d.polygon([(70, 160), (352, 160), (330, 330), (36, 330)], fill=front)
        d.rounded_rectangle([36, 160, 352, 330], 40, fill=front)
        d.polygon([(30, 330), (70, 150), (366, 150), (326, 330)], fill=front)
    else:
        d.rounded_rectangle([28, 120, 360, 330], 48, fill=front)
    d.line([(60, 128 if not open_ else 158), (330, 128 if not open_ else 158)], fill=mix(front, (255, 255, 255), 0.45), width=8)
    if label:
        fg = (255, 255, 255) if mode == "light" else mix(color, (255, 255, 255), 0.7)
        text(d, (194, 236), label, fg, label_size(label, 104))
    return finish(img)


def build_pack(theme, out_dir):
    slug, name, desc, style, mode, fcol, page, outline, tint = theme
    pid = f"com.codeforge.plugin.{slug}.icons"
    folder = out_dir / "src" / pid
    icons = folder / "icons"
    icons.mkdir(parents=True, exist_ok=True)
    mk_file = classic_file if style == "classic" else modern_file
    mk_folder = classic_folder if style == "classic" else modern_folder

    mk_file(theme, "", rgb("#888888")).save(icons / "file.png", optimize=True)
    mk_folder(theme, False, "", None).save(icons / "folder.png", optimize=True)
    mk_folder(theme, True, "", None).save(icons / "folder-open.png", optimize=True)
    for kind, (label, base) in KINDS.items():
        mk_file(theme, label, kind_color(base, mode, tint)).save(icons / f"{kind}.png", optimize=True)
    fmap, fopen = {}, {}
    for fname, (label, base) in FOLDERS.items():
        key = fname.lstrip(".") or fname
        col = kind_color(base, mode, tint)
        mk_folder(theme, False, label, col).save(icons / f"folder-{key}.png", optimize=True)
        mk_folder(theme, True, label, col).save(icons / f"folder-{key}-open.png", optimize=True)
        fmap[fname] = f"icons/folder-{key}.png"
        fopen[fname] = f"icons/folder-{key}-open.png"

    theme_json = {
        "file": "icons/file.png", "folder": "icons/folder.png", "folderOpen": "icons/folder-open.png",
        "fileExtensions": {e: f"icons/{k}.png" for e, k in EXT.items()},
        "fileNames": {n: f"icons/{k}.png" for n, k in NAMES.items()},
        "folderNames": fmap, "folderNamesOpen": fopen,
    }
    (folder / "icons.json").write_text(json.dumps(theme_json, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    label_de = {"classic": "klassisch", "modern": "modern"}[style]
    (folder / "plugin.json").write_text(json.dumps({
        "id": pid, "name": f"{name} (Icons)", "version": "1.0.0", "apiVersion": 2, "publisher": "codeforge",
        "description": desc, "permissions": [],
        "contributes": {"iconThemes": [{"id": slug, "label": f"{name} ({label_de}, {'hell' if mode == 'light' else 'dunkel'})", "path": "icons.json"}]},
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    for rel in (v for k, v in theme_json.items() if isinstance(v, str)):
        assert (folder / rel).is_file(), rel
    for group in ("fileExtensions", "fileNames", "folderNames", "folderNamesOpen"):
        for rel in theme_json[group].values():
            assert (folder / rel).is_file(), rel
    target = out_dir / f"{pid}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(folder).as_posix())
    print(f"{target.name}  {target.stat().st_size // 1024} KB  sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for theme in THEMES:
        build_pack(theme, out)
    pid = "com.codeforge.plugin.all-icons.bundle"
    folder = out / "src" / pid
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "plugin.json").write_text(json.dumps({
        "id": pid, "name": "Alle Icon-Pakete (Bundle)", "version": "1.0.0", "apiVersion": 2, "publisher": "codeforge",
        "description": "Alle 11 Icon-Pakete von CodeForge als Bundle (3 klassisch hell, 3 klassisch dunkel, 3 modern hell, 2 modern dunkel).",
        "permissions": [],
        "contributes": {"extensionPack": [f"com.codeforge.plugin.{t[0]}.icons" for t in THEMES]},
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    target = out / f"{pid}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(folder / "plugin.json", "plugin.json")
    print(f"{target.name}  sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
