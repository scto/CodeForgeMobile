"""Tests für scripts/pluginkit.py: reproduzierbare ZIPs, build-info, Ausschlüsse.  python3 -m unittest scripts/test_pluginkit.py"""
import json
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pluginkit  # noqa: E402


class PluginkitTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        src = self.dir / "src"
        pluginkit.dump_json(src / "plugin.json", pluginkit.manifest("a.b", "A", "Test", {"snippets": []}))
        (src / "data").mkdir()
        (src / "data" / "x.txt").write_text("hallo", encoding="utf-8")
        (src / "__pycache__").mkdir()
        (src / "__pycache__" / "m.pyc").write_bytes(b"\0")
        self.src = src
        os.environ.pop("CF_PLUGIN_SIGNING_KEY", None)

    def tearDown(self):
        self.tmp.cleanup()

    def test_reproducible_and_complete(self):
        d1 = pluginkit.pack(self.src, self.dir / "1.zip", "test", quiet=True)
        d2 = pluginkit.pack(self.src, self.dir / "2.zip", "test", quiet=True)
        self.assertEqual(d1, d2)
        names = zipfile.ZipFile(self.dir / "1.zip").namelist()
        self.assertEqual(names, sorted(names))
        self.assertIn("build-info.json", names)
        self.assertIn("data/x.txt", names)
        self.assertFalse(any("__pycache__" in n for n in names))

    def test_manifest_defaults(self):
        m = pluginkit.manifest("a.b", "A", "D", {}, version="1.2.0")
        self.assertEqual((m["apiVersion"], m["publisher"], m["version"], m["permissions"]), (2, "codeforge", "1.2.0", []))

    def test_manifest_only(self):
        pluginkit.pack_manifest_only({"id": "x.y", "name": "X"}, self.dir / "b.zip", "test", source=self.src)
        z = zipfile.ZipFile(self.dir / "b.zip")
        self.assertEqual(json.loads(z.read("plugin.json"))["id"], "x.y")
        self.assertEqual(json.loads(z.read("build-info.json"))["builder"], "test")


if __name__ == "__main__":
    unittest.main()
