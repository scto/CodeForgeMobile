#!/usr/bin/env python3
"""Erzeugt die Pixel-Sprites von Basti (32x32) und exportiert sie.

Aufruf (aus dem Repo-Wurzelverzeichnis): python3 scripts/basti/gen_sprites.py

Ausgabe:
  docs/assets/basti.gif, docs/assets/basti-puppy.gif       animierte GIFs für die Webseite (8-fach, Nearest-Neighbor)
  docs/assets/basti-small.gif                              kleine Fassung (4-fach) für die Kopfzeile
  core/designsystem/src/main/res/drawable-nodpi/basti_adult_sheet.png, basti_puppy_sheet.png
                                                           Sprite-Sheets für die App: Frames nebeneinander, je 32x32 px

Frame-Reihenfolge im Sheet (muss zu BastiAnimation.kt passen):
  0 ruhig, 1 blinzeln, 2 Rute mittel, 3 Rute hoch, 4 Ohr, 5 Zunge
Zeitplan: siehe TIMELINE (identisch zu BastiAnimation.TIMELINE).
"""
from pathlib import Path
from PIL import Image, ImageDraw

S = 32
ROOT = Path(__file__).resolve().parents[2]
P = {
    'O': (84, 60, 40), 'C': (240, 222, 176), 'c': (252, 244, 222), 's': (219, 194, 140),
    'G': (225, 186, 115), 'g': (196, 152, 80), 'N': (74, 52, 48), 'E': (38, 28, 26), 'W': (255, 255, 255),
    'T': (238, 143, 154), 'H': (36, 38, 48), 'B': (40, 160, 216), 'h': (150, 156, 168),
    # Welpe: etwas hellere Töne
    'p': (243, 226, 184), 'q': (222, 176, 96),
}
FRAMES = ["idle", "blink", "tail_mid", "tail_high", "ear", "tongue"]
# (Frame-Index, Dauer in ms) – gleiche Werte wie BastiAnimation.TIMELINE
TIMELINE = [(0, 900), (1, 120), (0, 600), (2, 160), (3, 160), (2, 160), (3, 160), (2, 160), (0, 500), (4, 250), (0, 600), (5, 550)]


def outline(layer):
    a = layer.split()[3].point(lambda v: 255 if v > 0 else 0)
    px = a.load()
    out = layer.copy()
    o = out.load()
    for y in range(S):
        for x in range(S):
            if px[x, y] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < S and 0 <= ny < S and px[nx, ny]:
                        o[x, y] = P['O'] + (255,)
                        break
    return out


def adult(kind):
    """Erwachsener Basti, sitzend von vorn: helles Fell, schwarz-blaues Geschirr, wedelnde Rute."""
    L = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(L)
    el = lambda b, c: d.ellipse(b, fill=P[c] + (255,))
    rc = lambda b, c: d.rectangle(b, fill=P[c] + (255,))
    pt = lambda xy, c: d.point(xy, fill=P[c] + (255,))
    # Rute (hinter dem Körper), je nach Frame in anderer Höhe
    tail = {'tail_mid': (26, 18, 31, 27), 'tail_high': (26, 14, 31, 23)}.get(kind, (26, 22, 31, 30))
    el(tail, 'C'); pt((tail[2] - 1, tail[1] + 1), 'c')
    # Hinterläufe/Körper/Vorderläufe
    el((6, 23, 12, 30), 'C'); el((19, 23, 25, 30), 'C')
    el((8, 14, 23, 30), 'C'); el((11, 16, 20, 27), 'c')
    rc((10, 24, 13, 30), 'C'); rc((18, 24, 21, 30), 'C'); rc((10, 30, 13, 30), 's'); rc((18, 30, 21, 30), 's')
    # Geschirr: Halsriemen, Brustplatte mit blauem Streifen, Bauchgurt
    d.line((9, 14, 22, 14), fill=P['H'] + (255,)); d.line((10, 15, 21, 15), fill=P['H'] + (255,))
    for x in (13, 14, 17, 18): pt((x, 14), 'B')
    rc((13, 17, 18, 20), 'H'); d.line((13, 17, 18, 17), fill=P['B'] + (255,)); d.line((14, 19, 17, 19), fill=P['h'] + (255,))
    d.line((15, 21, 15, 24), fill=P['H'] + (255,)); d.line((16, 21, 16, 24), fill=P['H'] + (255,))
    # Ohren, Kopf
    e = 1 if kind == 'ear' else 0
    el((3 - e, 3, 9, 14), 'G'); el((22, 3, 28 + e, 14), 'G')
    el((7, 1, 24, 15), 'C'); el((10, 2, 21, 7), 'c'); el((11, 8, 20, 15), 'c')
    # Nase, Augen, Mund
    rc((14, 9, 17, 10), 'N'); rc((15, 11, 16, 11), 'N')
    for ex in (10, 19):
        if kind == 'blink':
            d.line((ex, 7, ex + 2, 7), fill=P['E'] + (255,))
        else:
            rc((ex, 6, ex + 1, 8), 'E'); pt((ex + 1, 6), 'W')
    d.line((13, 13, 15, 14), fill=P['O'] + (255,)); d.line((16, 14, 18, 13), fill=P['O'] + (255,))
    if kind == 'tongue': rc((15, 15, 16, 16), 'T')
    return outline(L)


def puppy(kind):
    """Welpe (Foto 2019): Kopf-Büste mit Zunge, schwarzes Geschirr."""
    L = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(L)
    el = lambda b, c: d.ellipse(b, fill=P[c] + (255,))
    rc = lambda b, c: d.rectangle(b, fill=P[c] + (255,))
    bob = 1 if kind == 'tail_high' else 0
    y = bob
    rc((10, 25 + y, 13, 30), 'C'); rc((18, 25 + y, 21, 30), 'C'); rc((10, 30, 13, 30), 's'); rc((18, 30, 21, 30), 's')
    el((8, 18 + y, 23, 29), 'C')
    for i in range(5):
        for xx in (10 + i, 11 + i): d.point((xx, 19 + y + i), fill=P['H'] + (255,))
        for xx in (21 - i, 20 - i): d.point((xx, 19 + y + i), fill=P['H'] + (255,))
    for i in range(4):
        d.point((11 + i, 20 + y + i), fill=P['h'] + (255,)); d.point((20 - i, 20 + y + i), fill=P['h'] + (255,))
    rc((14, 24 + y, 17, 26 + y), 'H')
    e = 1 if kind == 'ear' else 0
    el((2 - e, 5 + y, 9, 18 + y), 'q'); el((22, 5 + y, 29 + e, 18 + y), 'q')
    el((6, 2 + y, 25, 21 + y), 'p'); el((9, 3 + y, 22, 9 + y), 'c'); el((11, 11 + y, 20, 19 + y), 'c')
    rc((14, 12 + y, 17, 13 + y), 'E'); rc((15, 14 + y, 16, 14 + y), 'E')
    for ex in (10, 19):
        if kind == 'blink': d.line((ex, 10 + y, ex + 2, 10 + y), fill=P['E'] + (255,))
        else:
            rc((ex, 9 + y, ex + 1, 11 + y), 'E'); d.point((ex + 1, 9 + y), fill=P['W'] + (255,))
    d.line((13, 16 + y, 15, 17 + y), fill=P['O'] + (255,)); d.line((16, 17 + y, 18, 16 + y), fill=P['O'] + (255,))
    if kind != 'tongue_in': rc((15, 18 + y, 16, 19 + y), 'T')
    return outline(L)


def sheet(fn):
    im = Image.new('RGBA', (S * len(FRAMES), S), (0, 0, 0, 0))
    for i, k in enumerate(FRAMES):
        im.paste(fn(k), (i * S, 0))
    return im


def gif(fn, scale, path):
    """Transparentes GIF (1-Bit-Alpha reicht: Nearest-Neighbor, keine weichen Kanten)."""
    frames = {k: fn(k) for k in FRAMES}
    out = []
    for idx, _ in TIMELINE:
        big = frames[FRAMES[idx]].resize((S * scale, S * scale), Image.NEAREST)
        pal = big.convert('RGB').quantize(colors=31, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        pal.paste(31, mask=big.split()[3].point(lambda v: 255 if v == 0 else 0))
        out.append(pal)
    out[0].save(path, save_all=True, append_images=out[1:], duration=[d for _, d in TIMELINE], loop=0,
                disposal=2, transparency=31, optimize=False)


if __name__ == '__main__':
    assets = ROOT / 'docs/assets'
    res = ROOT / 'core/designsystem/src/main/res/drawable-nodpi'
    assets.mkdir(parents=True, exist_ok=True)
    res.mkdir(parents=True, exist_ok=True)
    gif(adult, 8, assets / 'basti.gif')
    gif(puppy, 8, assets / 'basti-puppy.gif')
    gif(adult, 4, assets / 'basti-small.gif')
    sheet(adult).save(res / 'basti_adult_sheet.png', optimize=True)
    sheet(puppy).save(res / 'basti_puppy_sheet.png', optimize=True)
    # Vorschau (nicht eingecheckt)
    prev = Image.new('RGB', (S * 12 * len(FRAMES) + 12, S * 12 * 2 + 36), (253, 251, 255))
    for r, fn in enumerate((adult, puppy)):
        for i, k in enumerate(FRAMES):
            b = fn(k).resize((S * 12, S * 12), Image.NEAREST)
            prev.paste(b, (6 + i * S * 12, 12 + r * (S * 12 + 12)), b)
    prev.save('/tmp/basti/out/sheet-preview.png')
    print('ok')
