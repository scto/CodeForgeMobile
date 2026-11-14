#!/usr/bin/env python3
"""Erzeugt die Vorschaubilder (icon.png) der Projektvorlagen. Benötigt Pillow; Ergebnis liegt im Repo."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent / "src"
W, H, SS = 320, 200, 3


def canvas():
    img = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def box(d, x, y, w, h, fill, r=14):
    d.rounded_rectangle([x * SS, y * SS, (x + w) * SS, (y + h) * SS], radius=r * SS, fill=fill)


def save(img, pid):
    img.resize((W, H), Image.LANCZOS).save(ROOT / pid / "icon.png", optimize=True)


def compose_clean():
    img, d = canvas()
    box(d, 20, 16, 280, 168, (232, 222, 248, 255), 22)          # Hintergrund
    line = (103, 80, 164, 255)
    for x in (72, 160, 248):                                     # Abhängigkeiten zuerst, Kästen darüber
        d.line([(160 * SS, 64 * SS), (x * SS, 86 * SS)], fill=line, width=3 * SS)
        d.line([(x * SS, 116 * SS), (160 * SS, 138 * SS)], fill=line, width=3 * SS)
    box(d, 110, 30, 100, 34, (103, 80, 164, 255))               # :app
    for i, c in enumerate([(125, 82, 96, 255), (98, 91, 113, 255), (56, 106, 32, 255)]):
        box(d, 36 + i * 88, 86, 72, 30, c)                       # :feature :data :domain-Ebene
    box(d, 80, 138, 160, 30, (33, 0, 93, 255))                   # :core:domain
    save(img, "com.codeforge.plugin.compose-clean.template")


def jvm_cli():
    img, d = canvas()
    box(d, 20, 16, 280, 168, (30, 30, 46, 255), 22)
    box(d, 20, 16, 280, 26, (49, 50, 68, 255), 22)
    for i, c in enumerate([(243, 139, 168, 255), (249, 226, 175, 255), (166, 227, 161, 255)]):
        d.ellipse([(38 + i * 18) * SS, 24 * SS, (48 + i * 18) * SS, 34 * SS], fill=c)
    green, text = (166, 227, 161, 255), (205, 214, 244, 255)
    d.polygon([(40 * SS, 70 * SS), (56 * SS, 80 * SS), (40 * SS, 90 * SS)], fill=green)   # Prompt ›
    box(d, 66, 74, 120, 12, text, 4)
    box(d, 40, 106, 180, 12, (137, 180, 250, 255), 4)
    box(d, 40, 132, 90, 12, text, 4)
    box(d, 136, 130, 10, 18, green, 2)                            # Cursor
    save(img, "com.codeforge.plugin.jvm-cli.template")


if __name__ == "__main__":
    compose_clean()
    jvm_cli()
