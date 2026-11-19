#!/usr/bin/env python3
"""VS-Code-Extension -> CodeForge-Plugin (Sideload-ZIP).

Eingabe (relativ zum Projekt oder absolut):
  * eine .vsix, ein entpackter Extension-Ordner (mit package.json) oder eine einzelne Theme-JSON
  * openvsx:<publisher>.<name>[@version] – lädt die Extension von open-vsx.org

Übernommen werden alle deklarativen Teile, die CodeForge versteht:
  Farbschemata (themes), Icon-Themes, TextMate-Grammatiken (grammars + languages), Snippets und Extension-Packs
  (bekannte Extensions werden auf CodeForge-Plugins abgebildet, z. B. Language Server). Nicht übertragbar ist der
  JavaScript-Code einer Extension (Befehle, Ansichten, Debugger …) – das steht im Bericht.

Ausgabe: <Projekt>/cf-plugin-out/<plugin-id>/, <plugin-id>.zip und <plugin-id>.report.md.
Icon-Packs mit SVG brauchen 'rsvg-convert' (pkg install librsvg); PNG/WebP/JPEG werden direkt übernommen.

Auch am Rechner nutzbar: python3 tool-plugins/scripts/vscode_import.py <ausgabeordner> <vsix|ordner|openvsx:…>
"""
import json, os, re, shutil, subprocess, tempfile, urllib.request, zipfile
from common import args

# ---------- Farben ----------

def parse_hex(v):
    if not isinstance(v, str):
        return None
    h = v.strip().lstrip("#")
    if len(h) in (3, 4):
        h = "".join(c * 2 for c in h)
    if len(h) not in (6, 8) or not re.fullmatch(r"[0-9a-fA-F]+", h):
        return None
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))  # Alpha wird verworfen


def to_hex(rgb):
    return "#%02X%02X%02X" % tuple(max(0, min(255, round(c))) for c in rgb)


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


def lum(rgb):
    def ch(c):
        c /= 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def on(rgb):
    return (0, 0, 0) if lum(rgb) > 0.4 else (255, 255, 255)


def first(colors, *keys):
    for k in keys:
        c = parse_hex(colors.get(k))
        if c:
            return c
    return None


def map_colors(vs, dark):
    c = vs.get("colors", {}) or {}
    white, black = (255, 255, 255), (0, 0, 0)
    bg = first(c, "editor.background", "sideBar.background") or ((30, 30, 30) if dark else (255, 255, 255))
    fg = first(c, "editor.foreground", "foreground") or on(bg)
    surface = first(c, "sideBar.background", "editorGroupHeader.tabsBackground") or bg
    primary = first(c, "button.background", "focusBorder", "activityBarBadge.background", "textLink.foreground") or ((100, 150, 255) if dark else (30, 90, 200))
    secondary = first(c, "textLink.foreground", "terminal.ansiBlue", "progressBar.background") or mix(primary, fg, .3)
    tertiary = first(c, "terminal.ansiMagenta", "editorInfo.foreground", "gitDecoration.addedResourceForeground") or mix(primary, (255, 0, 128), .4)
    error = first(c, "errorForeground", "editorError.foreground", "terminal.ansiRed") or (220, 80, 80)
    base = white if dark else black  # in Richtung "Vordergrund" mischen
    r = {
        "primary": primary, "onPrimary": on(primary),
        "primaryContainer": mix(bg, primary, .35), "onPrimaryContainer": mix(fg, primary, .15),
        "secondary": secondary, "onSecondary": on(secondary),
        "secondaryContainer": mix(bg, secondary, .3), "onSecondaryContainer": mix(fg, secondary, .15),
        "tertiary": tertiary, "onTertiary": on(tertiary),
        "tertiaryContainer": mix(bg, tertiary, .3), "onTertiaryContainer": mix(fg, tertiary, .15),
        "error": error, "onError": on(error),
        "errorContainer": mix(bg, error, .3), "onErrorContainer": mix(fg, error, .15),
        "background": bg, "onBackground": fg,
        "surface": surface, "onSurface": fg,
        "surfaceVariant": first(c, "tab.inactiveBackground", "editorWidget.background") or mix(surface, base, .08),
        "onSurfaceVariant": first(c, "descriptionForeground", "sideBar.foreground") or mix(fg, bg, .3),
        "outline": first(c, "panel.border", "contrastBorder", "editorGroup.border") or mix(bg, base, .35),
        "outlineVariant": mix(bg, base, .18),
        "surfaceContainerLowest": mix(bg, black if dark else white, .2),
        "surfaceContainerLow": mix(surface, base, .03),
        "surfaceContainer": mix(surface, base, .06),
        "surfaceContainerHigh": mix(surface, base, .1),
        "surfaceContainerHighest": mix(surface, base, .14),
        "inverseSurface": fg, "inverseOnSurface": bg,
        "inversePrimary": mix(primary, white if not dark else black, .4),
    }
    return {k: to_hex(v) for k, v in r.items()}

# ---------- JSONC ----------

def load_jsonc(path):
    text = open(path, encoding="utf-8-sig").read()
    out, i, n, in_str = [], 0, len(text), False
    while i < n:
        ch = text[i]
        if in_str:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                out.append(text[i + 1]); i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True; out.append(ch)
        elif text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        else:
            out.append(ch)
        i += 1
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", "".join(out)))


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", str(s).lower()).strip("-") or "theme"


def resolve_theme(path, seen=None):
    """Lädt ein Theme samt 'include' (colors/tokenColors werden zusammengeführt)."""
    seen = seen or set()
    data = load_jsonc(path)
    if "include" in data and path not in seen:
        seen.add(path)
        base_path = os.path.join(os.path.dirname(path), data["include"])
        if os.path.isfile(base_path):
            base = resolve_theme(base_path, seen)
            data["colors"] = {**base.get("colors", {}), **data.get("colors", {})}
            data["tokenColors"] = base.get("tokenColors", []) + data.get("tokenColors", [])
    return data

# ---------- Import ----------

def find_root(inp, project, tmp):
    p = inp if os.path.isabs(inp) else os.path.join(project, inp)
    if not os.path.exists(p):
        raise SystemExit(f"Nicht gefunden: {inp}")
    if os.path.isfile(p) and p.lower().endswith((".vsix", ".zip")):
        with zipfile.ZipFile(p) as z:
            for name in z.namelist():  # Zip-Slip verhindern
                if os.path.isabs(name) or ".." in name.split("/"):
                    raise SystemExit("Ungültiges Archiv (unsicherer Pfad)")
            z.extractall(tmp)
        ext = os.path.join(tmp, "extension")
        return ext if os.path.isdir(ext) else tmp
    return p


def convert_icons(manifest_dir, icon_def, out_dir):
    path = os.path.join(manifest_dir, icon_def["path"])
    data = load_jsonc(path)
    base = os.path.dirname(path)
    icons = os.path.join(out_dir, "icons")
    os.makedirs(icons, exist_ok=True)
    cache, missing_svg = {}, [0]
    rsvg = shutil.which("rsvg-convert")

    def image(def_id):
        d = data.get("iconDefinitions", {}).get(def_id)
        if not d or "iconPath" not in d:
            return None
        if def_id in cache:
            return cache[def_id]
        src = os.path.normpath(os.path.join(base, d["iconPath"]))
        result = None
        if os.path.isfile(src):
            ext = src.rsplit(".", 1)[-1].lower()
            name = slug(def_id)
            if ext in ("png", "webp", "jpg", "jpeg"):
                shutil.copy(src, os.path.join(icons, f"{name}.{ext}"))
                result = f"icons/{name}.{ext}"
            elif ext == "svg" and rsvg:
                dst = os.path.join(icons, f"{name}.png")
                r = subprocess.run([rsvg, "-w", "64", "-h", "64", src, "-o", dst], capture_output=True)
                result = f"icons/{name}.png" if r.returncode == 0 else None
            elif ext == "svg":
                missing_svg[0] += 1
        cache[def_id] = result
        return result

    def mapping(d):
        res = {}
        for k, v in (d or {}).items():
            img = image(v)
            if img:
                res[k.lower()] = img
        return res

    theme = {k: v for k, v in {
        "file": image(data["file"]) if data.get("file") else None,
        "folder": image(data["folder"]) if data.get("folder") else None,
        "folderOpen": image(data["folderExpanded"]) if data.get("folderExpanded") else None,
        "fileExtensions": mapping(data.get("fileExtensions")),
        "fileNames": mapping(data.get("fileNames")),
        "folderNames": mapping(data.get("folderNames")),
        "folderNamesOpen": mapping(data.get("folderNamesExpanded")),
    }.items() if v}
    with open(os.path.join(out_dir, "icons.json"), "w", encoding="utf-8") as f:
        json.dump(theme, f, ensure_ascii=False, indent=2)
    if missing_svg[0]:
        print(f"  Hinweis: {missing_svg[0]} SVG-Icons übersprungen – 'pkg install librsvg' installieren und erneut starten.")
    return len(cache)


# ---------- Grammatiken, Snippets, Pakete ----------

# Bekannte VS-Code-Extensions → CodeForge-Plugins (für extensionPack und als Ergänzung zu Sprach-Extensions).
KNOWN = {
    "ms-python.python": ["com.codeforge.plugin.python-pack.bundle"],
    "ms-python.vscode-pylance": ["com.codeforge.plugin.pyright.lsp"],
    "charliermarsh.ruff": ["com.codeforge.plugin.ruff.lsp", "com.codeforge.plugin.ruff-format.formatter"],
    "rust-lang.rust-analyzer": ["com.codeforge.plugin.rust-pack.bundle"],
    "golang.go": ["com.codeforge.plugin.go-pack.bundle"],
    "ms-vscode.cpptools": ["com.codeforge.plugin.cpp-pack.bundle"],
    "llvm-vs-code-extensions.vscode-clangd": ["com.codeforge.plugin.clangd.lsp"],
    "redhat.vscode-yaml": ["com.codeforge.plugin.yaml-language-server.lsp"],
    "redhat.vscode-xml": ["com.codeforge.plugin.lemminx.lsp"],
    "redhat.java": ["com.codeforge.plugin.jdtls.lsp"],
    "fwcd.kotlin": ["com.codeforge.plugin.kotlin-language-server.lsp"],
    "mathiasfrohlich.kotlin": [],
    "tamasfe.even-better-toml": ["com.codeforge.plugin.taplo.lsp", "com.codeforge.plugin.taplo-format.formatter"],
    "esbenp.prettier-vscode": ["com.codeforge.plugin.prettier.formatter"],
    "dart-code.dart-code": ["com.codeforge.plugin.dart.lsp"],
    "dart-code.flutter": ["com.codeforge.plugin.dart.lsp"],
    "mads-hartmann.bash-ide-vscode": ["com.codeforge.plugin.shell-pack.bundle"],
    "foxundermoon.shell-format": ["com.codeforge.plugin.shfmt.formatter"],
    "ms-vscode.vscode-typescript-next": ["com.codeforge.plugin.web-pack.bundle"],
    "vscode.typescript-language-features": ["com.codeforge.plugin.web-pack.bundle"],
    "neocmakelsp.neocmakelsp": ["com.codeforge.plugin.neocmakelsp.lsp"],
    "humao.rest-client": ["com.codeforge.plugin.rest-client.code"],
    "yzhang.markdown-all-in-one": ["com.codeforge.plugin.markdown-preview.code"],
    "mechatroner.rainbow-csv": ["com.codeforge.plugin.data-viewer.code"],
    "alexcvzz.vscode-sqlite": ["com.codeforge.plugin.sqlite-viewer.code"],
    "qwtel.sqlite-viewer": ["com.codeforge.plugin.sqlite-viewer.code"],
    "vscjava.vscode-gradle": ["com.codeforge.plugin.gradle-tasks.code"],
}

# Beiträge, die Code der Extension brauchen und daher nicht übertragen werden (für den Bericht).
CODE_ONLY = ["commands", "menus", "keybindings", "views", "viewsContainers", "viewsWelcome", "debuggers", "breakpoints",
             "taskDefinitions", "problemMatchers", "customEditors", "notebooks", "webviews", "walkthroughs",
             "authentication", "terminal", "submenus", "codeActions", "semanticTokenTypes", "jsonValidation",
             "configuration", "chatParticipants", "languageModelTools"]

LANGUAGE_ID = re.compile(r"[A-Za-z0-9._+-]{1,60}")


def copy_json(src, dst):
    """Liest JSONC (Kommentare, Kommata am Ende) und schreibt reines JSON (die App liest kein JSONC)."""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    data = load_jsonc(src)
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    return data


def convert_grammars(root, contrib, out, report):
    """grammars + languages → CodeForge-Grammatiken (eine Sprache je Eintrag, Endungen ohne Punkt)."""
    langs = {}
    for lang in contrib.get("languages", []) or []:
        lid = lang.get("id")
        if not lid:
            continue
        exts = [e.lstrip(".").lower() for e in lang.get("extensions", []) or [] if isinstance(e, str)]
        names = [n for n in lang.get("filenames", []) or [] if isinstance(n, str)]
        cur = langs.setdefault(lid, [])
        cur += [x for x in exts + names if x and x not in cur]
    result = []
    for g in contrib.get("grammars", []) or []:
        lid, scope, path = g.get("language"), g.get("scopeName"), g.get("path")
        if not path or not scope:
            continue
        if not lid:
            report.append(f"- Grammatik `{scope}` ist eine Injektion (ohne Sprache) – nicht übernommen.")
            continue
        if not LANGUAGE_ID.fullmatch(lid):
            report.append(f"- Grammatik für `{lid}`: ungültige Sprach-ID – übersprungen.")
            continue
        src = os.path.normpath(os.path.join(root, path))
        if not os.path.isfile(src):
            report.append(f"- Grammatik `{path}` fehlt im Paket.")
            continue
        name = slug(scope) + (".tmLanguage.json" if src.lower().endswith(".json") else ".tmLanguage")
        dst = os.path.join(out, "grammars", name)
        if src.lower().endswith(".json"):
            copy_json(src, dst)
        else:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy(src, dst)
        exts = langs.get(lid, [])
        if not exts:
            report.append(f"- Grammatik `{scope}`: Sprache `{lid}` hat keine Dateiendungen – nur über andere Plugins nutzbar.")
        result.append({"languageId": lid, "scopeName": scope, "path": f"grammars/{name}", "extensions": exts})
        report.append(f"- Grammatik `{scope}` für `{lid}` ({', '.join(exts) or 'ohne Endungen'}).")
        if g.get("embeddedLanguages"):
            report.append(f"- `{scope}`: eingebettete Sprachen werden als Teil der Grammatik gefärbt, ohne eigene Regeln.")
    return result


def convert_snippets(root, contrib, out, report):
    result = []
    for i, s in enumerate(contrib.get("snippets", []) or []):
        lid, path = s.get("language"), s.get("path")
        if not lid or not path:
            continue
        src = os.path.normpath(os.path.join(root, path))
        if not os.path.isfile(src):
            report.append(f"- Snippets `{path}` fehlen im Paket.")
            continue
        try:
            data = copy_json(src, os.path.join(out, "snippets", f"{slug(lid)}-{i}.json"))
        except (ValueError, json.JSONDecodeError) as e:
            report.append(f"- Snippets `{path}`: ungültiges JSON ({e}).")
            continue
        # VS Code erlaubt mehrere Sprachen getrennt durch Komma; CodeForge je Eintrag eine.
        for one in [x.strip() for x in lid.split(",") if x.strip()]:
            result.append({"languageId": one, "path": f"snippets/{slug(lid)}-{i}.json"})
        report.append(f"- Snippets für `{lid}`: {len(data)} Einträge.")
    return result


def convert_pack(pkg, report):
    """extensionPack (VS-Code-IDs) und die Extension selbst → bekannte CodeForge-Plugins."""
    ids = []
    own = f"{pkg.get('publisher', '')}.{pkg.get('name', '')}".lower()
    for ext in [own] + [e.lower() for e in pkg.get("extensionPack", []) or []]:
        mapped = KNOWN.get(ext)
        if mapped:
            ids += [m for m in mapped if m not in ids]
            report.append(f"- `{ext}` → {', '.join(f'`{m}`' for m in mapped) or '(nichts nötig)'}")
        elif ext != own:
            report.append(f"- `{ext}`: kein CodeForge-Gegenstück bekannt.")
    return ids


# ---------- Open VSX ----------

def fetch_openvsx(spec, tmp):
    """openvsx:<publisher>.<name>[@version] → Pfad der heruntergeladenen .vsix."""
    m = re.fullmatch(r"([\w-]+)\.([\w.-]+?)(?:@([\w.+-]+))?", spec)
    if not m:
        raise SystemExit("Format: openvsx:<publisher>.<name>[@version], z. B. openvsx:redhat.vscode-yaml")
    ns, name, ver = m.groups()
    api = f"https://open-vsx.org/api/{ns}/{name}/{ver or 'latest'}"
    with urllib.request.urlopen(api, timeout=60) as r:
        meta = json.load(r)
    url = (meta.get("files") or {}).get("download")
    if not url or not url.startswith("https://"):
        raise SystemExit(f"Keine Download-Adresse für {ns}.{name}")
    dst = os.path.join(tmp, f"{ns}.{name}.vsix")
    with urllib.request.urlopen(url, timeout=300) as r, open(dst, "wb") as f:
        shutil.copyfileobj(r, f)
    print(f"Geladen: {ns}.{name} {meta.get('version', '')} ({meta.get('license') or 'Lizenz unbekannt'})")
    return dst


# ---------- Ablauf ----------

def convert(inp, project, tmp):
    """Konvertiert [inp]; liefert (plugin-id, zip-pfad, bericht-pfad)."""
    if inp.startswith("openvsx:"):
        inp = fetch_openvsx(inp[len("openvsx:"):], tmp)
    root = find_root(inp, project, os.path.join(tmp, "x"))
    report = []
    themes, icon_defs, ext_name, publisher, version, display, pkg = [], [], "theme", "imported", "1.0.0", None, {}
    if os.path.isfile(root) and root.endswith(".json"):
        themes = [{"label": os.path.basename(root)[:-5], "uiTheme": None, "path": os.path.basename(root)}]
        manifest_dir = os.path.dirname(root)
        ext_name = slug(os.path.basename(root)[:-5])
        contrib = {}
    else:
        pkg = load_jsonc(os.path.join(root, "package.json"))
        manifest_dir = root
        ext_name = slug(pkg.get("name", "extension"))
        publisher = slug(pkg.get("publisher", "imported"))
        version = pkg.get("version", "1.0.0")
        display = pkg.get("displayName") if not str(pkg.get("displayName", "")).startswith("%") else None
        contrib = pkg.get("contributes", {}) or {}
        themes, icon_defs = contrib.get("themes", []) or [], contrib.get("iconThemes", []) or []
    pid = f"imported.{publisher}.{ext_name}"
    out_root = os.path.join(project, "cf-plugin-out")
    out = os.path.join(out_root, pid)
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    contributes = {}
    if themes:
        os.makedirs(os.path.join(out, "themes"))
        os.makedirs(os.path.join(out, "syntax"))
        ct = []
        for t in themes:
            vs = resolve_theme(os.path.join(manifest_dir, t["path"]))
            ui = t.get("uiTheme") or ("dark" if vs.get("type", "dark") == "dark" else "light")
            dark = ui in ("vs-dark", "hc-black", "dark")
            tid = slug(t["label"])
            syntax = {"name": t["label"], "type": "dark" if dark else "light",
                      "colors": vs.get("colors", {}), "tokenColors": vs.get("tokenColors", [])}
            with open(os.path.join(out, "syntax", f"{tid}.json"), "w", encoding="utf-8") as f:
                json.dump(syntax, f, ensure_ascii=False, indent=2)
            with open(os.path.join(out, "themes", f"{tid}.json"), "w", encoding="utf-8") as f:
                json.dump({"colors": map_colors(vs, dark), "textmateTheme": f"syntax/{tid}.json"}, f, ensure_ascii=False, indent=2)
            ct.append({"id": tid, "label": t["label"], "uiTheme": "dark" if dark else "light", "path": f"themes/{tid}.json"})
        contributes["colorThemes"] = ct
        report.append(f"- {len(ct)} Farbschema(s): Farben auf Material-3-Rollen abgebildet, Syntaxfarben unverändert.")
    if icon_defs:
        its = []
        for d in icon_defs:
            iid = slug(d.get("id") or d.get("label", "icons"))
            sub = os.path.join(out, f"icons-{iid}")
            os.makedirs(sub)
            n = convert_icons(manifest_dir, d, sub)
            its.append({"id": iid, "label": d.get("label", iid), "path": f"icons-{iid}/icons.json"})
            report.append(f"- Icon-Theme `{iid}`: {n} Icon-Definitionen.")
        contributes["iconThemes"] = its
    grammars = convert_grammars(manifest_dir, contrib, out, report)
    if grammars:
        contributes["grammars"] = grammars
    snippets = convert_snippets(manifest_dir, contrib, out, report)
    if snippets:
        contributes["snippets"] = snippets
    pack = convert_pack(pkg, report) if pkg else []
    if pack:
        contributes["extensionPack"] = pack
    skipped = [k for k in CODE_ONLY if contrib.get(k)]
    if pkg.get("main") or pkg.get("browser"):
        report.append("- Die Extension enthält JavaScript-Code (`main`/`browser`). Er läuft in CodeForge nicht; "
                      "Funktionen daraus brauchen ein Code-Plugin (siehe docs/dev/PluginDevelopment.md).")
    if skipped:
        report.append("- Nicht übertragbar (brauchen Code der Extension): " + ", ".join(f"`{k}`" for k in skipped) + ".")
    if not contributes:
        shutil.rmtree(out, ignore_errors=True)
        raise SystemExit("Nichts Übertragbares gefunden (keine Themes, Icons, Grammatiken, Snippets oder bekannten Pakete).\n"
                         + "\n".join(report))
    manifest = {"id": pid, "name": (display or ext_name) + " (VS Code Import)",
                "version": version if re.fullmatch(r"[\w.\-+]+", str(version)) else "1.0.0",
                "apiVersion": 4, "publisher": "imported",
                "description": f"Aus VS Code importiert: {display or ext_name} ({publisher}). Lizenz des Originals beachten.",
                "permissions": [], "contributes": contributes,
                "tags": ["vscode", "imported"] + [slug(c) for c in (pkg.get("categories") or [])][:5]}
    with open(os.path.join(out, "plugin.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    zip_path = os.path.join(out_root, f"{pid}.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for d, _, files in os.walk(out):
            for fn in sorted(files):
                full = os.path.join(d, fn)
                z.write(full, os.path.relpath(full, out).replace(os.sep, "/"))
    report_path = os.path.join(out_root, f"{pid}.report.md")
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(f"# Import: {display or ext_name}\n\nPlugin-ID: `{pid}`, Beiträge: {', '.join(contributes)}\n\n" + "\n".join(report) + "\n")
    return pid, zip_path, report_path, report


def main():
    project, inp = args()
    if not inp:
        raise SystemExit("Bitte Pfad zu .vsix, Extension-Ordner, Theme-JSON oder openvsx:<publisher>.<name> angeben.")
    tmp = tempfile.mkdtemp(prefix="cf-vsc-")
    try:
        pid, zip_path, report_path, report = convert(inp, project, tmp)
        print("\n".join(report))
        print(f"\nFertig: {os.path.relpath(zip_path, project)}\nBericht: {os.path.relpath(report_path, project)}")
        print("Im Plugins-Bildschirm per „Aus Datei installieren“ hinzufügen. Lizenz der Original-Extension beachten.")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
