#!/usr/bin/env python3
"""Tests für .github/scripts/generate-apt-index.py (Task 53.5)."""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
GEN_INDEX_PY = os.path.join(REPO_ROOT, ".github", "scripts", "generate-apt-index.py")


class TestGenerateAptIndex(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp(prefix="cf-test-index-")
        # Struktur für APT-Repo aufbauen
        self.dists_dir = os.path.join(self.tmpdir, "dists", "stable", "main")
        os.makedirs(os.path.join(self.dists_dir, "binary-aarch64"), exist_ok=True)
        os.makedirs(os.path.join(self.dists_dir, "binary-arm"), exist_ok=True)

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_generate_index_includes_large_packages(self):
        # 1. Normale Pakete in Packages
        pkg_content = (
            "Package: jq\n"
            "Version: 1.7.1\n"
            "Architecture: aarch64\n"
            "Filename: pool/main/j/jq/jq_1.7.1_aarch64.deb\n"
            "Size: 345678\n"
            "Description: Command-line JSON processor\n"
            "\n"
        )
        with open(os.path.join(self.dists_dir, "binary-aarch64", "Packages"), "w", encoding="utf-8") as f:
            f.write(pkg_content)
        with open(os.path.join(self.dists_dir, "binary-arm", "Packages"), "w", encoding="utf-8") as f:
            f.write(pkg_content.replace("aarch64", "arm"))

        # 2. Ausgelagerte große Pakete in large-packages.txt
        large_content = (
            "openjdk-17|17.0.20|aarch64|openjdk-17_17.0.20_aarch64.deb|58f94720|96092408|https://github.com/scto/terminal-packages-codeforge/releases/download/large-packages/openjdk-17_17.0.20_aarch64.deb\n"
            "openjdk-17|17.0.20|arm|openjdk-17_17.0.20_arm.deb|833c2914|92374432|https://github.com/scto/terminal-packages-codeforge/releases/download/large-packages/openjdk-17_17.0.20_arm.deb\n"
            "openjdk-21-source|21.0.12|all|openjdk-21-source_21.0.12_all.deb|9483df07|49132232|https://github.com/scto/terminal-packages-codeforge/releases/download/large-packages/openjdk-21-source_21.0.12_all.deb\n"
        )
        with open(os.path.join(self.tmpdir, "large-packages.txt"), "w", encoding="utf-8") as f:
            f.write(large_content)

        # Skript ausführen
        res = subprocess.run(
            [sys.executable, "-I", GEN_INDEX_PY, self.tmpdir, "https://scto.github.io/terminal-packages-codeforge"],
            capture_output=True,
            text=True,
        )
        self.assertEqual(res.returncode, 0, f"Skript fehlgeschlagen: {res.stderr}")

        # index.html prüfen
        index_file = os.path.join(self.tmpdir, "index.html")
        self.assertTrue(os.path.isfile(index_file))
        with open(index_file, "r", encoding="utf-8") as f:
            html = f.read()

        # Reguläres Paket jq vorhanden
        self.assertIn("<b>jq</b>", html)
        self.assertIn("Command-line JSON processor", html)

        # Ausgelagerte Pakete vorhanden und mit Tag "groß" versehen
        self.assertIn("<b>openjdk-17</b> <span class=\"tag\">groß</span>", html)
        self.assertIn("<b>openjdk-21-source</b> <span class=\"tag\">groß</span>", html)
        self.assertIn("https://github.com/scto/terminal-packages-codeforge/releases/download/large-packages/openjdk-17_17.0.20_aarch64.deb", html)
        self.assertIn("91.6 MB", html)  # human-readable Size von openjdk-17


if __name__ == "__main__":
    unittest.main()
