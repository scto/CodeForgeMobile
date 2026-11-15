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


def phone(d, x=96, y=12, w=128, h=176):
    box(d, x, y, w, h, (28, 27, 31, 255), 20)                     # Gerät
    box(d, x + 6, y + 6, w - 12, h - 12, (254, 247, 255, 255), 15)  # Bildschirm


def compose_drawer():
    img, d = canvas()
    box(d, 20, 16, 280, 168, (232, 222, 248, 255), 22)
    phone(d)
    box(d, 102, 18, 116, 22, (103, 80, 164, 255), 12)             # obere Leiste
    for i in range(3):                                            # Inhalt (Karten)
        box(d, 150, 50 + i * 40, 62, 30, (232, 222, 248, 255), 8)
    box(d, 102, 18, 64, 164, (243, 237, 247, 255), 14)            # geöffnetes Menü
    box(d, 110, 52, 48, 16, (232, 222, 248, 255), 8)              # ausgewählter Eintrag
    for i, y in enumerate((52, 78, 104)):
        d.ellipse([114 * SS, (y + 4) * SS, 122 * SS, (y + 12) * SS], fill=(103, 80, 164, 255))
        box(d, 126, y + 5, 26 if i else 24, 6, (73, 69, 79, 255), 3)
    save(img, "com.codeforge.plugin.compose-drawer.template")


def compose_bottomnav():
    img, d = canvas()
    box(d, 20, 16, 280, 168, (232, 222, 248, 255), 22)
    phone(d)
    box(d, 102, 18, 116, 22, (103, 80, 164, 255), 12)             # obere Leiste
    for i in range(3):                                            # Liste mit Haken
        y = 50 + i * 30
        d.rounded_rectangle([110 * SS, y * SS, 122 * SS, (y + 12) * SS], radius=3 * SS,
                            fill=(103, 80, 164, 255) if i == 0 else None, outline=(103, 80, 164, 255), width=2 * SS)
        box(d, 128, y + 3, 80 - i * 14, 6, (73, 69, 79, 255), 3)
    box(d, 102, 146, 116, 36, (243, 237, 247, 255), 14)           # untere Leiste
    for i, x in enumerate((122, 160, 198)):
        if i == 1:
            box(d, x - 14, 152, 28, 14, (232, 222, 248, 255), 7)  # aktives Ziel
        d.ellipse([(x - 5) * SS, 154 * SS, (x + 5) * SS, 164 * SS], fill=(103, 80, 164, 255))
        box(d, x - 10, 170, 20, 4, (73, 69, 79, 255), 2)
    d.ellipse([166 * SS, 148 * SS, 176 * SS, 158 * SS], fill=(179, 38, 30, 255))  # Abzeichen
    save(img, "com.codeforge.plugin.compose-bottomnav.template")


def compose_notes():
    img, d = canvas()
    box(d, 20, 16, 280, 168, (255, 236, 229, 255), 22)
    phone(d)
    # Verlaufs-Kopf (Violett → Pink, angenähert in Streifen)
    for i in range(116):
        t = i / 115
        c = (int(127 + (228 - 127) * t), int(90 + (90 - 90) * t), int(240 + (201 - 240) * t), 255)
        d.line([((102 + i) * SS, 18 * SS), ((102 + i) * SS, 52 * SS)], fill=c, width=SS)
    box(d, 110, 40, 100, 10, (255, 255, 255, 200), 5)              # Suchfeld
    tints = [(232, 221, 255, 255), (200, 242, 218, 255), (205, 229, 255, 255), (255, 241, 184, 255)]
    heights = [44, 30, 30, 44]
    for i, (c, h) in enumerate(zip(tints, heights)):
        x = 108 + (i % 2) * 54
        y = 60 + (0 if i < 2 else (heights[i - 2] + 6))
        box(d, x, y, 50, h, c, 8)
    box(d, 168, 156, 44, 18, (127, 90, 240, 255), 9)               # Knopf „Neue Notiz“
    save(img, "com.codeforge.plugin.compose-notes.template")


def compose_podcast():
    img, d = canvas()
    box(d, 20, 16, 280, 168, (255, 232, 214, 255), 22)
    phone(d)
    for i in range(116):                                          # Verlauf Orange → Gelb
        t = i / 115
        c = (255, int(107 + (179 - 107) * t), int(107 + (71 - 107) * t), 255)
        d.line([((102 + i) * SS, 18 * SS), ((102 + i) * SS, 96 * SS)], fill=c, width=SS)
    box(d, 130, 30, 60, 60, (255, 255, 255, 90), 12)               # Cover
    d.polygon([(152 * SS, 48 * SS), (152 * SS, 72 * SS), (172 * SS, 60 * SS)], fill=(255, 255, 255, 255))  # Play
    box(d, 112, 106, 96, 6, (73, 69, 79, 255), 3)                  # Fortschritt
    box(d, 112, 106, 52, 6, (255, 107, 107, 255), 3)
    for x in (126, 194):
        d.ellipse([(x - 7) * SS, 122 * SS, (x + 7) * SS, 136 * SS], fill=(73, 69, 79, 255))
    d.ellipse([146 * SS, 116 * SS, 174 * SS, 144 * SS], fill=(255, 107, 107, 255))
    box(d, 102, 154, 116, 28, (243, 237, 247, 255), 14)            # Mini-Player/Leiste
    save(img, "com.codeforge.plugin.compose-podcast.template")


if __name__ == "__main__":
    compose_clean()
    jvm_cli()
    compose_drawer()
    compose_bottomnav()
    compose_notes()
    compose_podcast()
