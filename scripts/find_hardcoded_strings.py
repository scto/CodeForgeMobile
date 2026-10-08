#!/usr/bin/env python3
"""
Sucht sichtbare, hart kodierte Texte in Kotlin-Quellen (Heuristik, nur lesend).

Aufruf (Projekt-Root):
  python3 scripts/find_hardcoded_strings.py            # Bericht
  python3 scripts/find_hardcoded_strings.py --json     # maschinenlesbar (für agy)
  python3 scripts/find_hardcoded_strings.py --check    # Exit 1 bei neuen Funden (Pre-commit/CI)
  python3 scripts/find_hardcoded_strings.py --update-allowlist   # aktuelle Funde als „bewusst" übernehmen

Erkennt Literale in Compose-/UI-Kontexten (Text(...), text=, title=, label=, placeholder=, contentDescription=,
supportingText=, confirmText=, dismissText=, message=, hint=, snack(...), UiText.of(...), Toast).
Ignoriert: Tests, :core:resources, libs/, build/, Kommentare, Log-/TAG-/const-Zeilen, Regex, Pfade, reine Bezeichner.
Allowlist: scripts/hardcoded-strings.allowlist (Zeile = `<Datei>|<Text>`; `#` = Kommentar).
"""
import argparse, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCAN_DIRS = ["app", "feature", "core"]
SKIP_PARTS = {"build", "test", "androidTest", "resources", ".gradle"}
SKIP_PREFIX = ("core/resources/", "core/testing/")
ALLOWLIST = os.path.join(ROOT, "scripts", "hardcoded-strings.allowlist")

# Kontexte, in denen ein Literal fast sicher sichtbar ist
CTX = re.compile(
    r'(?:\b(?:text|title|label|placeholder|contentDescription|supportingText|hint|message|subtitle|headline|'
    r'confirmText|dismissText|description|summary|error|errorMessage|emptyText|tooltip)\s*=\s*'
    r'|\b(?:Text|Toast\.makeText|snack|showSnackbar|UiText\.of|UiText\.Plain|TextButton|stringOrNull)\s*\(\s*'
    r'|\bIcon\s*\([^)]*?,\s*)'
    r'"((?:[^"\\]|\\.)*)"')
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')

IGNORE_LINE = re.compile(
    r'^\s*(?://|\*|/\*|import |package )|\bLog\.[a-z]\(|\bTAG\b|\bconst\s+val\b|@Suppress|@Preview|Regex\(|\.toRegex\(|'
    r'\bR\.string\.|stringRes\(|Res\.string\(|\bcontentType\b|\btestTag\b|\bcheck\(|\brequire\(|\berror\("|\bthrow\b|'
    r'\bTODO\(|\bassert')


def looks_visible(s: str) -> bool:
    t = re.sub(r"\$\{[^}]*\}?|\$\w+", "", s.replace("\\n", " ")).strip()   # Template-Teile zählen nicht als Text
    if len(t) < 2 or not re.search(r"[A-Za-zÄÖÜäöüß]{2}", t):
        return False
    if re.fullmatch(r"[\w./:\-@$%{}#]+", t) and " " not in t:       # Bezeichner, Pfade, URLs, Keys
        if not re.match(r"[A-ZÄÖÜ][a-zäöüß]{2,}", t):                 # „Abbrechen" soll trotzdem zählen
            return False
    if re.fullmatch(r"[\W\d_]*", t):
        return False
    if t.startswith(("http", "/", "#", ".", "$", "@")) and " " not in t:
        return False
    return True


def iter_files():
    for d in SCAN_DIRS:
        for base, dirs, files in os.walk(os.path.join(ROOT, d)):
            dirs[:] = [x for x in dirs if x not in SKIP_PARTS]
            for f in files:
                if f.endswith(".kt"):
                    p = os.path.join(base, f)
                    rel = os.path.relpath(p, ROOT).replace(os.sep, "/")
                    if rel.startswith(SKIP_PREFIX) or "/src/test/" in rel or "/src/androidTest/" in rel:
                        continue
                    yield p, rel


def scan():
    found = []
    for path, rel in iter_files():
        in_block = False
        for no, line in enumerate(open(path, encoding="utf8", errors="ignore"), 1):
            st = line.strip()
            if in_block:
                if "*/" in st:
                    in_block = False
                continue
            if st.startswith("/*") and "*/" not in st:
                in_block = True
                continue
            if IGNORE_LINE.search(line):
                continue
            for m in CTX.finditer(line):
                lit = m.group(1)
                if looks_visible(lit):
                    found.append({"file": rel, "line": no, "text": lit, "code": st[:140]})
    return found


def load_allow():
    if not os.path.exists(ALLOWLIST):
        return set()
    return {l.rstrip("\n") for l in open(ALLOWLIST, encoding="utf8") if l.strip() and not l.startswith("#")}


def key(f):
    return f"{f['file']}|{f['text']}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--update-allowlist", action="store_true")
    a = ap.parse_args()
    allow = load_allow()
    res = [f for f in scan() if key(f) not in allow]
    if a.update_allowlist:
        keys = sorted(allow | {key(f) for f in res})
        with open(ALLOWLIST, "w", encoding="utf8") as fh:
            fh.write("# Bewusst belassene Literale: <Datei>|<Text>. Mit --update-allowlist ergänzt; Einträge nur mit Begründung behalten.\n")
            fh.write("\n".join(keys) + "\n")
        print(f"Allowlist: {len(keys)} Einträge")
        return 0
    if a.json:
        print(json.dumps(res, ensure_ascii=False, indent=1))
    else:
        by = {}
        for f in res:
            by.setdefault(f["file"], []).append(f)
        for fn in sorted(by):
            print(fn)
            for f in by[fn]:
                print(f"  {f['line']:>5}: \"{f['text']}\"")
        print(f"\n{len(res)} mögliche harte UI-Texte in {len(by)} Dateien")
    return 1 if (a.check and res) else 0


if __name__ == "__main__":
    sys.exit(main())
