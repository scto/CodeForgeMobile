#!/usr/bin/env python3
"""Statistik des Stores: Downloads (GitHub-Release `packages`) und Bewertungen (Issues mit Label `rating`) → stats.json.

Läuft täglich per .github/workflows/stats.yml. Nur Bordmittel (urllib), Token aus GITHUB_TOKEN.

stats.json:
  {"schema": 1, "updated": "2026-11-20T23:17:00Z",
   "totals": {"downloads": 1234, "ratings": 56},
   "items": {"<id>": {"downloads": 12, "rating": 4.5, "ratings": 4, "stars": [0, 0, 1, 0, 3],
                      "reviews": [{"user": "…", "stars": 5, "text": "…", "date": "2026-11-20", "url": "…"}]}}}

Regeln für Bewertungen: je Nutzer und Paket zählt die neueste; Issues mit Label `invalid` oder `spam` zählen nicht;
Text wird auf 600 Zeichen gekürzt, höchstens 5 Rezensionen je Paket (neueste zuerst).
"""
import datetime, json, os, re, sys, urllib.request

API = "https://api.github.com"
EXCLUDED = {"invalid", "spam", "duplicate"}
STARS = re.compile(r"\((\d)\)|^([1-5])\b|^(★+)")


def get(path, token):
    req = urllib.request.Request(API + path, headers={"Accept": "application/vnd.github+json", "User-Agent": "codeforge-store-stats",
                                                      **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def paged(path, token, limit=50):
    sep = "&" if "?" in path else "?"
    for page in range(1, limit + 1):
        batch = get(f"{path}{sep}per_page=100&page={page}", token)
        if not batch:
            return
        yield from batch
        if len(batch) < 100:
            return


def item_for_asset(name, ids):
    """`<id>-<version>.zip` → id (längster passender Präfix)."""
    if not name.endswith(".zip"):
        return None
    best = None
    for pid in ids:
        if name.startswith(pid + "-") and (best is None or len(pid) > len(best)):
            best = pid
    return best


def downloads(assets, ids):
    out = {}
    for a in assets:
        pid = item_for_asset(a.get("name", ""), ids)
        if pid:
            out[pid] = out.get(pid, 0) + int(a.get("download_count", 0))
    return out


def sections(body):
    """Abschnitte eines Issue-Formulars: Überschrift (### …) → Text."""
    out, current = {}, None
    for line in (body or "").splitlines():
        if line.startswith("### "):
            current = line[4:].strip().lower()
            out[current] = []
        elif current is not None:
            out[current].append(line)
    return {k: "\n".join(v).strip() for k, v in out.items()}


def parse_rating(issue):
    """(plugin-id, sterne, text) aus einem Bewertungs-Issue oder None."""
    s = sections(issue.get("body"))
    pid = next((v for k, v in s.items() if k.startswith("paket") or k.startswith("package")), "").strip().strip("`")
    raw = next((v for k, v in s.items() if k.startswith("sterne") or k.startswith("stars")), "")
    text = next((v for k, v in s.items() if k.startswith("bewertung") or k.startswith("review")), "")
    if not pid and issue.get("title", "").lower().startswith("bewertung:"):
        pid = issue["title"].split(":", 1)[1].strip()
    m = STARS.search(raw.strip())
    if not pid or not m:
        return None
    stars = int(m.group(1) or m.group(2)) if (m.group(1) or m.group(2)) else len(m.group(3))
    if not 1 <= stars <= 5:
        return None
    text = "" if text in ("_No response_", "_Keine Antwort_") else text
    return pid, stars, text[:600]


def ratings(issues, ids):
    latest = {}
    for it in issues:
        if "pull_request" in it:
            continue
        labels = {l["name"].lower() for l in it.get("labels", [])}
        if labels & EXCLUDED:
            continue
        parsed = parse_rating(it)
        if not parsed or parsed[0] not in ids:
            continue
        pid, stars, text = parsed
        user = (it.get("user") or {}).get("login", "?")
        date = it.get("updated_at") or it.get("created_at") or ""
        key = (pid, user)
        if key not in latest or date > latest[key]["date"]:
            latest[key] = {"user": user, "stars": stars, "text": text, "date": date, "url": it.get("html_url", "")}
    out = {}
    for (pid, _), r in latest.items():
        out.setdefault(pid, []).append(r)
    return out


def build(catalog, assets, issues, now):
    ids = [i["id"] for i in catalog.get("items", [])]
    dl = downloads(assets, ids)
    rt = ratings(issues, set(ids))
    items = {}
    for pid in ids:
        rs = rt.get(pid, [])
        entry = {"downloads": dl.get(pid, 0)}
        if rs:
            hist = [0] * 5
            for r in rs:
                hist[r["stars"] - 1] += 1
            entry.update({
                "rating": round(sum(r["stars"] for r in rs) / len(rs), 1), "ratings": len(rs), "stars": hist,
                "reviews": [dict(r, date=r["date"][:10]) for r in sorted(rs, key=lambda r: r["date"], reverse=True) if r["text"]][:5],
            })
        items[pid] = entry
    return {"schema": 1, "updated": now,
            "totals": {"downloads": sum(dl.values()), "ratings": sum(len(v) for v in rt.values())}, "items": items}


def main(root="."):
    token = os.environ.get("GITHUB_TOKEN", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "scto/codeforge-store")
    catalog = json.load(open(os.path.join(root, "store.json"), encoding="utf-8"))
    try:
        release = get(f"/repos/{repo}/releases/tags/packages", token)
        assets = list(paged(f"/repos/{repo}/releases/{release['id']}/assets", token))
    except Exception as e:  # noqa: BLE001 – ohne Release (noch) keine Downloads
        print(f"Release 'packages' nicht lesbar: {e}")
        assets = []
    issues = list(paged(f"/repos/{repo}/issues?labels=rating&state=all", token))
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    stats = build(catalog, assets, issues, now)
    path = os.path.join(root, "stats.json")
    old = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    if {k: v for k, v in old.items() if k != "updated"} == {k: v for k, v in stats.items() if k != "updated"}:
        print("Keine Änderung.")
        return 0
    with open(path, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{stats['totals']['downloads']} Downloads, {stats['totals']['ratings']} Bewertungen")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
