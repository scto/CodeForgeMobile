#!/usr/bin/env python3
"""Findet unbenutzte Android-Ressourcen. Eingabe: 'check' (Standard) oder 'apply' (löscht unbenutzte Dateien in drawable*/layout*/menu*/anim*/xml*)."""
import os, re
from common import args, walk, read, rel, human

FILE_TYPES = ("drawable", "layout", "menu", "anim", "animator", "xml", "raw", "color", "font")
VALUE_TAGS = {"string", "color", "dimen", "style", "bool", "integer", "array", "plurals", "string-array", "integer-array", "attr"}


def main():
    project, mode = args()
    apply = mode.lower() == "apply"
    declared = {}  # (type, name) -> [Pfad]
    for path in walk(project, exts={"xml", "png", "webp", "jpg", "jpeg", "gif", "ttf", "otf", "mp3", "ogg", "wav", "json"}):
        parts = path.split(os.sep)
        if "res" not in parts:
            continue
        i = len(parts) - 1 - parts[::-1].index("res")
        if i + 2 >= len(parts):
            continue
        folder = parts[i + 1].split("-")[0]
        fname = parts[-1].split(".")[0]
        if folder == "values":
            if path.endswith(".xml"):
                for m in re.finditer(r'<(\w[\w-]*)\s+name="([^"]+)"', read(path)):
                    tag = m.group(1)
                    if tag in VALUE_TAGS:
                        t = {"string-array": "array", "integer-array": "array"}.get(tag, tag)
                        declared.setdefault((t, m.group(2).replace(".", "_")), []).append(path)
        elif folder in FILE_TYPES:
            declared.setdefault((folder, fname), []).append(path)

    corpus = []
    for path in walk(project, exts={"kt", "java", "xml", "kts", "gradle", "pro", "json"}):
        try:
            corpus.append(read(path))
        except OSError:
            pass
    text = "\n".join(corpus)
    used = set()
    for m in re.finditer(r"\bR\.(\w+)\.(\w+)", text):
        used.add((m.group(1), m.group(2)))
    for m in re.finditer(r"@(?:\+id/)?(\w+)/([\w.]+)", text):
        used.add((m.group(1), m.group(2).replace(".", "_")))
    for m in re.finditer(r"\bstyle=\"[@?]?(?:style/)?([\w.]+)\"|parent=\"([\w.]+)\"", text):
        for g in m.groups():
            if g:
                used.add(("style", g.replace(".", "_")))
    # Style-Vererbung über Namenspunkt: Foo.Bar benötigt Foo
    for (t, n) in list(used):
        if t == "style" and "_" in n:
            pass
    manifest_refs = set(re.findall(r"@\w+/([\w.]+)", text))

    unused = []
    for (t, name), paths in sorted(declared.items()):
        if t in ("attr",):
            continue
        if (t, name) in used:
            continue
        if t == "layout" and name.startswith("abc_"):
            continue
        # layouts: ViewBinding (FooBarBinding) zählt als Nutzung
        if t == "layout":
            camel = "".join(w.capitalize() for w in name.split("_")) + "Binding"
            if camel in text:
                continue
        if t == "style" and any(name == u[1] or name.startswith(u[1] + "_") for u in used if u[0] == "style"):
            continue
        unused.append((t, name, paths))
    if not unused:
        print("Keine unbenutzten Ressourcen gefunden.")
        return
    total = 0
    deletable = []
    print(f"Unbenutzt: {len(unused)} Ressourcen\n")
    for t, name, paths in unused:
        print(f"  {t}/{name}")
        for p in paths:
            if t in FILE_TYPES:
                size = os.path.getsize(p)
                total += size
                deletable.append(p)
    print(f"\nDateien löschbar: {len(deletable)} ({human(total)}). Werte in values/*.xml werden nur gemeldet.")
    print("Achtung: dynamische Zugriffe (getIdentifier, Reflection) werden nicht erkannt.")
    if apply:
        for p in deletable:
            os.remove(p)
        print(f"{len(deletable)} Dateien gelöscht.")
    else:
        print("Mit Eingabe 'apply' erneut starten, um die Dateien zu löschen (vorher committen!).")


main()
