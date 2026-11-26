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
  * KSP >= 2.3.6 und Hilt >= 2.59.2 (Mindestversionen für AGP 9)
  * kapt(…) -> ksp(…) für Prozessoren mit KSP-Unterstützung (bekannte Liste bzw. Prüfung des JARs im
    Gradle-Cache auf META-INF/services/…SymbolProcessorProvider); bleibt kein kapt(…) übrig, ersetzt KSP das
    kapt-Plugin, sonst kapt -> com.android.legacy-kapt
  * buildConfigField ohne buildFeatures { buildConfig = true } -> Schalter ergänzen
  * CommonExtension<*, …> -> CommonExtension, dexOptions-Blöcke entfernen
  * gradle.properties: in AGP 9 entfernte/erzwungene Schalter entfernen, resValues beibehalten, wenn genutzt;
    Übergangsschalter android.builtInKotlin, android.uniquePackageNames, android.enableAppCompileTimeRClass
    entfernen, android.newDsl nur, wenn keine alte Variant-API mehr vorkommt
Nur gemeldet: alte Variant-API (applicationVariants, variantFilter …), BaseExtension, kotlin.sourceSets,
KMP + Android-Plugin im selben Modul, android.newDsl=false, buildConfigField -> addCustomBuildConfigFields,
Paparazzi < 2.0.0-alpha05.
Abgleich: Googles Skill agp-9-upgrade (https://github.com/android/skills, build-system/agp, Apache-2.0).
"""
import glob
import os
import re
import sys
import zipfile

from common import args, walk, read, write, rel

DEFAULT_AGP = "9.0.1"
MIN_GRADLE = (9, 1, 0)
MIN_KOTLIN = (2, 2, 10)
MIN_BUILD_TOOLS = (36, 0, 0)
MIN_KSP = "2.3.6"
MIN_HILT = "2.59.2"
KSP_ID = "com.google.devtools.ksp"
HILT_ID = "com.google.dagger.hilt.android"
KSP_SERVICE = "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
# Annotation-Prozessoren mit KSP-Unterstützung (group:name); andere werden im Gradle-Cache geprüft.
KSP_CAPABLE = {
    "androidx.room:room-compiler", "com.google.dagger:hilt-compiler", "com.google.dagger:hilt-android-compiler",
    "com.google.dagger:dagger-compiler", "com.google.dagger:dagger-android-processor", "androidx.hilt:hilt-compiler",
    "com.squareup.moshi:moshi-kotlin-codegen", "com.github.bumptech.glide:ksp", "io.insert-koin:koin-ksp-compiler",
    "io.github.raamcosta.compose-destinations:ksp", "me.tatarka.inject:kotlin-inject-compiler-ksp",
}
# Übergangsschalter, die nach der Migration entfallen (Skill agp-9-upgrade).
TRANSITION_PROPS = ("android.builtInKotlin", "android.uniquePackageNames", "android.enableAppCompileTimeRClass")

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
        self.libraries = {}      # normalisierter Catalog-Alias -> group:name
        self.ksp_alias = None    # normalisierter Plugin-Alias von KSP
        self.kapt_alias = None   # normalisierter Plugin-Alias von kapt
        self.legacy_api = False  # alte Variant-API gefunden -> android.newDsl bleibt

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
        # KSP und Hilt: Mindestversionen für AGP 9
        bumps = [(r, "KSP", MIN_KSP) for p, (a, r) in refs.items() if p == KSP_ID and r]
        bumps += [(r, "Hilt", MIN_HILT) for p, (a, r) in refs.items() if p == HILT_ID and r]
        bumps += [(r, "Hilt", MIN_HILT) for r in re.findall(
            r'module\s*=\s*"com\.google\.dagger:hilt-[\w-]+"\s*,\s*version\.ref\s*=\s*"([^"]+)"', text)]
        for key, label, minimum in dict.fromkeys(bumps):
            if key in versions and vtuple(versions[key]) < vtuple(minimum):
                new = set_version(new, key, minimum)
                self.change(path, f"{label} {versions[key]} -> {minimum} (Mindestversion für AGP 9)")
        libs = section(text, "libraries")
        for alias, module in re.findall(r'(?m)^\s*([\w.-]+)\s*=\s*\{[^}\n]*module\s*=\s*"([^":]+:[^":]+)"', libs):
            self.libraries[norm(alias)] = module
        for alias, g, n in re.findall(r'(?m)^\s*([\w.-]+)\s*=\s*\{[^}\n]*group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"', libs):
            self.libraries[norm(alias)] = f"{g}:{n}"
        if KSP_ID in refs:
            self.ksp_alias = norm(refs[KSP_ID][0])
        # Kotlin-Android-Plugin-Eintrag; kapt -> legacy-kapt
        aliases = set()
        for pid in KOTLIN_ANDROID_IDS:
            if pid in refs:
                aliases.add(refs[pid][0])
        kapt_alias = next((refs[p][0] for p in KAPT_IDS if p in refs), None)
        self.kapt_alias = norm(kapt_alias) if kapt_alias else None
        if kapt_alias and agp_key:
            pat = re.compile(rf'(?m)^([ \t]*{re.escape(kapt_alias)}\s*=\s*)\{{[^}}\n]*\}}')
            if pat.search(new):
                new = pat.sub(lambda m: f'{m.group(1)}{{ id = "{LEGACY_KAPT}", version.ref = "{agp_key}" }}', new, count=1)
                self.change(path, f"kapt-Plugin ({kapt_alias}) -> {LEGACY_KAPT} (für Prozessoren ohne KSP-Unterstützung)")
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
        # 2. kapt: KSP-fähige Prozessoren auf ksp(…), sonst legacy-kapt
        new = self.kapt_to_ksp(path, new, kts)
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
        # 7. BuildConfig-Felder brauchen den Schalter
        new = self.build_config(path, new)
        # 8. buildToolsVersion
        m = re.search(r'buildToolsVersion\s*=?\s*\(?\s*["\']([\d.]+)["\']', new)
        if m and vtuple(m.group(1)) < MIN_BUILD_TOOLS:
            new = new[:m.start(1)] + vstr(MIN_BUILD_TOOLS) + new[m.end(1):]
            self.change(path, f"buildToolsVersion {m.group(1)} -> {vstr(MIN_BUILD_TOOLS)}")
        # Bericht
        self.report_legacy(path, new)
        self.save(path, text, new)
        return new

    def coordinate(self, ref):
        """group:name einer kapt-Abhängigkeit (Catalog-Alias oder String-Koordinate)."""
        if ref.startswith("libs."):
            return self.libraries.get(norm(ref[5:]))
        parts = ref.strip("\"'").split(":")
        return ":".join(parts[:2]) if len(parts) >= 2 else None

    @staticmethod
    def ksp_capable(coord):
        if not coord:
            return False
        if coord in KSP_CAPABLE:
            return True
        group, _, name = coord.partition(":")
        base = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1", group, name)
        for jar in sorted(glob.glob(os.path.join(base, "*", "*", "*.jar")), reverse=True):
            try:
                with zipfile.ZipFile(jar) as z:
                    if KSP_SERVICE in z.namelist():
                        return True
            except (OSError, zipfile.BadZipFile):
                continue
        return False

    def kapt_to_ksp(self, path, text, kts):
        dep = re.compile(r'(?m)^([ \t]*)kapt(\s*\(\s*|\s+)(libs\.[\w.-]+|"[^"\n]+"|\'[^\'\n]+\')')
        new, converted = text, []
        for m in list(dep.finditer(text))[::-1]:
            coord = self.coordinate(m.group(3))
            if self.ksp_capable(coord):
                at = m.start() + len(m.group(1))
                new = new[:at] + "ksp" + new[at + 4:]
                converted.append(coord)
        if not converted:
            return text
        for coord in reversed(converted):
            self.change(path, f"kapt -> ksp ({coord})")
        if re.search(r'(?m)^[ \t]*kapt\w*(\s*\(|\s+["\'l])', new):
            return new  # weitere kapt-Prozessoren: legacy-kapt bleibt
        flat = norm_aliases(new)
        has_ksp = KSP_ID in new or bool(self.ksp_alias and f"libs.plugins.{self.ksp_alias}" in flat)
        if has_ksp:
            plugin_line = ""
        elif self.ksp_alias:
            plugin_line = f"alias(libs.plugins.{self.ksp_alias})"
        elif kts:
            plugin_line = f'id("{KSP_ID}")'
        else:
            plugin_line = f"id '{KSP_ID}'"
        ids = "|".join(re.escape(p) for p in KAPT_IDS)
        pats = [rf'(?m)^([ \t]*)id\s*\(?\s*["\'](?:{ids})["\']\s*\)?[^\n]*\n',
                r'(?m)^([ \t]*)kotlin\s*\(\s*"kapt"\s*\)[^\n]*\n']
        if self.kapt_alias:
            pats.append(rf'(?m)^([ \t]*)alias\s*\(\s*libs\.plugins\.{alias_pattern(self.kapt_alias)}\s*\)[^\n]*\n')
        groovy_apply = rf'(?m)^([ \t]*)apply\s*plugin\s*:\s*["\'](?:{ids})["\'][^\n]*\n'
        m = re.search(groovy_apply, new)
        if m:
            repl = "" if has_ksp else f"{m.group(1)}apply plugin: '{KSP_ID}'\n"
            new = new[:m.start()] + repl + new[m.end():]
            self.change(path, "kapt-Plugin -> KSP (keine kapt-Abhängigkeiten mehr)")
            return new
        for pat in pats:
            m = re.search(pat, new)
            if m:
                repl = f"{m.group(1)}{plugin_line}\n" if plugin_line else ""
                new = new[:m.start()] + repl + new[m.end():]
                self.change(path, "kapt-Plugin -> KSP (keine kapt-Abhängigkeiten mehr)")
                if not self.ksp_alias and not has_ksp:
                    self.note(path, f"KSP-Plugin ohne Version eingetragen – im Wurzel-Build "
                                    f"id(\"{KSP_ID}\") version \"{MIN_KSP}\" apply false ergänzen")
                return new
        if not has_ksp:
            self.note(path, f"kapt-Abhängigkeiten auf ksp umgestellt – KSP-Plugin ({KSP_ID}, >= {MIN_KSP}) im Modul anwenden")
        return new

    def build_config(self, path, text):
        if not re.search(r'\bbuildConfigField\b', text):
            return text
        new = text
        if not re.search(r'\bbuildConfig\s*=?\s*true\b', text):
            block = find_block(text, "buildFeatures")
            target = block or find_block(text, "android")
            if target:
                brace = text.index("{", target[0]) + 1
                indent = re.match(r"[ \t]*", text[text.rfind("\n", 0, target[0]) + 1:]).group(0)
                eq = " = " if path.endswith(".kts") else " "
                inner = f"\n{indent}    buildConfig{eq}true" if block else \
                    f"\n{indent}    buildFeatures {{\n{indent}        buildConfig{eq}true\n{indent}    }}"
                new = text[:brace] + inner + text[brace:]
                self.change(path, "buildFeatures { buildConfig = true } ergänzt (Modul nutzt buildConfigField)")
        self.note(path, "buildConfigField – auf androidComponents.onVariants { it.buildConfigFields?.put(…) } umstellen "
                        "(Rezept addCustomBuildConfigFields in android/gradle-recipes; String-Werte mit Anführungszeichen)")
        return new

    def inline_versions(self, path, text):
        new = text
        for pat, label, minimum in (
            (r'(id\s*\(?\s*["\']com\.android\.(?:application|library|test|dynamic-feature)["\']\s*\)?\s*version\s*\(?\s*["\'])([^"\']+)', "AGP", None),
            (r'(["\']com\.android\.tools\.build:gradle:)([^"\']+)', "AGP", None),
            (r'(id\s*\(?\s*["\']org\.jetbrains\.kotlin\.[\w.-]+["\']\s*\)?\s*version\s*\(?\s*["\'])([^"\']+)', "Kotlin", MIN_KOTLIN),
            (r'(kotlin\s*\(\s*"[\w.-]+"\s*\)\s*version\s*")([^"]+)', "Kotlin", MIN_KOTLIN),
            (r'(["\']org\.jetbrains\.kotlin:kotlin-gradle-plugin:)([^"\'$]+)', "Kotlin", MIN_KOTLIN),
            (r'(id\s*\(?\s*["\']com\.google\.devtools\.ksp["\']\s*\)?\s*version\s*\(?\s*["\'])([^"\']+)', "KSP", MIN_KSP),
            (r'(["\']com\.google\.devtools\.ksp:symbol-processing-gradle-plugin:)([^"\'$]+)', "KSP", MIN_KSP),
            (r'(id\s*\(?\s*["\']com\.google\.dagger\.hilt\.android["\']\s*\)?\s*version\s*\(?\s*["\'])([^"\']+)', "Hilt", MIN_HILT),
            (r'(["\']com\.google\.dagger:hilt-[\w-]+:)([^"\'$]+)', "Hilt", MIN_HILT),
        ):
            target = self.agp if minimum is None else (minimum if isinstance(minimum, str) else vstr(minimum))
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
                self.legacy_api = True
        for v in re.findall(r'(?:app\.cash\.paparazzi["\']\s*\)?\s*version\s*\(?\s*["\']|app\.cash\.paparazzi:paparazzi-gradle-plugin:)([\w.-]+)', text):
            if paparazzi_too_old(v):
                self.note(path, f"Paparazzi {v} läuft nicht mit AGP 9 – auf 2.0.0-alpha05 oder neuer anheben")
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
        for key in TRANSITION_PROPS:
            pat = re.compile(rf'(?m)^[ \t]*{re.escape(key)}\s*=.*\n?')
            if pat.search(new):
                new = pat.sub("", new)
                self.change(path, f"{key} entfernt (Übergangsschalter, nach der Migration überflüssig)")
        dsl = re.compile(r'(?m)^[ \t]*android\.newDsl\s*=.*\n?')
        if dsl.search(new) and not self.legacy_api:
            new = dsl.sub("", new)
            self.change(path, "android.newDsl entfernt (keine alte Variant-API mehr gefunden)")
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
        if getattr(self, "catalog_text", None):
            pv = re.search(r'(?m)^\s*paparazzi\s*=\s*"([^"]+)"', section(self.catalog_text, "versions"))
            if pv and paparazzi_too_old(pv.group(1)):
                self.note(self.catalog_path, f"Paparazzi {pv.group(1)} läuft nicht mit AGP 9 – auf 2.0.0-alpha05 oder neuer anheben")
        still = set()
        for c in contents:
            still.update(a.replace("-", ".").replace("_", ".") for a in re.findall(r'libs\.plugins\.([\w.]+)', c))
        self.drop_catalog_aliases(kt_aliases, still)
        self.properties(uses_res_values)
        self.wrapper()
        if not files:
            self.notes.append("Keine build.gradle(.kts) gefunden – ist das ein Gradle-Projekt?")


def norm(alias):
    return alias.replace("-", ".").replace("_", ".")


def norm_aliases(text):
    return re.sub(r"libs\.[\w.-]+", lambda m: norm(m.group(0)), text)


def alias_pattern(alias):
    return r"[.\-_]".join(re.escape(p) for p in alias.split("."))


def paparazzi_too_old(v):
    """AGP 9 braucht Paparazzi 2.0.0-alpha05 oder neuer."""
    base = vtuple(v.split("-")[0])
    if base != (2, 0, 0):
        return base < (2, 0, 0)
    m = re.search(r"alpha(\d+)", v)
    return bool(m) and int(m.group(1)) < 5


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
        print(f"\nDanach prüfen (ohne clean): Gradle-Sync, ./gradlew help, ./gradlew build --dry-run. "
              f"Voraussetzungen: JDK 17, Gradle {vstr(MIN_GRADLE)}, Build-Tools {vstr(MIN_BUILD_TOOLS)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
