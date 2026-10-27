#!/usr/bin/env python3
"""Erzeugt TextMate-Editor-Theme-Plugins (je Familie eine Dunkel- und eine Hell-Variante).

ID:        com.codeforge.plugin.<name>.theme      Publisher: codeforge
Je Variante: App-Farben (Material-3-Rollen, aus der Palette abgeleitet) + VS-Code/TextMate-Theme für den Editor.
Der Editor übernimmt das TextMate-Theme, wenn in den Editor-Einstellungen „auto“ gewählt ist und das Farbschema des Plugins aktiv ist.

Die Paletten sind den bekannten Themes nachempfunden (Farbwerte aus dem Gedächtnis, nicht gegen die Originale geprüft).

Aufruf: python3 textmate-plugins/generate.py [ausgabeordner]   (Standard: textmate-plugins/build)
"""
import hashlib, json, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# Schlüssel: bg fg sel cursor comment string keyword number function type variable tag attr op  | UI: primary secondary tertiary error
def v(bg, fg, sel, comment, string, keyword, number, function, type_, variable, tag, attr, op, primary, secondary, tertiary, error):
    return dict(bg=bg, fg=fg, sel=sel, comment=comment, string=string, keyword=keyword, number=number, function=function,
                type=type_, variable=variable, tag=tag, attr=attr, op=op, primary=primary, secondary=secondary,
                tertiary=tertiary, error=error)

FAMILIES = [
    ("dracula", "Dracula",
     v("#282A36", "#F8F8F2", "#44475A", "#6272A4", "#F1FA8C", "#FF79C6", "#BD93F9", "#50FA7B", "#8BE9FD", "#F8F8F2", "#FF79C6", "#50FA7B", "#FF79C6", "#BD93F9", "#FF79C6", "#8BE9FD", "#FF5555"),
     v("#FFFBEB", "#1F1F1F", "#CFCFDE", "#6C664B", "#846E15", "#A3144D", "#644AC9", "#14710A", "#036A96", "#1F1F1F", "#A3144D", "#14710A", "#A3144D", "#644AC9", "#A3144D", "#036A96", "#CB3A2A")),
    ("one", "One",
     v("#282C34", "#ABB2BF", "#3E4451", "#5C6370", "#98C379", "#C678DD", "#D19A66", "#61AFEF", "#E5C07B", "#E06C75", "#E06C75", "#D19A66", "#56B6C2", "#61AFEF", "#C678DD", "#98C379", "#E06C75"),
     v("#FAFAFA", "#383A42", "#E5E5E6", "#A0A1A7", "#50A14F", "#A626A4", "#986801", "#4078F2", "#C18401", "#E45649", "#E45649", "#986801", "#0184BC", "#4078F2", "#A626A4", "#50A14F", "#E45649")),
    ("nord", "Nord",
     v("#2E3440", "#D8DEE9", "#434C5E", "#616E88", "#A3BE8C", "#81A1C1", "#B48EAD", "#88C0D0", "#8FBCBB", "#D8DEE9", "#81A1C1", "#8FBCBB", "#81A1C1", "#88C0D0", "#81A1C1", "#A3BE8C", "#BF616A"),
     v("#ECEFF4", "#2E3440", "#D8DEE9", "#7B88A1", "#5A7D3F", "#5E81AC", "#8F5E88", "#3B7A9C", "#4E8787", "#2E3440", "#5E81AC", "#4E8787", "#5E81AC", "#5E81AC", "#81A1C1", "#6B8F4E", "#BF616A")),
    ("gruvbox", "Gruvbox",
     v("#282828", "#EBDBB2", "#504945", "#928374", "#B8BB26", "#FB4934", "#D3869B", "#8EC07C", "#FABD2F", "#83A598", "#FB4934", "#FABD2F", "#FE8019", "#83A598", "#D3869B", "#B8BB26", "#FB4934"),
     v("#FBF1C7", "#3C3836", "#D5C4A1", "#928374", "#79740E", "#9D0006", "#8F3F71", "#427B58", "#B57614", "#076678", "#9D0006", "#B57614", "#AF3A03", "#076678", "#8F3F71", "#79740E", "#9D0006")),
    ("tokyo-night", "Tokyo Night",
     v("#1A1B26", "#C0CAF5", "#33467C", "#565F89", "#9ECE6A", "#BB9AF7", "#FF9E64", "#7AA2F7", "#2AC3DE", "#C0CAF5", "#F7768E", "#BB9AF7", "#89DDFF", "#7AA2F7", "#BB9AF7", "#9ECE6A", "#F7768E"),
     v("#E1E2E7", "#3760BF", "#B7C1E3", "#848CB5", "#587539", "#9854F1", "#B15C00", "#2E7DE9", "#007197", "#3760BF", "#F52A65", "#9854F1", "#006A83", "#2E7DE9", "#9854F1", "#587539", "#F52A65")),
    ("catppuccin", "Catppuccin",
     v("#1E1E2E", "#CDD6F4", "#45475A", "#6C7086", "#A6E3A1", "#CBA6F7", "#FAB387", "#89B4FA", "#F9E2AF", "#CDD6F4", "#F38BA8", "#F9E2AF", "#89DCEB", "#CBA6F7", "#89B4FA", "#A6E3A1", "#F38BA8"),
     v("#EFF1F5", "#4C4F69", "#CCD0DA", "#9CA0B0", "#40A02B", "#8839EF", "#FE640B", "#1E66F5", "#DF8E1D", "#4C4F69", "#D20F39", "#DF8E1D", "#04A5E5", "#8839EF", "#1E66F5", "#40A02B", "#D20F39")),
    ("material", "Material",
     v("#0F111A", "#A6ACCD", "#1F2233", "#464B5D", "#C3E88D", "#C792EA", "#F78C6C", "#82AAFF", "#FFCB6B", "#EEFFFF", "#F07178", "#FFCB6B", "#89DDFF", "#82AAFF", "#C792EA", "#C3E88D", "#F07178"),
     v("#FAFAFA", "#546E7A", "#CCEAE7", "#AABFC9", "#91B859", "#7C4DFF", "#F76D47", "#6182B8", "#FFB62C", "#272727", "#E53935", "#FFB62C", "#39ADB5", "#6182B8", "#7C4DFF", "#91B859", "#E53935")),
    ("night-owl", "Night Owl",
     v("#011627", "#D6DEEB", "#1D3B53", "#637777", "#ECC48D", "#C792EA", "#F78C6C", "#82AAFF", "#FFCB8B", "#D6DEEB", "#7FDBCA", "#C5E478", "#C792EA", "#82AAFF", "#C792EA", "#C5E478", "#EF5350"),
     v("#FBFBFB", "#403F53", "#E0E0E0", "#989FB1", "#C96765", "#994CC3", "#AA0982", "#4876D6", "#0C969B", "#403F53", "#0C969B", "#4876D6", "#994CC3", "#4876D6", "#994CC3", "#08916A", "#DE3D3B")),
    ("ayu", "Ayu",
     v("#1F2430", "#CCCAC2", "#34455A", "#5C6773", "#D5FF80", "#FFA759", "#DFBFFF", "#FFD173", "#73D0FF", "#CCCAC2", "#5CCFE6", "#FFAD66", "#F29E74", "#FFD173", "#73D0FF", "#D5FF80", "#FF6666"),
     v("#FCFCFC", "#5C6166", "#D1E4F4", "#ABB0B6", "#86B300", "#FA8D3E", "#A37ACC", "#B07A1F", "#399EE6", "#5C6166", "#55B4D4", "#B07A1F", "#ED9366", "#FA8D3E", "#399EE6", "#86B300", "#E65050")),
    ("github", "GitHub",
     v("#0D1117", "#E6EDF3", "#264F78", "#8B949E", "#A5D6FF", "#FF7B72", "#79C0FF", "#D2A8FF", "#FFA657", "#E6EDF3", "#7EE787", "#79C0FF", "#FF7B72", "#58A6FF", "#BC8CFF", "#3FB950", "#F85149"),
     v("#FFFFFF", "#1F2328", "#B6E3FF", "#6E7781", "#0A3069", "#CF222E", "#0550AE", "#8250DF", "#953800", "#1F2328", "#116329", "#0550AE", "#CF222E", "#0969DA", "#8250DF", "#1A7F37", "#CF222E")),
    ("rose-pine", "Rosé Pine",
     v("#191724", "#E0DEF4", "#403D52", "#6E6A86", "#F6C177", "#3E8FB0", "#EBBCBA", "#EBBCBA", "#9CCFD8", "#E0DEF4", "#9CCFD8", "#C4A7E7", "#908CAA", "#C4A7E7", "#EBBCBA", "#9CCFD8", "#EB6F92"),
     v("#FAF4ED", "#575279", "#DFDAD9", "#9893A5", "#B97312", "#286983", "#C0625F", "#C0625F", "#3F7E89", "#575279", "#3F7E89", "#907AA9", "#797593", "#907AA9", "#D7827E", "#56949F", "#B4637A")),
    ("everforest", "Everforest",
     v("#2D353B", "#D3C6AA", "#475258", "#859289", "#A7C080", "#E67E80", "#D699B6", "#83C092", "#DBBC7F", "#D3C6AA", "#E69875", "#DBBC7F", "#E69875", "#A7C080", "#7FBBB3", "#D699B6", "#E67E80"),
     v("#FDF6E3", "#5C6A72", "#E6E2CC", "#939F91", "#6F8200", "#F85552", "#DF69BA", "#35A77C", "#BF8600", "#5C6A72", "#F57D26", "#BF8600", "#F57D26", "#6F8200", "#3A94C5", "#DF69BA", "#F85552")),
    ("kanagawa", "Kanagawa",
     v("#1F1F28", "#DCD7BA", "#2D4F67", "#727169", "#98BB6C", "#957FB8", "#D27E99", "#7E9CD8", "#7AA89F", "#DCD7BA", "#E6C384", "#E6C384", "#C0A36E", "#7E9CD8", "#957FB8", "#98BB6C", "#E46876"),
     v("#F2ECBC", "#545464", "#C9CBD1", "#8A8980", "#6F894E", "#624C83", "#B35B79", "#4D699B", "#597B75", "#545464", "#77713F", "#77713F", "#836F4A", "#4D699B", "#624C83", "#6F894E", "#C84053")),
    ("tomorrow", "Tomorrow",
     v("#1D1F21", "#C5C8C6", "#373B41", "#969896", "#B5BD68", "#B294BB", "#DE935F", "#81A2BE", "#F0C674", "#CC6666", "#CC6666", "#DE935F", "#8ABEB7", "#81A2BE", "#B294BB", "#B5BD68", "#CC6666"),
     v("#FFFFFF", "#4D4D4C", "#D6D6D6", "#8E908C", "#718C00", "#8959A8", "#F5871F", "#4271AE", "#C99E00", "#C82829", "#C82829", "#F5871F", "#3E999F", "#4271AE", "#8959A8", "#718C00", "#C82829")),
    ("solarized", "Solarized",
     v("#002B36", "#839496", "#073642", "#586E75", "#2AA198", "#859900", "#D33682", "#268BD2", "#B58900", "#839496", "#268BD2", "#93A1A1", "#859900", "#268BD2", "#2AA198", "#859900", "#DC322F"),
     v("#FDF6E3", "#657B83", "#EEE8D5", "#93A1A1", "#2AA198", "#859900", "#D33682", "#268BD2", "#B58900", "#657B83", "#268BD2", "#586E75", "#859900", "#268BD2", "#2AA198", "#859900", "#DC322F")),
]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hexs(c):
    return "#%02X%02X%02X" % tuple(max(0, min(255, round(x))) for x in c)


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return hexs([ra[i] + (rb[i] - ra[i]) * t for i in range(3)])


def lum(h):
    def f(x):
        x /= 255
        return x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4
    r, g, b = rgb(h)
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def on(h):
    return "#000000" if lum(h) > 0.4 else "#FFFFFF"


def app_colors(p, dark):
    bg, fg = p["bg"], p["fg"]
    pc = mix(bg, p["primary"], 0.32 if dark else 0.22)
    sc = mix(bg, p["secondary"], 0.32 if dark else 0.22)
    tc = mix(bg, p["tertiary"], 0.32 if dark else 0.22)
    ec = mix(bg, p["error"], 0.32 if dark else 0.22)
    hi = "#FFFFFF" if dark else "#000000"
    return {
        "primary": p["primary"], "onPrimary": on(p["primary"]), "primaryContainer": pc,
        "onPrimaryContainer": mix(p["primary"], hi, 0.55),
        "secondary": p["secondary"], "onSecondary": on(p["secondary"]), "secondaryContainer": sc,
        "onSecondaryContainer": mix(p["secondary"], hi, 0.55),
        "tertiary": p["tertiary"], "onTertiary": on(p["tertiary"]), "tertiaryContainer": tc,
        "onTertiaryContainer": mix(p["tertiary"], hi, 0.55),
        "error": p["error"], "onError": on(p["error"]), "errorContainer": ec, "onErrorContainer": mix(p["error"], hi, 0.55),
        "background": bg, "onBackground": fg, "surface": bg, "onSurface": fg,
        "surfaceVariant": mix(bg, fg, 0.14), "onSurfaceVariant": mix(fg, bg, 0.25),
        "outline": mix(bg, fg, 0.45), "outlineVariant": mix(bg, fg, 0.22),
        "surfaceContainerLowest": mix(bg, "#000000" if dark else "#FFFFFF", 0.25 if dark else 0.6),
        "surfaceContainerLow": mix(bg, fg, 0.03), "surfaceContainer": mix(bg, fg, 0.06),
        "surfaceContainerHigh": mix(bg, fg, 0.09), "surfaceContainerHighest": mix(bg, fg, 0.12),
        "inverseSurface": fg, "inverseOnSurface": bg, "inversePrimary": mix(p["primary"], bg, 0.35),
    }


def textmate(label, p, dark):
    bg, fg = p["bg"], p["fg"]
    def tok(scope, color, style=None):
        s = {"foreground": color}
        if style:
            s["fontStyle"] = style
        return {"scope": scope, "settings": s}
    return {
        "name": label,
        "type": "dark" if dark else "light",
        "colors": {
            "editor.background": bg, "editor.foreground": fg,
            "editor.selectionBackground": p["sel"], "editor.inactiveSelectionBackground": mix(bg, p["sel"], 0.6),
            "editor.lineHighlightBackground": mix(bg, fg, 0.06),
            "editor.findMatchBackground": mix(bg, p["primary"], 0.45), "editor.findMatchHighlightBackground": mix(bg, p["primary"], 0.25),
            "editorCursor.foreground": p["primary"],
            "editorLineNumber.foreground": mix(bg, fg, 0.38), "editorLineNumber.activeForeground": mix(bg, fg, 0.8),
            "editorIndentGuide.background1": mix(bg, fg, 0.12), "editorIndentGuide.activeBackground1": mix(bg, fg, 0.3),
            "editorWhitespace.foreground": mix(bg, fg, 0.18), "editorBracketMatch.background": mix(bg, p["primary"], 0.3),
        },
        "tokenColors": [
            {"settings": {"foreground": fg, "background": bg}},
            tok(["comment", "punctuation.definition.comment"], p["comment"], "italic"),
            tok(["string", "string.quoted", "punctuation.definition.string"], p["string"]),
            tok(["string.regexp", "constant.character.escape"], p["op"]),
            tok(["constant.numeric", "constant.language", "constant.character", "constant.other"], p["number"]),
            tok(["keyword", "keyword.control", "storage", "storage.type", "storage.modifier"], p["keyword"]),
            tok(["keyword.operator", "punctuation.separator.key-value"], p["op"]),
            tok(["entity.name.function", "support.function", "meta.function-call", "variable.function"], p["function"]),
            tok(["entity.name.type", "entity.name.class", "entity.other.inherited-class", "support.class", "support.type"], p["type"]),
            tok(["entity.name.namespace", "entity.name.section", "entity.name.package"], p["type"]),
            tok(["variable", "variable.other"], p["variable"]),
            tok(["variable.parameter", "meta.parameter"], mix(p["variable"], p["attr"], 0.5), "italic"),
            tok(["entity.name.tag", "meta.tag", "punctuation.definition.tag"], p["tag"]),
            tok(["entity.other.attribute-name", "support.type.property-name", "meta.object-literal.key"], p["attr"]),
            tok(["punctuation", "meta.brace"], mix(fg, bg, 0.2)),
            tok(["markup.heading", "entity.name.section.markdown"], p["function"], "bold"),
            tok(["markup.bold"], p["keyword"], "bold"), tok(["markup.italic"], p["keyword"], "italic"),
            tok(["markup.inline.raw", "markup.fenced_code"], p["string"]),
            tok(["markup.inserted", "meta.diff.header.to-file"], p["tertiary"]),
            tok(["markup.deleted", "meta.diff.header.from-file"], p["error"]),
            tok(["invalid", "invalid.illegal"], p["error"]),
        ],
    }


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for slug, label, dark_p, light_p in FAMILIES:
        plugin_id = f"com.codeforge.plugin.{slug}.theme"
        folder = ROOT / "src" / plugin_id
        themes = []
        for mode, p in (("dark", dark_p), ("light", light_p)):
            dump(folder / "themes" / f"{mode}.json", {"colors": app_colors(p, mode == "dark"), "textmateTheme": f"syntax/{mode}.json"})
            dump(folder / "syntax" / f"{mode}.json", textmate(f"{label} {mode.capitalize()}", p, mode == "dark"))
            themes.append({"id": f"{slug}-{mode}", "label": f"{label} {mode.capitalize()}", "uiTheme": mode, "path": f"themes/{mode}.json"})
        dump(folder / "plugin.json", {
            "id": plugin_id, "name": f"{label} (Editor-Theme)", "version": "1.0.0", "apiVersion": 2, "publisher": "codeforge",
            "description": f"{label} als Farbschema mit TextMate-Editor-Theme, Dunkel- und Hell-Variante (Palette nachempfunden).",
            "permissions": [], "contributes": {"colorThemes": themes},
        })
        target = out / f"{plugin_id}.zip"
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
            for f in sorted(folder.rglob("*")):
                if f.is_file():
                    z.write(f, f.relative_to(folder).as_posix())
        print(f"{target.name}  sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
