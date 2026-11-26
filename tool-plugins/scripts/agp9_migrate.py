#!/usr/bin/env python3
"""Migration auf Android Gradle Plugin 9.0.

Eingabe: '<modus> [agp-version]' – Modus 'check' (Standard, nur Bericht) oder 'apply' (Dateien ändern),
AGP-Version optional (Standard 9.0.1, muss 9.x sein).

Umgestellt (apply):
  * AGP-Version (Version Catalog, plugins-Block, buildscript-classpath), Gradle-Wrapper >= 9.1.0,
    Kotlin (KGP) >= 2.2.10, buildToolsVersion >= 36.0.0
  * eingebautes Kotlin: Plugin org.jetbrains.kotlin.android / kotlin-android entfernen (Module, Wurzel, Catalog)
  * android { kotlinOptions { … } } -> kotlin { compilerOptions { … } } (jvmTarget, freeCompilerArgs,
    languageVersion, apiVersion, allWarningsAsErrors, suppressWarnings)
  * kapt -> com.android.legacy-kapt (KSP empfohlen)
  * CommonExtension<*, …> -> CommonExtension, dexOptions-Blöcke entfernen
  * gradle.properties: in AGP 9 entfernte/erzwungene Schalter entfernen, resValues beibehalten, wenn genutzt
Nur gemeldet: alte Variant-API (applicationVariants, variantFilter …), BaseExtension, kotlin.sourceSets,
KMP + Android-Plugin im selben Modul, android.newDsl=false / android.builtInKotlin=false.
"""
import os
import re
import sys

from common import args, walk, read, write, rel

DEFAULT_AGP = "9.0.1"
MIN_GRADLE = (9, 1, 0)
MIN_KOTLIN = (2, 2, 10)
MIN_BUILD_TOOLS = (36, 0, 0)

KOTLIN_ANDROID_IDS = ("org.jetbrains.kotlin.android", "kotlin-android")
KAPT_IDS = ("org.jetbrains.kotlin.kapt", "kotlin-kapt")
LEGACY_KAPT = "com.android.legacy-kapt"

# In AGP 9 entfernt (Fehler beim Setzen) -> Zeile entfernen.
REMOVED_PROPS = {
    "android.defaults.buildfeatures.aidl": "entfernt – bei Bedarf je Modul android { buildFeatures { aidl = true } }",
    "android.defaults.buildfeatures.renderscript": "entfernt – bei Bedarf je Modul android { buildFeatures { renderScript = true } }",
    "android.r8.integratedResourceShrinking": "erzwungen, darf nicht mehr gesetzt werden",
    "android.enableNewResourceShrinker.preciseShrinking": "erzwungen, darf nicht mehr gesetzt werden",
}
OPT_OUTS = {
    "android.newDsl": "alte DSL – in AGP 10 nicht mehr abschaltbar",
    "android.builtInKotlin": "eingebautes Kotlin abgeschaltet – in AGP 10 nicht mehr möglich",
}
# Alte API, die mit der neuen DSL nicht mehr existiert: nur Bericht.
LEGACY_API = [
    (r"\b(applicationVariants|libraryVariants|testVariants|unitTestVariants)\b",
     "alte Variant-API – androidComponents.onVariants { } verwenden"),
    (r"\bvariantFilter\b", "variantFilter – androidComponents.beforeVariants { } verwenden"),
    (r"\b(sdkDirectory|ndkDirectory|bootClasspath|adbExecutable|adbExe)\b",
     "SDK-Pfade aus android { } – androidComponents.sdkComponents verwenden"),
    (r"\bBaseExtension\b", "BaseExtension – ApplicationExtension/LibraryExtension bzw. CommonExtension verwenden"),
    (r"\bgeneratePureSplits\b", "generatePureSplits – entfällt (App Bundles)"),
    (r"\bwearApp\b", "eingebettete Wear-OS-Apps (wearApp) – in AGP 9 entfernt"),
    (r"\bsourceSets\b[^\n]*\bjni\b|\bjni\s*\{", "AndroidSourceSet.jni – entfernt"),
    (r"\bsetDimension\s*\(", "ProductFlavor.setDimension – dimension = \"…\" verwenden"),
    (r"\bisRenderscriptDebuggable\b|\brenderscriptDebuggable\b", "BuildType.isRenderscriptDebuggable – entfernt"),
    (r"\bregisterTransform\s*\(", "registerTransform – entfernt (Instrumentation-API verwenden)"),
]


def vtuple(v):
    nums = re.findall(r"\d+", v)
    return tuple(int(x) for x in (nums + ["0", "0", "0"])[:3])


def vstr(t):
    return ".".join(str(x) for x in t)


class Migration:
    def __init__(self, root, apply, agp):
        self.root, self.apply, self.agp = root, apply, agp
        self.changes, self.notes = [], []

    # ---------------- Hilfen ----------------
    def change(self, path, what):
        self.changes.append(f"{rel(path, self.root)}: {what}")

    def note(self, path, what):
        self.notes.append(f"{rel(path, self.root)}: {what}")

    def save(self, path, old, new):
        if new != old and self.apply:
            write(path, new)

    # ---------------- Version Catalog ----------------
    def catalog(self):
        path = os.path.join(self.root, "gradle", "libs.versions.toml")
        if not os.path.isfile(path):
            return set()
        text = read(path)
        new = text
        versions = dict(re.findall(r'(?m)^\s*([\w.-]+)\s*=\s*"([^"]+)"', section(text, "versions")))
        refs = {}  # plugin-id -> (alias, version.ref)
        for alias, body in re.findall(r'(?m)^\s*([\w.-]+)\s*=\s*\{([^}\n]*)\}', section(text, "plugins")):
            pid = re.search(r'id\s*=\s*"([^"]+)"', body)
            ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', body)
            if pid:
                refs[pid.group(1)] = (alias, ref.group(1) if ref else None)
        agp_key = next((r for p, (a, r) in refs.items() if p.startswith("com.android.") and r), None)
        if agp_key is None:
            m = re.search(r'module\s*=\s*"com\.android\.tools\.build:gradle"\s*,\s*version\.ref\s*=\s*"([^"]+)"', text)
            agp_key = m.group(1) if m else None
        if agp_key and agp_key in versions and vtuple(versions[agp_key]) < vtuple(self.agp):
            new = set_version(new, agp_key, self.agp)
            self.change(path, f"AGP {versions[agp_key]} -> {self.agp} (versions.{agp_key})")
        kt_key = next((r for p, (a, r) in refs.items() if p.startswith("org.jetbrains.kotlin.") and r), None)
        if kt_key is None:
            m = re.search(r'module\s*=\s*"org\.jetbrains\.kotlin:kotlin-gradle-plugin"\s*,\s*version\.ref\s*=\s*"([^"]+)"', text)
            kt_key = m.group(1) if m else None
        if kt_key and kt_key in versions and vtuple(versions[kt_key]) < MIN_KOTLIN:
            new = set_version(new, kt_key, vstr(MIN_KOTLIN))
            self.change(path, f"Kotlin {versions[kt_key]} -> {vstr(MIN_KOTLIN)} (eingebautes Kotlin braucht KGP >= 2.2.10)")
            ksp = next((r for p, (a, r) in refs.items() if p == "com.google.devtools.ksp" and r), None)
            if ksp and ksp in versions and versions[ksp].count("-") == 1 and vtuple(versions[ksp]) < MIN_KOTLIN:
                self.note(path, f"KSP {versions[ksp]} passt nicht zu Kotlin {vstr(MIN_KOTLIN)} – auf eine dazu passende KSP-Version anheben")
        # Kotlin-Android-Plugin-Eintrag; kapt -> legacy-kapt
        aliases = set()
        for pid in KOTLIN_ANDROID_IDS:
            if pid in refs:
                aliases.add(refs[pid][0])
        kapt_alias = next((refs[p][0] for p in KAPT_IDS if p in refs), None)
        if kapt_alias and agp_key:
            pat = re.compile(rf'(?m)^([ \t]*{re.escape(kapt_alias)}\s*=\s*)\{{[^}}\n]*\}}')
            if pat.search(new):
                new = pat.sub(lambda m: f'{m.group(1)}{{ id = "{LEGACY_KAPT}", version.ref = "{agp_key}" }}', new, count=1)
                self.change(path, f"kapt-Plugin ({kapt_alias}) -> {LEGACY_KAPT} (KSP wird empfohlen)")
        if new != text:
            self.save(path, text, new)
        self.catalog_path, self.catalog_text = path, new
        return {a.replace("-", ".").replace("_", ".") for a in aliases}

    def drop_catalog_aliases(self, aliases, still_used):
        if not aliases or not getattr(self, "catalog_path", None):
            return
        text = read(self.catalog_path) if self.apply else self.catalog_text
        new = text
        for alias in sorted(aliases):
            if alias in still_used:
                continue
            pat = re.compile(rf'(?m)^\s*{re.escape(alias).replace(r"\.", "[.\\-_]")}\s*=\s*\{{[^}}\n]*\}}\s*\n')
            if pat.search(new):
                new = pat.sub("", new, count=1)
                self.change(self.catalog_path, f"Plugin-Eintrag {alias} (Kotlin Android) entfernt")
        self.save(self.catalog_path, text, new)

    # ---------------- Build-Dateien ----------------
    def build_file(self, path, kt_aliases):
        text = read(path)
        kts = path.endswith(".kts")
        new = text
        removed_aliases = False
        # 1. kotlin-android entfernen
        patterns = [
            r'(?m)^[ \t]*id\s*\(?\s*["\'](?:org\.jetbrains\.kotlin\.android|kotlin-android)["\']\s*\)?(?:\s*version\s*\(?\s*["\'][^"\']*["\']\s*\)?)?(?:\s*apply\s*\(?\s*false\s*\)?)?[ \t]*\n',
            r'(?m)^[ \t]*kotlin\s*\(\s*"android"\s*\)(?:\s*version\s*"[^"]*")?(?:\s*apply\s*false)?[ \t]*\n',
            r'(?m)^[ \t]*apply\s*(?:plugin\s*:\s*|\(\s*plugin\s*=\s*)["\'](?:org\.jetbrains\.kotlin\.android|kotlin-android)["\']\s*\)?[ \t]*\n',
        ]
        for alias in kt_aliases:
            patterns.append(rf'(?m)^[ \t]*alias\s*\(\s*libs\.plugins\.{re.escape(alias)}\s*\)(?:\s*apply\s*\(?\s*false\s*\)?)?[ \t]*\n')
        for pat in patterns:
            new2 = re.sub(pat, "", new)
            if new2 != new:
                removed_aliases = True
                new = new2
        if removed_aliases:
            self.change(path, "Plugin Kotlin Android entfernt (AGP 9 kompiliert Kotlin selbst)")
        # 2. kapt
        for pid in KAPT_IDS:
            new2 = re.sub(rf'(?m)(^[ \t]*id\s*\(?\s*["\']){re.escape(pid)}(["\']\s*\)?)', rf'\g<1>{LEGACY_KAPT}\g<2>', new)
            new2 = re.sub(rf'(?m)(^[ \t]*apply\s*plugin\s*:\s*["\']){re.escape(pid)}(["\'])', rf'\g<1>{LEGACY_KAPT}\g<2>', new2)
            if new2 != new:
                self.change(path, f"{pid} -> {LEGACY_KAPT} (KSP wird empfohlen)")
                new = new2
        if re.search(r'(?m)^[ \t]*kotlin\s*\(\s*"kapt"\s*\)', new):
            new = re.sub(r'(?m)^([ \t]*)kotlin\s*\(\s*"kapt"\s*\)', rf'\g<1>id("{LEGACY_KAPT}")', new)
            self.change(path, f'kotlin("kapt") -> {LEGACY_KAPT}')
        # 3. Versionen direkt in der Build-Datei
        new = self.inline_versions(path, new)
        # 4. kotlinOptions
        new = self.kotlin_options(path, new, kts)
        # 5. dexOptions
        block = find_block(new, "dexOptions")
        while block:
            s, e = block
            new = new[:s] + new[e:].lstrip("\n") if new[e:e + 1] == "\n" else new[:s] + new[e:]
            self.change(path, "dexOptions { } entfernt (ohne Wirkung seit AGP 7)")
            block = find_block(new, "dexOptions")
        # 6. CommonExtension<…>
        new2 = re.sub(r'\bCommonExtension\s*<[\s*,]*>', "CommonExtension", new)
        if new2 != new:
            self.change(path, "CommonExtension<*, …> -> CommonExtension")
            new = new2
        # 7. buildToolsVersion
        m = re.search(r'buildToolsVersion\s*=?\s*\(?\s*["\']([\d.]+)["\']', new)
        if m and vtuple(m.group(1)) < MIN_BUILD_TOOLS:
            new = new[:m.start(1)] + vstr(MIN_BUILD_TOOLS) + new[m.end(1):]
            self.change(path, f"buildToolsVersion {m.group(1)} -> {vstr(MIN_BUILD_TOOLS)}")
        # Bericht
        self.report_legacy(path, new)
        self.save(path, text, new)
        return new

    def inline_versions(self, path, text):
        new = text
        for pat, label, minimum in (
            (r'(id\s*\(?\s*["\']com\.android\.(?:application|library|test|dynamic-feature)["\']\s*\)?\s*version\s*\(?\s*["\'])([^"\']+)', "AGP", None),
            (r'(["\']com\.android\.tools\.build:gradle:)([^"\']+)', "AGP", None),
            (r'(id\s*\(?\s*["\']org\.jetbrains\.kotlin\.[\w.-]+["\']\s*\)?\s*version\s*\(?\s*["\'])([^"\']+)', "Kotlin", MIN_KOTLIN),
            (r'(kotlin\s*\(\s*"[\w.-]+"\s*\)\s*version\s*")([^"]+)', "Kotlin", MIN_KOTLIN),
            (r'(["\']org\.jetbrains\.kotlin:kotlin-gradle-plugin:)([^"\'$]+)', "Kotlin", MIN_KOTLIN),
        ):
            target = self.agp if minimum is None else vstr(minimum)
            for m in list(re.finditer(pat, new))[::-1]:
                old = m.group(2)
                if vtuple(old) < vtuple(target):
                    new = new[:m.start(2)] + target + new[m.end(2):]
                    self.change(path, f"{label} {old} -> {target}")
        return new

    def kotlin_options(self, path, text, kts):
        block = find_block(text, "kotlinOptions")
        if not block:
            return text
        s, e = block
        body = text[text.index("{", s) + 1:e - 1]
        lines, unknown = [], []
        for raw in body.splitlines():
            line = raw.strip()
            if not line or line.startswith("//"):
                continue
            conv = convert_option(line, kts)
            if conv:
                lines.append(conv)
            else:
                unknown.append(line)
                lines.append(f"// TODO AGP 9: {line}")
        indent = "    "
        inner = "\n".join(f"{indent * 2}{x}" for x in lines)
        replacement = f"kotlin {{\n{indent}compilerOptions {{\n{inner}\n{indent}}}\n}}\n"
        # Block aus android { } herauslösen, kotlin { } ans Dateiende setzen
        start_line = text.rfind("\n", 0, s) + 1
        end = e + 1 if text[e:e + 1] == "\n" else e
        new = text[:start_line] + text[end:]
        new = new.rstrip("\n") + "\n\n" + replacement
        self.change(path, "android { kotlinOptions { } } -> kotlin { compilerOptions { } }")
        if unknown:
            self.note(path, "kotlinOptions nicht automatisch übertragbar (als TODO markiert): " + "; ".join(unknown))
        return new

    def report_legacy(self, path, text):
        for pat, msg in LEGACY_API:
            if re.search(pat, text):
                self.note(path, msg)
        if re.search(r'\bkotlin\s*\{[^}]*\bsourceSets\b', text, re.S):
            self.note(path, "kotlin.sourceSets – Quellordner über android.sourceSets.<name>.kotlin.directories angeben")
        if re.search(r'kotlin\.multiplatform|kotlin\("multiplatform"\)', text) and re.search(r'com\.android\.(application|library)|android\.(application|library)', text):
            self.note(path, "KMP und Android-Plugin im selben Modul – auf com.android.kotlin.multiplatform.library umstellen")
        if re.search(r'kotlinCompilerExtensionVersion', text):
            self.note(path, "composeOptions.kotlinCompilerExtensionVersion – mit Kotlin 2.x das Plugin org.jetbrains.kotlin.plugin.compose verwenden")

    # ---------------- gradle.properties / Wrapper ----------------
    def properties(self, uses_res_values):
        path = os.path.join(self.root, "gradle.properties")
        text = read(path) if os.path.isfile(path) else ""
        new = text
        for key, why in REMOVED_PROPS.items():
            pat = re.compile(rf'(?m)^[ \t]*{re.escape(key)}\s*=.*\n?')
            if pat.search(new):
                new = pat.sub("", new)
                self.change(path, f"{key} entfernt ({why})")
        for key, why in OPT_OUTS.items():
            if re.search(rf'(?m)^[ \t]*{re.escape(key)}\s*=\s*false', new):
                self.note(path, f"{key}=false – {why}")
        if uses_res_values and not re.search(r'(?m)^[ \t]*android\.defaults\.buildfeatures\.resvalues\s*=', new):
            new = new.rstrip("\n") + ("\n" if new.strip() else "") + \
                "# AGP 9: resValue() bleibt aktiv (neuer Standard: aus)\nandroid.defaults.buildfeatures.resvalues=true\n"
            self.change(path, "android.defaults.buildfeatures.resvalues=true (Projekt nutzt resValue)")
        if new != text and self.apply:
            write(path, new)

    def wrapper(self):
        path = os.path.join(self.root, "gradle", "wrapper", "gradle-wrapper.properties")
        if not os.path.isfile(path):
            self.notes.append(f"gradle/wrapper/gradle-wrapper.properties fehlt – AGP 9 braucht Gradle >= {vstr(MIN_GRADLE)}")
            return
        text = read(path)
        m = re.search(r'gradle-([\d.]+)-(bin|all)\.zip', text)
        if m and vtuple(m.group(1)) < MIN_GRADLE:
            new = text.replace(m.group(0), f"gradle-{vstr(MIN_GRADLE)}-{m.group(2)}.zip")
            # Prüfsumme gehört zur alten Distribution
            new = re.sub(r'(?m)^distributionSha256Sum=.*\n?', "", new)
            self.change(path, f"Gradle {m.group(1)} -> {vstr(MIN_GRADLE)}")
            self.save(path, text, new)

    # ---------------- Ablauf ----------------
    def run(self):
        kt_aliases = self.catalog()
        files = [p for p in walk(self.root, names={"build.gradle", "build.gradle.kts"})]
        logic = [p for p in walk(self.root, exts={"kt"}) if "build-logic" in p or "buildSrc" in p]
        uses_res_values = False
        contents = []
        for p in sorted(files):
            contents.append(self.build_file(p, kt_aliases))
        for p in sorted(logic):
            text = read(p)
            new = re.sub(r'\bCommonExtension\s*<[\s*,]*>', "CommonExtension", text)
            if new != text:
                self.change(p, "CommonExtension<*, …> -> CommonExtension")
                self.save(p, text, new)
            if re.search(r'\bkotlinOptions\b', new):
                self.note(p, "kotlinOptions in Build-Logik – auf KotlinAndroidProjectExtension.compilerOptions umstellen")
            self.report_legacy(p, new)
            if 'pluginManager.apply("org.jetbrains.kotlin.android")' in new or "kotlin-android" in new:
                self.note(p, "Konventions-Plugin wendet org.jetbrains.kotlin.android an – entfernen")
            contents.append(new)
        uses_res_values = any(re.search(r'\bresValue\s*\(', c) for c in contents)
        still = set()
        for c in contents:
            still.update(a.replace("-", ".").replace("_", ".") for a in re.findall(r'libs\.plugins\.([\w.]+)', c))
        self.drop_catalog_aliases(kt_aliases, still)
        self.properties(uses_res_values)
        self.wrapper()
        if not files:
            self.notes.append("Keine build.gradle(.kts) gefunden – ist das ein Gradle-Projekt?")


def section(toml, name):
    m = re.search(rf'(?ms)^\[{name}\]\s*\n(.*?)(?=^\[|\Z)', toml)
    return m.group(1) if m else ""


def set_version(toml, key, value):
    sec = re.search(r'(?ms)^\[versions\]\s*\n(.*?)(?=^\[|\Z)', toml)
    if not sec:
        return toml
    body = re.sub(rf'(?m)^(\s*{re.escape(key)}\s*=\s*")[^"]+(")', rf'\g<1>{value}\g<2>', sec.group(1), count=1)
    return toml[:sec.start(1)] + body + toml[sec.end(1):]


def find_block(text, name):
    """(start, ende) des ersten Blocks `name { … }` (Klammern gezählt, Strings und Kommentare grob übersprungen)."""
    m = re.search(rf'(?m)^[ \t]*{name}\s*\{{', text)
    if not m:
        return None
    i, depth, n = m.end(), 1, len(text)
    while i < n and depth:
        c = text[i]
        if c in "\"'":
            q = c
            i += 1
            while i < n and text[i] != q and text[i] != "\n":
                i += 2 if text[i] == "\\" else 1
        elif text.startswith("//", i):
            i = text.find("\n", i)
            i = n if i < 0 else i
            continue
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        i += 1
    return (m.start(), i) if depth == 0 else None


def convert_option(line, kts):
    jt = "org.jetbrains.kotlin.gradle.dsl.JvmTarget"
    kv = "org.jetbrains.kotlin.gradle.dsl.KotlinVersion"
    m = re.match(r'jvmTarget\s*=\s*["\']([\w.]+)["\']$', line)
    if m:
        return f'jvmTarget.set({jt}.fromTarget("{m.group(1)}"))' if kts else f"jvmTarget = {jt}.fromTarget('{m.group(1)}')"
    m = re.match(r'jvmTarget\s*=\s*JavaVersion\.VERSION_(\w+)\.toString\(\)$', line)
    if m:
        t = m.group(1).replace("_", ".")
        return f'jvmTarget.set({jt}.fromTarget("{t}"))' if kts else f"jvmTarget = {jt}.fromTarget('{t}')"
    m = re.match(r'(languageVersion|apiVersion)\s*=\s*["\'](\d+)\.(\d+)["\']$', line)
    if m:
        v = f"{kv}.KOTLIN_{m.group(2)}_{m.group(3)}"
        return f"{m.group(1)}.set({v})" if kts else f"{m.group(1)} = {v}"
    m = re.match(r'freeCompilerArgs\s*\+?=\s*(.+)$', line)
    if m:
        literals = re.findall(r'"[^"]*"|\'[^\']*\'', m.group(1))
        if not literals:
            return None
        if kts:
            items = ", ".join('"' + x[1:-1] + '"' for x in literals)
            return f"freeCompilerArgs.addAll({items})"
        items = ", ".join("'" + x[1:-1] + "'" for x in literals)
        return f"freeCompilerArgs.addAll([{items}])"
    m = re.match(r'(allWarningsAsErrors|suppressWarnings|verbose)\s*=\s*(true|false)$', line)
    if m:
        return f"{m.group(1)}.set({m.group(2)})" if kts else f"{m.group(1)} = {m.group(2)}"
    return None


def main():
    project, inp = args()
    parts = inp.split()
    mode = parts[0].lower() if parts else "check"
    if mode not in ("check", "apply"):
        print(f"Unbekannter Modus '{mode}' – erlaubt: check, apply")
        return 2
    agp = parts[1] if len(parts) > 1 else DEFAULT_AGP
    if not re.fullmatch(r"9\.\d+\.\d+(-[\w.]+)?", agp):
        print(f"AGP-Version '{agp}' ungültig – erwartet 9.x.y, z. B. {DEFAULT_AGP}")
        return 2
    mig = Migration(project, mode == "apply", agp)
    mig.run()
    title = "Umgestellt" if mig.apply else "Würde umgestellt (Modus check)"
    print(f"AGP-9-Migration nach {agp} – {project}\n")
    print(f"{title}: {len(mig.changes)}")
    for c in mig.changes:
        print(f"  • {c}")
    print(f"\nVon Hand prüfen: {len(mig.notes)}")
    for n in mig.notes:
        print(f"  • {n}")
    if not mig.changes and not mig.notes:
        print("\nNichts zu tun – das Projekt ist bereits auf AGP 9 eingestellt.")
    elif not mig.apply and mig.changes:
        print("\nMit 'apply' umstellen (vorher committen). Danach einen Gradle-Sync bzw. Build starten.")
    else:
        print(f"\nDanach: Gradle-Build starten. Voraussetzungen: JDK 17, Gradle {vstr(MIN_GRADLE)}, Build-Tools {vstr(MIN_BUILD_TOOLS)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
