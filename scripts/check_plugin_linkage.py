#!/usr/bin/env python3
"""
Prüft, ob die Code-Plugins gegen eine per R8 geschrumpfte App linken können.

Plugins enthalten nur ihre eigenen Klassen; Kotlin-Stdlib, Coroutines, Compose und das Plugin-SDK kommen zur Laufzeit
aus der App. Benennt R8 dort etwas um oder entfernt es, lädt das Plugin im Release-Build nicht. Das Skript vergleicht
die Referenzen jedes Plugin-Dex mit den Klassen und Mitgliedern im Dex der App.

Aufruf (nach `./gradlew :app:minifyReleaseWithR8` und `packageAll` der Code-Plugins):
    python3 scripts/check_plugin_linkage.py [app-dex-ordner] [plugin-zips…]
"""
import glob
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PREFIXES = ("Lkotlin/", "Lkotlinx/", "Landroidx/compose/", "Lcom/codeforge/")


def dexdump() -> str:
    tools = sorted(glob.glob("/opt/android-sdk/build-tools/*/dexdump") + glob.glob(str(Path.home() / "Android/Sdk/build-tools/*/dexdump")))
    if not tools:
        sys.exit("dexdump nicht gefunden (Android build-tools)")
    return tools[-1]


def dump(dex: Path, disassemble: bool = False) -> str:
    args = [dexdump()] + (["-d"] if disassemble else []) + [str(dex)]
    return subprocess.run(args, capture_output=True, text=True, errors="ignore").stdout


def app_model(dex_dir: Path):
    members, supers, ifaces = {}, {}, {}
    cls = None
    in_ifaces = False
    for dex in sorted(dex_dir.glob("*.dex")):
        for line in dump(dex).splitlines():
            if m := re.match(r"\s*Class descriptor\s*: '(L[^']+;)'", line):
                cls = m.group(1)
                members.setdefault(cls, set())
                in_ifaces = False
            elif cls and (m := re.match(r"\s*Superclass\s*: '(L[^']+;)'", line)):
                supers[cls] = m.group(1)
            elif cls and re.match(r"\s*Interfaces\s*-", line):
                in_ifaces = True
            elif cls and in_ifaces and (m := re.match(r"\s*#\d+\s*: '(L[^']+;)'", line)):
                ifaces.setdefault(cls, []).append(m.group(1))
            elif cls and (m := re.match(r"\s*name\s*: '([^']+)'", line)):
                members[cls].add(m.group(1))
                in_ifaces = False
    return members, supers, ifaces


def has_member(model, cls: str, name: str) -> bool:
    members, supers, ifaces = model
    todo, seen = [cls], set()
    while todo:
        c = todo.pop()
        if c in seen:
            continue
        seen.add(c)
        if name in members.get(c, ()):
            return True
        if c in supers:
            todo.append(supers[c])
        todo.extend(ifaces.get(c, []))
    # Klassen außerhalb der App (Android, Java) nicht prüfbar
    return any(c not in members for c in seen)


def main() -> int:
    dex_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/build/intermediates/dex/release/minifyReleaseWithR8"
    zips = [Path(p) for p in sys.argv[2:]] or sorted((ROOT / "code-plugins/out").glob("*/*.zip"))
    if not any(dex_dir.glob("*.dex")):
        sys.exit(f"Kein App-Dex unter {dex_dir} – erst ./gradlew :app:minifyReleaseWithR8")
    model = app_model(dex_dir)
    failed = 0
    for z in zips:
        with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(z) as zf:
            if "classes.dex" not in zf.namelist():
                continue
            zf.extract("classes.dex", tmp)
            text = dump(Path(tmp) / "classes.dex", disassemble=True)
        own = set(re.findall(r"Class descriptor\s*: '(L[^']+;)'", text))
        missing = set()
        for c, m in set(re.findall(r"(L[\w/$]+;)\.([\w$<>-]+):", text)):
            if c in own or not c.startswith(PREFIXES):
                continue
            if c not in model[0]:
                missing.add(f"Klasse fehlt: {c}")
            elif m != "<init>" and not has_member(model, c, m):
                missing.add(f"Mitglied fehlt: {c}.{m}")
        status = "OK" if not missing else f"{len(missing)} fehlen"
        print(f"{z.name}: {status}")
        for x in sorted(missing)[:20]:
            print("   ", x)
        failed += bool(missing)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
