#!/usr/bin/env python3
"""Erzeugt die Projektvorlagen für Plugin-Autoren: je Plugin-Art eine Vorlage `plugin-<art>.template`.

Aus den Autoren-Vorlagen unter `templates/<art>/` entsteht ein eigenständiges Gradle-Projekt:
* deklarativ: `plugin/` (Plugin-Dateien), `./gradlew packagePlugin` prüft und packt nach `build/plugin/<id>-<version>.zip`
* Code: Kotlin-Modul mit `libs/codeforge-plugin-sdk.jar` (compileOnly), d8 und Packen über `gradle/code-plugin.gradle.kts`

Plugin-ID = Package des neuen Projekts, Name = Projektname. Platzhalter der Autoren-Vorlage `project-template`
bleiben erhalten (`{{{…}}}`, siehe PluginTemplateGenerator).

Vorher für das SDK (Code-Vorlagen):  siehe scripts/build_plugin_sdk.py
Aufruf:  python3 project-templates/make_plugin_templates.py   (Pillow für die Vorschaubilder)
"""
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
SRC = ROOT / "src"
TEMPLATES = REPO / "templates"
DOCS = "https://github.com/scto/CodeForgeMobile/blob/main/docs/extensions.md"
SUBMIT = "https://github.com/scto/codeforge-store/issues/new?template=submit.yml"
# Dateien, die die Store-Prüfung als Code ablehnt, liegen mit `.tpl` in der Vorlage; der Assistent entfernt die Endung.
CODE_SUFFIXES = (".dex", ".jar", ".so", ".apk", ".class", ".aar", ".sh")
PLACEHOLDERS = ("projectName", "packageName", "packagePath", "packageJni", "minSdk")
VERSION = "1.0.0"

# slug, Quelle (templates/<ordner>), Art, Label DE/EN, Beschreibung DE/EN, Farbe, Kürzel, Store-fähig, Hinweise DE
SPECS = [
    ("color-theme", "color-theme", "declarative", "Plugin · Farbschema", "Plugin · Color scheme",
     "App-Farbschema (Material-3-Rollen) mit TextMate-Editor-Theme", "App color scheme (Material 3 roles) with TextMate editor theme",
     (99, 102, 241), "Aa", True,
     "`plugin/themes/midnight.json` enthält die Farbrollen und verweist auf `themes/syntax/midnight-tm.json` (Editor). "
     "Für Hell und Dunkel zwei Einträge in `colorThemes` anlegen (`uiTheme`: `light`/`dark`); die App wählt passend zum Modus."),
    ("icon-theme", "icon-theme", "declarative", "Plugin · Icon-Theme", "Plugin · Icon theme",
     "Datei- und Ordner-Icons nach Endung, Name und Ordner", "File and folder icons by extension, name and folder",
     (245, 158, 11), "◆", True,
     "`plugin/icons/icon-theme.json` bildet Endungen, Dateinamen und Ordner auf PNG-Dateien ab (empfohlen 48×48 px). "
     "Fehlende Bilder fallen auf das Standard-Icon zurück."),
    ("language-pack", "language-pack", "declarative", "Plugin · Sprachpaket", "Plugin · Language pack",
     "Übersetzung der App-Oberfläche", "Translation of the app UI",
     (16, 185, 129), "文", True,
     "`plugin/l10n/fr.json` überschreibt Texte nach Schlüssel (Namen aus `core/resources/.../strings.xml`). "
     "Nicht übersetzte Schlüssel bleiben in der App-Sprache."),
    ("grammar", "grammar", "declarative", "Plugin · Grammatik", "Plugin · Grammar",
     "TextMate-Grammatik: Syntaxfarben für einen eigenen Dateityp", "TextMate grammar: syntax colors for your own file type",
     (236, 72, 153), "{ }", True,
     "`plugin/grammars/mylang.tmLanguage.json` (TextMate-Format, `scopeName` eindeutig wählen). "
     "Gilt für die in `fileExtensions` genannten Endungen ohne eingebaute Grammatik."),
    ("snippets", "snippets", "declarative", "Plugin · Snippets", "Plugin · Snippets",
     "Code-Bausteine mit Kürzel und Platzhaltern", "Code snippets with prefix and placeholders",
     (14, 165, 233), "✂", True,
     "`plugin/snippets/kotlin.json` im VS-Code-Format (`prefix`, `body`, `$1`/`${1:wert}`)."),
    ("lsp-server", "lsp-server", "declarative", "Plugin · Sprachserver", "Plugin · Language server",
     "Anbindung eines Language Servers (LSP) inklusive Installation", "Language server (LSP) integration including installation",
     (139, 92, 246), "LSP", True,
     "Befehl, Dateiendungen und `requires` (Pakete, die bei Bedarf installiert werden) in `plugin/plugin.json`."),
    ("formatter", "formatter", "declarative", "Plugin · Formatierer", "Plugin · Formatter",
     "Externer Code-Formatierer (stdin → stdout)", "External code formatter (stdin → stdout)",
     (20, 184, 166), "≡", True,
     "Der Befehl bekommt den Text auf stdin und liefert den formatierten Text auf stdout; `${file}` ist der Dateipfad."),
    ("tool", "tool", "declarative", "Plugin · Werkzeug", "Plugin · Tool",
     "Befehl im PT-Menü des Dateibaums (Skript, Eingabe, Zeitlimit)", "Command in the file tree's PT menu (script, input, timeout)",
     (234, 88, 12), ">_", True,
     "Skript `plugin/tool.py` (Python 3, über `requires` installiert); Platzhalter `${pluginDir}`, `${projectDir}`, `${input}`. Braucht `shell`."),
    ("extension-pack", "extension-pack", "declarative", "Plugin · Bundle", "Plugin · Bundle",
     "Paket, das mehrere Plugins gemeinsam installiert", "Pack that installs several plugins together",
     (100, 116, 139), "▦", True,
     "`extensionPack` listet Plugin-IDs aus dem Store; sie werden mit dem Bundle installiert."),
    ("project-template", "project-template", "declarative", "Plugin · Projektvorlage", "Plugin · Project template",
     "Eigene Vorlage für den Projekt-Assistenten", "Your own template for the project wizard",
     (37, 99, 235), "⊞", True,
     "Ebenen unter `plugin/template/` (`common/`, `kotlin/`, `kts/` …); Platzhalter `{{projectName}}`, `{{packageName}}`, "
     "`{{packagePath}}`, `{{minSdk}}` werden beim Erzeugen ersetzt."),
    ("agent-tool", "agent-tool", "declarative", "Plugin · Agent-Werkzeug", "Plugin · Agent tool",
     "Werkzeuge für den KI-Agenten (nur Sideload)", "Tools for the AI agent (sideload only)",
     (217, 70, 239), "AI", False,
     "Skripte unter `plugin/`; `kind` `read` oder `write`. Der Store nimmt Agent-Werkzeuge nicht an – nur Sideload."),
    ("code-sandbox", None, "sandbox", "Plugin · Code (Sandbox)", "Plugin · Code (sandbox)",
     "Kotlin-Plugin mit Editor-Aktion, läuft isoliert – Store-fähig", "Kotlin plugin with an editor action, runs isolated – store-ready",
     (124, 58, 237), "K", True,
     "Läuft mit `\"sandbox\": true` im isolierten Prozess: sieht nur den Text, den die App schickt, und liefert Änderungen "
     "zurück. Kein Datei-, Netz- oder Prozesszugriff."),
    ("code-plugin", None, "code", "Plugin · Code (voll)", "Plugin · Code (full)",
     "Kotlin-Plugin mit Befehl, Menü und Ereignissen (nur Sideload)", "Kotlin plugin with command, menu and events (sideload only)",
     (71, 85, 105), "K+", False,
     "Läuft im App-Prozess und darf nach Berechtigung Dateien lesen, Befehle und Menüs anlegen. Der Store nimmt nur "
     "Sandbox-Plugins an – dieses nur per Sideload."),
]


def escape(text):
    """Platzhalter der Autoren-Vorlage für den Projekt-Assistenten erhalten: {{x}} → {{{x}}}."""
    return re.sub(r"\{\{(" + "|".join(PLACEHOLDERS) + r")\}\}", r"{{{\1}}}", text)


def tpl(name):
    return name + ".tpl" if name.lower().endswith(CODE_SUFFIXES) else name


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def copy_tree(src, dst):
    for f in sorted(src.rglob("*")):
        if not f.is_file() or "build" in f.relative_to(src).parts or f.name == "README.md":
            continue
        target = dst / tpl(escape(str(f.relative_to(src))))
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            write(target, escape(f.read_text(encoding="utf-8")))
        except UnicodeDecodeError:
            shutil.copyfile(f, target)
        if f.stat().st_mode & 0o111:
            target.chmod(0o755)


def own_manifest(manifest, entry=None):
    m = dict(manifest)
    m["id"] = "{{packageName}}"
    m["name"] = "{{projectName}}"
    m["version"] = "1.0.0"
    m["publisher"] = "me"
    if entry:
        m["entryPointClass"] = entry
    return json.dumps(m, ensure_ascii=False, indent=2) + "\n"


def readme(spec, layout):
    slug, _, kind, de, _, desc, _, _, _, store, hints = spec
    hints = escape(hints)
    build = {
        "declarative": "`plugin/` enthält das Plugin (`plugin.json` und Dateien). `./gradlew packagePlugin` prüft JSON und "
                       "Verweise und schreibt `build/plugin/<id>-<version>.zip`.",
        "sandbox": "`src/main/kotlin/…` enthält den Code, `plugin.json` das Manifest. `./gradlew packagePlugin` kompiliert, "
                   "dext (d8, braucht `ANDROID_HOME` mit build-tools) und schreibt `build/plugin/<id>.zip`. "
                   "`libs/codeforge-plugin-sdk.jar` ist die Plugin-API (nur zum Kompilieren).",
    }
    build["code"] = build["sandbox"]
    publish = (f"Veröffentlichen: Issue **„Plugin einreichen“** im Store-Repo öffnen ({SUBMIT}), ZIP anhängen, kurz "
               "beschreiben. Nach der Prüfung erscheint das Plugin im Store; Updates mit höherer `version` genauso einreichen."
               if store else
               "Veröffentlichen: Diese Plugin-Art nimmt der Store nicht an. Weitergeben als ZIP (Sideload).")
    return f"""# {{{{projectName}}}}

{de.replace("Plugin · ", "Plugin: ")} – {desc}.

## Anpassen

* `{layout}`: `id` ist das Package des Projekts (`{{{{packageName}}}}`, weltweit eindeutig halten), `name` der
  Anzeigename, `publisher` dein Name, `description` die Store-Beschreibung. Optional `categories` und `tags` (API 4).
* {hints}
* Referenz aller Felder: {DOCS}

## Bauen und testen

* {build[kind]}
* Im Terminal von CodeForge: `./gradlew packagePlugin`
* Installieren: Einstellungen → Store → Installiert → „+“ → ZIP aus `build/plugin/` wählen. Nach Änderungen
  `version` erhöhen und erneut installieren.

## Veröffentlichen

{publish}
"""


DECL_BUILD = r'''// Packt das Plugin aus plugin/ als installierbares ZIP: ./gradlew packagePlugin → build/plugin/<id>-<version>.zip
import groovy.json.JsonSlurper

val pluginDir = layout.projectDirectory.dir("plugin")
val manifest = JsonSlurper().parse(pluginDir.file("plugin.json").asFile) as Map<*, *>
val pluginId = manifest["id"] as String
val pluginVersion = manifest["version"] as String

val validatePlugin by tasks.registering {
    group = "codeforge plugin"
    description = "Prüft JSON-Dateien und alle in plugin.json genannten Dateien."
    inputs.dir(pluginDir)
    doLast {
        val root = pluginDir.asFile
        root.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            runCatching { JsonSlurper().parse(f) }
                .onFailure { throw GradleException("Ungültiges JSON: ${f.relativeTo(root)}: ${it.message}") }
        }
        val contributes = manifest["contributes"] as? Map<*, *> ?: emptyMap<Any, Any>()
        fun items(key: String) = (contributes[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val files = listOf("colorThemes", "iconThemes", "languagePacks", "grammars", "snippets")
            .flatMap { k -> items(k).mapNotNull { it["path"] as? String } } +
            items("projectTemplates").mapNotNull { it["icon"] as? String } +
            listOf("tools", "agentTools", "formatters").flatMap { k -> items(k) }
                .flatMap { (it["command"] as? List<*>).orEmpty().filterIsInstance<String>() }
                .filter { it.startsWith("\${pluginDir}/") }.map { it.removePrefix("\${pluginDir}/") }
        val dirs = items("projectTemplates").mapNotNull { it["path"] as? String }
        (files.map { it to false } + dirs.map { it to true }).forEach { (rel, dir) ->
            val f = File(root, rel).canonicalFile
            val ok = f.path.startsWith(root.canonicalPath + File.separator) && (if (dir) f.isDirectory else f.isFile)
            if (!ok) throw GradleException("plugin.json verweist auf fehlende Datei: $rel")
        }
        logger.lifecycle("Plugin $pluginId $pluginVersion: in Ordnung")
    }
}

val packagePlugin by tasks.registering(Zip::class) {
    group = "codeforge plugin"
    description = "Baut das installierbare Plugin-ZIP."
    dependsOn(validatePlugin)
    from(pluginDir)
    archiveFileName.set("$pluginId-$pluginVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
'''

CODE_BUILD = r'''import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plugin-API nur zum Kompilieren: zur Laufzeit stellt die App SDK, Kotlin und Coroutines bereit.
dependencies {
    compileOnly(files("libs/codeforge-plugin-sdk.jar"))
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(files("libs/codeforge-plugin-sdk.jar"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Nur Kotlin-APIs nutzen, die die Kotlin-Laufzeit der App kennt.
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

// d8, build-info.json, optionale Signatur (CF_PLUGIN_SIGNING_KEY) → build/plugin/<id>.zip
extra["pluginOutDir"] = "build/plugin"
apply(from = file("gradle/code-plugin.gradle.kts"))
'''

CODE_SETTINGS = '''pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "{{projectName}}"
'''

CODE_CATALOG = '''[versions]
kotlin = "2.3.21"
coroutines = "1.10.2"
junit = "4.13.2"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
'''

SANDBOX_PLUGIN = '''package {{packageName}}

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit

/**
 * Sandbox-Plugin: registriert Editor-Aktionen (Editor → Erweiterungs-Schaltfläche). Läuft im isolierten Prozess,
 * bekommt den Text vom Editor und liefert Änderungen zurück, die die App anwendet.
 */
class MyPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginExtensionPoints.EDITOR_ACTION, Reverse)
        context.register(PluginExtensionPoints.EDITOR_ACTION, CountWords)
    }

    override fun onUnload() = Unit

    /** Kehrt die Auswahl um (ohne Auswahl: Hinweis). */
    private object Reverse : EditorAction {
        override val id = "reverse"
        override val title = "Auswahl umkehren"

        override suspend fun perform(context: EditorContext): List<EditorEffect> {
            if (!context.hasSelection) return listOf(EditorEffect.ShowMessage("Bitte Text markieren"))
            val start = context.selectionStart
            val end = context.selectionEnd
            val reversed = Text.reverse(context.text.substring(start, end))
            return listOf(EditorEffect.ApplyEdits(listOf(TextEdit(start, end, reversed))), EditorEffect.Select(start, end))
        }
    }

    /** Zählt Wörter in der Auswahl bzw. der ganzen Datei. */
    private object CountWords : EditorAction {
        override val id = "count-words"
        override val title = "Wörter zählen"
        override val kind = EditorAction.KIND_SOURCE

        override suspend fun perform(context: EditorContext): List<EditorEffect> {
            val text = if (context.hasSelection) context.selectedText else context.text
            return listOf(EditorEffect.ShowMessage("${Text.words(text)} Wörter"))
        }
    }
}

/** Reine Textfunktionen – ohne SDK testbar (siehe src/test). */
object Text {
    fun reverse(s: String): String = s.reversed()

    fun words(s: String): Int = s.split(Regex("\\\\s+")).count { it.isNotBlank() }
}
'''

SANDBOX_TEST = '''package {{packageName}}

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTest {
    @Test
    fun reverses() = assertEquals("cba", Text.reverse("abc"))

    @Test
    fun countsWords() = assertEquals(3, Text.words("  eins zwei\\n drei "))
}
'''


def code_files(spec, dst):
    slug = spec[0]
    common = dst / "template/common"
    write(common / "settings.gradle.kts", CODE_SETTINGS)
    write(common / "build.gradle.kts", CODE_BUILD)
    write(common / "gradle/libs.versions.toml", CODE_CATALOG)
    write(common / ".gitignore", "build/\n.gradle/\n.kotlin/\n*.iml\nlocal.properties\n")
    script = (REPO / "gradle/code-plugin.gradle.kts").read_text(encoding="utf-8")
    script = script.replace('?: "code-plugins/out/${project.name}"', '?: "build/plugin"')
    script = script.replace('rootProject.file("scripts/plugin-sign.py")', 'rootProject.file("gradle/plugin-sign.py")')
    write(common / "gradle/code-plugin.gradle.kts", escape(script))
    shutil.copyfile(REPO / "scripts/plugin-sign.py", common / "gradle/plugin-sign.py")
    sdk = common / "libs/codeforge-plugin-sdk.jar.tpl"
    sdk.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(REPO / "scripts/build_plugin_sdk.py"), str(sdk)], check=True)
    pkg = common / "src/main/kotlin/{{packagePath}}"
    if slug == "code-sandbox":
        base = json.loads((REPO / "code-plugins/text-tools/plugin.json").read_text(encoding="utf-8"))
        base.update(description="Editor-Aktionen: Auswahl umkehren, Wörter zählen. Läuft in der Sandbox.",
                    permissions=["editor"], categories=["tools/text"], tags=["text", "sandbox"])
        write(common / "plugin.json", own_manifest(base, "{{packageName}}.MyPlugin"))
        write(pkg / "MyPlugin.kt", SANDBOX_PLUGIN)
        write(common / "src/test/kotlin/{{packagePath}}/TextTest.kt", SANDBOX_TEST)
    else:
        base = json.loads((TEMPLATES / "code-plugin/plugin.json").read_text(encoding="utf-8"))
        base["description"] = "Befehl „Zeilen zählen“, Menüeintrag im Dateibaum und Ereignis beim Speichern."
        write(common / "plugin.json", own_manifest(base, "{{packageName}}.MyPlugin"))
        src = (TEMPLATES / "code-plugin/src/main/kotlin/com/example/codeforgeplugin/template/TemplatePlugin.kt").read_text(encoding="utf-8")
        src = src[src.index("package "):]
        src = src.replace("package com.example.codeforgeplugin.template", "package {{packageName}}")
        src = src.replace("class TemplatePlugin", "class MyPlugin")
        src = src.replace("/** Beispiel-Plugin, das als Vorlage für eigene Code-Plugins dient. */",
                          "/** Code-Plugin (API 4): Befehl, Dateibaum-Menüeintrag und Ereignis-Listener. */")
        write(pkg / "MyPlugin.kt", src)


def declarative_files(spec, dst):
    src = TEMPLATES / spec[1]
    common = dst / "template/common"
    copy_tree(src, common / "plugin")
    manifest = json.loads(escape((src / "plugin.json").read_text(encoding="utf-8")))
    write(common / "plugin/plugin.json", own_manifest(manifest))
    write(common / "settings.gradle.kts", 'rootProject.name = "{{projectName}}"\n')
    write(common / "build.gradle.kts", DECL_BUILD)
    write(common / ".gitignore", "build/\n.gradle/\n")


def icon(spec, dst):
    color, mark = spec[7], spec[8]
    W, H, SS = 320, 200, 3
    img = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    light = tuple(int(c + (255 - c) * 0.82) for c in color) + (255,)
    d.rounded_rectangle([20 * SS, 16 * SS, 300 * SS, 184 * SS], radius=22 * SS, fill=light)
    d.rounded_rectangle([110 * SS, 46 * SS, 210 * SS, 146 * SS], radius=24 * SS, fill=color + (255,))
    for font_name in ("DejaVuSans-Bold.ttf", "NotoSansCJK-Bold.ttc"):
        try:
            font = ImageFont.truetype(font_name, (44 if len(mark) <= 2 else 34) * SS)
            break
        except OSError:
            font = ImageFont.load_default(size=40 * SS)
    d.text((160 * SS, 96 * SS), mark, fill=(255, 255, 255, 255), font=font, anchor="mm")
    d.rounded_rectangle([60 * SS, 158 * SS, 260 * SS, 166 * SS], radius=4 * SS, fill=color + (140,))
    img.resize((W, H), Image.LANCZOS).save(dst / "icon.png", optimize=True)


def main():
    for spec in SPECS:
        slug, _, kind, de, en, desc_de, desc_en, _, _, store, _ = spec
        dst = SRC / f"com.codeforge.plugin.plugin-{slug}.template"
        shutil.rmtree(dst, ignore_errors=True)
        dst.mkdir(parents=True)
        if kind == "declarative":
            declarative_files(spec, dst)
            layout = "plugin/plugin.json"
        else:
            code_files(spec, dst)
            layout = "plugin.json"
        write(dst / "template/common/README.md", readme(spec, layout))
        icon(spec, dst)
        manifest = {
            "id": f"com.codeforge.plugin.plugin-{slug}.template",
            "name": f"{de} (Projektvorlage)",
            "version": VERSION,
            "apiVersion": 2,
            "publisher": "codeforge",
            "description": f"Projektvorlage für Plugin-Autoren: {desc_de}. Anpassen, mit ./gradlew packagePlugin bauen, "
                           f"installieren und {'im Store einreichen' if store else 'als ZIP weitergeben'}.",
            "permissions": [],
            "contributes": {"projectTemplates": [{
                "id": f"plugin-{slug}",
                "label": de,
                "description": desc_de,
                "l10n": {"en": {"label": en, "description": desc_en}},
                "path": "template",
                "languages": ["kotlin"],
                "gradleDsl": ["kts"],
                "android": False,
                "icon": "icon.png",
            }]},
        }
        write(dst / "plugin.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        print(dst.name)


if __name__ == "__main__":
    main()
