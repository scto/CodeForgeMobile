#!/usr/bin/env python3
import io
import os
import sys
import tempfile
import unittest
import zipfile

# check-apt-consistency importieren
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib
check_consistency = importlib.import_module("check-apt-consistency")


class TestDebianVersionCompare(unittest.TestCase):

    def test_version_equality(self):
        self.assertEqual(check_consistency.compare_debian_versions("1.0", "1.0"), 0)
        self.assertEqual(check_consistency.compare_debian_versions("1.0-1", "1.0-1"), 0)
        self.assertEqual(check_consistency.compare_debian_versions("1:1.0", "1:1.0"), 0)

    def test_version_greater_smaller(self):
        self.assertEqual(check_consistency.compare_debian_versions("1.1", "1.0"), 1)
        self.assertEqual(check_consistency.compare_debian_versions("1.0", "1.1"), -1)
        self.assertEqual(check_consistency.compare_debian_versions("1.0-2", "1.0-1"), 1)
        self.assertEqual(check_consistency.compare_debian_versions("2:1.0", "1:2.0"), 1)
        self.assertEqual(check_consistency.compare_debian_versions("1.0~rc1", "1.0"), -1)

    def test_version_relation_operators(self):
        self.assertTrue(check_consistency.check_version_relation("1.2", ">=", "1.0"))
        self.assertTrue(check_consistency.check_version_relation("1.0", "=", "1.0"))
        self.assertTrue(check_consistency.check_version_relation("0.9", "<<", "1.0"))
        self.assertFalse(check_consistency.check_version_relation("0.9", ">=", "1.0"))
        self.assertFalse(check_consistency.check_version_relation("1.2", "<=", "1.0"))


class TestBootstrapConsistency(unittest.TestCase):

    def _create_bootstrap_zip(self, status_text):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("var/lib/dpkg/status", status_text)
        buf.seek(0)
        tmp = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
        tmp.write(buf.read())
        tmp.close()
        return tmp.name

    def test_all_satisfied(self):
        status = """
Package: bash
Version: 5.2
Status: install ok installed
Depends: readline (>= 8.0)

Package: readline
Version: 8.2
Status: install ok installed
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path, max_packages=10)
            self.assertEqual(len(errors), 0)
        finally:
            os.remove(zip_path)

    def test_missing_dependency(self):
        status = """
Package: bash
Version: 5.2
Status: install ok installed
Depends: missing-pkg
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path)
            self.assertEqual(len(errors), 1)
            self.assertIn("missing-pkg", errors[0])
        finally:
            os.remove(zip_path)

    def test_alternative_dependencies(self):
        status = """
Package: myapp
Version: 1.0
Status: install ok installed
Depends: foo | bar

Package: bar
Version: 2.0
Status: install ok installed
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path)
            self.assertEqual(len(errors), 0)
        finally:
            os.remove(zip_path)

    def test_version_condition_violated(self):
        status = """
Package: myapp
Version: 1.0
Status: install ok installed
Depends: libtest (>= 2.0)

Package: libtest
Version: 1.5
Status: install ok installed
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path)
            self.assertEqual(len(errors), 1)
            self.assertIn("libtest (>= 2.0)", errors[0])
        finally:
            os.remove(zip_path)

    def test_provides(self):
        status = """
Package: myapp
Version: 1.0
Status: install ok installed
Depends: default-mta

Package: postfix
Version: 3.8
Status: install ok installed
Provides: default-mta
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path)
            self.assertEqual(len(errors), 0)
        finally:
            os.remove(zip_path)

    def test_pre_depends_and_recommends_ignored(self):
        status = """
Package: myapp
Version: 1.0
Status: install ok installed
Pre-Depends: essential-tool
Recommends: optional-doc

Package: essential-tool
Version: 1.0
Status: install ok installed
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path)
            # Recommends: optional-doc is missing but MUST be ignored
            self.assertEqual(len(errors), 0)
        finally:
            os.remove(zip_path)

    def test_forbidden_list(self):
        status = """
Package: tor
Version: 0.4.8
Status: install ok installed
"""
        zip_path = self._create_bootstrap_zip(status)
        with tempfile.NamedTemporaryFile("w", delete=False) as f:
            f.write("tor\n")
            forbidden_file = f.name
        try:
            errors = check_consistency.run_bootstrap_check(zip_path, forbidden_path=forbidden_file)
            self.assertEqual(len(errors), 1)
            self.assertIn("Verbotsliste", errors[0])
        finally:
            os.remove(zip_path)
            os.remove(forbidden_file)

    def test_case_from_task_45_1(self):
        """Der Fall aus 45.1: dpkg-perl verlangt clang, clang fehlt -> Fehler."""
        status = """
Package: dpkg-perl
Version: 1.22.6-5
Status: install ok installed
Depends: perl, clang, make, dpkg (= 1.22.6-5)

Package: perl
Version: 5.38
Status: install ok installed

Package: make
Version: 4.4
Status: install ok installed

Package: dpkg
Version: 1.22.6-5
Status: install ok installed
"""
        zip_path = self._create_bootstrap_zip(status)
        try:
            errors = check_consistency.run_bootstrap_check(zip_path)
            self.assertEqual(len(errors), 1)
            self.assertIn("dpkg-perl", errors[0])
            self.assertIn("clang", errors[0])
        finally:
            os.remove(zip_path)


class TestRepoConsistency(unittest.TestCase):

    def test_repo_with_large_packages(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            bin_dir = os.path.join(tmpdir, "dists/stable/main/binary-aarch64")
            os.makedirs(bin_dir)

            packages_content = """Package: myapp
Version: 1.0
Architecture: aarch64
Depends: openjdk-17, libc++

Package: libc++
Version: 18.1
Architecture: aarch64
"""
            with open(os.path.join(bin_dir, "Packages"), "w", encoding="utf-8") as f:
                f.write(packages_content)

            # openjdk-17 kommt aus large-packages.txt
            large_pkg_content = "openjdk-17|17.0.10|aarch64|openjdk-17_17.0.10_aarch64.deb|sha|12345|url\n"
            lp_file = os.path.join(tmpdir, "large-packages.txt")
            with open(lp_file, "w", encoding="utf-8") as f:
                f.write(large_pkg_content)

            errors = check_consistency.run_repo_check(tmpdir, large_packages_path=lp_file)
            self.assertEqual(len(errors), 0)

    def test_repo_with_multiple_large_manifests_exact_version(self):
        """Basis-Manifest (gh-pages) + big-new.txt: spätere Einträge gewinnen, '= Version' wird erfüllt."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bin_dir = os.path.join(tmpdir, "dists/stable/main/binary-aarch64")
            os.makedirs(bin_dir)
            with open(os.path.join(bin_dir, "Packages"), "w", encoding="utf-8") as f:
                f.write("Package: openjdk-21-x\nVersion: 21.0.12\nArchitecture: aarch64\n"
                        "Depends: openjdk-21 (= 21.0.12)\n")
            base = os.path.join(tmpdir, "base-large.txt")
            new = os.path.join(tmpdir, "big-new.txt")
            with open(base, "w", encoding="utf-8") as f:
                f.write("openjdk-21|21.0.11|aarch64|a.deb|sha|1|url\n")
            with open(new, "w", encoding="utf-8") as f:
                f.write("openjdk-21|21.0.12|aarch64|b.deb|sha|1|url\n")
            self.assertEqual(check_consistency.run_repo_check(tmpdir, large_packages_path=[base, new]), [])
            # ohne big-new.txt bleibt der Fehler sichtbar
            self.assertEqual(len(check_consistency.run_repo_check(tmpdir, large_packages_path=[base])), 1)

    def test_repo_missing_dependency(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            bin_dir = os.path.join(tmpdir, "dists/stable/main/binary-aarch64")
            os.makedirs(bin_dir)

            packages_content = """Package: myapp
Version: 1.0
Architecture: aarch64
Depends: missing-dep
"""
            with open(os.path.join(bin_dir, "Packages"), "w", encoding="utf-8") as f:
                f.write(packages_content)

            errors = check_consistency.run_repo_check(tmpdir)
            self.assertEqual(len(errors), 1)
            self.assertIn("missing-dep", errors[0])


if __name__ == "__main__":
    unittest.main()
