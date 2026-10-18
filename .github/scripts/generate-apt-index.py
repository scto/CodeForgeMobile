#!/usr/bin/env python3
"""Erzeugt <repo>/index.html: durchsuchbare Übersicht aller Pakete des APT-Repos.

Aufruf: generate-apt-index.py REPO_DIR [APT_URL]
Liest dists/*/main/binary-*/Packages (und large-packages.txt, falls vorhanden). Keine externen Abhängigkeiten.
"""
import datetime
import glob
import html
import os
import sys


def parse_packages(path):
    pkgs, cur, last = [], {}, None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                if cur:
                    pkgs.append(cur)
                cur, last = {}, None
            elif line[0] in " \t" and last:
                cur[last] += "\n" + line.strip()
            elif ":" in line:
                last, _, val = line.partition(":")
                cur[last] = val.strip()
        if cur:
            pkgs.append(cur)
    return pkgs


def human(n):
    n = float(n)
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024


def vkey(v):  # grobe Versionsordnung genügt für die Anzeige der neuesten Version
    import re
    return [int(x) if x.isdigit() else x for x in re.split(r"(\d+)", v)]


def main():
    repo = sys.argv[1]
    apt_url = (sys.argv[2] if len(sys.argv) > 2 else "").rstrip("/")
    rows = {}  # name -> {arch -> Eintrag}
    dist = "stable"
    for p in sorted(glob.glob(os.path.join(repo, "dists", "*", "main", "binary-*", "Packages"))):
        dist = p.split(os.sep)[-4]
        for e in parse_packages(p):
            name, arch = e.get("Package", ""), e.get("Architecture", "")
            if not name:
                continue
            old = rows.setdefault(name, {}).get(arch)
            if old is None or vkey(e.get("Version", "")) > vkey(old.get("Version", "")):
                rows[name][arch] = e
    large = []
    lp = os.path.join(repo, "large-packages.txt")
    if os.path.isfile(lp):
        for line in open(lp, encoding="utf-8"):
            f = line.rstrip("\n").split("|")
            if len(f) >= 7:
                large.append(f)
    for f in large:  # große Pakete erscheinen ebenfalls in der Liste
        rows.setdefault(f[0], {})[f[2]] = {"Package": f[0], "Version": f[1], "Architecture": f[2], "Size": f[5],
                                           "Description": "(großes Paket – Release-Asset)", "_url": f[6]}

    body = []
    for name in sorted(rows):
        archs = rows[name]
        any_e = next(iter(archs.values()))
        ver = ", ".join(sorted({e.get("Version", "") for e in archs.values()}))
        size = max(int(e.get("Size", "0") or 0) for e in archs.values())
        desc = any_e.get("Description", "").split("\n")[0]
        links = []
        for arch, e in sorted(archs.items()):
            href = e.get("_url") or (e.get("Filename") and e["Filename"].replace(" ", "%20"))
            label = html.escape(arch)
            links.append(f'<a href="{html.escape(href)}">{label}</a>' if href else label)
        deps = html.escape(any_e.get("Depends", ""))
        big = ' <span class="tag">groß</span>' if any(e.get("_url") for e in archs.values()) else ""
        body.append(
            f'<tr data-q="{html.escape((name + " " + desc).lower())}"><td><b>{html.escape(name)}</b>{big}</td>'
            f'<td>{html.escape(ver)}</td><td>{" · ".join(links)}</td><td class="n">{human(size)}</td>'
            f'<td>{html.escape(desc)}<div class="d">{deps}</div></td></tr>'
        )

    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    src = f"deb {apt_url or '<APT-URL>'} {dist} main"
    page = f"""<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CodeForge APT-Repo</title>
<style>
:root{{--bg:#fff;--fg:#1b1d22;--mu:#5d6370;--ln:#dfe2e8;--ac:#2a5bd7;--hd:#f3f5f9}}
@media (prefers-color-scheme:dark){{:root{{--bg:#111318;--fg:#e6e8ee;--mu:#9aa1ae;--ln:#2a2f3a;--ac:#7aa2ff;--hd:#1a1d25}}}}
body{{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif}}
main{{max-width:1100px;margin:0 auto}} h1{{font-size:1.4rem;margin:.2em 0}}
.m{{color:var(--mu)}} code,pre{{background:var(--hd);border-radius:6px;padding:2px 6px;font-size:.9em}}
pre{{padding:10px;overflow-x:auto}} a{{color:var(--ac)}}
input{{width:100%;box-sizing:border-box;padding:10px;margin:12px 0;border:1px solid var(--ln);border-radius:8px;background:var(--bg);color:var(--fg);font:inherit}}
.w{{overflow-x:auto}} table{{border-collapse:collapse;width:100%}}
th,td{{text-align:left;padding:7px 9px;border-bottom:1px solid var(--ln);vertical-align:top}}
th{{background:var(--hd);position:sticky;top:0}} .n{{white-space:nowrap;text-align:right}}
.d{{color:var(--mu);font-size:.82em;margin-top:2px}} .tag{{font-size:.75em;background:var(--hd);border:1px solid var(--ln);border-radius:4px;padding:0 5px}}
</style></head><body><main>
<h1>CodeForge APT-Repo</h1>
<p class="m">{len(rows)} Pakete · Stand {now} · Distribution <code>{html.escape(dist)}</code></p>
<p>Quelle: <code>{html.escape(src)}</code> · Schlüssel: <a href="codeforge.asc">codeforge.asc</a> · <a href="dists/{html.escape(dist)}/Release">Release</a>{' · <a href="large-packages.txt">große Pakete</a>' if large else ''}</p>
<input id="q" type="search" placeholder="Suchen (Name oder Beschreibung) …" autofocus>
<div class="w"><table><thead><tr><th>Paket</th><th>Version</th><th>Architektur (Download)</th><th>Größe</th><th>Beschreibung / Abhängigkeiten</th></tr></thead>
<tbody id="t">
{chr(10).join(body)}
</tbody></table></div>
<p class="m" id="c"></p>
</main>
<script>
const q=document.getElementById('q'),rs=[...document.querySelectorAll('#t tr')],c=document.getElementById('c');
function f(){{const v=q.value.trim().toLowerCase();let n=0;for(const r of rs){{const s=!v||r.dataset.q.includes(v);r.hidden=!s;if(s)n++}}c.textContent=n+' von '+rs.length+' angezeigt'}}
q.addEventListener('input',f);f();
</script></body></html>
"""
    with open(os.path.join(repo, "index.html"), "w", encoding="utf-8") as f:
        f.write(page)
    print(f"index.html: {len(rows)} Pakete")


if __name__ == "__main__":
    main()
