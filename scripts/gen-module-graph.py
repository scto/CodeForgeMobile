#!/usr/bin/env python3
"""Erzeugt docs/dev/module-graph.md (Mermaid) aus settings.gradle.kts und den project()-Abhängigkeiten.

Aufruf im Projektstamm: python3 scripts/gen-module-graph.py
"""
import collections
import os
import re

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(root)
mods = re.findall(r'"(:[^"]+)"', open("settings.gradle.kts").read())
edges = set()
for m in mods:
    p = m[1:].replace(":", "/") + "/build.gradle.kts"
    if os.path.exists(p):
        for d in re.findall(r'project\("(:[^"]+)"\)', open(p).read()):
            if d in mods:
                edges.add((m, d))


def layer(m):
    return m.split(":")[1]


def node(m):
    return "n_" + re.sub(r"\W", "_", m[1:])


order = ["app", "feature", "core", "libs", "examples", "templates"]
titles = {"app": "App", "feature": "Feature-Module", "core": "Core", "libs": "Bibliotheken (libs)",
          "examples": "Beispiel-Plugins", "templates": "Templates"}
out = ["# Modulgraph", "",
       "Automatisch erzeugt mit `python3 scripts/gen-module-graph.py` — nicht von Hand ändern.", "",
       f"{len(mods)} Module, {len(edges)} direkte Projektabhängigkeiten.", "",
       "## Schichten (aggregiert)", "", "```mermaid", "flowchart TD"]
agg = collections.Counter((layer(a), layer(b)) for a, b in edges if layer(a) != layer(b))
for l in order:
    n = sum(1 for m in mods if layer(m) == l)
    if n:
        out.append(f'    {l}["{titles[l]} ({n})"]')
for (a, b), c in sorted(agg.items()):
    out.append(f"    {a} -->|{c}| {b}")
out += ["```", "", "## Abhängigkeiten je Modul", ""]
for l in order:
    ms = [m for m in mods if layer(m) == l]
    if not ms:
        continue
    out += [f"### {titles[l]}", "", "| Modul | hängt ab von |", "|---|---|"]
    for m in ms:
        deps = sorted(b for a, b in edges if a == m)
        out.append(f"| `{m}` | " + (", ".join(f"`{d}`" for d in deps) if deps else "–") + " |")
    out.append("")
# Detailgraph nur für :app und :core (überschaubar)
out += ["## Detail: `:app` und seine direkten Abhängigkeiten", "", "```mermaid", "flowchart LR"]
for a, b in sorted(edges):
    if a == ":app":
        out.append(f'    app --> {node(b)}["{b}"]')
out += ["```", ""]
open("docs/dev/module-graph.md", "w").write("\n".join(out))
print("docs/dev/module-graph.md:", len(mods), "Module,", len(edges), "Kanten")
