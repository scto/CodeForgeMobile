#!/usr/bin/env python3
# =============================================================================
# check-apt-consistency.py
#
# Prüft die Abhängigkeits-Konsistenz von Termux/CodeForge-Bootstraps und APT-Repositories.
# Reine Python-Standardbibliothek, lauffähig mit `python3 -I`, keine Netzwerkzugriffe.
#
# Aufruf:
#   python3 scripts/check-apt-consistency.py bootstrap <zip-datei | verzeichnis> [--forbidden <pfad>] [--max-packages <n>]
#   python3 scripts/check-apt-consistency.py repo <repo-verzeichnis> [--large-packages <pfad>] [--on-error fail|warn]
# =============================================================================

import argparse
import gzip
import os
import re
import sys
import zipfile


def parse_debian_version(v_str):
    """Zerlegt eine Debian-Versionsnummer in (epoch, upstream_version, debian_revision)."""
    if not v_str:
        return (0, "", "")
    epoch = 0
    if ":" in v_str:
        ep_str, rest = v_str.split(":", 1)
        try:
            epoch = int(ep_str)
        except ValueError:
            epoch = 0
    else:
        rest = v_str
    if "-" in rest:
        upstream, revision = rest.rsplit("-", 1)
    else:
        upstream, revision = rest, ""
    return (epoch, upstream, revision)


def _order_char(c):
    if c == "~":
        return -1
    if not c:
        return 0
    if c.isalpha():
        return ord(c)
    return ord(c) + 256


def _compare_version_part(p1, p2):
    i = 0
    j = 0
    while i < len(p1) or j < len(p2):
        # 1. Nicht-Ziffern-Teile vergleichen
        s1 = []
        while i < len(p1) and not p1[i].isdigit():
            s1.append(p1[i])
            i += 1
        s2 = []
        while j < len(p2) and not p2[j].isdigit():
            s2.append(p2[j])
            j += 1

        k = 0
        while k < len(s1) or k < len(s2):
            c1 = s1[k] if k < len(s1) else ""
            c2 = s2[k] if k < len(s2) else ""
            if c1 != c2:
                o1 = _order_char(c1)
                o2 = _order_char(c2)
                return -1 if o1 < o2 else 1
            k += 1

        # 2. Ziffern-Teile vergleichen
        n1 = []
        while i < len(p1) and p1[i].isdigit():
            n1.append(p1[i])
            i += 1
        n2 = []
        while j < len(p2) and p2[j].isdigit():
            n2.append(p2[j])
            j += 1

        v1 = int("".join(n1)) if n1 else 0
        v2 = int("".join(n2)) if n2 else 0
        if v1 != v2:
            return -1 if v1 < v2 else 1

    return 0


def compare_debian_versions(v1_str, v2_str):
    """Vergleicht zwei Debian-Versionsnummern (-1 wenn v1 < v2, 0 wenn gleich, 1 wenn v1 > v2)."""
    ep1, up1, rev1 = parse_debian_version(v1_str)
    ep2, up2, rev2 = parse_debian_version(v2_str)

    if ep1 != ep2:
        return -1 if ep1 < ep2 else 1

    res = _compare_version_part(up1, up2)
    if res != 0:
        return res

    return _compare_version_part(rev1, rev2)


def check_version_relation(installed_ver, op, req_ver):
    """Prüft, ob installed_ver die Bedingung op req_ver erfüllt."""
    if not op or not req_ver:
        return True
    cmp_res = compare_debian_versions(installed_ver, req_ver)
    if op == "=":
        return cmp_res == 0
    elif op == ">=":
        return cmp_res >= 0
    elif op == "<=":
        return cmp_res <= 0
    elif op == ">>":
        return cmp_res > 0
    elif op == "<<":
        return cmp_res < 0
    elif op == ">":
        return cmp_res >= 0  # Debian behandelt > wie >=
    elif op == "<":
        return cmp_res <= 0  # Debian behandelt < wie <=
    return False


def parse_dpkg_control_blocks(text):
    """Parst einen Text mit RFC-822-ähnlichen dpkg-Blöcken in eine Liste von Dictionaries."""
    packages = []
    current = {}
    last_key = None

    for line in text.splitlines():
        line = line.rstrip("\r\n")
        if not line.strip():
            if current.get("Package"):
                packages.append(current)
            current = {}
            last_key = None
            continue

        if (line.startswith(" ") or line.startswith("\t")) and last_key:
            current[last_key] += "\n" + line.strip()
        elif ":" in line:
            key, val = line.split(":", 1)
            key = key.strip()
            val = val.strip()
            current[key] = val
            last_key = key

    if current.get("Package"):
        packages.append(current)

    return packages


def parse_dependency_clause(clause):
    """
    Parst eine Abhängigkeitsklausel wie 'pkg (>= 1.0)' in (pkg_name, op, version).
    """
    clause = clause.strip()
    m = re.match(r"^([a-zA-Z0-9_\.\+-]+)(?:\s*\(([><=!]+)\s*([^\)]+)\))?$", clause)
    if m:
        name = m.group(1)
        op = m.group(2) or ""
        ver = m.group(3) or ""
        return (name, op, ver)
    # Fallback für unübliche Notationen
    parts = clause.split()
    return (parts[0], "", "")


def check_package_dependencies(packages_by_name, provides_map, pkg_info, arch_context="all"):
    """
    Prüft, ob alle Depends und Pre-Depends von pkg_info erfüllbar sind.
    Gibt eine Liste von Fehlermeldungen zurück.
    """
    errors = []
    pkg_name = pkg_info.get("Package", "unbekannt")
    dep_fields = [pkg_info.get("Pre-Depends", ""), pkg_info.get("Depends", "")]
    full_dep_str = ", ".join(f for f in dep_fields if f)

    if not full_dep_str.strip():
        return errors

    # Mehrere Abhängigkeiten sind durch Komma getrennt
    for dep_entry in full_dep_str.split(","):
        dep_entry = dep_entry.strip()
        if not dep_entry:
            continue

        # Alternativen sind durch Pipe '|' getrennt
        alternatives = [alt.strip() for alt in dep_entry.split("|")]
        satisfied = False

        for alt in alternatives:
            req_name, op, req_ver = parse_dependency_clause(alt)

            # 1. Direkt über installiertes/verfügbares Paket mit diesem Namen
            if req_name in packages_by_name:
                cand = packages_by_name[req_name]
                cand_ver = cand.get("Version", "")
                if check_version_relation(cand_ver, op, req_ver):
                    satisfied = True
                    break

            # 2. Über Provides
            if req_name in provides_map:
                for provider_name, prov_ver in provides_map[req_name]:
                    if not op or not req_ver or (prov_ver and check_version_relation(prov_ver, op, req_ver)):
                        satisfied = True
                        break
                if satisfied:
                    break

        if not satisfied:
            errors.append(f"FEHLER: {pkg_name} {arch_context}: Abhängigkeit '{dep_entry}' nicht erfüllbar")

    return errors


def read_forbidden_list(forbidden_path):
    """Liest die Verbotsliste für Bootstrap-Pakete ein."""
    if not forbidden_path or not os.path.isfile(forbidden_path):
        return set()
    forbidden = set()
    with open(forbidden_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                forbidden.add(line)
    return forbidden


def run_bootstrap_check(target_path, forbidden_path=None, max_packages=80):
    """Prüft ein Bootstrap-ZIP oder ein entpacktes Verzeichnis."""
    status_content = None

    if os.path.isfile(target_path) and (target_path.endswith(".zip") or zipfile.is_zipfile(target_path)):
        with zipfile.ZipFile(target_path, "r") as zf:
            candidates = [
                "usr/var/lib/dpkg/status",
                "var/lib/dpkg/status",
                "./usr/var/lib/dpkg/status",
                "./var/lib/dpkg/status",
            ]
            for cand in candidates:
                if cand in zf.namelist():
                    status_content = zf.read(cand).decode("utf-8", errors="replace")
                    break
            if status_content is None:
                for name in zf.namelist():
                    if name.endswith("var/lib/dpkg/status"):
                        status_content = zf.read(name).decode("utf-8", errors="replace")
                        break
    elif os.path.isdir(target_path):
        candidates = [
            os.path.join(target_path, "usr/var/lib/dpkg/status"),
            os.path.join(target_path, "var/lib/dpkg/status"),
        ]
        for cand in candidates:
            if os.path.isfile(cand):
                with open(cand, "r", encoding="utf-8", errors="replace") as f:
                    status_content = f.read()
                break

    if status_content is None:
        print(f"FEHLER: dpkg/status in '{target_path}' nicht gefunden.", file=sys.stderr)
        return [f"FEHLER: dpkg/status in '{target_path}' nicht gefunden"]

    raw_packages = parse_dpkg_control_blocks(status_content)
    # Nur installierte Pakete berücksichtigen
    installed_packages = {}
    provides_map = {}

    for p in raw_packages:
        status_field = p.get("Status", "")
        # Standard Termux-Status: "install ok installed"
        if "installed" in status_field or not status_field:
            name = p.get("Package")
            if name:
                installed_packages[name] = p
                prov_field = p.get("Provides", "")
                if prov_field:
                    for prov in prov_field.split(","):
                        p_name, _, p_ver = parse_dependency_clause(prov)
                        provides_map.setdefault(p_name, []).append((name, p_ver or p.get("Version", "")))

    errors = []

    # 1. Abhängigkeitsprüfung
    for name, pkg in installed_packages.items():
        errs = check_package_dependencies(installed_packages, provides_map, pkg, arch_context="bootstrap")
        errors.extend(errs)

    # 2. Verbotsliste prüfen
    forbidden_set = read_forbidden_list(forbidden_path)
    for forbidden_pkg in forbidden_set:
        if forbidden_pkg in installed_packages:
            errors.append(f"FEHLER: {forbidden_pkg} bootstrap: Paket steht auf der Verbotsliste für den Bootstrap")

    # 3. Obergrenze Paketzahl (Warnung)
    pkg_count = len(installed_packages)
    print(f"Bootstrap enthält {pkg_count} installierte Pakete.")
    if pkg_count > max_packages:
        print(f"WARNUNG: Paketzahl {pkg_count} übersteigt Richtwert ({max_packages}) für minimalen Bootstrap.")

    return errors


def run_repo_check(repo_path, large_packages_path=None):
    """
    Prüft alle Pakete in einem APT-Repository auf Erfüllbarkeit ihrer Abhängigkeiten.
    """
    errors = []
    # 1. Große Pakete aus large-packages.txt laden
    large_pkgs = {}
    large_cand_paths = []
    if large_packages_path:
        large_cand_paths.append(large_packages_path)
    large_cand_paths.extend([
        os.path.join(repo_path, "large-packages.txt"),
        os.path.join(repo_path, "../large-packages.txt"),
    ])

    for lp_path in large_cand_paths:
        if os.path.isfile(lp_path):
            with open(lp_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    parts = line.split("|")
                    if len(parts) >= 3:
                        p_name, p_ver, p_arch = parts[0], parts[1], parts[2]
                        large_pkgs.setdefault(p_arch, {})[p_name] = {
                            "Package": p_name,
                            "Version": p_ver,
                            "Architecture": p_arch,
                        }
            break

    # 2. Packages-Dateien suchen
    packages_files = {}  # arch -> list of packages
    arch_dirs = []

    # Suche dists/*/binary-* oder direkte Packages-Dateien
    for root, dirs, files in os.walk(repo_path):
        for file in files:
            if file == "Packages" or file == "Packages.gz":
                arch = None
                parent = os.path.basename(root)
                if parent.startswith("binary-"):
                    arch = parent.split("-", 1)[1]
                else:
                    m = re.search(r"binary-([a-zA-Z0-9_]+)", root)
                    if m:
                        arch = m.group(1)
                if arch:
                    fpath = os.path.join(root, file)
                    if fpath.endswith(".gz") and os.path.exists(fpath[:-3]):
                        continue  # Bevorzuge unkomprimiertes Packages
                    arch_dirs.append((arch, fpath))

    if not arch_dirs:
        # Fallback: Suche .deb-Dateien im Pool, falls Packages noch nicht gebaut
        errors.append(f"FEHLER: Keine Packages-Indexdateien in '{repo_path}' gefunden.")
        return errors

    # Gruppiere Packages nach Architektur
    for arch, fpath in arch_dirs:
        try:
            if fpath.endswith(".gz"):
                with gzip.open(fpath, "rt", encoding="utf-8", errors="replace") as f:
                    content = f.read()
            else:
                with open(fpath, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()
            pkgs = parse_dpkg_control_blocks(content)
            packages_files.setdefault(arch, []).extend(pkgs)
        except Exception as e:
            errors.append(f"FEHLER: Fehler beim Lesen von {fpath}: {e}")

    # Pro Architektur prüfen
    for arch, pkg_list in packages_files.items():
        by_name = {}
        provides_map = {}

        # a) Echte Pakete im Index
        for p in pkg_list:
            p_name = p.get("Package")
            if p_name:
                by_name[p_name] = p
                prov_field = p.get("Provides", "")
                if prov_field:
                    for prov in prov_field.split(","):
                        pr_name, _, pr_ver = parse_dependency_clause(prov)
                        provides_map.setdefault(pr_name, []).append((p_name, pr_ver or p.get("Version", "")))

        # b) Große Pakete dieser Architektur bzw. 'all'
        for l_arch in [arch, "all"]:
            for lp_name, lp_data in large_pkgs.get(l_arch, {}).items():
                if lp_name not in by_name:
                    by_name[lp_name] = lp_data

        # c) Prüfung jedes Pakets
        for p in pkg_list:
            errs = check_package_dependencies(by_name, provides_map, p, arch_context=arch)
            errors.extend(errs)

    return errors


def write_github_step_summary(title, errors, warnings=None):
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_file:
        return
    try:
        with open(summary_file, "a", encoding="utf-8") as f:
            f.write(f"\n### {title}\n\n")
            if errors:
                f.write(f"❌ **{len(errors)} Fehler gefunden:**\n\n")
                for err in errors:
                    f.write(f"- `{err}`\n")
            else:
                f.write("✅ **Konsistenzprüfung erfolgreich (keine Fehler).**\n")
            if warnings:
                f.write("\n**Warnungen:**\n\n")
                for w in warnings:
                    f.write(f"- ⚠️ {w}\n")
            f.write("\n")
    except Exception as e:
        print(f"Hinweis: Konnte GITHUB_STEP_SUMMARY nicht schreiben: {e}", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description="Prüft APT- und Bootstrap-Abhängigkeitskonsistenz.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # bootstrap sub-command
    p_boot = subparsers.add_parser("bootstrap", help="Prüft ein Bootstrap-Image.")
    p_boot.add_argument("target", help="Pfad zum bootstrap-<arch>.zip oder entpackten Verzeichnis.")
    p_boot.add_argument("--forbidden", help="Pfad zu scripts/bootstrap-forbidden.txt.", default=None)
    p_boot.add_argument("--max-packages", type=int, default=80, help="Obergrenze empfohlener Pakete.")

    # repo sub-command
    p_repo = subparsers.add_parser("repo", help="Prüft ein APT-Repository Verzeichnis.")
    p_repo.add_argument("target", help="Pfad zum Repository (Verzeichnis mit dists/).")
    p_repo.add_argument("--large-packages", help="Pfad zur large-packages.txt.", default=None)
    p_repo.add_argument("--on-error", choices=["fail", "warn"], default=None,
                        help="Verhalten bei Fehlern ('fail'=Exit 1, 'warn'=Exit 0).")

    args = parser.parse_args()

    # Default-Pfade auflösen falls vorhanden
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_forbidden = os.path.join(script_dir, "bootstrap-forbidden.txt")

    if args.command == "bootstrap":
        forbidden_file = args.forbidden or (default_forbidden if os.path.isfile(default_forbidden) else None)
        errors = run_bootstrap_check(args.target, forbidden_file, args.max_packages)

        write_github_step_summary(f"Bootstrap Konsistenzprüfung: {os.path.basename(args.target)}", errors)

        if errors:
            print("\nKonsistenzprüfung des Bootstraps FEHLGESCHLAGEN:")
            for err in errors:
                print(f"  {err}", file=sys.stderr)
            sys.exit(1)
        else:
            print("OK: Bootstrap-Abhängigkeiten sind vollständig und konsistent.")
            sys.exit(0)

    elif args.command == "repo":
        errors = run_repo_check(args.target, args.large_packages)
        on_error = args.on_error or os.environ.get("CONSISTENCY_CHECK", "fail").lower()

        write_github_step_summary(f"APT-Repo Konsistenzprüfung: {os.path.basename(args.target)}", errors)

        if errors:
            print(f"\nKonsistenzprüfung des Repositories ergab {len(errors)} Fehler:")
            for err in errors:
                print(f"  {err}", file=sys.stderr)
            if on_error == "warn":
                print("\nWARNUNG: Konsistenzfehler werden ignoriert (CONSISTENCY_CHECK=warn).", file=sys.stderr)
                sys.exit(0)
            else:
                sys.exit(1)
        else:
            print("OK: Alle Paket-Abhängigkeiten im APT-Repository sind konsistent und erfüllbar.")
            sys.exit(0)


if __name__ == "__main__":
    main()
