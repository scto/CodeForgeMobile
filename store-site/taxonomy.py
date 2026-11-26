"""Kategorienbaum und Schlagwörter des Stores (wie die Kategorien und Tags im VS-Code-Marketplace, aber verschachtelt).

Der Baum ist flach gespeichert: jede Kategorie ist ein Pfad (`languages/jvm/kotlin`), die Eltern ergeben sich aus dem
Pfad. Ein Paket kann in mehreren Kategorien stehen. Pakete können eigene Kategorien und Tags im Manifest angeben
(`categories`, `tags`, ab API 4); sonst leitet der Store sie aus den Beiträgen ab.
"""

TITLES = {
    "themes": ("Themes", "Themes"),
    "themes/dark": ("Dunkel", "Dark"),
    "themes/light": ("Hell", "Light"),
    "themes/terminal": ("Terminal", "Terminal"),
    "icons": ("Icon-Pakete", "Icon packs"),
    "languages": ("Sprachen", "Languages"),
    "languages/jvm": ("JVM", "JVM"),
    "languages/jvm/kotlin": ("Kotlin", "Kotlin"),
    "languages/jvm/java": ("Java", "Java"),
    "languages/jvm/groovy": ("Groovy", "Groovy"),
    "languages/web": ("Web", "Web"),
    "languages/web/javascript": ("JavaScript", "JavaScript"),
    "languages/web/typescript": ("TypeScript", "TypeScript"),
    "languages/web/html": ("HTML", "HTML"),
    "languages/web/css": ("CSS", "CSS"),
    "languages/systems": ("Systemnah", "Systems"),
    "languages/systems/c-cpp": ("C/C++", "C/C++"),
    "languages/systems/rust": ("Rust", "Rust"),
    "languages/systems/zig": ("Zig", "Zig"),
    "languages/systems/cmake": ("CMake", "CMake"),
    "languages/python": ("Python", "Python"),
    "languages/go": ("Go", "Go"),
    "languages/dart": ("Dart/Flutter", "Dart/Flutter"),
    "languages/shell": ("Shell", "Shell"),
    "languages/data": ("Daten und Konfiguration", "Data and config"),
    "languages/data/json": ("JSON", "JSON"),
    "languages/data/yaml": ("YAML", "YAML"),
    "languages/data/toml": ("TOML", "TOML"),
    "languages/data/xml": ("XML", "XML"),
    "languages/data/sql": ("SQL", "SQL"),
    "languages/markup": ("Auszeichnung", "Markup"),
    "languages/markup/markdown": ("Markdown", "Markdown"),
    "languages/other": ("Weitere", "Other"),
    "language-servers": ("Sprachserver", "Language servers"),
    "formatters": ("Formatierer", "Formatters"),
    "snippets": ("Snippets", "Snippets"),
    "grammars": ("Grammatiken", "Grammars"),
    "localization": ("Sprachpakete", "Language packs"),
    "templates": ("Projektvorlagen", "Project templates"),
    "templates/android": ("Android", "Android"),
    "templates/jvm": ("JVM", "JVM"),
    "tools": ("Werkzeuge", "Tools"),
    "tools/project": ("Projekt", "Project"),
    "tools/refactoring": ("Umbau", "Refactoring"),
    "tools/import": ("Import", "Import"),
    "tools/database": ("Datenbanken", "Databases"),
    "tools/http": ("HTTP/APIs", "HTTP/APIs"),
    "tools/text": ("Text", "Text"),
    "tools/debug": ("Debugger", "Debugger"),
    "previews": ("Vorschauen", "Previews"),
    "previews/markdown": ("Markdown", "Markdown"),
    "previews/data": ("Daten", "Data"),
    "build": ("Build", "Build"),
    "build/gradle": ("Gradle", "Gradle"),
    "build/tasks": ("Aufgaben", "Tasks"),
    "testing": ("Tests", "Testing"),
    "testing/python": ("Python", "Python"),
    "bundles": ("Bundles", "Bundles"),
    "bundles/language-packs": ("Sprachpakete", "Language bundles"),
    "bundles/collections": ("Sammlungen", "Collections"),
}

LANG_PATH = {
    "kotlin": "languages/jvm/kotlin", "java": "languages/jvm/java", "groovy": "languages/jvm/groovy",
    "javascript": "languages/web/javascript", "javascriptreact": "languages/web/javascript",
    "typescript": "languages/web/typescript", "typescriptreact": "languages/web/typescript",
    "html": "languages/web/html", "css": "languages/web/css", "scss": "languages/web/css", "less": "languages/web/css",
    "c": "languages/systems/c-cpp", "cpp": "languages/systems/c-cpp", "objective-c": "languages/systems/c-cpp",
    "rust": "languages/systems/rust", "zig": "languages/systems/zig", "cmake": "languages/systems/cmake",
    "python": "languages/python", "go": "languages/go", "dart": "languages/dart",
    "shellscript": "languages/shell", "bash": "languages/shell", "sh": "languages/shell", "dockerfile": "languages/shell",
    "makefile": "languages/shell",
    "json": "languages/data/json", "jsonc": "languages/data/json", "yaml": "languages/data/yaml", "toml": "languages/data/toml",
    "xml": "languages/data/xml", "sql": "languages/data/sql",
    "markdown": "languages/markup/markdown",
}

# Dateiendung → Sprach-ID (für Formatierer, die nur Endungen nennen).
EXT_LANG = {
    "kt": "kotlin", "kts": "kotlin", "java": "java", "js": "javascript", "mjs": "javascript", "jsx": "javascript",
    "ts": "typescript", "tsx": "typescript", "html": "html", "css": "css", "scss": "css", "py": "python", "go": "go",
    "rs": "rust", "sh": "shellscript", "bash": "shellscript", "json": "json", "yaml": "yaml", "yml": "yaml",
    "toml": "toml", "md": "markdown", "c": "c", "cpp": "cpp", "h": "c", "dart": "dart", "xml": "xml",
}


def lang_path(lang_id):
    lid = (lang_id or "").lower()
    return LANG_PATH.get(lid, f"languages/other/{lid}" if lid else None)


def title(path):
    if path in TITLES:
        return TITLES[path]
    last = path.rsplit("/", 1)[-1]
    nice = last.replace("-", " ").title()
    return nice, nice


def derive(manifest, category):
    """(Kategoriepfade, Tags) für ein Paket; Manifest-Angaben haben Vorrang und werden ergänzt."""
    c = manifest.get("contributes", {}) or {}
    pid = manifest["id"]
    cats, tags = [], []

    def add_lang(lid):
        p = lang_path(lid)
        if p and p not in cats:
            cats.append(p)
        if lid:
            tags.append(lid.lower())

    if category == "theme":
        cats.append("themes")
        for t in c.get("colorThemes", []):
            cats.append("themes/dark" if t.get("uiTheme") == "dark" else "themes/light")
            tags.append(t.get("uiTheme", "dark"))
        if c.get("terminalThemes"):
            cats.append("themes/terminal")
            tags += ["terminal"] + [t.get("uiTheme", "dark") for t in c["terminalThemes"]]
        tags.append("theme")
    elif category == "icons":
        cats.append("icons"); tags += ["icons", "file-icons"]
    elif category == "language":
        cats.append("localization")
        for lp in c.get("languagePacks", []):
            tags += ["translation", lp.get("locale", "").lower()]
    elif category == "grammar":
        cats.append("grammars")
        for g in c.get("grammars", []):
            add_lang(g.get("languageId"))
        tags.append("syntax")
    elif category == "snippets":
        cats.append("snippets")
        for s in c.get("snippets", []):
            add_lang(s.get("languageId"))
        tags.append("snippets")
    elif category == "lsp":
        cats.append("language-servers")
        for srv in c.get("lspServers", []):
            for lid in srv.get("languages", []):
                add_lang(lid)
        tags.append("lsp")
    elif category == "formatter":
        cats.append("formatters")
        for f in c.get("formatters", []):
            for ext in f.get("extensions", []):
                lid = EXT_LANG.get(ext.lower())
                if lid:
                    add_lang(lid)
        tags.append("formatter")
    elif category == "template":
        for t in c.get("projectTemplates", []):
            cats.append("templates/jvm" if t.get("android") is False else "templates/android")
            tags += [x.lower() for x in t.get("languages", [])]
        tags += ["template", "compose"] if "compose" in pid else ["template"]
        cats.insert(0, "templates")
    elif category == "tool":
        cats.append("tools")
        name = pid.split(".")[3] if pid.count(".") >= 4 else pid
        if "features" in c and "debugger" in c["features"]:
            cats.append("tools/debug"); tags += ["debugger", "breakpoints", "jvm", "android"]
        elif name in ("vscode-import",):
            cats.append("tools/import"); tags += ["vscode", "import"]
        elif name in ("androidx-migrate", "catalog-migrate", "agp9-migrate", "unused-resources", "format-all"):
            cats.append("tools/refactoring"); tags += ["android", "gradle"] if name != "format-all" else ["format"]
        else:
            cats.append("tools/project")
        tags.append("tool")
    elif category == "bundle":
        cats.append("bundles")
        cats.append("bundles/language-packs" if pid.endswith("-pack.bundle") else "bundles/collections")
        tags.append("bundle")
    elif category == "plugin":
        tags += ["code-plugin", "sandbox"] if manifest.get("sandbox") else ["code-plugin", "sideload"]

    for p in manifest.get("categories", []) or []:
        if p not in cats:
            cats.append(p)
    tags += [t.lower() for t in manifest.get("tags", []) or []]
    # Eindeutig, Reihenfolge behalten
    seen, out_tags = set(), []
    for t in tags:
        t = t.strip()
        if t and t not in seen and len(out_tags) < 12:
            seen.add(t)
            out_tags.append(t)
    return list(dict.fromkeys(cats)), out_tags


def tree(paths):
    """Alle Knoten (inkl. Eltern) als sortierte Liste [{id, title}] – flache Speicherung des Baums."""
    nodes = set()
    for p in paths:
        parts = p.split("/")
        for i in range(1, len(parts) + 1):
            nodes.add("/".join(parts[:i]))
    order = list(TITLES)
    def key(p):
        return (order.index(p) if p in order else len(order), p)
    return [{"id": p, "title": {"de": title(p)[0], "en": title(p)[1]}} for p in sorted(nodes, key=key)]
