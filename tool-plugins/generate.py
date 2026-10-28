#!/usr/bin/env python3
"""Erzeugt Tool-Plugins: Formatierer (`contributes.formatters`), Language Server (`contributes.lspServers`) und
Werkzeuge für das PT-Menü des Dateibaums (`contributes.tools`, nur Sideload, Berechtigung `shell`; Skripte in scripts/).

ID:        com.codeforge.plugin.<name>.<art>   (art = formatter | lsp)     Publisher: codeforge
Benötigte Programme stehen in `contributes.requires`; die App installiert sie nach Bestätigung (Plugins-Bildschirm bzw. PT-Menü).

Aufruf: python3 tool-plugins/generate.py [ausgabeordner]   (Standard: tool-plugins/build)
Schreibt Quellen nach tool-plugins/src/<id>/ und ZIPs nach <ausgabeordner>/<id>.zip sowie je Art ein Sammelpaket (Bundle).
"""
import hashlib, json, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
F = "${file}"

# (name, label, extensions, fileNames, command, install)
FORMATTERS = [
    ("prettier", "Prettier", ["js", "jsx", "mjs", "cjs", "ts", "tsx", "json", "css", "scss", "less", "html", "md", "yaml", "yml", "vue"], [],
     ["prettier", "--stdin-filepath", F], "npm install -g prettier"),
    ("shfmt", "shfmt", ["sh", "bash", "zsh"], [], ["shfmt", "-filename", F], "pkg install shfmt"),
    ("ruff-format", "Ruff Format", ["py", "pyi"], [], ["ruff", "format", "--stdin-filename", F, "-"], "pkg install ruff"),
    ("gofmt", "gofmt", ["go"], [], ["gofmt"], "pkg install golang"),
    ("rustfmt", "rustfmt", ["rs"], [], ["rustfmt", "--edition", "2021"], "pkg install rust"),
    ("taplo-format", "Taplo Format", ["toml"], [], ["taplo", "format", "-"], "pkg install taplo"),
]

# (name, label, languages, command, install) – `languages` = Sprach-IDs des Editors; mehrere Server je Plugin möglich
LSP = [
    ("clangd", "clangd (C/C++)", [("clangd", ["c", "cpp"], ["clangd"])], "pkg install clang"),
    ("rust-analyzer", "rust-analyzer (Rust)", [("rust-analyzer", ["rust"], ["rust-analyzer"])], "pkg install rust-analyzer"),
    ("taplo", "Taplo (TOML)", [("taplo", ["toml"], ["taplo", "lsp", "stdio"])], "pkg install taplo"),
    ("gopls", "gopls (Go)", [("gopls", ["go"], ["gopls"])], "pkg install gopls"),
    ("ruff", "Ruff (Python-Linting)", [("ruff", ["python"], ["ruff", "server"])], "pkg install ruff"),
    ("neocmakelsp", "neocmakelsp (CMake)", [("neocmakelsp", ["cmake"], ["neocmakelsp", "stdio"])], "pkg install neocmakelsp"),
    ("dart", "Dart Analysis Server", [("dart", ["dart"], ["dart", "language-server"])], "pkg install dart"),
    ("bash-language-server", "Bash Language Server", [("bash-language-server", ["shellscript"], ["bash-language-server", "start"])],
     "npm install -g bash-language-server (optional: pkg install shellcheck shfmt)"),
    ("typescript-language-server", "TypeScript/JavaScript Language Server",
     [("typescript-language-server", ["typescript", "javascript"], ["typescript-language-server", "--stdio"])],
     "npm install -g typescript typescript-language-server"),
    ("pyright", "Pyright (Python)", [("pyright", ["python"], ["pyright-langserver", "--stdio"])], "npm install -g pyright"),
    ("web-langservers", "HTML/CSS/JSON Language Servers", [
        ("vscode-html-language-server", ["html"], ["vscode-html-language-server", "--stdio"]),
        ("vscode-css-language-server", ["css"], ["vscode-css-language-server", "--stdio"]),
        ("vscode-json-language-server", ["json"], ["vscode-json-language-server", "--stdio"])],
     "npm install -g vscode-langservers-extracted"),
    ("yaml-language-server", "YAML Language Server", [("yaml-language-server", ["yaml"], ["yaml-language-server", "--stdio"])],
     "npm install -g yaml-language-server"),
]


def _r(manager, name, check=None):
    d = {"manager": manager, "name": name}
    if check:
        d["check"] = check
    return d


NODE = _r("pkg", "nodejs", "node")
# Voraussetzungen je Plugin-Name (Formatierer und Language Server)
REQUIRES = {
    "prettier": [NODE, _r("npm", "prettier")],
    "shfmt": [_r("pkg", "shfmt")],
    "ruff-format": [_r("pkg", "ruff")],
    "gofmt": [_r("pkg", "golang", "gofmt")],
    "rustfmt": [_r("pkg", "rust", "rustfmt")],
    "taplo-format": [_r("pkg", "taplo")],
    "clangd": [_r("pkg", "clang", "clangd")],
    "rust-analyzer": [_r("pkg", "rust-analyzer")],
    "taplo": [_r("pkg", "taplo")],
    "gopls": [_r("pkg", "gopls")],
    "ruff": [_r("pkg", "ruff")],
    "neocmakelsp": [_r("pkg", "neocmakelsp")],
    "dart": [_r("pkg", "dart")],
    "bash-language-server": [NODE, _r("npm", "bash-language-server")],
    "typescript-language-server": [NODE, _r("npm", "typescript", "tsc"), _r("npm", "typescript-language-server")],
    "pyright": [NODE, _r("npm", "pyright", "pyright-langserver")],
    "web-langservers": [NODE, _r("npm", "vscode-langservers-extracted", "vscode-html-language-server")],
    "yaml-language-server": [NODE, _r("npm", "yaml-language-server")],
}

PY = _r("pkg", "python", "python3")
SCRIPT = ["python3", "${pluginDir}/tool.py", "${projectDir}", "${input}"]
MODE_PROMPT = {"label": "Modus: check (nur Bericht) oder apply", "hint": "check", "default": "check"}

# (name, label, beschreibung, tool-id, tool-label, tool-beschreibung, skript, prompt, confirm, requires, timeout)
TOOLS = [
    ("vscode-import", "VS Code Import", "Konvertiert VS-Code-Themes und Icon-Packs (.vsix, Ordner oder Theme-JSON) in ein CodeForge-Plugin (ZIP in cf-plugin-out/).",
     "import", "VS-Code-Theme/Icon-Pack importieren", "Aus .vsix, Extension-Ordner oder Theme-JSON ein Plugin erzeugen", "vscode_import.py",
     {"label": "Pfad zu .vsix / Ordner / Theme-JSON", "hint": "z. B. downloads/theme.vsix"}, None, [PY, _r("pkg", "librsvg", "rsvg-convert")], 300),
    ("androidx-migrate", "AndroidX-Migration", "Ersetzt android.support.* / android.arch.* durch AndroidX (Imports, Layout-XML, Gradle) und setzt useAndroidX/Jetifier.",
     "migrate", "Nach AndroidX migrieren", "Bericht oder Umstellung der Quellen und Gradle-Dateien", "androidx_migrate.py",
     MODE_PROMPT, "Vor dem Umstellen (apply) bitte committen. Mit check gibt es nur einen Bericht.", [PY], 300),
    ("catalog-migrate", "Version-Catalog-Migration", "Verschiebt hartcodierte Abhängigkeiten aus build.gradle(.kts) in gradle/libs.versions.toml.",
     "migrate", "Nach Version Catalog migrieren", "Bericht oder Umstellung der Abhängigkeiten", "catalog_migrate.py",
     MODE_PROMPT, "Vor dem Umstellen (apply) bitte committen. Mit check gibt es nur einen Bericht.", [PY], 120),
    ("unused-resources", "Unbenutzte Ressourcen", "Findet nicht referenzierte Drawables, Layouts, Strings, Farben und mehr; löscht auf Wunsch Dateien.",
     "scan", "Unbenutzte Ressourcen finden", "Bericht oder Löschen unbenutzter Ressourcen-Dateien", "unused_resources.py",
     MODE_PROMPT, "Dynamische Zugriffe (getIdentifier) werden nicht erkannt. Vor apply bitte committen.", [PY], 300),
    ("format-all", "Alles formatieren", "Formatiert oder prüft das ganze Projekt mit den installierten Formatierern (ktlint, shfmt, ruff, gofmt, rustfmt, taplo, prettier).",
     "run", "Projekt formatieren/prüfen", "Modus check (Standard) oder fix", "format_all.py",
     {"label": "Modus: check oder fix", "hint": "check", "default": "check"}, None, [PY], 600),
    ("gitignore", ".gitignore-Generator", "Ergänzt .gitignore um fehlende Einträge für Android, Kotlin, Node, Python, Rust oder Go.",
     "generate", ".gitignore ergänzen", "Typ(en): android kotlin node python rust go", "gitignore.py",
     {"label": "Typ(en)", "hint": "android node", "default": "android"}, None, [PY], 30),
    ("license", "LICENSE-Generator", "Schreibt eine LICENSE-Datei (MIT, BSD-2, BSD-3, ISC, Unlicense). Apache-2.0 und GPL sind nicht enthalten.",
     "generate", "LICENSE schreiben", "Eingabe: <lizenz> <Name>, z. B. mit Max Mustermann", "license.py",
     {"label": "Lizenz und Name", "hint": "mit Max Mustermann", "default": "mit "}, "Eine vorhandene LICENSE-Datei wird überschrieben.", [PY], 30),
    ("project-clean", "Projekt bereinigen", "Löscht Build-Artefakte (build/, .gradle/, .kotlin/, .cxx/, captures/, *.iml) und zeigt die freigegebene Größe.",
     "clean", "Build-Artefakte löschen", "Bericht oder Löschen (check/apply)", "project_clean.py",
     MODE_PROMPT, None, [PY], 120),
]


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def manifest(pid, name, desc, contributes):
    return {"id": pid, "name": name, "version": "1.0.0", "apiVersion": 2, "publisher": "codeforge",
            "description": desc, "permissions": [], "contributes": contributes}


def pack(out, pid, folder):
    target = out / f"{pid}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(folder).as_posix())
    print(f"{target.name}  sha256={hashlib.sha256(target.read_bytes()).hexdigest()}")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    fmt_ids, lsp_ids = [], []
    for name, label, exts, names, cmd, install in FORMATTERS:
        pid = f"com.codeforge.plugin.{name}.formatter"
        fmt_ids.append(pid)
        folder = ROOT / "src" / pid
        item = {"id": name, "label": label, "extensions": exts, "command": cmd, "timeoutSeconds": 20}
        if names:
            item["fileNames"] = names
        dump(folder / "plugin.json", manifest(pid, f"{label} (Formatierer)",
             f"Formatiert {', '.join(exts[:6])}{' …' if len(exts) > 6 else ''} mit {label}. Fehlende Programme installiert die App nach Bestätigung (sonst: {install}).",
             {"formatters": [item], "requires": REQUIRES[name]}))
        pack(out, pid, folder)
    for name, label, servers, install in LSP:
        pid = f"com.codeforge.plugin.{name}.lsp"
        lsp_ids.append(pid)
        folder = ROOT / "src" / pid
        items = [{"id": sid, "label": label, "languages": langs, "command": cmd} for sid, langs, cmd in servers]
        dump(folder / "plugin.json", manifest(pid, f"{label} (Language Server)",
             f"Bindet {label} ein. Fehlende Programme installiert die App nach Bestätigung (sonst: {install}).",
             {"lspServers": items, "requires": REQUIRES[name]}))
        pack(out, pid, folder)
    tool_ids = []
    for name, label, desc, tid, tlabel, tdesc, script, prompt, confirm, req, timeout in TOOLS:
        pid = f"com.codeforge.plugin.{name}.tool"
        tool_ids.append(pid)
        folder = ROOT / "src" / pid
        tool = {"id": tid, "label": tlabel, "description": tdesc, "command": SCRIPT, "timeoutSeconds": timeout}
        if prompt:
            tool["prompt"] = prompt
        if confirm:
            tool["confirm"] = confirm
        m = manifest(pid, f"{label} (Tool)", desc + " Erscheint im PT-Menü des Dateibaums; nur per Sideload installierbar.",
                     {"tools": [tool], "requires": req})
        m["permissions"] = ["shell"]
        dump(folder / "plugin.json", m)
        (folder / "tool.py").write_text((ROOT / "scripts" / script).read_text(encoding="utf-8"), encoding="utf-8")
        (folder / "common.py").write_text((ROOT / "scripts" / "common.py").read_text(encoding="utf-8"), encoding="utf-8")
        pack(out, pid, folder)
    import zipfile as _z
    allzip = out / "codeforge-tool-plugins-alle.zip"
    with _z.ZipFile(allzip, "w", _z.ZIP_DEFLATED) as z:
        for pid in tool_ids:
            z.write(out / f"{pid}.zip", f"{pid}.zip")
    for kind, ids, title in (("formatters", fmt_ids, "Alle Formatierer"), ("lsp-servers", lsp_ids, "Alle Language Server")):
        pid = f"com.codeforge.plugin.all-{kind}.bundle"
        folder = ROOT / "src" / pid
        dump(folder / "plugin.json", manifest(pid, f"{title} (Bundle)", f"{title} von CodeForge als Bundle. Die Programme selbst müssen installiert werden.",
                                               {"extensionPack": ids}))
        pack(out, pid, folder)


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
