#!/usr/bin/env python3
"""Lädt neue Paketversionen als Assets in das Release `packages` (Downloads werden dort gezählt).

Jede Version ist ein eigenes Asset `<id>-<version>.zip`; vorhandene bleiben unverändert (ihre Zähler auch).
Ein Asset mit gleichem Namen, aber anderer SHA-256 wird ersetzt (Paket ohne Versionssprung neu gebaut).
Braucht die GitHub-CLI (`gh`) mit GH_TOKEN. Aufruf (im Store-Repo): python3 scripts/upload_packages.py
"""
import hashlib, json, os, shutil, subprocess, sys, tempfile

TAG = "packages"


def gh(*args, check=True):
    return subprocess.run(["gh", *args], capture_output=True, text=True, check=check)


def main():
    catalog = json.load(open("store.json", encoding="utf-8"))
    if gh("release", "view", TAG, check=False).returncode != 0:
        gh("release", "create", TAG, "--title", "Store-Pakete", "--notes",
           "Pakete des CodeForge Stores (eine Datei je Version). Download-Zähler fließen in stats.json.", "--latest=false")
    existing = json.loads(gh("release", "view", TAG, "--json", "assets").stdout)["assets"]
    digests = {a["name"]: (a.get("digest") or "").removeprefix("sha256:") for a in existing}
    tmp = tempfile.mkdtemp()
    uploaded = 0
    try:
        for it in catalog["items"]:
            name = f"{it['id']}-{it['version']}.zip"
            src = os.path.join("packages", f"{it['id']}.zip")
            if not os.path.isfile(src):
                print(f"fehlt: {src}")
                continue
            sha = hashlib.sha256(open(src, "rb").read()).hexdigest()
            if name in digests and (not digests[name] or digests[name] == sha):
                continue
            dst = os.path.join(tmp, name)
            shutil.copyfile(src, dst)
            gh("release", "upload", TAG, dst, "--clobber")
            uploaded += 1
            print(f"hochgeladen: {name}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"{uploaded} neue Assets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
