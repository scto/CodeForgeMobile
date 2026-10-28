#!/usr/bin/env python3
"""Hartcodierte Abhängigkeiten ("g:a:v") -> gradle/libs.versions.toml. Eingabe: 'check' (Standard) oder 'apply'."""
import os, re
from common import args, walk, read, write, rel

CONFIGS = r"(?:api|implementation|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|androidTestImplementation|debugImplementation|releaseImplementation|annotationProcessor|kapt|ksp)"
# Kotlin-DSL: implementation("g:a:v")   Groovy: implementation 'g:a:v'  /  implementation "g:a:v"
KTS = re.compile(rf'\b({CONFIGS})\(\s*"([\w.\-]+):([\w.\-]+):([\w.\-+]+)"\s*\)')
GROOVY = re.compile(rf"\b({CONFIGS})\s+['\"]([\w.\-]+):([\w.\-]+):([\w.\-+]+)['\"]")


def alias_of(group, artifact):
    a = re.sub(r"[^a-z0-9]+", "-", artifact.lower()).strip("-")
    return a


def main():
    project, mode = args()
    apply = mode.lower() == "apply"
    toml_path = os.path.join(project, "gradle", "libs.versions.toml")
    toml = read(toml_path) if os.path.isfile(toml_path) else ""
    existing = {}
    for m in re.finditer(r'^\s*([\w-]+)\s*=\s*\{[^}]*module\s*=\s*"([^"]+)"', toml, re.M):
        existing[m.group(2)] = m.group(1)
    versions, libs, used_alias = {}, {}, set(existing.values())
    plan = []
    for path in walk(project, names={"build.gradle", "build.gradle.kts"}):
        text = read(path)
        kts = path.endswith(".kts")
        pattern = KTS if kts else GROOVY
        if not pattern.search(text):
            continue

        def repl(m):
            cfg, g, a, v = m.groups()
            module = f"{g}:{a}"
            if module in existing:
                alias = existing[module]
            else:
                alias = alias_of(g, a)
                if alias in used_alias and libs.get(alias, (None,))[0] != module:
                    alias = f"{g.split('.')[-1]}-{alias}"
                used_alias.add(alias)
                libs[alias] = (module, v)
                existing[module] = alias
            ref = "libs." + alias.replace("-", ".")
            return f"{cfg}({ref})" if kts else f"{cfg} {ref}"

        new = pattern.sub(repl, text)
        if new != text:
            plan.append((path, new))
    if not plan:
        print("Keine hartcodierten Abhängigkeiten gefunden.")
        return
    print(f"{'Geändert' if apply else 'Würden geändert'}: {len(plan)} Build-Dateien")
    for p, _ in plan:
        print("  " + rel(p, project))
    print("\nNeue Katalog-Einträge:")
    for alias, (module, v) in sorted(libs.items()):
        print(f'  {alias} = {{ module = "{module}", version = "{v}" }}')
    if apply:
        add = "".join(f'{alias} = {{ module = "{module}", version = "{v}" }}\n' for alias, (module, v) in sorted(libs.items()))
        if "[libraries]" in toml:
            toml = toml.replace("[libraries]\n", "[libraries]\n" + add, 1)
        else:
            toml = toml + ("\n" if toml and not toml.endswith("\n") else "") + "\n[libraries]\n" + add
        os.makedirs(os.path.dirname(toml_path), exist_ok=True)
        write(toml_path, toml)
        for p, new in plan:
            write(p, new)
        print("\nFertig. Bitte Gradle-Sync/Build ausführen und Ergebnis prüfen.")
    else:
        print("\nMit Eingabe 'apply' erneut starten, um zu schreiben (vorher committen!).")


main()
