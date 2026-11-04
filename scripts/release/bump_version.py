#!/usr/bin/env python3
"""Version hochzählen und Release Notes erzeugen.

Was das Skript tut (alles in einem Lauf):
  1. liest versionName/versionCode aus app/build.gradle.kts,
  2. berechnet die neue Version (patch | minor | major | X.Y.Z) und den Code
     (MAJOR*10000 + MINOR*100 + PATCH, 3.0.1 -> 30001),
  3. schreibt beide Werte zurück,
  4. verschiebt den Abschnitt „[Unreleased]“ der CHANGELOG.md in „[X.Y.Z] - Datum“ und legt einen
     leeren „[Unreleased]“ an (ist er leer, werden die Einträge aus den Commits seit dem letzten
     Tag erzeugt, Conventional Commits),
  5. erzeugt docs/release-notes/X.Y.Z.md (Deutsch) und X.Y.Z.en.md (Englisch),
  6. trägt die Notes in mkdocs.yml ein und zieht die Versionsangaben in README, STATUS, Doku nach.

Beispiele:
  python3 scripts/release/bump_version.py patch              # 3.0.1 -> 3.0.2
  python3 scripts/release/bump_version.py minor --dry-run    # nur anzeigen
  python3 scripts/release/bump_version.py 3.1.0 --date 2026-12-01
  python3 scripts/release/bump_version.py --notes-only 3.0.1 # Notes aus CHANGELOG neu erzeugen

Nur Python-Standardbibliothek. Die englischen Notes übernehmen die Texte aus dem CHANGELOG (deutsch)
und markieren sie mit einem TODO-Kommentar; die Überschriften und der Rahmen sind englisch.
"""
from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

GRADLE = "app/build.gradle.kts"
CHANGELOG = "CHANGELOG.md"
NOTES_DIR = "docs/release-notes"
MKDOCS = "mkdocs.yml"

SEMVER = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
CODE_RE = re.compile(r"(versionCode\s*=\s*)(\d+)")
NAME_RE = re.compile(r'(versionName\s*=\s*")([^"]+)(")')

# Kategorie im CHANGELOG -> (deutsche Überschrift, englische Überschrift), Reihenfolge der Ausgabe
CATEGORIES = [
    ("Hinzugefügt", "Neu", "New"),
    ("Geändert", "Geändert", "Changed"),
    ("Behoben", "Fixes", "Fixes"),
    ("Entfernt", "Entfernt", "Removed"),
    ("Sicherheit", "Sicherheit", "Security"),
]


# ---------------------------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------------------------
def parse_version(text: str) -> tuple[int, int, int]:
    m = SEMVER.match(text.strip())
    if not m:
        raise SystemExit(f"Ungültige Version: {text!r} (erwartet MAJOR.MINOR.PATCH)")
    return int(m[1]), int(m[2]), int(m[3])


def version_code(v: tuple[int, int, int]) -> int:
    major, minor, patch = v
    if minor > 99 or patch > 99:
        raise SystemExit("MINOR und PATCH müssen ≤ 99 sein (Schema MAJOR*10000 + MINOR*100 + PATCH)")
    return major * 10000 + minor * 100 + patch


def next_version(cur: tuple[int, int, int], how: str) -> tuple[int, int, int]:
    major, minor, patch = cur
    if how == "patch":
        return major, minor, patch + 1
    if how == "minor":
        return major, minor + 1, 0
    if how == "major":
        return major + 1, 0, 0
    return parse_version(how)


def fmt(v: tuple[int, int, int]) -> str:
    return "%d.%d.%d" % v


def read_current(root: Path) -> tuple[tuple[int, int, int], int]:
    text = (root / GRADLE).read_text(encoding="utf-8")
    name, code = NAME_RE.search(text), CODE_RE.search(text)
    if not name or not code:
        raise SystemExit(f"versionName/versionCode nicht gefunden in {GRADLE}")
    return parse_version(name[2]), int(code[2])


# ---------------------------------------------------------------------------------------------
# CHANGELOG
# ---------------------------------------------------------------------------------------------
def split_changelog(text: str) -> tuple[str, str, str]:
    """Teilt in (Kopf bis einschließlich '## [Unreleased]', Unreleased-Rumpf, Rest ab nächstem '## [')."""
    m = re.search(r"^## \[Unreleased\][^\n]*\n", text, re.M)
    if not m:
        raise SystemExit("Abschnitt '## [Unreleased]' fehlt in CHANGELOG.md")
    nxt = re.search(r"^## \[", text[m.end():], re.M)
    end = m.end() + nxt.start() if nxt else len(text)
    return text[: m.end()], text[m.end(): end], text[end:]


def section_body(text: str, version: str) -> str:
    m = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n", text, re.M)
    if not m:
        raise SystemExit(f"Abschnitt '## [{version}]' fehlt in CHANGELOG.md")
    nxt = re.search(r"^## \[", text[m.end():], re.M)
    return text[m.end(): m.end() + nxt.start() if nxt else len(text)]


def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, check=False).stdout.strip()


def changelog_from_git(root: Path) -> str:
    """Erzeugt CHANGELOG-Einträge aus Conventional Commits seit dem letzten Versions-Tag."""
    last = git(root, "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
    rng = f"{last}..HEAD" if last else "HEAD"
    log = git(root, "log", rng, "--no-merges", "--pretty=format:%s")
    groups: dict[str, list[str]] = {"Hinzugefügt": [], "Geändert": [], "Behoben": []}
    for subject in filter(None, log.splitlines()):
        if re.match(r"^(Merge|Upgrade|Optimize images|Update README)", subject):
            continue
        m = re.match(r"^(\w+)(?:\(([^)]+)\))?!?:\s*(.+)$", subject)
        kind, scope, msg = (m[1], m[2], m[3]) if m else ("chore", None, subject)
        cat = "Hinzugefügt" if kind == "feat" else "Behoben" if kind == "fix" else "Geändert"
        groups[cat].append(f"{scope}: {msg}" if scope else msg)
    out = []
    for cat, items in groups.items():
        if items:
            out.append(f"### {cat}\n" + "\n".join(f"- {i}" for i in items) + "\n")
    return "\n" + "\n".join(out) if out else ""


# ---------------------------------------------------------------------------------------------
# Release Notes
# ---------------------------------------------------------------------------------------------
def first_sentence(text: str, limit: int = 260) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    for abbr in ("z. B.", "d. h.", "u. a.", "u. U.", "bzw.", "inkl.", "ca.", "e. g.", "i. e."):
        text = text.replace(abbr, abbr.replace(".", "\u2024").replace(" ", "\u00a0"))
    m = re.search(r"(?<=[.!?])\s+(?=[A-ZÄÖÜ„*`(])", text)
    s = text[: m.start()] if m else text
    s = s.replace("\u2024", ".").replace("\u00a0", " ")
    if len(s) > limit:
        s = s[: limit].rsplit(" ", 1)[0] + " …"
    return s


def parse_entries(body: str) -> dict[str, list[tuple[str, str]]]:
    """CHANGELOG-Rumpf -> {Kategorie: [(Bereich, Text)]}. Unter-Aufzählungen werden verworfen."""
    result: dict[str, list[tuple[str, str]]] = {}
    cat, area = None, ""
    cur: list[str] | None = None

    def flush() -> None:
        nonlocal cur
        if cur is not None and cat:
            result.setdefault(cat, []).append((area, " ".join(cur)))
        cur = None

    for line in body.splitlines():
        h = re.match(r"^###\s+([^\s(]+)\s*(?:\((.+)\))?", line)
        if h:
            flush()
            cat, area = h[1], (h[2] or "")
        elif line.startswith("- "):
            flush()
            cur = [line[2:].strip()]
        elif cur is not None and line.startswith("  ") and line.strip() and not line.lstrip().startswith("- "):
            cur.append(line.strip())
        elif not line.strip():
            continue
        elif cur is not None and not line.startswith("  "):
            flush()
    flush()
    return result


def render_groups(entries: dict[str, list[tuple[str, str]]], lang: str) -> str:
    out: list[str] = []
    for key, de, en in CATEGORIES:
        items = entries.get(key)
        if not items:
            continue
        out.append(f"### {en if lang == 'en' else de}\n")
        for area, text in items:
            label = f"**{area}:** " if area else ""
            out.append(f"- {label}{first_sentence(text)}")
        out.append("")
    return "\n".join(out).rstrip() + "\n"


def previous_notes(root: Path, version: str, lang: str) -> tuple[str, Path] | None:
    suffix = ".en.md" if lang == "en" else ".md"
    cands = []
    for p in (root / NOTES_DIR).glob("*" + suffix):
        name = p.name[: -len(suffix)]
        if SEMVER.match(name) and name != version and (lang == "en" or not p.name.endswith(".en.md")):
            cands.append((parse_version(name), p))
    return (max(cands)[1].read_text(encoding="utf-8"), max(cands)[1]) if cands else None


def carry_over(prev: str | None, lang: str) -> tuple[str, str, str]:
    """Übernimmt Intro, Warnhinweis und den Block ab 'Installation' aus den vorherigen Notes."""
    de_intro = "Android-Entwicklungsumgebung auf dem Handy: Editor, Terminal, Git, Gradle-Build, Language Server, Layout-Designer und Asset Studio – alles in einer App."
    en_intro = "Android development environment on your phone: editor, terminal, Git, Gradle build, language servers, layout designer and Asset Studio – all in one app."
    de_warn = "> ⚠️ **Vorabversion / Debug-APK.** Das Release ist mit dem Android-Debug-Schlüssel signiert und noch nicht systematisch auf Geräten getestet (Checkliste: `docs/device-test-checklist.md`). Bitte Fehler als Issue melden."
    en_warn = "> ⚠️ **Pre-release / debug APK.** Signed with the Android debug key and not yet systematically tested on devices (checklist: `docs/device-test-checklist.md`). Please report bugs as issues."
    de_tail = "## Installation\n\n1. APK `app-debug.apk` herunterladen und Prüfsumme mit `SHA256SUMS.txt` vergleichen.\n2. Installieren (Installation aus unbekannten Quellen erlauben).\n3. Beim ersten Start dem Onboarding folgen.\n\n## Lizenz\n\nGPLv3 (wegen der enthaltenen Termux-Module). Details: `LICENSE`, `docs/sub/NOTICE.md`.\n"
    en_tail = "## Installation\n\n1. Download `app-debug.apk` and compare the checksum with `SHA256SUMS.txt`.\n2. Install it (allow installation from unknown sources).\n3. Follow the onboarding on first start.\n\n## License\n\nGPLv3 (because of the bundled Termux modules). Details: `LICENSE`, `docs/sub/NOTICE.md`.\n"
    intro, warn, tail = (en_intro, en_warn, en_tail) if lang == "en" else (de_intro, de_warn, de_tail)
    if prev:
        paras = [p for p in prev.split("\n\n") if p.strip()]
        if len(paras) > 1 and not paras[1].startswith(("#", ">")):
            intro = paras[1].strip()
        w = re.search(r"^> ⚠️.*(?:\n>.*)*", prev, re.M)
        if w:
            warn = w[0]
        t = re.search(r"^## Installation", prev, re.M)
        f = re.search(r"^---\s*$", prev, re.M)
        if t and f and t.start() < f.start():
            tail = prev[t.start(): f.start()].rstrip() + "\n"
    return intro, warn, tail


def build_notes(root: Path, version: str, prev_version: str, body: str, lang: str) -> str:
    prev = previous_notes(root, version, lang)
    intro, warn, tail = carry_over(prev[0] if prev else None, lang)
    entries = parse_entries(body)
    groups = render_groups(entries, lang)
    if lang == "en":
        head = (
            f"# CodeForge Mobile {version}\n\n{intro}\n\n{warn}\n\n"
            f"<!-- TODO: bullets are copied from CHANGELOG.md (German) – translate before release. -->\n\n"
            f"## Changes since {prev_version}\n\n{groups}\n"
        )
        foot = "\n---\n**Full changelog:** [`CHANGELOG.md`](../../CHANGELOG.md)\n"
    else:
        head = f"# CodeForge Mobile {version}\n\n{intro}\n\n{warn}\n\n## Änderungen seit {prev_version}\n\n{groups}\n"
        foot = "\n---\n**Vollständiges Änderungsprotokoll:** [`CHANGELOG.md`](../../CHANGELOG.md)\n"
    return head + tail + foot


# ---------------------------------------------------------------------------------------------
# Nachziehen weiterer Stellen
# ---------------------------------------------------------------------------------------------
def sub_file(root: Path, rel: str, pattern: str, repl: str, dry: bool, flags: int = 0) -> bool:
    p = root / rel
    if not p.exists():
        return False
    text = p.read_text(encoding="utf-8")
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n and new != text and not dry:
        p.write_text(new, encoding="utf-8")
    return bool(n and new != text)


def update_references(root: Path, old: str, new: str, code: int, dry: bool) -> list[str]:
    changed: list[str] = []
    o = re.escape(old)
    jobs = [
        ("README.md", rf"Version-{o}-blueviolet", f"Version-{new}-blueviolet"),
        ("README_DE.md", rf"Version-{o}-blueviolet", f"Version-{new}-blueviolet"),
        ("STATUS.md", rf"(\*\*Version:\*\* ){o}( \(`versionCode )\d+(`\))", rf"\g<1>{new}\g<2>{code}\g<3>"),
        ("docs/apk/APK-Dokumentation.md", rf"(\| Version \| `){o}(` \(`versionCode` )\d+", rf"\g<1>{new}\g<2>{code}"),
        ("docs/apk/APK-Dokumentation.md", rf"debug-{o}-1", f"debug-{new}-1"),
        ("docs/overview.md", rf"(; Version ){o}", rf"\g<1>{new}"),
        ("docs/index.md", rf"\[Release Notes {o}\]\(release-notes/{o}\.md\)", f"[Release Notes {new}](release-notes/{new}.md)"),
        (".github/workflows/release-debug-apk.yml", rf"debug-{o}-1", f"debug-{new}-1"),
    ]
    for rel, pat, rep in jobs:
        if sub_file(root, rel, pat, rep, dry):
            changed.append(rel)
    return sorted(set(changed))


def update_mkdocs(root: Path, version: str, dry: bool) -> bool:
    p = root / MKDOCS
    if not p.exists():
        return False
    text = p.read_text(encoding="utf-8")
    if f"release-notes/{version}.md" in text:
        return False
    m = re.search(r"^(\s*)- Release Notes [^\n]*\n", text, re.M)
    if not m:
        return False
    ind = m[1]
    add = f"{ind}- Release Notes {version}: release-notes/{version}.md\n{ind}- Release Notes {version} (EN): release-notes/{version}.en.md\n"
    if not dry:
        p.write_text(text[: m.start()] + add + text[m.start():], encoding="utf-8")
    return True


# ---------------------------------------------------------------------------------------------
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("bump", nargs="?", default="patch", help="patch | minor | major | X.Y.Z (Standard: patch)")
    ap.add_argument("--date", default=dt.date.today().isoformat(), help="Datum im CHANGELOG (YYYY-MM-DD)")
    ap.add_argument("--root", default=None, help="Projektwurzel (Standard: Repository dieses Skripts)")
    ap.add_argument("--dry-run", action="store_true", help="nichts schreiben, nur anzeigen")
    ap.add_argument("--no-notes", action="store_true", help="keine Release Notes erzeugen")
    ap.add_argument("--notes-only", metavar="VERSION", help="nur Notes für bestehende CHANGELOG-Version neu erzeugen")
    ap.add_argument("--from-git", action="store_true", help="Einträge aus Commits erzeugen, auch wenn [Unreleased] gefüllt ist")
    args = ap.parse_args()

    root = Path(args.root) if args.root else Path(__file__).resolve().parents[2]
    dry = args.dry_run
    log = lambda m: print(("[dry-run] " if dry else "") + m)  # noqa: E731

    cl_path = root / CHANGELOG
    cl = cl_path.read_text(encoding="utf-8")

    if args.notes_only:
        ver = fmt(parse_version(args.notes_only))
        body = section_body(cl, ver)
        prevs = re.findall(r"^## \[(\d+\.\d+\.\d+)\]", cl, re.M)
        prev_v = next((v for v in prevs[prevs.index(ver) + 1:]), "—") if ver in prevs else "—"
        for lang, suffix in (("de", ".md"), ("en", ".en.md")):
            target = root / NOTES_DIR / f"{ver}{suffix}"
            if not dry:
                target.write_text(build_notes(root, ver, prev_v, body, lang), encoding="utf-8")
            log(f"Notes: {target.relative_to(root)}")
        update_mkdocs(root, ver, dry)
        return 0

    cur, cur_code = read_current(root)
    new = next_version(cur, args.bump)
    if new <= cur:
        raise SystemExit(f"Neue Version {fmt(new)} muss größer sein als {fmt(cur)}")
    old_s, new_s, code = fmt(cur), fmt(new), version_code(new)
    log(f"Version {old_s} ({cur_code}) -> {new_s} ({code})")

    # build.gradle.kts
    gp = root / GRADLE
    g = gp.read_text(encoding="utf-8")
    g = CODE_RE.sub(lambda m: f"{m[1]}{code}", g, count=1)
    g = NAME_RE.sub(lambda m: f'{m[1]}{new_s}{m[3]}', g, count=1)
    if not dry:
        gp.write_text(g, encoding="utf-8")
    log(f"{GRADLE}: versionCode={code}, versionName=\"{new_s}\"")

    # CHANGELOG
    head, unreleased, rest = split_changelog(cl)
    if args.from_git or not unreleased.strip():
        generated = changelog_from_git(root)
        if not generated:
            raise SystemExit("[Unreleased] ist leer und die Commits seit dem letzten Tag liefern keine Einträge.")
        unreleased = generated if not unreleased.strip() else unreleased.rstrip() + "\n" + generated
        log("CHANGELOG: Einträge aus Commits erzeugt")
    new_cl = f"{head}\n## [{new_s}] - {args.date}\n{unreleased.rstrip()}\n\n{rest}"
    if not dry:
        cl_path.write_text(new_cl, encoding="utf-8")
    log(f"CHANGELOG: [Unreleased] -> [{new_s}] - {args.date}")

    # Notes
    if not args.no_notes:
        for lang, suffix in (("de", ".md"), ("en", ".en.md")):
            target = root / NOTES_DIR / f"{new_s}{suffix}"
            if target.exists():
                log(f"Notes vorhanden, übersprungen: {target.relative_to(root)}")
                continue
            if not dry:
                target.write_text(build_notes(root, new_s, old_s, unreleased, lang), encoding="utf-8")
            log(f"Notes: {target.relative_to(root)}")
        if update_mkdocs(root, new_s, dry):
            log("mkdocs.yml: Navigation ergänzt")

    for rel in update_references(root, old_s, new_s, code, dry):
        log(f"Version nachgezogen: {rel}")

    print(f"\nFertig: {new_s} (versionCode {code}). Als Nächstes: Notes prüfen, committen, Tag setzen:")
    print(f"  git add -A && git commit -m 'chore(release): {new_s}' && git tag v{new_s}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
