#!/usr/bin/env python3
"""Baut alle Plugin-Pakete und schreibt Katalog + Archive in ein Checkout von scto/codeforge-store.

Aufruf: python3 store-site/publish.py <pfad/zum/codeforge-store-checkout>
* Archive landen unter packages/<id>.zip (GitHub Pages: https://scto.github.io/codeforge-store/packages/<id>.zip)
* store.json wird neu erzeugt (Kategorien bleiben, items werden ersetzt); sha256 wird aus den veröffentlichten Dateien berechnet.
* Werkzeug-Plugins (`.tool`) führen Programme aus und sind im Store nur zum Download (Kategorie `plugin`, Sideload).
"""
import datetime, hashlib, json, subprocess, sys, tempfile, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASE = "https://scto.github.io/codeforge-store"
HOME = "https://github.com/scto/CodeForgeMobile"
GENERATORS = ["theme-plugins", "textmate-plugins", "tool-plugins"]


def category(manifest):
    pid, c = manifest["id"], manifest.get("contributes", {})
    if pid.endswith(".bundle"):
        return "bundle"
    if "tools" in c:
        return "plugin"
    if "lspServers" in c:
        return "lsp"
    if "formatters" in c:
        return "formatter"
    if "colorThemes" in c:
        return "theme"
    if "iconThemes" in c:
        return "icons"
    if "languagePacks" in c:
        return "language"
    if "grammars" in c:
        return "grammar"
    if "snippets" in c:
        return "snippets"
    raise SystemExit(f"Keine Kategorie für {pid}")


def main(store):
    store = Path(store)
    pk = store / "packages"
    pk.mkdir(parents=True, exist_ok=True)
    for old in pk.glob("*.zip"):
        old.unlink()
    items, seen = [], set()
    with tempfile.TemporaryDirectory() as tmp:
        for g in GENERATORS:
            out = Path(tmp) / g
            subprocess.run([sys.executable, str(REPO / g / "generate.py"), str(out)], check=True, capture_output=True)
            for z in sorted(out.glob("*.zip")):
                if z.name.startswith("codeforge-"):  # Sammelarchive nicht einzeln veröffentlichen
                    continue
                with zipfile.ZipFile(z) as zf:
                    m = json.loads(zf.read("plugin.json"))
                if m["id"] in seen:
                    raise SystemExit(f"Doppelte Plugin-ID: {m['id']}")
                seen.add(m["id"])
                data = z.read_bytes()
                (pk / z.name).write_bytes(data)
                cat = category(m)
                desc = m.get("description", "")
                items.append({
                    "id": m["id"], "category": cat,
                    "name": {"de": m["name"], "en": m["name"]},
                    "summary": {"de": desc, "en": desc},
                    "version": m["version"], "author": m.get("publisher", "codeforge"),
                    "homepage": HOME, "downloadUrl": f"{BASE}/packages/{z.name}",
                    "sha256": hashlib.sha256(data).hexdigest(),
                })
    # Bundles: Mitglieder müssen im Katalog stehen
    for it in items:
        if it["category"] == "bundle":
            m = json.loads(zipfile.ZipFile(pk / f"{it['id']}.zip").read("plugin.json"))
            missing = [x for x in m["contributes"]["extensionPack"] if x not in seen]
            if missing:
                raise SystemExit(f"{it['id']}: Mitglieder fehlen im Katalog: {missing}")
    order = {c: i for i, c in enumerate(["theme", "icons", "language", "grammar", "snippets", "lsp", "formatter", "bundle", "plugin"])}
    items.sort(key=lambda i: (order[i["category"]], i["id"]))
    cat_file = store / "store.json"
    catalog = json.loads(cat_file.read_text(encoding="utf-8"))
    catalog["items"] = items
    catalog["updated"] = datetime.date.today().isoformat()
    cat_file.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = {}
    for i in items:
        counts[i["category"]] = counts.get(i["category"], 0) + 1
    print(f"{len(items)} Pakete: {counts}")


main(sys.argv[1])
