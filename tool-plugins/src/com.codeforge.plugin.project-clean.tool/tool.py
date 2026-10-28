#!/usr/bin/env python3
"""Löscht Build-Artefakte (build/, .gradle/, .kotlin/, .cxx/, captures/, *.iml). Eingabe: 'apply' löscht, sonst nur Bericht."""
import os, shutil
from common import args, human

DIRS = {"build", ".gradle", ".kotlin", ".cxx", ".externalNativeBuild", "captures"}


def size(path):
    total = 0
    for d, _, files in os.walk(path):
        for f in files:
            try:
                total += os.lstat(os.path.join(d, f)).st_size
            except OSError:
                pass
    return total


def main():
    project, mode = args()
    apply = mode.lower() == "apply"
    targets = []
    for d, dirs, files in os.walk(project):
        keep = []
        for x in dirs:
            p = os.path.join(d, x)
            if x in {".git", "node_modules", ".cfm"} or os.path.islink(p):
                continue
            if x in DIRS and not (x == "captures" and os.path.exists(os.path.join(p, ".keep"))):
                targets.append(p)
                continue  # nicht hineinlaufen
            keep.append(x)
        dirs[:] = keep
        targets += [os.path.join(d, f) for f in files if f.endswith(".iml")]
    total = 0
    for t in sorted(targets):
        s = size(t) if os.path.isdir(t) else os.path.getsize(t)
        total += s
        print(f"  {os.path.relpath(t, project)}  ({human(s)})")
        if apply:
            shutil.rmtree(t, ignore_errors=True) if os.path.isdir(t) else os.remove(t)
    if not targets:
        print("Nichts zu bereinigen.")
    else:
        print(f"\n{'Gelöscht' if apply else 'Würde freigeben'}: {human(total)} in {len(targets)} Einträgen.")
        if not apply:
            print("Mit Eingabe 'apply' erneut starten.")


main()
