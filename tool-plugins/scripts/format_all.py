#!/usr/bin/env python3
"""Formatiert/prüft das ganze Projekt mit den installierten Formatierern. Eingabe: 'check' (Standard) oder 'fix'."""
import os, shutil, subprocess
from common import args, walk, rel

# (Endungen, Programm, Argumente fix, Argumente check, Paket-Hinweis)
TOOLS = [
    ({"kt", "kts"}, "ktlint", ["-F"], [], "pkg install ktlint"),
    ({"sh", "bash"}, "shfmt", ["-w"], ["-d"], "pkg install shfmt"),
    ({"py"}, "ruff", ["format"], ["format", "--check"], "pkg install ruff"),
    ({"go"}, "gofmt", ["-w"], ["-l"], "pkg install golang"),
    ({"rs"}, "rustfmt", ["--edition", "2021"], ["--edition", "2021", "--check"], "pkg install rust"),
    ({"toml"}, "taplo", ["format"], ["format", "--check"], "pkg install taplo"),
    ({"js", "jsx", "ts", "tsx", "json", "css", "scss", "html", "md", "yaml", "yml"}, "prettier", ["--write"], ["--check"], "npm install -g prettier"),
]


def main():
    project, mode = args()
    fix = mode.lower() == "fix"
    failed = 0
    for exts, prog, fix_args, check_args, hint in TOOLS:
        files = list(walk(project, exts=exts))
        if not files:
            continue
        exe = shutil.which(prog)
        if not exe:
            print(f"[übersprungen] {prog}: nicht installiert ({hint}) – {len(files)} Dateien")
            continue
        print(f"[{prog}] {len(files)} Dateien, Modus {'fix' if fix else 'check'}")
        for i in range(0, len(files), 50):
            chunk = files[i:i + 50]
            r = subprocess.run([exe] + (fix_args if fix else check_args) + chunk, cwd=project, capture_output=True, text=True)
            out = (r.stdout + r.stderr).strip()
            if r.returncode != 0 or (prog == "gofmt" and r.stdout.strip()):
                failed += 1
                for line in out.splitlines()[:30]:
                    print("  " + line.replace(project + os.sep, ""))
    print("\nFertig." + (" Es gab Befunde." if failed else " Keine Befunde."))
    if not fix and failed:
        print("Mit Eingabe 'fix' erneut starten, um zu formatieren (vorher committen!).")


main()
