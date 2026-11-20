#!/usr/bin/env python3
"""Baut alle Plugin-Pakete und schreibt Katalog, Archive und Website in ein Checkout von scto/codeforge-store.

Aufruf: python3 store-site/publish.py <pfad/zum/codeforge-store-checkout>

* Archive: packages/<id>.zip (Spiegel auf GitHub Pages). Heruntergeladen wird aus dem Release `packages` des Store-Repos
  (`<id>-<version>.zip`, hochgeladen von .github/workflows/packages.yml) – nur dort zählt GitHub Downloads.
* store.json (schema 1, nur ergänzt): Kategorienbaum `tree`, je Paket `categories` (Pfade), `tags`, `kind`, `size`,
  `published`, `updated`, `permissions`, `apiVersion`, `mirrorUrl`. Ältere App-Versionen lesen weiter `category`.
* Code-Plugins (code-plugins/out, vorher `./gradlew :code-plugins:packageAll`) erscheinen in der Kategorie `plugin`
  (nur Sideload: Download von der Website, Installation über Einstellungen → Plugins).
* Website, Statistik-Skript, Workflows und Bewertungsformular kommen aus store-site/site/.
* Bewertungen und Downloads schreibt der tägliche Workflow nach stats.json (scripts/stats.py).
"""
import datetime, hashlib, json, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import taxonomy  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
SITE = Path(__file__).resolve().parent / "site"
STORE_REPO = "scto/codeforge-store"
BASE = "https://scto.github.io/codeforge-store"
RELEASE = f"https://github.com/{STORE_REPO}/releases/download/packages"
HOME = "https://github.com/scto/CodeForgeMobile"
GENERATORS = ["theme-plugins", "textmate-plugins", "tool-plugins", "icon-plugins", "content-plugins", "project-templates"]
CATEGORY_ORDER = ["theme", "icons", "language", "grammar", "snippets", "lsp", "template", "formatter", "bundle", "tool", "plugin"]


def category(manifest):
    pid, c = manifest["id"], manifest.get("contributes", {})
    if manifest.get("entryPointClass"):
        return "plugin"
    if pid.endswith(".bundle"):
        return "bundle"
    if "tools" in c:
        return "tool"
    if "lspServers" in c:
        return "lsp"
    if "projectTemplates" in c:
        return "template"
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


def asset_name(pid, version):
    return f"{pid}-{version}.zip"


def collect(tmp):
    """Alle Pakete: (Manifest, Datei) aus den Generatoren und den gebauten Code-Plugins."""
    found = []
    for g in GENERATORS:
        out = Path(tmp) / g
        run = subprocess.run([sys.executable, str(REPO / g / "generate.py"), str(out)], capture_output=True, text=True)
        if run.returncode != 0:
            detail = (run.stderr or run.stdout).strip().splitlines()[-15:]
            raise SystemExit(f"{g}/generate.py fehlgeschlagen (Exit {run.returncode}):\n" + "\n".join(detail))
        found += [z for z in sorted(out.glob("*.zip")) if not z.name.startswith("codeforge-")]
    code = sorted((REPO / "code-plugins" / "out").glob("*/*.zip"))
    if not code:
        print("Hinweis: keine Code-Plugins gefunden (vorher ./gradlew :code-plugins:packageAll) – sie fehlen im Katalog.")
    found += code
    result = []
    for z in found:
        with zipfile.ZipFile(z) as zf:
            result.append((json.loads(zf.read("plugin.json")), z))
    return result


def item_for(m, data, cat, old, today):
    pid, version = m["id"], m["version"]
    cats, tags = taxonomy.derive(m, cat)
    prev = old.get(pid, {})
    published = prev.get("published") or today
    updated = prev.get("updated") or today
    if prev and prev.get("version") != version:
        updated = today
    desc = m.get("description", "")
    return {
        "id": pid, "category": cat, "categories": cats, "tags": tags,
        "kind": ("sandbox" if m.get("sandbox") else "code") if cat == "plugin" else "declarative",
        "name": {"de": m["name"], "en": m["name"]},
        "summary": {"de": desc, "en": desc},
        "version": version, "author": m.get("publisher", "codeforge"),
        "apiVersion": m.get("apiVersion", 1), "permissions": m.get("permissions", []),
        "homepage": HOME, "downloadUrl": f"{RELEASE}/{asset_name(pid, version)}", "mirrorUrl": f"{BASE}/packages/{pid}.zip",
        "sha256": hashlib.sha256(data).hexdigest(), "size": len(data),
        "published": published, "updated": updated,
    }


def main(store):
    store = Path(store)
    pk = store / "packages"
    pk.mkdir(parents=True, exist_ok=True)
    cat_file = store / "store.json"
    catalog = json.loads(cat_file.read_text(encoding="utf-8")) if cat_file.is_file() else {"schema": 1, "categories": [], "items": []}
    old = {i["id"]: i for i in catalog.get("items", [])}
    today = datetime.date.today().isoformat()
    for f in pk.glob("*.zip"):
        f.unlink()
    items, seen = [], set()
    with tempfile.TemporaryDirectory() as tmp:
        for m, z in collect(tmp):
            if m["id"] in seen:
                raise SystemExit(f"Doppelte Plugin-ID: {m['id']}")
            seen.add(m["id"])
            data = z.read_bytes()
            (pk / f"{m['id']}.zip").write_bytes(data)
            items.append(item_for(m, data, category(m), old, today))
    # Sammel-Bundle „Alle Themes“ (erst hier bekannt)
    theme_ids = sorted(i["id"] for i in items if i["category"] == "theme")
    bid = "com.codeforge.plugin.all-themes.bundle"
    bm = {"id": bid, "name": "Alle Themes (Bundle)", "version": "1.0.0", "apiVersion": 2, "publisher": "codeforge",
          "description": f"Alle Farbschemata von CodeForge als Bundle ({len(theme_ids)} Pakete).", "permissions": [],
          "contributes": {"extensionPack": theme_ids}}
    bzip = pk / f"{bid}.zip"
    with zipfile.ZipFile(bzip, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("plugin.json", json.dumps(bm, ensure_ascii=False, indent=2) + "\n")
    seen.add(bid)
    items.append(item_for(bm, bzip.read_bytes(), "bundle", old, today))
    # Bundles: Mitglieder müssen im Katalog stehen
    for it in items:
        if it["category"] == "bundle":
            m = json.loads(zipfile.ZipFile(pk / f"{it['id']}.zip").read("plugin.json"))
            missing = [x for x in m["contributes"]["extensionPack"] if x not in seen]
            if missing:
                raise SystemExit(f"{it['id']}: Mitglieder fehlen im Katalog: {missing}")
    order = {c: i for i, c in enumerate(CATEGORY_ORDER)}
    items.sort(key=lambda i: (order[i["category"]], i["id"]))

    ids = [c["id"] for c in catalog.get("categories", [])]
    defaults = [("tool", "Code-Tools", "Code tools"), ("template", "Projektvorlagen", "Project templates"),
                ("plugin", "Plugins (Sideload)", "Plugins (sideload)")]
    for cid, de, en in defaults:
        if cid not in ids:
            catalog.setdefault("categories", []).append({"id": cid, "title": {"de": de, "en": en}})
    catalog["schema"] = 1
    catalog["tree"] = taxonomy.tree(p for i in items for p in i["categories"])
    catalog["statsUrl"] = f"{BASE}/stats.json"
    catalog["ratingUrl"] = f"https://github.com/{STORE_REPO}/issues/new?template=rating.yml"
    catalog["items"] = items
    catalog["updated"] = today
    cat_file.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # Website, Skripte, Workflows, Bewertungsformular
    for src in SITE.rglob("*"):
        if src.is_file() and "__pycache__" not in src.parts:
            dst = store / src.relative_to(SITE)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
    if not (store / "stats.json").is_file():
        (store / "stats.json").write_text(json.dumps({"schema": 1, "updated": None, "items": {}}, indent=2) + "\n", encoding="utf-8")

    counts = {}
    for i in items:
        counts[i["category"]] = counts.get(i["category"], 0) + 1
    print(f"{len(items)} Pakete, {len(catalog['tree'])} Kategorien: {counts}")


if __name__ == "__main__":
    main(sys.argv[1])
