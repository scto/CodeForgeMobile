#!/usr/bin/env python3
"""Prüft die Locale-Dateien von :core:resources (values = Englisch, values-de = Deutsch).

Aufruf (Projekt-Root):
  python3 scripts/check_string_locales.py            # Bericht, Exit 1 bei Fehlern
  python3 scripts/check_string_locales.py --german-baseline <git-ref>
      # zusätzlich: values-de muss inhaltlich dem alten values/strings.xml in <git-ref> entsprechen

Geprüft: wohlgeformtes XML, keine doppelten Namen, gleiche Schlüsselmenge (<string>, <plurals>),
gleiche Format-Platzhalter (%1$s, %2$d, %%, \\n ...) je Schlüssel, deutsche Reste in values/ (Heuristik).
"""
import re, subprocess, sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path("core/resources/src/main/res")
EN, DE = RES / "values/strings.xml", RES / "values-de/strings.xml"
PH = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\\n|\\'|\\\"|\\@")
GERMAN_HINT = re.compile(r"[äöüÄÖÜß]|\b(und|oder|nicht|der|die|das|ein|eine|wird|werden|fehlgeschlagen|"
                         r"Datei|Ordner|Einstellungen|Abbrechen|Speichern|Löschen|Fehler|Neu|Zurück)\b")

def load(path_or_text, is_text=False):
    root = ET.fromstring(path_or_text) if is_text else ET.parse(path_or_text).getroot()
    out, dups = {}, []
    for el in root:
        if el.tag not in ("string", "plurals", "string-array"):
            continue
        name = el.get("name")
        if name in out:
            dups.append(name)
        if el.tag == "string":
            out[name] = "".join(el.itertext())
        else:
            out[name] = "\n".join("".join(i.itertext()) for i in el)
    return out, dups

def main():
    errors = []
    en, d1 = load(EN); de, d2 = load(DE)
    errors += [f"doppelt in values: {n}" for n in d1] + [f"doppelt in values-de: {n}" for n in d2]
    errors += [f"fehlt in values-de: {k}" for k in sorted(en.keys() - de.keys())]
    errors += [f"fehlt in values: {k}" for k in sorted(de.keys() - en.keys())]
    for k in sorted(en.keys() & de.keys()):
        if sorted(PH.findall(en[k])) != sorted(PH.findall(de[k])):
            errors.append(f"Platzhalter/Escapes verschieden: {k}: en={PH.findall(en[k])} de={PH.findall(de[k])}")
    same = [k for k in en if k in de and en[k] == de[k] and re.search(r"[A-Za-z]{4,}", en[k])]
    german = [k for k, v in en.items() if GERMAN_HINT.search(v)]
    if "--german-baseline" in sys.argv:
        ref = sys.argv[sys.argv.index("--german-baseline") + 1]
        old = subprocess.run(["git", "show", f"{ref}:{EN.as_posix()}"], capture_output=True, text=True, check=True).stdout
        base, _ = load(old, True)
        for k, v in base.items():
            if de.get(k) != v:
                errors.append(f"values-de weicht vom deutschen Original ab: {k}")
    print(f"values: {len(en)} Einträge, values-de: {len(de)} Einträge")
    print(f"Hinweis (kein Fehler) – identisch in en/de, evtl. unübersetzt ({len(same)}): {same[:25]}")
    print(f"Hinweis (kein Fehler) – deutsche Reste in values/ vermutet ({len(german)}): {german[:25]}")
    for e in errors:
        print("FEHLER:", e)
    sys.exit(1 if errors else 0)

main()
