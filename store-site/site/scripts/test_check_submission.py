"""python3 -m unittest scripts/test_check_submission.py"""
import io
import json
import os
import sys
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_submission as cs  # noqa: E402


def zipped(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, content in files.items():
            z.writestr(name, content if isinstance(content, (bytes, str)) else json.dumps(content))
    return buf.getvalue()


THEME = {"id": "de.ada.theme", "name": "Ada", "version": "1.0.0", "apiVersion": 2, "publisher": "ada",
         "contributes": {"colorThemes": [{"id": "t", "label": "T", "uiTheme": "dark", "path": "themes/t.json"}]}}


class CheckSubmissionTest(unittest.TestCase):
    def test_gültiges_theme(self):
        errors, _, m = cs.check_archive(zipped({"plugin.json": THEME, "themes/t.json": {"colors": {}}}),
                                        "de.ada.theme", "1.0.0")
        self.assertEqual([], errors)
        self.assertEqual("de.ada.theme", m["id"])

    def test_fehlende_datei_und_abweichende_id(self):
        errors, _, _ = cs.check_archive(zipped({"plugin.json": THEME}), "de.ada.other", "1.0.0")
        self.assertTrue(any("fehlende Datei" in e for e in errors))
        self.assertTrue(any("Formular-ID" in e for e in errors))

    def test_code_und_skripte_abgelehnt(self):
        code = dict(THEME, entryPointClass="de.ada.P", contributes={})
        errors, _, _ = cs.check_archive(zipped({"plugin.json": code, "classes.dex": b"x", "run.sh": "echo"}))
        self.assertTrue(any("sandbox" in e for e in errors))
        self.assertTrue(any("run.sh" in e for e in errors))

    def test_sandbox_erlaubt(self):
        code = dict(THEME, entryPointClass="de.ada.P", sandbox=True, apiVersion=4, contributes={})
        errors, _, _ = cs.check_archive(zipped({"plugin.json": code, "classes.dex": b"dex"}))
        self.assertEqual([], errors)

    def test_vorbehaltene_id_api1_und_pfad(self):
        bad = dict(THEME, id="com.codeforge.plugin.x", apiVersion=1)
        errors, _, _ = cs.check_archive(zipped({"plugin.json": bad, "../evil": "x", "themes/t.json": "{}"}))
        self.assertTrue(any("vorbehalten" in e for e in errors))
        self.assertTrue(any("apiVersion" in e for e in errors))
        self.assertTrue(any("Pfad" in e for e in errors))

    def test_update_braucht_höhere_version(self):
        catalog = {"items": [{"id": "de.ada.theme", "version": "1.0.0", "author": "ada"}]}
        files = {"plugin.json": THEME, "themes/t.json": "{}"}
        errors, _, _ = cs.check_archive(zipped(files), catalog=catalog)
        self.assertTrue(any("nicht höher" in e for e in errors))
        newer = dict(THEME, version="1.1.0")
        errors, notes, _ = cs.check_archive(zipped(dict(files, **{"plugin.json": newer})), catalog=catalog)
        self.assertEqual([], errors)
        self.assertTrue(any("Update" in n for n in notes))

    def test_formularfelder(self):
        body = "### Plugin-ID\n\nde.ada.theme\n\n### Version\n\n1.0.0\n\n### Plugin-ZIP\n\n[a.zip](https://github.com/user-attachments/files/123/a.zip)\n"
        self.assertEqual("de.ada.theme", cs.field(body, "Plugin-ID"))
        self.assertEqual(["https://github.com/user-attachments/files/123/a.zip"], cs.ATTACHMENT_RE.findall(body))


if __name__ == "__main__":
    unittest.main()
