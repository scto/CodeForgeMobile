#!/usr/bin/env python3
"""Erzeugt deklarative Inhalts-Plugins: Grammatiken, Snippets und Sprachpakete (+ je ein Bundle „alle …“).

IDs:  com.codeforge.plugin.<name>.grammar | .snippets | .language   und   com.codeforge.plugin.all-grammars|all-snippets|all-languages.bundle
Aufruf: python3 content-plugins/generate.py [ausgabeordner]   (Standard: content-plugins/build)
"""
import hashlib, json, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import grammars, langs, snippets  # noqa: E402

REPO = ROOT.parent
STRINGS = REPO / "core/resources/src/main/res/values/strings.xml"


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def pack(out, pid, folder):
    target = out / f"{pid}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(folder).as_posix())
    return target


def manifest(pid, name, desc, contributes):
    return {"id": pid, "name": name, "version": "1.0.0", "apiVersion": 2, "publisher": "codeforge",
            "description": desc, "permissions": [], "contributes": contributes}


def bundle(out, kind, title, ids):
    pid = f"com.codeforge.plugin.all-{kind}.bundle"
    folder = out / "src" / pid
    dump(folder / "plugin.json", manifest(pid, f"{title} (Bundle)", f"{title} von CodeForge als Bundle ({len(ids)} Pakete).", {"extensionPack": ids}))
    return pack(out, pid, folder)


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    gids, sids, lids = [], [], []
    for lid, name, lang_id, scope, exts, names, grammar in grammars.LANGS:
        pid = f"com.codeforge.plugin.{lid}.grammar"
        folder = out / "src" / pid
        dump(folder / "g" / f"{lid}.tmLanguage.json", grammar)
        contrib = {"languageId": lang_id, "scopeName": scope, "path": f"g/{lid}.tmLanguage.json", "extensions": exts + names}
        dump(folder / "plugin.json", manifest(pid, f"{name} (Grammatik)", f"Syntaxfärbung für {name} (vereinfachte TextMate-Grammatik, gilt für Dateien ohne eingebaute Grammatik).", {"grammars": [contrib]}))
        pack(out, pid, folder); gids.append(pid)
    for sid, (name, lang_id, items) in snippets.SNIPPETS.items():
        pid = f"com.codeforge.plugin.{sid}.snippets"
        folder = out / "src" / pid
        dump(folder / "snippets" / f"{sid}.json", {n: {"prefix": p, "body": b, "description": d} for n, (p, b, d) in items.items()})
        dump(folder / "plugin.json", manifest(pid, f"{name} (Snippets)", f"{len(items)} Code-Snippets für {name}.", {"snippets": [{"languageId": lang_id, "path": f"snippets/{sid}.json"}]}))
        pack(out, pid, folder); sids.append(pid)
    keys = langs.load_keys(STRINGS)
    langs.check(keys)
    for lid, locale, name, vals in langs.PACKS:
        pid = f"com.codeforge.plugin.lang-{lid.lower()}.language"
        folder = out / "src" / pid
        strings = {k: v for (k, en), v in zip(keys, vals) if v != en}
        dump(folder / "lang" / f"{lid}.json", {"strings": strings})
        dump(folder / "plugin.json", manifest(pid, f"{name} (Sprachpaket)", f"Teilübersetzung der Oberfläche ({len(strings)} Texte: Allgemeines und Dateibaum); übrige Texte bleiben in der App-Sprache.",
                                              {"languagePacks": [{"locale": locale, "label": name, "path": f"lang/{lid}.json"}]}))
        pack(out, pid, folder); lids.append(pid)
    bundle(out, "grammars", "Alle Grammatiken", gids)
    bundle(out, "snippets", "Alle Snippets", sids)
    bundle(out, "languages", "Alle Sprachpakete", lids)
    print(f"{len(gids)} Grammatiken, {len(sids)} Snippet-Pakete, {len(lids)} Sprachpakete, 3 Bundles")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
