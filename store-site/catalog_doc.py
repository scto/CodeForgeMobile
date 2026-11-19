#!/usr/bin/env python3
"""Schreibt die Liste aller mitgelieferten Plugins nach docs/dev/PluginCatalog.md (gleiche Quelle wie der Store).

Aufruf: python3 store-site/catalog_doc.py   (vorher optional ./gradlew :code-plugins:packageAll für die Code-Plugins)
"""
import sys, tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import publish  # noqa: E402
import taxonomy  # noqa: E402

GROUPS = [
    ("plugin", "Code-Plugins (API 4, nur Sideload)", "`code-plugins/`"),
    ("bundle", "Bundles und Sprachpakete", "`tool-plugins/`, `content-plugins/`"),
    ("lsp", "Sprachserver", "`tool-plugins/`"),
    ("formatter", "Formatierer", "`tool-plugins/`"),
    ("tool", "Werkzeuge (PT-Menü)", "`tool-plugins/`"),
    ("template", "Projektvorlagen", "`project-templates/`"),
    ("grammar", "Grammatiken", "`content-plugins/`"),
    ("snippets", "Snippets", "`content-plugins/`"),
    ("language", "Sprachpakete der Oberfläche", "`content-plugins/`"),
    ("theme", "Farbschemata", "`theme-plugins/`, `textmate-plugins/`"),
    ("icons", "Icon-Pakete", "`icon-plugins/`"),
]


def main():
    with tempfile.TemporaryDirectory() as tmp:
        entries = [(m, publish.category(m)) for m, _ in publish.collect(tmp)]
    out = ["# Plugin-Liste", "",
           f"Alle {len(entries)} Plugins, die das Repository baut (erzeugt von `store-site/catalog_doc.py`; nicht von Hand ändern).",
           "Deklarative Plugins gibt es im [Store](../store.md), Code-Plugins als Sideload-ZIP. Kategorien und Tags wie im Store.", ""]
    for cat, title, src in GROUPS:
        rows = sorted((m for m, c in entries if c == cat), key=lambda m: m["name"].lower())
        if not rows:
            continue
        out += [f"## {title} ({len(rows)})", "", f"Quelle: {src}", "", "| Name | ID | Version | Kategorien | Beschreibung |", "|---|---|---|---|---|"]
        for m in rows:
            cats, _ = taxonomy.derive(m, cat)
            desc = m.get("description", "").replace("|", "\\|").replace("\n", " ")
            if len(desc) > 140:
                desc = desc[:137].rstrip() + " …"
            nice = ", ".join(" › ".join(taxonomy.title("/".join(c.split("/")[:i + 1]))[0] for i in range(len(c.split("/")))) for c in cats[:3])
            out.append(f"| {m['name']} | `{m['id']}` | {m['version']} | {nice} | {desc} |")
        out.append("")
    path = Path(__file__).resolve().parent.parent / "docs" / "dev" / "PluginCatalog.md"
    path.write_text("\n".join(out), encoding="utf-8")
    print(f"{path}: {len(entries)} Plugins")


if __name__ == "__main__":
    main()
