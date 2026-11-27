#!/usr/bin/env python3
"""Erzeugt Tool-Plugins: Formatierer (`contributes.formatters`), Language Server (`contributes.lspServers`) und
Werkzeuge für das PT-Menü des Dateibaums (`contributes.tools`, nur Sideload, Berechtigung `shell`; Skripte in scripts/).

ID:        com.codeforge.plugin.<name>.<art>   (art = formatter | lsp)     Publisher: codeforge
Benötigte Programme stehen in `contributes.requires`; die App installiert sie nach Bestätigung (Plugins-Bildschirm bzw. PT-Menü).

Aufruf: python3 tool-plugins/generate.py [ausgabeordner]   (Standard: tool-plugins/build)
Schreibt Quellen nach tool-plugins/src/<id>/ und ZIPs nach <ausgabeordner>/<id>.zip sowie je Art ein Sammelpaket (Bundle).
"""
import sys
from pathlib import Path
import os as _os
sys.path.insert(0, _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "scripts"))
import pluginkit  # noqa: E402

ROOT = Path(__file__).resolve().parent
F = "${file}"

# (name, label, extensions, fileNames, command, install)
FORMATTERS = [
    ("prettier", "Prettier", ["js", "jsx", "mjs", "cjs", "ts", "tsx", "json", "css", "scss", "less", "html", "md", "yaml", "yml", "vue"], [],
     ["prettier", "--stdin-filepath", F], "npm install -g prettier"),
    ("shfmt", "shfmt", ["sh", "bash", "zsh"], [], ["shfmt", "-filename", F], "pkg install shfmt"),
    ("ruff-format", "Ruff Format", ["py", "pyi"], [], ["ruff", "format", "--stdin-filename", F, "-"], "pkg install ruff"),
    ("gofmt", "gofmt", ["go"], [], ["gofmt"], "pkg install golang"),
    ("rustfmt", "rustfmt", ["rs"], [], ["rustfmt", "--edition", "2021"], "pkg install rust"),
    ("taplo-format", "Taplo Format", ["toml"], [], ["taplo", "format", "-"], "pkg install taplo"),
]

# (name, label, languages, command, install) – `languages` = Sprach-IDs des Editors; mehrere Server je Plugin möglich
LSP = [
    ("clangd", "clangd (C/C++)", [("clangd", ["c", "cpp"], ["clangd", "--background-index"])], "pkg install clang"),
    ("rust-analyzer", "rust-analyzer (Rust)", [("rust-analyzer", ["rust"], ["rust-analyzer"])], "pkg install rust-analyzer"),
    ("taplo", "Taplo (TOML)", [("taplo", ["toml"], ["taplo", "lsp", "stdio"])], "pkg install taplo"),
    ("gopls", "gopls (Go)", [("gopls", ["go"], ["gopls"])], "pkg install gopls"),
    ("ruff", "Ruff (Python-Linting)", [("ruff", ["python"], ["ruff", "server"])], "pkg install ruff"),
    ("neocmakelsp", "neocmakelsp (CMake)", [("neocmakelsp", ["cmake"], ["neocmakelsp", "stdio"])], "pkg install neocmakelsp"),
    ("dart", "Dart Analysis Server", [("dart", ["dart"], ["dart", "language-server"])], "pkg install dart"),
    ("bash-language-server", "Bash Language Server", [("bash-language-server", ["shellscript"], ["bash-language-server", "start"])],
     "npm install -g bash-language-server (optional: pkg install shellcheck shfmt)"),
    ("typescript-language-server", "TypeScript/JavaScript Language Server",
     [("typescript-language-server", ["typescript", "javascript"], ["typescript-language-server", "--stdio"])],
     "npm install -g typescript typescript-language-server"),
    ("pyright", "Pyright (Python)", [("pyright", ["python"], ["pyright-langserver", "--stdio"])], "npm install -g pyright"),
    ("web-langservers", "HTML/CSS/JSON Language Servers", [
        ("vscode-html-language-server", ["html"], ["vscode-html-language-server", "--stdio"]),
        ("vscode-css-language-server", ["css"], ["vscode-css-language-server", "--stdio"]),
        ("vscode-json-language-server", ["json"], ["vscode-json-language-server", "--stdio"])],
     "npm install -g vscode-langservers-extracted"),
    ("yaml-language-server", "YAML Language Server", [("yaml-language-server", ["yaml"], ["yaml-language-server", "--stdio"])],
     "npm install -g yaml-language-server"),
    # JVM-Server: Dateien kommen als Download-Voraussetzung (SHA-256 geprüft) nach ${deps}/<name>.
    # ${jvmArg} wird je JVM-Option des Nutzers wiederholt, ${jvmArgs} steht für alle (Einstellungen → Sprachserver).
    ("kotlin-language-server", "Kotlin Language Server",
     [("kotlin-language-server", ["kotlin"], ["${deps}/kotlin-language-server/server/bin/kotlin-language-server"],
       {"KOTLIN_LANGUAGE_SERVER_OPTS": "${jvmArgs}"})],
     "Download server.zip 1.3.11 von GitHub, JDK 17"),
    ("jdtls", "Eclipse JDT LS (Java)",
     [("jdtls", ["java"], ["python3", "${deps}/jdtls/bin/jdtls", "--jvm-arg=${jvmArg}"])],
     "Download jdt-language-server 1.45.0 von eclipse.org, JDK 17, Python"),
    ("lemminx", "LemMinX (XML)",
     [("lemminx", ["xml"], ["java", "${jvmArg}", "-jar", "${deps}/lemminx/org.eclipse.lemminx-uber.jar"])],
     "Download lemminx-uber.jar 0.31.2 von eclipse.org, JDK 17"),
    # Rust-Server (tree-sitter, ohne JVM/Gradle-Import); Alternative zu Kotlin LS/JDT LS – beide schaltet man in
    # Einstellungen → Sprachserver getrennt ab. Termux-Binary je Architektur aus dem Release termux-2 von scto/kmp-lsp.
    ("kmp-lsp", "kmp-lsp (Kotlin/Java/Swift)",
     # kmp-lsp startet für Bibliotheks-Symbole `java -jar kmp-jar-indexer.jar` (ohne eigene Optionen); der
     # java-Launcher liest JDK_JAVA_OPTIONS – darüber kommen die JVM-Optionen aus den Einstellungen an.
     [("kmp-lsp", ["kotlin", "java", "swift"], ["${deps}/kmp-lsp/kmp-lsp"], {"JDK_JAVA_OPTIONS": "${jvmArgs}"})],
     "Download kmp-lsp termux-2 von GitHub (aarch64/x86_64)"),
]


def _dl(name, url, sha256, archive, check):
    return {"manager": "download", "name": name, "url": url, "sha256": sha256, "archive": archive, "check": check}


def _r(manager, name, check=None):
    d = {"manager": manager, "name": name}
    if check:
        d["check"] = check
    return d


NODE = _r("pkg", "nodejs", "node")
JDK = _r("pkg", "openjdk-17", "java")
# Voraussetzungen je Plugin-Name (Formatierer und Language Server)
REQUIRES = {
    "prettier": [NODE, _r("npm", "prettier")],
    "shfmt": [_r("pkg", "shfmt")],
    "ruff-format": [_r("pkg", "ruff")],
    "gofmt": [_r("pkg", "golang", "gofmt")],
    "rustfmt": [_r("pkg", "rust", "rustfmt")],
    "taplo-format": [_r("pkg", "taplo")],
    "clangd": [_r("pkg", "clang", "clangd")],
    "rust-analyzer": [_r("pkg", "rust-analyzer")],
    "taplo": [_r("pkg", "taplo")],
    "gopls": [_r("pkg", "gopls")],
    "ruff": [_r("pkg", "ruff")],
    "neocmakelsp": [_r("pkg", "neocmakelsp")],
    "dart": [_r("pkg", "dart")],
    "bash-language-server": [NODE, _r("npm", "bash-language-server")],
    "typescript-language-server": [NODE, _r("npm", "typescript", "tsc"), _r("npm", "typescript-language-server")],
    "pyright": [_r("pkg", "python", "python3"), NODE, _r("npm", "pyright", "pyright-langserver")],
    "web-langservers": [NODE, _r("npm", "vscode-langservers-extracted", "vscode-html-language-server")],
    "yaml-language-server": [NODE, _r("npm", "yaml-language-server")],
    "kotlin-language-server": [JDK, _dl(
        "kotlin-language-server", "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.11/server.zip",
        "c65efea1c09d88ddb1fe227d8e2c5dbb3d8dd568453bccbbf2e20dfa2f004dcd", "zip", "server/bin/kotlin-language-server")],
    "jdtls": [JDK, _r("pkg", "python", "python3"), _dl(
        "jdtls", "https://download.eclipse.org/jdtls/milestones/1.45.0/jdt-language-server-1.45.0-202502271238.tar.gz",
        "c09e79e958beb5ce41fb3b5097aec0455298bc88a14c8e41462b57edb2812797", "tar.gz", "bin/jdtls")],
    "kmp-lsp": [
        {**_dl("kmp-lsp", "https://github.com/scto/kmp-lsp/releases/download/termux-2/kmp-lsp-termux-aarch64.tar.gz",
               "c1f5ad310fc90f7f7019c0f8a988b010152a22cc8e59917f2f9a0543a3a9a08a", "tar.gz", "kmp-lsp"),
         "arch": "aarch64", "stripTopDir": True},
        {**_dl("kmp-lsp", "https://github.com/scto/kmp-lsp/releases/download/termux-2/kmp-lsp-termux-x86_64.tar.gz",
               "b0a554fe44dbe07e2ca9c9d4871bb9dc05a9fe0b40ab3aa07b2b1596ed765c1a", "tar.gz", "kmp-lsp"),
         "arch": "x86_64", "stripTopDir": True}],
    "lemminx": [JDK, _dl(
        "lemminx", "https://download.eclipse.org/lemminx/releases/0.31.2/org.eclipse.lemminx-uber.jar",
        "f4fde164e785c635e5f86361dbf4b993bfe2c5f83fdb52891d058b7dfc9bcfc8", "file", "org.eclipse.lemminx-uber.jar")],
}

MAVEN = "https://repo1.maven.org/maven2"
GJF_VERSION, KTFMT_VERSION = "1.37.0", "0.65"


def _bool(key, title, desc, default=True):
    return {"key": key, "type": "boolean", "title": title, "description": desc, "default": str(default).lower()}


def _heap(default):
    return {"key": "heapMb", "type": "number", "title": "JVM-Heap (MB)", "description": "Obergrenze für -Xmx.",
            "default": str(default), "min": 128, "max": 2048}


# JVM-Formatierer mit Einstellungsseite: Schalter über bedingte Platzhalter `${setting:key=WERT?--flag}` (Element entfällt,
# wenn die Bedingung nicht zutrifft). JARs kommen als geprüfter Download aus Maven Central nach ${deps}/<name>.
JVM_FAST = ["-Xmx${setting:heapMb}m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1"]
GJF_EXPORTS = [f"--add-exports=jdk.compiler/com.sun.tools.javac.{p}=ALL-UNNAMED" for p in ("api", "code", "file", "parser", "tree", "util")]
# (name, label, extensions, command, settings, requires, beschreibung)
JVM_FORMATTERS = [
    ("google-java-format", "google-java-format", ["java"],
     ["java", *JVM_FAST, *GJF_EXPORTS, "-jar", "${deps}/google-java-format/google-java-format.jar",
      "--style=${setting:style}", "--max-line-length=${setting:maxLineLength}", "${setting:sortImports=false?--skip-sorting-imports}",
      "${setting:removeUnused=false?--skip-removing-unused-imports}", "${setting:reflowStrings=false?--skip-reflowing-long-strings}",
      "${setting:formatJavadoc=false?--skip-javadoc-formatting}", "${setting:reorderModifiers=false?--skip-reordering-modifiers}",
      "${setting:importsOnly?--fix-imports-only}", "--assume-filename", "${fileName}", "-"],
     [{"key": "style", "type": "choice", "title": "Stil", "default": "google",
       "description": "Google Java Style (2 Leerzeichen) oder AOSP (4 Leerzeichen, Android).",
       "options": [{"value": "google", "label": "Google Java Style"}, {"value": "aosp", "label": "AOSP (Android)"}]},
      {"key": "maxLineLength", "type": "number", "title": "Maximale Zeilenlänge", "description": "--max-line-length (Standard 100).",
       "default": "100", "min": 40, "max": 400},
      _bool("sortImports", "Importe sortieren", "Aus: --skip-sorting-imports"),
      _bool("removeUnused", "Unbenutzte Importe entfernen", "Aus: --skip-removing-unused-imports"),
      _bool("reflowStrings", "Lange Strings umbrechen", "Aus: --skip-reflowing-long-strings"),
      _bool("formatJavadoc", "Javadoc formatieren", "Aus: --skip-javadoc-formatting"),
      _bool("reorderModifiers", "Modifier ordnen", "JLS-Reihenfolge; aus: --skip-reordering-modifiers"),
      _bool("importsOnly", "Nur Importe korrigieren", "--fix-imports-only: sonst nichts umformatieren", False),
      _heap(256)],
     [JDK, _dl("google-java-format", f"{MAVEN}/com/google/googlejavaformat/google-java-format/{GJF_VERSION}/google-java-format-{GJF_VERSION}-all-deps.jar",
               "834b2a0c38cb774953322a84b5ca3f2f40dd3156650b3cd44d3b744345962f7a", "file", "google-java-format.jar")],
     f"Formatiert Java mit google-java-format {GJF_VERSION} (Google- oder AOSP-Stil). Einstellungsseite für alle Optionen der Kommandozeile."),
    ("ktfmt", "ktfmt", ["kt", "kts"],
     ["java", *JVM_FAST, "-jar", "${deps}/ktfmt/ktfmt.jar",
      "${setting:style=kotlinlang?--kotlinlang-style}", "${setting:style=google?--google-style}", "${setting:style=meta?--meta-style}",
      "${setting:removeUnused=false?--do-not-remove-unused-imports}", "${setting:editorconfig?--enable-editorconfig}",
      "--quiet", "--stdin-name=${file}", "-"],
     [{"key": "style", "type": "choice", "title": "Stil", "default": "kotlinlang",
       "description": "Kotlinlang: 4 Leerzeichen (offizielle Konventionen). Google/Meta: 2 Leerzeichen. Zeilenbreite jeweils 100.",
       "options": [{"value": "kotlinlang", "label": "Kotlinlang (4 Leerzeichen)"}, {"value": "google", "label": "Google (2 Leerzeichen)"},
                   {"value": "meta", "label": "Meta (2 Leerzeichen)"}]},
      _bool("removeUnused", "Unbenutzte Importe entfernen", "Aus: --do-not-remove-unused-imports"),
      _bool("editorconfig", ".editorconfig beachten",
            "Zeilenbreite und Einzüge aus der .editorconfig des Projekts: max_line_length, ij_kotlin_indent_size, "
            "ij_kotlin_continuation_indent_size (ktfmt hat dafür keine eigenen Schalter)."),
      _heap(512)],
     [JDK, _dl("ktfmt", f"https://github.com/Kotlin/ktfmt/releases/download/v{KTFMT_VERSION}/ktfmt-{KTFMT_VERSION}-with-dependencies.jar",
               "aba9753b6ab387926179ab7318ba8e0c33dad483ebc72f633e79522265b15214", "file", "ktfmt.jar")],
     f"Formatiert Kotlin mit ktfmt {KTFMT_VERSION} (Kotlinlang-, Google- oder Meta-Stil). Einstellungsseite für Stil, Importe, .editorconfig (Breite/Einzüge) und Speicher."),
]
JVM_FORMATTER_HOST = "3.6.3"  # repo1.maven.org, ${deps} und bedingte Platzhalter in Formatierern

PY = _r("pkg", "python", "python3")
SCRIPT = ["python3", "${pluginDir}/tool.py", "${projectDir}", "${input}"]
MODE_PROMPT = {"label": "Modus", "default": "check", "options": [
    {"value": "check", "label": "Prüfen – nur Bericht, nichts ändern"},
    {"value": "apply", "label": "Anwenden – Änderungen durchführen"}]}
FIX_PROMPT = {"label": "Modus", "default": "check", "options": [
    {"value": "check", "label": "Prüfen – nur Bericht, nichts ändern"},
    {"value": "fix", "label": "Korrigieren – Dateien formatieren"}]}
README_PROMPT = {"label": "Sprache", "default": "de", "options": [
    {"value": "de", "label": "Deutsch"},
    {"value": "en", "label": "English"}]}
LICENSE_PROMPT = {"label": "Lizenz", "default": "mit", "options": [
    {"value": "mit", "label": "MIT"},
    {"value": "bsd2", "label": "BSD 2-Clause"},
    {"value": "bsd3", "label": "BSD 3-Clause"},
    {"value": "isc", "label": "ISC"},
    {"value": "gpl", "label": "GPL-2.0 (GNU General Public License v2)"},
    {"value": "gplv3", "label": "GPL-3.0 (GNU General Public License v3)"},
    {"value": "unlicense", "label": "Unlicense (Public Domain)"}],
    "textLabel": "Name des Urhebers", "textHint": "Max Mustermann"}

# (name, label, beschreibung, tool-id, tool-label, tool-beschreibung, skript, prompt, confirm, requires, timeout)
TOOLS = [
    ("vscode-import", "VS Code Import", "Konvertiert VS-Code-Extensions (.vsix, Ordner, Theme-JSON oder direkt von Open VSX) in ein CodeForge-Plugin: Farbschemata, Icon-Themes, Grammatiken, Snippets und Extension-Packs; mit Bericht über nicht Übertragbares (ZIP in cf-plugin-out/).",
     "import", "VS-Code-Extension importieren", "Aus .vsix, Extension-Ordner, Theme-JSON oder openvsx:<publisher>.<name> ein Plugin erzeugen", "vscode_import.py",
     {"label": "Pfad oder openvsx:<publisher>.<name>", "hint": "z. B. downloads/theme.vsix oder openvsx:dracula-theme.theme-dracula"}, None, [PY, _r("pkg", "librsvg", "rsvg-convert")], 600),
    ("androidx-migrate", "AndroidX-Migration", "Ersetzt android.support.* / android.arch.* durch AndroidX (Imports, Layout-XML, Gradle) und setzt useAndroidX/Jetifier.",
     "migrate", "Nach AndroidX migrieren", "Bericht oder Umstellung der Quellen und Gradle-Dateien", "androidx_migrate.py",
     MODE_PROMPT, "Vor dem Umstellen (apply) bitte committen. Mit check gibt es nur einen Bericht.", [PY], 300),
    ("catalog-migrate", "Version-Catalog-Migration", "Verschiebt hartcodierte Abhängigkeiten aus build.gradle(.kts) in gradle/libs.versions.toml.",
     "migrate", "Nach Version Catalog migrieren", "Bericht oder Umstellung der Abhängigkeiten", "catalog_migrate.py",
     MODE_PROMPT, "Vor dem Umstellen (apply) bitte committen. Mit check gibt es nur einen Bericht.", [PY], 120),
    ("unused-resources", "Unbenutzte Ressourcen", "Findet nicht referenzierte Drawables, Layouts, Strings, Farben und mehr; löscht auf Wunsch Dateien.",
     "scan", "Unbenutzte Ressourcen finden", "Bericht oder Löschen unbenutzter Ressourcen-Dateien", "unused_resources.py",
     MODE_PROMPT, "Dynamische Zugriffe (getIdentifier) werden nicht erkannt. Vor apply bitte committen.", [PY], 300),
    ("format-all", "Alles formatieren", "Formatiert oder prüft das ganze Projekt mit den installierten Formatierern (ktlint, shfmt, ruff, gofmt, rustfmt, taplo, prettier).",
     "run", "Projekt formatieren/prüfen", "Prüfen (Standard) oder Korrigieren", "format_all.py",
     FIX_PROMPT, None, [PY], 600),
    ("gitignore", ".gitignore-Generator", "Ergänzt .gitignore um fehlende Einträge für Android, Kotlin, Node, Python, Rust oder Go.",
     "generate", ".gitignore ergänzen", "Typ(en): android kotlin node python rust go", "gitignore.py",
     {"label": "Typ(en)", "hint": "android node", "default": "android"}, None, [PY], 30),
    ("license", "LICENSE-Generator", "Schreibt eine LICENSE-Datei (MIT, BSD-2, BSD-3, ISC, GPL-2.0, GPL-3.0, Unlicense) – Auswahl per Dropdown. Der GPL-Volltext wird von gnu.org geladen (Internet nötig). Apache-2.0 ist nicht enthalten.",
     "generate", "LICENSE schreiben", "Lizenz im Dropdown wählen und Namen des Urhebers eintragen", "license.py",
     LICENSE_PROMPT, "Eine vorhandene LICENSE-Datei wird überschrieben.", [PY], 30),
    ("readme", "README-Generator", "Erzeugt eine README.md aus dem Projekt (Name, Module, Technik, SDK-Werte, Build-Befehle, Lizenz) auf Deutsch oder Englisch. Eine vorhandene README.md bleibt unangetastet (dann entsteht README.generated.md).",
     "generate", "README.md erstellen", "Sprache wählen; Inhalt aus den Gradle-Dateien abgeleitet", "readme.py",
     README_PROMPT, None, [PY], 30),
    ("agp9-migrate", "AGP-9-Migration", "Stellt ein Android-Projekt auf Android Gradle Plugin 9.0 um: AGP-, Gradle- (9.1) und Kotlin-Version (2.2.10), eingebautes Kotlin (Plugin kotlin-android entfernen, kotlinOptions -> kotlin.compilerOptions), KSP (2.3.6) und Hilt (2.59.2), kapt -> KSP für KSP-fähige Prozessoren (sonst legacy-kapt), buildFeatures.buildConfig, entfernte bzw. Übergangs-Schalter in gradle.properties und DSL (dexOptions, CommonExtension<…>); meldet alte Variant-API, buildConfigField, Paparazzi und andere Handarbeit. Schritte nach Googles Skill agp-9-upgrade.",
     "migrate", "Auf AGP 9 migrieren", "Prüfen (Bericht) oder Anwenden; optional Zielversion, z. B. 9.0.1", "agp9_migrate.py",
     dict(MODE_PROMPT, textLabel="AGP-Version (leer = 9.0.1)", textHint="9.0.1"),
     "Vor dem Umstellen (apply) bitte committen. Mit check gibt es nur einen Bericht.", [PY], 120),
    ("project-clean", "Projekt bereinigen", "Löscht Build-Artefakte (build/, .gradle/, .kotlin/, .cxx/, captures/, *.iml) und zeigt die freigegebene Größe.",
     "clean", "Build-Artefakte löschen", "Bericht oder Löschen (Prüfen/Anwenden)", "project_clean.py",
     MODE_PROMPT, None, [PY], 120),
]


dump = pluginkit.dump_json

FEATURE_PLUGINS = [
    ("com.codeforge.plugin.debugger.feature", "Debugger",
     "Schaltet den eingebauten Debugger frei: Haltepunkte in der Randspalte (mit Bedingung und Log-Meldung), "
     "Haltepunkt-Übersicht, Debug-Leiste mit JVM-, Android- (ADB/JDWP) und DAP-Sitzungen, Variablen, Aufrufstapel und "
     "Ausdrucksauswertung. Ohne dieses Plugin sind alle Debugger-Elemente ausgeblendet; deaktivieren blendet sie wieder aus.",
     ["debugger"]),
]


# Plugins mit geänderter Fassung (Store erkennt Updates an der Version); alle anderen 1.0.0.
VERSIONS = {"com.codeforge.plugin.pyright.lsp": "1.0.1", "com.codeforge.plugin.jvm-pack.bundle": "1.1.0", "com.codeforge.plugin.all-formatters.bundle": "1.1.0", "com.codeforge.plugin.ktfmt.formatter": "1.0.1", "com.codeforge.plugin.google-java-format.formatter": "1.0.1", "com.codeforge.plugin.kmp-lsp.lsp": "1.0.2", "com.codeforge.plugin.vscode-import.tool": "1.1.0"}  # 1.0.1: JVM-Optionen für kmp-jar-indexer; 1.0.2: termux-2 (keine falschen Argument-Warnungen bei Bibliotheks-Klassen)


def manifest(pid, name, desc, contributes):
    return pluginkit.manifest(pid, name, desc, contributes, version=VERSIONS.get(pid, "1.0.0"))


def pack(out, pid, folder):
    pluginkit.pack(folder, out / f"{pid}.zip", "tool-plugins/generate.py")


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    fmt_ids, lsp_ids = [], []
    for name, label, exts, names, cmd, install in FORMATTERS:
        pid = f"com.codeforge.plugin.{name}.formatter"
        fmt_ids.append(pid)
        folder = ROOT / "src" / pid
        item = {"id": name, "label": label, "extensions": exts, "command": cmd, "timeoutSeconds": 20}
        if names:
            item["fileNames"] = names
        dump(folder / "plugin.json", manifest(pid, f"{label} (Formatierer)",
             f"Formatiert {', '.join(exts[:6])}{' …' if len(exts) > 6 else ''} mit {label}. Fehlende Programme installiert die App nach Bestätigung (sonst: {install}).",
             {"formatters": [item], "requires": REQUIRES[name]}))
        pack(out, pid, folder)
    for name, label, exts, command, settings, req, desc in JVM_FORMATTERS:
        pid = f"com.codeforge.plugin.{name}.formatter"
        fmt_ids.append(pid)
        folder = ROOT / "src" / pid
        item = {"id": name, "label": label, "extensions": exts, "command": command, "timeoutSeconds": 60}
        m = manifest(pid, f"{label} (Formatierer)", desc,
                     {"formatters": [item], "requires": req, "settings": {"title": label, "items": settings}})
        m["minHostVersion"] = JVM_FORMATTER_HOST
        dump(folder / "plugin.json", m)
        pack(out, pid, folder)
    for name, label, servers, install in LSP:
        pid = f"com.codeforge.plugin.{name}.lsp"
        lsp_ids.append(pid)
        folder = ROOT / "src" / pid
        items = []
        for sid, langs, cmd, *env in servers:
            item = {"id": sid, "label": label, "languages": langs, "command": cmd}
            if env:
                item["env"] = env[0]
            items.append(item)
        dump(folder / "plugin.json", manifest(pid, f"{label} (Language Server)",
             f"Bindet {label} ein. Fehlende Programme installiert die App nach Bestätigung (sonst: {install}).",
             {"lspServers": items, "requires": REQUIRES[name]}))
        pack(out, pid, folder)
    tool_ids = []
    for name, label, desc, tid, tlabel, tdesc, script, prompt, confirm, req, timeout in TOOLS:
        pid = f"com.codeforge.plugin.{name}.tool"
        tool_ids.append(pid)
        folder = ROOT / "src" / pid
        tool = {"id": tid, "label": tlabel, "description": tdesc, "command": SCRIPT, "timeoutSeconds": timeout}
        if prompt:
            tool["prompt"] = prompt
        if confirm:
            tool["confirm"] = confirm
        m = manifest(pid, f"{label} (Tool)", desc + " Erscheint im PT-Menü des Dateibaums. Führt Skripte aus (Berechtigung shell).",
                     {"tools": [tool], "requires": req})
        m["permissions"] = ["shell"]
        dump(folder / "plugin.json", m)
        (folder / "tool.py").write_text((ROOT / "scripts" / script).read_text(encoding="utf-8"), encoding="utf-8")
        (folder / "common.py").write_text((ROOT / "scripts" / "common.py").read_text(encoding="utf-8"), encoding="utf-8")
        pack(out, pid, folder)
    # App-Funktionen, die erst ein Plugin freischaltet (contributes.features) – Code liegt in der App.
    for pid, name, desc, features in FEATURE_PLUGINS:
        folder = ROOT / "src" / pid
        dump(folder / "plugin.json", manifest(pid, name, desc, {"features": features}))
        pack(out, pid, folder)
    pluginkit.write_zip(out / "codeforge-tool-plugins-alle.zip",
                        [(f"{pid}.zip", (out / f"{pid}.zip").read_bytes(), 0o644) for pid in tool_ids])
    for kind, ids, title in (("formatters", fmt_ids, "Alle Formatierer"), ("lsp-servers", lsp_ids, "Alle Language Server")):
        pid = f"com.codeforge.plugin.all-{kind}.bundle"
        folder = ROOT / "src" / pid
        dump(folder / "plugin.json", manifest(pid, f"{title} (Bundle)", f"{title} von CodeForge als Bundle. Die Programme selbst müssen installiert werden.",
                                               {"extensionPack": ids}))
        pack(out, pid, folder)
    # Sprachpakete wie die „Extension Packs“ von VS Code: Server + Formatierer einer Sprache in einem Schritt.
    for name, title, desc, members in LANGUAGE_PACKS:
        pid = f"com.codeforge.plugin.{name}.bundle"
        folder = ROOT / "src" / pid
        missing = [m for m in members if m not in fmt_ids + lsp_ids]
        assert not missing, f"{pid}: unbekannte Mitglieder {missing}"
        dump(folder / "plugin.json", manifest(pid, f"{title} (Bundle)", desc, {"extensionPack": members}))
        pack(out, pid, folder)


LANGUAGE_PACKS = [
    ("python-pack", "Python", "Python-Unterstützung: Pyright (Typen, Navigation), Ruff (Linting) und Ruff Format.",
     ["com.codeforge.plugin.pyright.lsp", "com.codeforge.plugin.ruff.lsp", "com.codeforge.plugin.ruff-format.formatter"]),
    ("rust-pack", "Rust", "Rust-Unterstützung: rust-analyzer, rustfmt und Taplo für Cargo.toml.",
     ["com.codeforge.plugin.rust-analyzer.lsp", "com.codeforge.plugin.rustfmt.formatter", "com.codeforge.plugin.taplo.lsp"]),
    ("go-pack", "Go", "Go-Unterstützung: gopls und gofmt.",
     ["com.codeforge.plugin.gopls.lsp", "com.codeforge.plugin.gofmt.formatter"]),
    ("web-pack", "Web (TypeScript, JavaScript, HTML, CSS)", "Web-Entwicklung: TypeScript/JavaScript-Server, HTML/CSS/JSON-Server und Prettier.",
     ["com.codeforge.plugin.typescript-language-server.lsp", "com.codeforge.plugin.web-langservers.lsp", "com.codeforge.plugin.prettier.formatter"]),
    ("cpp-pack", "C/C++", "C/C++-Unterstützung: clangd und neocmakelsp für CMake.",
     ["com.codeforge.plugin.clangd.lsp", "com.codeforge.plugin.neocmakelsp.lsp"]),
    ("shell-pack", "Shell", "Shell-Skripte: Bash Language Server und shfmt.",
     ["com.codeforge.plugin.bash-language-server.lsp", "com.codeforge.plugin.shfmt.formatter"]),
    ("config-pack", "Konfigurationsdateien (YAML, TOML, XML)", "YAML-, TOML- (Taplo) und XML-Server (LemMinX) samt Taplo-Formatierer.",
     ["com.codeforge.plugin.yaml-language-server.lsp", "com.codeforge.plugin.taplo.lsp", "com.codeforge.plugin.lemminx.lsp", "com.codeforge.plugin.taplo-format.formatter"]),
    ("jvm-pack", "Kotlin und Java", "Kotlin Language Server, Eclipse JDT LS (Java), LemMinX für Android-XML sowie ktfmt und google-java-format.",
     ["com.codeforge.plugin.kotlin-language-server.lsp", "com.codeforge.plugin.jdtls.lsp", "com.codeforge.plugin.lemminx.lsp",
      "com.codeforge.plugin.ktfmt.formatter", "com.codeforge.plugin.google-java-format.formatter"]),
]


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build")
