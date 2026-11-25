#!/usr/bin/env python3
"""Baut das Compile-SDK für eigenständige Code-Plugin-Projekte (`codeforge-plugin-sdk.jar`).

Inhalt: alle Klassen von :libs:plugin-api und die davon (transitiv) erreichbaren Klassen aus :core:domain und
:libs:terminal-engine, samt `META-INF/*.kotlin_module`. Nur zum Kompilieren (`compileOnly`); zur Laufzeit stellt die
App diese Klassen bereit.

Vorher:  ./gradlew :libs:plugin-api:bundleLibCompileToJarRelease :core:domain:bundleLibCompileToJarRelease \
             :libs:terminal-engine:bundleLibCompileToJarRelease
Aufruf:  python3 scripts/build_plugin_sdk.py <ziel.jar>
"""
import re
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = "build/intermediates/compile_library_classes_jar/release/bundleLibCompileToJarRelease/classes.jar"
API = ROOT / "libs/plugin-api" / JAR
DEPS = [ROOT / "core/domain" / JAR, ROOT / "libs/terminal-engine" / JAR]
FIXED_DATE = (2026, 1, 1, 0, 0, 0)
NAME = re.compile(r"com/codeforge/[A-Za-z0-9_/$]+")


def referenced(data):
    """Klassennamen aus dem Konstantenpool (Class-Einträge und alle UTF-8-Strings, auch Kotlin-Metadaten)."""
    count = struct.unpack(">H", data[8:10])[0]
    pos, i, out = 10, 1, set()
    while i < count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            text = data[pos + 3:pos + 3 + length].decode("utf-8", "replace")
            out.update(NAME.findall(text))
            pos += 3 + length
        elif tag in (7, 8, 16, 19, 20):
            pos += 3
        elif tag == 15:
            pos += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        else:
            raise ValueError(f"Unbekannter Konstantenpool-Tag {tag}")
        i += 1
    return out


def load(jar):
    with zipfile.ZipFile(jar) as z:
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def main(target):
    for jar in [API, *DEPS]:
        if not jar.is_file():
            raise SystemExit(f"Fehlt: {jar.relative_to(ROOT)} (Gradle-Aufruf siehe Kopf dieses Skripts)")
    api = load(API)
    deps = {}
    for jar in DEPS:
        deps.update(load(jar))
    classes = {n[:-6]: b for n, b in deps.items() if n.endswith(".class")}
    todo = [b for n, b in api.items() if n.endswith(".class")]
    keep = set()
    while todo:
        for name in referenced(todo.pop()):
            # Auch äußere Klassen (Foo$Bar → Foo) mitnehmen, sonst fehlen Kotlin dem Compiler die Metadaten.
            for candidate in {name, name.split("$")[0]}:
                if candidate in classes and candidate not in keep:
                    keep.add(candidate)
                    todo.append(classes[candidate])
    entries = {n: b for n, b in api.items() if n.endswith(".class") or n.endswith(".kotlin_module")}
    entries.update({f"{k}.class": classes[k] for k in keep})
    entries.update({n: b for n, b in deps.items() if n.endswith(".kotlin_module")})
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, FIXED_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, entries[name])
    print(f"{target}: {len(entries)} Einträge, {target.stat().st_size // 1024} KiB")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
