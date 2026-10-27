#!/usr/bin/env python3
"""Erzeugt deklarative Tool-Plugins: Formatierer (`contributes.formatters`) und Language Server (`contributes.lspServers`).

ID:        com.codeforge.plugin.<name>.<art>   (art = formatter | lsp)     Publisher: codeforge
Die Plugins installieren KEINE Programme: das Programm muss vorab im Terminal installiert werden (siehe `install` in der Beschreibung).

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
             f"Formatiert {', '.join(exts[:6])}{' …' if len(exts) > 6 else ''} mit {label}. Voraussetzung (Terminal): {install}",
             {"formatters": [item]}))
        pack(out, pid, folder)
    for name, label, servers, install in LSP:
        pid = f"com.codeforge.plugin.{name}.lsp"
        lsp_ids.append(pid)
        folder = ROOT / "src" / pid
        items = [{"id": sid, "label": label, "languages": langs, "command": cmd} for sid, langs, cmd in servers]
        dump(folder / "plugin.json", manifest(pid, f"{label} (Language Server)",
             f"Bindet {label} ein. Voraussetzung (Terminal): {install}", {"lspServers": items}))
        pack(out, pid, folder)
    for kind, ids, title in (("formatters", fmt_ids, "Alle Formatierer"), ("lsp-servers", lsp_ids, "Alle Language Server")):
        pid = f"com.codeforge.plugin.all-{kind}.bundle"
        folder = ROOT / "src" / pid
        dump(folder / "plugin.json", manifest(pid, f"{title} (Bundle)", f"{title} von CodeForge als Bundle. Die Programme selbst müssen installiert werden.",
                                               {"extensionPack": ids}))
        pack(out, pid, folder)


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
