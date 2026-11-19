"""Tests für vscode_import.py: python3 -m unittest tool-plugins/scripts/test_vscode_import.py (aus dem Repo-Wurzelordner)."""
import json, os, sys, tempfile, unittest, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vscode_import as vi  # noqa: E402


def make_vsix(path):
    pkg = {
        "name": "zig-lang", "publisher": "acme", "version": "1.2.3", "displayName": "Zig Language",
        "main": "./out/extension.js", "categories": ["Programming Languages", "Snippets"],
        "extensionPack": ["redhat.vscode-yaml", "unknown.thing"],
        "contributes": {
            "languages": [{"id": "zig", "extensions": [".zig", ".ZON"], "filenames": ["build.zig.zon"]}],
            "grammars": [
                {"language": "zig", "scopeName": "source.zig", "path": "./syntaxes/zig.tmLanguage.json"},
                {"scopeName": "markdown.zig.codeblock", "path": "./syntaxes/inj.json", "injectTo": ["text.html.markdown"]},
            ],
            "snippets": [{"language": "zig", "path": "./snippets/zig.json"}],
            "themes": [{"label": "Zig Dark", "uiTheme": "vs-dark", "path": "./themes/dark.json"}],
            "commands": [{"command": "zig.build", "title": "Build"}],
            "debuggers": [{"type": "zig"}],
        },
    }
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("extension/package.json", json.dumps(pkg))
        z.writestr("extension/syntaxes/zig.tmLanguage.json", '{"scopeName": "source.zig", // Kommentar\n "patterns": [],}')
        z.writestr("extension/syntaxes/inj.json", "{}")
        z.writestr("extension/snippets/zig.json", '{\n // Kommentar\n "fn": {"prefix": "fn", "body": ["fn $1() void {", "}"]},\n}')
        z.writestr("extension/themes/dark.json", '{"type": "dark", "colors": {"editor.background": "#101010"}, "tokenColors": []}')


class VscodeImportTest(unittest.TestCase):
    def test_vollständige_extension(self):
        project = tempfile.mkdtemp()
        tmp = tempfile.mkdtemp()
        make_vsix(os.path.join(project, "zig.vsix"))
        pid, zip_path, report_path, report = vi.convert("zig.vsix", project, tmp)
        self.assertEqual("imported.acme.zig-lang", pid)
        with zipfile.ZipFile(zip_path) as z:
            names = set(z.namelist())
            manifest = json.loads(z.read("plugin.json"))
            grammar = json.loads(z.read("grammars/source-zig.tmLanguage.json"))
            snippets = json.loads(z.read(manifest["contributes"]["snippets"][0]["path"]))
        c = manifest["contributes"]
        self.assertEqual(4, manifest["apiVersion"])
        self.assertEqual([{"languageId": "zig", "scopeName": "source.zig", "path": "grammars/source-zig.tmLanguage.json",
                           "extensions": ["zig", "zon", "build.zig.zon"]}], c["grammars"])
        self.assertEqual("source.zig", grammar["scopeName"])          # JSONC → JSON
        self.assertIn("fn", snippets)
        self.assertEqual("zig", c["snippets"][0]["languageId"])
        self.assertEqual(["com.codeforge.plugin.yaml-language-server.lsp"], c["extensionPack"])
        self.assertEqual("dark", c["colorThemes"][0]["uiTheme"])
        self.assertIn("themes/zig-dark.json", names)
        text = "\n".join(report)
        self.assertIn("Injektion", text)
        self.assertIn("`commands`", text)
        self.assertIn("`debuggers`", text)
        self.assertIn("JavaScript-Code", text)
        self.assertIn("unknown.thing", text)
        self.assertTrue(os.path.isfile(report_path))
        self.assertIn("programming-languages", manifest["tags"])

    def test_unsicheres_archiv(self):
        project = tempfile.mkdtemp()
        with zipfile.ZipFile(os.path.join(project, "bad.vsix"), "w") as z:
            z.writestr("../evil.txt", "x")
        with self.assertRaises(SystemExit):
            vi.convert("bad.vsix", project, tempfile.mkdtemp())

    def test_nichts_übertragbar(self):
        project = tempfile.mkdtemp()
        with zipfile.ZipFile(os.path.join(project, "c.vsix"), "w") as z:
            z.writestr("extension/package.json", json.dumps({"name": "c", "publisher": "p", "main": "x.js", "contributes": {"commands": [{}]}}))
        with self.assertRaises(SystemExit) as e:
            vi.convert("c.vsix", project, tempfile.mkdtemp())
        self.assertIn("Nichts Übertragbares", str(e.exception))

    def test_openvsx_format(self):
        with self.assertRaises(SystemExit):
            vi.fetch_openvsx("kein format!", tempfile.mkdtemp())


if __name__ == "__main__":
    unittest.main()
