#!/usr/bin/env python3
"""
Prüft die Abhängigkeitsreihenfolge der Gruppen in apt-groups.json.
Stellt sicher, dass keine Gruppe von Paketen abhängt, die erst in einer
späteren Gruppe gebaut oder nie gebaut werden (außer Basis-Bootstrap).

Verwendung:
    python3 -I scripts/check-group-order.py [--packages-dir PFAD]
"""

import json
import os
import re
import sys

# Pakete, die standardmäßig im Basis-Bootstrap enthalten sind und daher vorausgesetzt werden können.
BOOTSTRAP_BASE_PACKAGES = {
    "bash", "busybox", "bzip2", "ca-certificates", "command-not-found",
    "coreutils", "curl", "dash", "debianutils", "diffutils", "dos2unix",
    "dpkg", "ed", "findutils", "gawk", "grep", "gzip", "inetutils",
    "less", "libandroid-support", "libbz2", "libc++", "libcrypt",
    "libcurl", "libexpat", "libffi", "libgmp", "libiconv", "liblzma",
    "libmpfr", "libnghttp2", "libnghttp3", "libsqlite", "libssh2",
    "libssl", "libunistring", "lsof", "nano", "ncurses", "ncurses-ui-libs",
    "net-tools", "openssl", "patch", "pcre2", "procps", "psmisc",
    "readline", "sed", "tar", "termux-am", "termux-exec", "termux-keyring",
    "termux-licenses", "termux-tools", "time", "unzip", "util-linux",
    "which", "xz-utils", "zlib"
}

def load_stages(stages_path):
    stages = {}
    if not os.path.isfile(stages_path):
        return stages
    with open(stages_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = [p.strip() for p in line.split("|")]
            if len(parts) >= 2:
                stage_name = parts[0]
                pkgs = [p.strip() for p in parts[1].split(",") if p.strip()]
                stages[stage_name] = pkgs
    return stages

def load_groups(groups_path, stages):
    with open(groups_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    groups = []
    for g in data.get("groups", []):
        name = g.get("name")
        stage = g.get("stage", "")
        extra_pkgs_str = g.get("repo_packages", "")
        extra_pkgs = [p.strip() for p in extra_pkgs_str.split(",") if p.strip()]
        stage_pkgs = stages.get(stage, [])
        all_pkgs = []
        for p in extra_pkgs + stage_pkgs:
            if p not in all_pkgs:
                all_pkgs.append(p)
        groups.append({
            "name": name,
            "heavy": g.get("heavy", False),
            "packages": all_pkgs,
        })
    return groups

def find_packages_dir(cand=None):
    if cand and os.path.isdir(cand):
        return cand
    cands = [
        "terminal-packages-codeforge/packages",
        "../terminal-packages-codeforge/packages",
        "/data/data/com.termux/files/home/terminal-packages-codeforge/packages",
    ]
    for c in cands:
        if os.path.isdir(c):
            return c
    return None

def extract_depends_from_recipe(recipe_path):
    if not os.path.isfile(recipe_path):
        return []
    deps = []
    with open(recipe_path, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()

    # Suche nach TERMUX_PKG_DEPENDS="..."
    m = re.search(r'TERMUX_PKG_DEPENDS=["\']([^"\']+)["\']', content)
    if m:
        raw = m.group(1)
        for part in raw.split(","):
            part = part.strip()
            if not part:
                continue
            # Alternatives: "a | b"
            alts = []
            for alt in part.split("|"):
                alt = alt.strip()
                # Versionen entfernen: "pkg (>= 1.0)" -> "pkg"
                alt_name = re.sub(r'\(.*?\)', '', alt).strip()
                if alt_name:
                    alts.append(alt_name)
            if alts:
                deps.append(alts)
    return deps

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    stages_path = os.path.join(repo_root, "apt-stages.conf")
    groups_path = os.path.join(repo_root, "apt-groups.json")

    stages = load_stages(stages_path)
    groups = load_groups(groups_path, stages)

    pkg_dir = find_packages_dir()
    if not pkg_dir:
        print("Hinweis: Fork-Verzeichnis 'packages' nicht gefunden. Prüfe nur statische Deklarationen.")

    # Welches Paket wird in welcher Gruppe zuerst eingeführt?
    pkg_to_group = {}
    for idx, grp in enumerate(groups):
        for p in grp["packages"]:
            if p not in pkg_to_group:
                pkg_to_group[p] = (idx, grp["name"])

    violations = []
    missing = []

    print(f"Prüfe {len(groups)} Gruppen auf Abhängigkeitsreihenfolge...")
    for idx, grp in enumerate(groups):
        grp_name = grp["name"]
        print(f"  [{idx + 1}/{len(groups)}] Gruppe {grp_name}: {len(grp['packages'])} Pakete")
        for pkg in grp["packages"]:
            if not pkg_dir:
                continue
            recipe_file = os.path.join(pkg_dir, pkg, "build.sh")
            deps = extract_depends_from_recipe(recipe_file)
            for alt_deps in deps:
                # Mindestens eine Alternative muss erfüllt sein
                satisfied = False
                alt_info = []
                for d in alt_deps:
                    if d in BOOTSTRAP_BASE_PACKAGES:
                        satisfied = True
                        break
                    if d in pkg_to_group:
                        dep_idx, dep_grp = pkg_to_group[d]
                        if dep_idx <= idx:
                            satisfied = True
                            break
                        else:
                            alt_info.append(f"{d} (in späterer Gruppe: {dep_grp})")
                    else:
                        alt_info.append(f"{d} (in keiner Gruppe)")

                if not satisfied:
                    if any("späterer" in s for s in alt_info):
                        violations.append((grp_name, pkg, ", ".join(alt_info)))
                    else:
                        missing.append((grp_name, pkg, ", ".join(alt_info)))

    print("\n--- Ergebnis der Reihenfolge-Prüfung ---")
    if violations:
        print(f"WARNUNG: {len(violations)} Abhängigkeiten liegen in SPÄTEREN Gruppen:")
        for grp, pkg, info in violations:
            print(f"  * {grp} -> Paket '{pkg}' benötigt: {info}")
    else:
        print("Keine Reihenfolge-Verletzungen zwischen vorhandenen Gruppen gefunden.")

    if missing:
        print(f"\nHinweis: {len(missing)} Abhängigkeiten wurden in keiner Gruppe oder im Bootstrap deklariert:")
        for grp, pkg, info in missing:
            print(f"  * {grp} -> Paket '{pkg}' benötigt: {info}")

    return 0

if __name__ == "__main__":
    sys.exit(main())
